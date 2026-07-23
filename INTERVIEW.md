# 面试准备指南

## 简历条目

```
TencentKona JDK 11 HotSpot 开发

基于 OpenJDK 11 移植和实现 4 项 JVM 特性，涉及 GC、运行时、可观测性：

• G1 Abortable Mixed GC（JEP 344 backport）— 22 文件 · 807 行
  从 JDK 12 回移植核心 GC 特性：引入 optional region 两档分拣机制，
  Mixed GC 中 old region 按时间预算增量回收、超时中止、未完成 region
  归还 chooser 下次优先处理。G1OptionalCSet 管理 + G1OopStarChunkedList
  推迟扫描。

• JFR Unsafe 内存分配事件 — 5 文件 · 248 行
  字节码插桩拦截 Unsafe.allocateMemory/freeMemory/reallocateMemory，
  产出 3 个新 JFR 事件。修复 JFR metadata 编译链路——源 metadata.xml →
  jfrEventClasses.hpp → 重编 libjvm.so。验证录制 3000+ 事件。

• ByteBuffer.allocateDirect OOME — 3 文件 · 97 行
  打通直接内存 OOM → JVM OOM 广播的完整路径，使 ExitOnOutOfMemoryError
  等 flag 对 native 内存分配生效。通过 -Djdk.nio.reportOomOnDirectMemoryOom
  系统属性控制。

• jmap/jcmd 诊断增强 — 4 文件 + 2 新文件 · 725 行
  jcmd heap dump 支持 gzip 压缩（GZipCompressor + CompressionBackend，
  压缩比 3x），jmap -histo 改造为 G1 多线程并行堆扫描。
```

---

## 自问自答：面试官可能问的

### Q1：G1 Abortable Mixed GC 做了什么？

**答**：JDK 11 G1 的 Mixed GC 把所有 old region 一次性回收，预测不准就会超
MaxGCPauseMillis。JEP 344 的方案是把 old region 分成两档——

在 `finalize_old_part()` 中，按回收效率从 cset_chooser 依次取 region：
- 预测时间 <= 剩余 20%：进入"保证 CSet"，必须回收
- 预测时间 > 剩余 20%：进入 optional 数组，有时间就做

执行时在 `evacuate_optional_collection_set()` 中循环处理 optional region：
- 每轮计算 `time_left = MaxGCPauseMillis - elapsed`
- 拿 `time_left * 0.75` 作为当前批的预算
- 超时 → break 中止，未处理 region 归还 chooser（推回头部，下次优先）

关键在于 root scan 阶段遇到 optional region 的引用不能立刻 evacuate
（可能被中止），也不能直接跳过（下次要重新扫 root）。所以用
`G1OopStarChunkedList` 推迟存储——root scan 时 push 进去，region 确认
被选中后消费，中止时丢弃。

---

### Q2：G1OopStarChunkedList 为什么需要？

**答**：这是 Abortable Mixed GC 的核心数据结构。问题是：root scan 时遇到
指向 optional region 的引用怎么处理？

- 方案 A：立刻 evacuate → region 最后可能被中止，白做
- 方案 B：直接跳过 → region 被选中时需重扫全部 root，太慢
- 方案 C（我的方案）：暂存进 chunked list，region 确认选中后消费

每个 ChunkedList chunk 固定 64 个元素，满了分配新 chunk 链式串联。分四种
类型存储（oop*/narrowOop* × root/非 root），对应 GC 的指针体系。

optional evacuation 时调用 `chunks_do()` 遍历全部推迟指针，对每个调
OopClosure 完成标记和拷贝。

---

### Q3：JFR 事件是怎么加进 JVM 的？

**答**：分两步——Java 层的事件定义 + C++ 层的 JVM 注册。

Java 层：定义 3 个事件类继承 AbstractJDKEvent，写 UnsafeInstrumentor
字节码插桩器（@JIInstrumentationTarget），注册进 JDKEvents.java。

C++ 层（关键难点）：JFR 事件需要在 libjvm.so 编译时就注册。流程是：
源 metadata.xml（src/hotspot/share/jfr/metadata/）
→ GenerateJfrFiles 工具生成 jfrEventClasses.hpp
→ cmake 编译进 libjvm.so

