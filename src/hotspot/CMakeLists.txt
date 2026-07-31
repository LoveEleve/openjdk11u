# libjvm.so — OpenJDK 11u Hotspot Virtual Machine
# ~795 .cpp source files, trimmed to Linux x86_64, G1 GC only

# === 1. Include Directories ===
set(HOTSPOT_SHARE  ${CMAKE_SOURCE_DIR}/src/hotspot/share)
set(HOTSPOT_OS     ${CMAKE_SOURCE_DIR}/src/hotspot/os)
set(HOTSPOT_CPU    ${CMAKE_SOURCE_DIR}/src/hotspot/cpu)
set(HOTSPOT_OSCPU  ${CMAKE_SOURCE_DIR}/src/hotspot/os_cpu)
set(HOTSPOT_GENSRC ${CMAKE_SOURCE_DIR}/build/linux-x86_64-normal-server-slowdebug/hotspot/variant-server/gensrc)

include_directories(
    ${HOTSPOT_SHARE}
    ${HOTSPOT_OS}/linux
    ${HOTSPOT_OS}/posix
    ${HOTSPOT_CPU}/x86
    ${HOTSPOT_OSCPU}/linux_x86
    ${HOTSPOT_GENSRC}
    ${HOTSPOT_SHARE}/precompiled
    ${HOTSPOT_SHARE}/include
    ${HOTSPOT_OS}/posix/include
    ${CMAKE_SOURCE_DIR}/build/linux-x86_64-normal-server-slowdebug/support/modules_include/java.base
    ${CMAKE_SOURCE_DIR}/build/linux-x86_64-normal-server-slowdebug/support/modules_include/java.base/linux
    ${CMAKE_SOURCE_DIR}/src/java.base/share/native/libjimage
)

# === 2. Compile Definitions ===
add_compile_definitions(
    DONT_USE_PRECOMPILED_HEADER
    ASSERT _NMT_NOINLINE_                         # slowdebug
    COMPILER1 COMPILER2                           # server variant
    TARGET_ARCH_x86
    INCLUDE_SUFFIX_OS=_linux
    INCLUDE_SUFFIX_CPU=_x86
    INCLUDE_SUFFIX_COMPILER=_gcc
    TARGET_COMPILER_gcc
    AMD64
    HOTSPOT_LIB_ARCH="amd64"
    VM_LITTLE_ENDIAN _LP64=1
    INCLUDE_G1GC=1                                # only GC retained
    INCLUDE_JFR=1 INCLUDE_JVMTI=1                # diagnostics
    INCLUDE_MANAGEMENT=1 INCLUDE_SERVICES=1       # JMX + attach/DCmd
    INCLUDE_CDS=1 INCLUDE_NMT=1                   # CDS + NMT
    INCLUDE_VM_STRUCTS=1 INCLUDE_JNI_CHECK=1      # SA support + debug
    INCLUDE_CMSGC=0 INCLUDE_PARALLELGC=0 INCLUDE_SERIALGC=0
    INCLUDE_EPSILONGC=0 INCLUDE_ZGC=0 INCLUDE_SHENANDOAHGC=0
    INCLUDE_JVMCI=0 INCLUDE_AOT=0
    __STDC_FORMAT_MACROS __STDC_LIMIT_MACROS __STDC_CONSTANT_MACROS
)

