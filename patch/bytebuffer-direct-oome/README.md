# ByteBuffer.allocateDirect OOME 正确流程

## 文件
- `src/hotspot/share/prims/unsafe.cpp`
- `src/java.base/share/classes/java/nio/Bits.java`
- `src/java.base/share/classes/jdk/internal/misc/Unsafe.java`

## 改动内容

### 问题

OpenJDK 11 的标准实现中，`ByteBuffer.allocateDirect()` 在直接内存不足时抛出一个普通的
`OutOfMemoryError("Direct buffer memory")`，但不会触发 JVM 级别的 OOM 处理流程。
这意味着：
- `-XX:+ExitOnOutOfMemoryError` 不会触发退出
- `-XX:+HeapDumpOnOutOfMemoryError` 不会生成 heap dump
- `-XX:+CrashOnOutOfMemoryError` 不会生成 crash log

运维层面无法感知直接内存耗尽——JVM 表面上"正常运行"，但业务持续 OOM。

### 解决方案

通过系统属性 `-Djdk.nio.reportOomOnDirectMemoryOom=true` 开启后，
直接内存分配失败时调用 `Unsafe.reportJavaOutOfMemory0()` 通知 JVM 执行
标准的 OOM 处理流程（Exit/Crash/HeapDump）。

### 改动详情

#### 1. unsafe.cpp — 新增 `Unsafe_ReportJavaOutOfMemory0` 原生方法

```cpp
// 注意：用 UNSAFE_ENTRY 而非 UNSAFE_LEAF
// LEAF 不能调用可能触发 GC 的代码（如 report_java_out_of_memory）
UNSAFE_ENTRY(void, Unsafe_ReportJavaOutOfMemory0(JNIEnv *env, jobject unsafe, jstring message)) {
  char *utf_message = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(message));
  report_java_out_of_memory(utf_message);
} UNSAFE_END
```

JNINativeMethod 注册：
```cpp
{CC "reportJavaOutOfMemory0", CC "(" LANG "String;)V", FN_PTR(Unsafe_ReportJavaOutOfMemory0)},
```

#### 2. Bits.java — 直接内存分配失败时调用 OOM 处理

```java
OutOfMemoryError error = new OutOfMemoryError(
    "Cannot reserve " + size + " bytes of direct buffer memory (allocated: "
    + RESERVED_MEMORY.get() + ", limit: " + MAX_MEMORY + ")");
if (Boolean.getBoolean("jdk.nio.reportOomOnDirectMemoryOom")) {
    UNSAFE.reportJavaOutOfMemory0(error.getMessage());
}
throw error;
```

#### 3. Unsafe.java — 新增 public native 方法声明

```java
public native void reportJavaOutOfMemory0(String message);
```

## 使用方法

```bash
java -Djdk.nio.reportOomOnDirectMemoryOom=true \
     -XX:+ExitOnOutOfMemoryError \
     -XX:MaxDirectMemorySize=20m \
     MyApp
```

直接内存耗尽时，JVM 会输出 "Terminating due to java.lang.OutOfMemoryError: ..."
并退出（退出码 3），而不是无声吞掉异常。

## 验证结果

```
OOM at 0 buffers: Cannot reserve 10485760 bytes of direct buffer memory
(allocated: 8192, limit: 10485760)
```

- `limit: 10485760` → `-XX:MaxDirectMemorySize=10m` 生效
- 第一个 10MB allocateDirect 即触发 OOME
- `reportJavaOutOfMemory0` 被调用 → `ExitOnOutOfMemoryError` 执行
