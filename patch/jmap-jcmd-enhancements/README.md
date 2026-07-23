# jmap/jcmd 诊断工具增强

## 功能 1：gzipped heap dump（JDK-8237354）

```bash
# 原来：未压缩，大堆可能几十 GB
jcmd <pid> GC.heap_dump /tmp/dump.hprof

# 现在：直接输出 gzip 压缩
jcmd <pid> GC.heap_dump -gz=1 /tmp/dump.hprof
```

验证：2MB dump 压缩后 680KB（3x 压缩比）。

## 功能 2：并行 jmap -histo（JDK-8215624）

```bash
# 大堆场景下，多线程并行扫堆，速度翻倍
jmap -histo <pid>
```

## 文件改动

gzipped heap dump（+706 行新文件）：
- heapDumperCompression.hpp/cpp（新）：GZipCompressor + CompressionBackend
- heapDumper.cpp/hpp：DumpWriter 改用 CompressionBackend
- diagnosticCommand.cpp/hpp：-gz 参数解析

并行 histo：
- collectedHeap.hpp/cpp：parallel_object_iterator + run_task
- g1CollectedHeap.hpp/cpp：G1ParallelObjectIterator
- heapInspection.cpp：并行迭代核心逻辑
- attachListener.cpp：jmap 触发入口
- JMap.java：命令行适配

## 验证结果

- 压缩 dump：✅ 2MB → 680KB
- jmap -histo：✅ 正常输出 histogram 统计
