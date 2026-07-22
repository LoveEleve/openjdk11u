# JFR Unsafe 内存分配事件

## 文件
- `src/jdk.jfr/share/classes/jdk/jfr/events/JavaNativeAllocationEvent.java` (新)
- `src/jdk.jfr/share/classes/jdk/jfr/events/JavaNativeFreeEvent.java` (新)
- `src/jdk.jfr/share/classes/jdk/jfr/events/JavaNativeReallocateEvent.java` (新)
- `src/jdk.jfr/share/classes/jdk/jfr/internal/instrument/UnsafeInstrumentor.java` (新)
- `src/jdk.jfr/share/classes/jdk/jfr/internal/instrument/JDKEvents.java` (修改)

## 问题

JFR 标准事件只追踪 Java 堆内的对象分配。通过 `Unsafe.allocateMemory()` 分配的
native 内存（JNI 直接内存）完全不在 JFR 的视野内。NMT (Native Memory Tracking)
可以追踪但需要重启开启，且对性能有影响。

## 解决方案

通过 JFR 字节码插桩机制，拦截 `jdk.internal.misc.Unsafe` 的三个方法，
在调用前后插入 JFR 事件记录：

| 拦截方法 | JFR 事件 | 记录字段 |
|---------|---------|---------|
| `allocateMemory(bytes)` | JavaNativeAllocation | addr, allocationSize |
| `freeMemory(address)` | JavaNativeFree | addr |
| `reallocateMemory(address, bytes)` | JavaNativeReallocate | freeAddr, allocAddr, allocationSize |

## 核心机制：JFR Bytecode Instrumentation

```
1. JVM 启动时加载 JDKEvents.java
2. 遍历 instrumentationClasses 数组
3. 对每个 @JIInstrumentationTarget 标注的类：
   - 找到字节码中对应的方法
   - 插入 JFR 事件记录代码（begin/end/commit）
4. 运行时：每次调用 Unsafe.allocateMemory → 自动触发 JFR 事件
```

## UnsafeInstrumentor 示例

```java
@JIInstrumentationTarget("jdk.internal.misc.Unsafe")
final class UnsafeInstrumentor {
    @JIInstrumentationMethod
    public long allocateMemory(long bytes) {
        JavaNativeAllocationEvent event = ...;
        if (!event.isEnabled()) return allocateMemory(bytes);  // JFR 未开启时零开销
        event.begin();
        long addr = allocateMemory(bytes);
        event.end();
        if (event.shouldCommit()) { event.addr = addr; event.commit(); }
        return addr;
    }
}
```

## 使用方式

```bash
# 启动 JFR 录制（包含 native 分配事件）
java -XX:StartFlightRecording=filename=rec.jfr MyApp

# 分析
jfr print --events JavaNativeAllocation,JavaNativeFree rec.jfr
```
