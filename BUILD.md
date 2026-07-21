# OpenJDK 11u 裁剪版 — 构建 & CLion 调试指南

## 这是什么

从 OpenJDK 11u 完整源码裁剪出来的极简 HotSpot JVM 构建项目。只保留 G1 GC + C1/C2 + JFR + JVMTI，砍掉其他 GC / JVMCI / AOT / 多架构支持。用于实验和学习 JVM 修改。

**核心目的**：~800 个 cpp 文件的 HotSpot，改一行十几秒就能增量编译完，改完直接用编译出的 JVM 跑 Java 程序。

---

## 在新机器上从零搭建

### 前提条件

- **OS**：Linux x86_64
- **JDK 11**：作为 boot JDK（编译 OpenJDK 本身的 Java 代码用），需要在机器上安装一个 JDK 11。推荐从 <https://github.com/Tencent/TencentKona-11/releases> 下载 TencentKona-11.0.31，解压到某个目录

```bash
# 安装系统依赖
sudo apt-get install build-essential cmake g++ \
    libasound2-dev libx11-dev libxext-dev libxrender-dev libxrandr-dev \
    libcups2-dev libfontconfig1-dev libfreetype6-dev

# 下载并解压 boot JDK
wget https://github.com/Tencent/TencentKona-11/releases/download/kona11.0.31/TencentKona-11.0.31.b1-jdk_linux-x86_64.tar.gz
tar xzf TencentKona-11.0.31.b1-jdk_linux-x86_64.tar.gz
# 记下解压路径，例如 /home/user/TencentKona-11.0.31.b1
```

### 搭建步骤

```bash
# 1. 克隆
git clone git@github.com:LoveEleve/openjdk11u.git
cd openjdk11u

# 2. configure（生成 build/linux-x86_64-normal-server-slowdebug/ 目录）
#    注意：必须显式指定 boot JDK 11，系统默认 JDK 如果是 17+ 会报错
bash configure \
    --enable-option-checking=fatal \
    --with-jvm-variants=server \
    --with-debug-level=slowdebug \
    --with-boot-jdk=/path/to/TencentKona-11.0.31.b1 \
    --with-native-debug-symbols=none \
    --disable-precompiled-headers \
    --disable-javac-server \
    --with-memory-size=32768

# 3. 编译 Java 基础类 + 生成 JNI headers
make java.base-java

# 4. 从 spec.gmk 提取编译参数，生成 cmake 配置
bash cmake/spec2cmake.sh \
    build/linux-x86_64-normal-server-slowdebug/spec.gmk \
    build/linux-x86_64-normal-server-slowdebug/cmake_config.cmake
```

---

## CLion 配置

### 打开项目

直接用 CLion `Open...` 选 `jdk11u-copy/` 根目录。CLion 会自动识别 `CMakeLists.txt` 并提示配置 CMake。

### CMake Profiles 配置

`Settings → Build, Execution, Deployment → CMake`：

| 字段 | 值 |
|------|-----|
| Name | `Debug` 或 `local` |
| Build type | `Debug` |
| Toolchain | 系统默认（或指定 GCC） |
| Generator | `Unix Makefiles` 或 `Ninja` |
| Build directory | `cmake-build-debug` |
| CMake options | 留空（配置文件在 `CMakeLists.txt` 里通过 `include(cmake_config.cmake)` 加载） |

CLion 会提示 "CMake project is not loaded"，点 **Load CMake Project**。

### 编译

- **菜单**：`Build → Build Project`
- **快捷键**：`Ctrl+F9`（macOS: `Cmd+F9`）
- **命令行等价**：`cd cmake-build-debug && cmake --build . --target jvm -j$(nproc)`

编译产物：`build/linux-x86_64-normal-server-slowdebug/jdk/lib/server/libjvm.so`

**注意**：CLion 的 cmake 构建只会产出 `libjvm.so`。Java 类库和 `java` 启动器是通过第 2~3 步（configure + make）生成的，不需要每次都重编。

### main.c 硬编码参数（开发效率 hack）

`src/java.base/share/native/launcher/main.c` 硬编码了默认 JVM 参数，目的：**不用每次在 CLion 右上角 Configuration 里手动填参数**。

```c
"-Xlog:gc+heap=debug",                 // 默认开 GC 日志
"-Xms8G", "-Xmx8G",                     // 固定 8G 堆
"-cp", "/data/workspace/demo",          // classpath
"HelloWorld",                           // 默认主类
```

**换机器时需要**：把 `-cp` 路径指向新机器上的 class 文件目录。其他参数按需调整。

### 修改代码后的工作流