# === 3. Source Files ===
# 3a. Shared sources (22 subsystems)
file(GLOB_RECURSE JVM_SHARE_SOURCES 
    ${HOTSPOT_SHARE}/asm/*.cpp
    ${HOTSPOT_SHARE}/c1/*.cpp
    ${HOTSPOT_SHARE}/ci/*.cpp
    ${HOTSPOT_SHARE}/classfile/*.cpp
    ${HOTSPOT_SHARE}/code/*.cpp
    ${HOTSPOT_SHARE}/compiler/*.cpp
    ${HOTSPOT_SHARE}/gc/g1/*.cpp
    ${HOTSPOT_SHARE}/gc/shared/*.cpp
    ${HOTSPOT_SHARE}/interpreter/*.cpp
    ${HOTSPOT_SHARE}/jfr/*/*.cpp
    ${HOTSPOT_SHARE}/jfr/*.cpp
    ${HOTSPOT_SHARE}/logging/*.cpp
    ${HOTSPOT_SHARE}/memory/*/*.cpp
    ${HOTSPOT_SHARE}/memory/*.cpp
    ${HOTSPOT_SHARE}/metaprogramming/*.cpp
    ${HOTSPOT_SHARE}/oops/*.cpp
    ${HOTSPOT_SHARE}/opto/*.cpp
    ${HOTSPOT_SHARE}/prims/*.cpp
    ${HOTSPOT_SHARE}/runtime/*.cpp
    ${HOTSPOT_SHARE}/runtime/flags/*.cpp
    ${HOTSPOT_SHARE}/services/*.cpp
    ${HOTSPOT_SHARE}/utilities/*.cpp
    ${HOTSPOT_SHARE}/libadt/*.cpp
)

# 3b. Platform-specific sources (exclude 32-bit + JVMCI)
file(GLOB JVM_CPU_SOURCES    ${HOTSPOT_CPU}/x86/*.cpp)
file(GLOB JVM_CPU_GC_SOURCES ${HOTSPOT_CPU}/x86/gc/g1/*.cpp ${HOTSPOT_CPU}/x86/gc/shared/*.cpp)
list(FILTER JVM_CPU_SOURCES EXCLUDE REGEX "_32\\.cpp$|x86_32|jvmciCodeInstaller|compiledIC_aot")
# Also filter AOT + JVMCI files from share sources (JVM_EXCLUDE_FILES equivalent)
list(FILTER JVM_SHARE_SOURCES EXCLUDE REGEX "aotCodeHeap\\.cpp$|aotCompiledMethod\\.cpp$|aotLoader\\.cpp$|compiledIC_aot\\.cpp$|compilerRuntime\\.cpp$")
file(GLOB JVM_OS_SOURCES     ${HOTSPOT_OS}/linux/*.cpp ${HOTSPOT_OS}/posix/*.cpp)
file(GLOB JVM_OSCPU_SOURCES  ${HOTSPOT_OSCPU}/linux_x86/*.cpp)

# 3c. Assembly files (copy functions, SpinPause etc.)
file(GLOB JVM_ASM_SOURCES ${HOTSPOT_OSCPU}/linux_x86/linux_x86_64.s)

# 3d. Generated sources (ADLC + JFR + JVMTI)
set(GENSRC_DIR ${HOTSPOT_GENSRC})
file(GLOB JVM_ADLC_SOURCES  ${GENSRC_DIR}/adfiles/*.cpp)
file(GLOB JVM_JFR_SOURCES   ${GENSRC_DIR}/jfrfiles/*.cpp)
file(GLOB JVM_JVMTI_SOURCES ${GENSRC_DIR}/jvmtifiles/*.cpp)

# === 4. Build libjvm.so ===
add_library(jvm SHARED
    ${JVM_SHARE_SOURCES}
    ${JVM_CPU_SOURCES}
    ${JVM_CPU_GC_SOURCES}
    ${JVM_OS_SOURCES}
    ${JVM_OSCPU_SOURCES}
    ${JVM_ASM_SOURCES}
    ${JVM_ADLC_SOURCES}
    ${JVM_JFR_SOURCES}
    ${JVM_JVMTI_SOURCES}
)

# Hotspot-specific compile flags (matching Make spec.gmk exactly)
target_compile_options(jvm PRIVATE
    -g   # debug info (line numbers/types) for gdb/CLion breakpoints
    -std=gnu++98 -fno-rtti -fno-exceptions
    -fno-strict-aliasing -fno-omit-frame-pointer
    -fvisibility=hidden -fcheck-new -fstack-protector
    -fno-delete-null-pointer-checks -fno-lifetime-dse   # critical for JVM correctness
    -DLIBC=gnu -DSUPPORTS_CLOCK_MONOTONIC                 # critical for ABI
    -pipe
    # Warning flags matching Make spec.gmk JVM_CFLAGS:
    -Wall -Wextra -Wformat=2 -Wpointer-arith -Wsign-compare
    -Wunused-function -Wundef -Wunused-value -Woverloaded-virtual -Wreturn-type
    -Wno-format-zero-length -Wtype-limits -Wuninitialized
    -m64
)

# Link flags
target_link_options(jvm PRIVATE
    -Wl,-z,defs -Wl,-z,relro -Wl,-z,now -Wl,-z,noexecstack
    -Wl,-O1 -Wl,--hash-style=both
    -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/jvm_sym.ver
)

# System libraries
target_link_libraries(jvm PRIVATE pthread dl m)

# Version macro for abstract_vm_version.cpp
set_source_files_properties(
    ${HOTSPOT_SHARE}/runtime/abstract_vm_version.cpp
    PROPERTIES COMPILE_FLAGS
    "-DHOTSPOT_VERSION_STRING='\"11.0.32-internal\"' -DVERSION_FEATURE=11 -DVERSION_INTERIM=0 -DVERSION_UPDATE=32 -DVERSION_PATCH=0 -DVERSION_BUILD=0 -DVERSION_STRING='\"11.0.32-internal\"' -DDEBUG_LEVEL='\"slowdebug\"' -DHOTSPOT_VM_DISTRO='\"OpenJDK\"' -DHOTSPOT_BUILD_USER='\"root\"' -DCPU='\"amd64\"'")

# Version macro for arguments.cpp  
set_source_files_properties(
    ${HOTSPOT_SHARE}/runtime/arguments.cpp
    PROPERTIES COMPILE_FLAGS
    "-DHOTSPOT_VERSION_STRING='\"11.0.32-internal\"' -DVERSION_FEATURE=11 -DVERSION_INTERIM=0 -DVERSION_UPDATE=32 -DVERSION_PATCH=0 -DVERSION_BUILD=0 -DVERSION_STRING='\"11.0.32-internal\"' -DDEBUG_LEVEL='\"slowdebug\"' -DHOTSPOT_VM_DISTRO='\"OpenJDK\"' -DHOTSPOT_BUILD_USER='\"root\"' -DCPU='\"amd64\"'")

# Special file options (from JvmOverrideFiles.gmk)
set_source_files_properties(
    ${HOTSPOT_SHARE}/services/vmStructs.cpp
    PROPERTIES COMPILE_FLAGS "-fno-var-tracking-assignments")

set_source_files_properties(
    ${HOTSPOT_CPU}/x86/assembler_x86.cpp
    ${HOTSPOT_SHARE}/gc/shared/cardTableBarrierSetAssembler_x86.cpp
    PROPERTIES COMPILE_FLAGS "-Wno-maybe-uninitialized")

set_source_files_properties(
    ${HOTSPOT_CPU}/x86/interp_masm_x86.cpp
    PROPERTIES COMPILE_FLAGS "-Wno-uninitialized")

set_source_files_properties(
    ${HOTSPOT_SHARE}/runtime/sharedRuntimeTrig.cpp
    ${HOTSPOT_SHARE}/runtime/sharedRuntimeTrans.cpp
    PROPERTIES COMPILE_FLAGS "-DNO_PCH -ffp-contract=off")

set_source_files_properties(
    ${HOTSPOT_SHARE}/runtime/ostream.cpp
    ${HOTSPOT_SHARE}/logging/logFileOutput.cpp
    PROPERTIES COMPILE_FLAGS "-D_FILE_OFFSET_BITS=64")

set_target_properties(jvm PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/build/linux-x86_64-normal-server-slowdebug/jdk/lib/server"
    OUTPUT_NAME jvm
    SUFFIX .so
    CXX_STANDARD 98
    CXX_EXTENSIONS OFF)