坑在于 metadata.xml 有两份——源码版和 build 下的副本。只改副本不管用，
必须改源文件 + 重生成 .hpp + 重编 libjvm。而且 `make hotspot` 和
cmake 构建不兼容，混用会破坏 java 启动器。必须用单独工具生成 header，
只用 cmake 编译 C++。

验证：用 jcmd 启动录制，程序循环 10000 次 Unsafe.allocateMemory/freeMemory，
jfr 文件确认 3000 个 JavaNativeAllocation + 3000 个 JavaNativeFree 事件。

---

### Q4：ByteBuffer OOME 到底解决了什么？

**答**：OpenJDK 11 中，直接内存（ByteBuffer.allocateDirect）耗尽时抛的
OutOfMemoryError 不会触发 JVM 级的 OOM 处理。也就是说
`-XX:+ExitOnOutOfMemoryError` 和 `-XX:+HeapDumpOnOutOfMemoryError`
这两个运维常用的 flag 对直接内存无效。

解决方式：在 Bits.java 中，直接内存分配失败时如果系统属性
`jdk.nio.reportOomOnDirectMemoryOom=true`，调用
`Unsafe.reportJavaOutOfMemory0()` 通知 JVM。这个方法在 unsafe.cpp 中
调用 `report_java_out_of_memory()`——这是 JVM 的 OOM 广播器，会触发
所有注册的 OOM 处理器（exit/crash/dump）。

改动只有 97 行，3 个文件。但打通了一条原来不存在的路径。

---

### Q5：这个过程中最大的技术挑战是什么？

**答**：JFR metadata 编译链路的调试。

最开始以为 metadata.xml 在 jdk/modules 下的那份是有效的——改了没用。
后来发现源文件在 src/hotspot/share/jfr/metadata/，改了、重新生成 header、
重编 libjvm.so——但 `make hotspot` 破坏了 cmake 构建的 java 启动器。

最终找到正确路径：系统 java 单独运行 GenerateJfrFiles 工具生成 header
→ cmake 重编 libjvm.so → jcmd 验证录制。这个过程让我深刻理解了
OpenJDK 的双构建体系（make 管 Java/cmake 管 C++）不能混用。

---

### Q6：你对 G1 GC 的整体理解？

**答**：G1 把堆分成等大小的 Region（4MB by default），eden/survivor/old/humongous
四种角色动态分配。GC 周期分 young-only → mixed → young-only 循环。
Mixed GC 回收部分 old region，配合并发标记找到高收益 region。

我移植的 Abortable Mixed GC 改的是 mixed 阶段的回收策略——从"全量必须完成"
变成"看时间分批做"。不改 region 管理、不改标记算法、不改 evacuation 机制，
只在 CollectionSet 构建和执行层加了一层 optional/abort 控制。

基本的 G1 流程我理解：并发标记→ 最终标记（remark）→ 清理（cleanup）
→ 根据标记结果选出 old region → mixed GC 回收。我改的部分在这个链路的
最后一步——mixed GC 的执行方式。

---

### Q7：如果让你重新设计这 4 个特性，你会怎么做？

**答**：

G1 Abortable Mixed GC：不改。这个 backport 是标准 JEP 344 实现，
设计本身没问题。

JFR Unsafe 事件：当前用的是字节码插桩（@JIInstrumentationTarget）。
另一种方案是在 unsafe.cpp 的 C++ 层直接嵌入 JFR 事件——代码更简单、
不依赖 metadata，但需要 C++ JFR event class 并且跨模块引用更复杂。
issue4 里有详细的方案对比，最终选择 Java 方案是因为和其他 JFR 事件
（FileForce、SocketRead）模式一致。

ByteBuffer OOME：当前需要手动设系统属性。更好的方案是直接默认开启，
但考虑到兼容性和性能影响，保守地做了 opt-in。

jmap/jcmd 增强：这两个是标准 JDK 补丁的 backport，不需要重新设计。

---

### Q8：你在 team 里是什么角色？

**答**：这 4 个特性中——JFR Unsafe 事件和 ByteBuffer OOME 是两个独立
的较小特性，每个约 100-250 行，完全由我独立完成。G1 Abortable Mixed GC
和 jmap/jcmd 增强是 JDK 标准补丁的回移植——我分析了上游代码、适配到
JDK 11 基线、解决了编译兼容性问题、编写了验证测试。重点是理解设计意图
并保证移植正确性。