```
1. 改 C++ 代码 → Build Project（增量编译，十几秒）
2. 运行 Java 程序测试：
   cd build/linux-x86_64-normal-server-slowdebug/jdk/bin
   ./java -XX:+PrintGCDetails -version
3. 需要 GC 日志确认效果
```

---

## 调试 JVM C++ 代码

### 方式一：命令行 GDB（简单快速）

```bash
# 终端 1：启动 Java 程序，让 GDB 接管
cd build/linux-x86_64-normal-server-slowdebug/jdk/bin
gdb --args ./java -XX:+PrintGCDetails TestClass

# GDB 中：
(gdb) break G1CollectedHeap::do_collection_pause_at_safepoint
(gdb) run
# 触发 GC 时会停在断点

# 或者 attach 到已在运行的 Java 进程
gdb -p <pid>
```

### 方式二：CLion Attach to Process（推荐）

1. 先运行 Java 程序（带 `-XX:+PrintGCDetails` 等 flag）
2. CLion 菜单：`Run → Attach to Process...`
3. 选择 `java` 进程
4. 设置断点在 `.cpp` 文件中（如 `g1CollectionSet.cpp`）
5. 触发 GC（比如写个分配大对象的 Java 程序），断点命中

**CLion 需要以 root 运行或配置 ptrace 权限**：
```bash
# 永久生效
sudo sh -c 'echo 0 > /proc/sys/kernel/yama/ptrace_scope'
```

---

## 目录结构

```
jdk11u-copy/
├── CMakeLists.txt                    # 顶层 cmake 配置
├── BUILD.md                          # 这个文件
├── cmake/spec2cmake.sh               # 从 spec.gmk 生成 cmake_config.cmake
│
├── src/
│   ├── hotspot/                      # JVM C++ 源码
│   │   ├── CMakeLists.txt            # HotSpot cmake 配置（~800 个 cpp 文件）
│   │   ├── share/gc/g1/              # ← G1 GC（主要修改区域）
│   │   ├── share/gc/shared/          # GC 共享层
│   │   ├── share/runtime/            # 运行时（线程、锁、参数等）
│   │   ├── share/jfr/                # JFR 事件
│   │   ├── share/oops/               # 对象模型
│   │   ├── cpu/x86/                  # x86 汇编 / 解释器 / C1/C2
│   │   ├── os/linux/                 # Linux 系统调用
│   │   └── os_cpu/linux_x86/         # OS+CPU 联合实现
│   └── java.base/ / jdk.jfr/ / ...   # Java 类库
│
├── build/
│   └── linux-x86_64-normal-server-slowdebug/
│       ├── spec.gmk                  # configure 生成的 Makefile 参数
│       ├── cmake_config.cmake        # spec2cmake.sh 提取的 cmake 参数
│       ├── hotspot/variant-server/   # configure 生成的 JVM 头文件
│       └── jdk/
│           ├── bin/java javac ...    # Java 启动器（configure+make 产物）
│           └── lib/libjvm.so         # ← JVM 动态库（cmake 编译产物）
│
└── make/                             # OpenJDK 原生 Makefile
```

---

## 新增源文件

放进去 → 重新 cmake。不碰 CMakeLists.txt。

```bash
# 在 cmake-build-debug 目录下
cmake .. && cmake --build . --target jvm -j$(nproc)
```

---

## 常见问题

### configure 失败："Could not find a valid Boot JDK"

安装 JDK 11 并用 `--with-boot-jdk=/path/to/jdk11` 显式指定路径。

### 编译报错："fatal error: jni.h: No such file or directory"

说明 `make java.base-java` 没跑完或者 JNI headers 路径不对。检查：
```bash
ls build/linux-x86_64-normal-server-slowdebug/support/modules_include/java.base/jni.h
```

### CLion 无法 attach 到 java 进程

```bash
sudo sh -c 'echo 0 > /proc/sys/kernel/yama/ptrace_scope'
```

### CMake 找不到新加的 cpp 文件

重新 `cmake ..`，因为 cmake 在 configure 时做了一次 glob，之后加的文件不自动发现。

---

## 构建原理

为什么不用 OpenJDK 原生的 `make`？

1. `make` 全量重新分析依赖很慢（每次几分钟）
2. CMake 的增量编译 + `ninja` 可以做到**改一行十几秒出结果**
3. CLion 原生支持 CMake 项目的代码跳转、自动补全、断点调试

构建链路：

```
configure → spec.gmk (Makefile 参数)
  ├→ make java.base-java (生成 JNI headers + Java 类)
  └→ spec2cmake.sh → cmake_config.cmake
                        └→ CMakeLists.txt → file(GLOB) → cmake --build → libjvm.so
```
