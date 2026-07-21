# java.base native libraries
# Builds: libjli, libjava, libverify, libzip, libjimage, libnet, libnio
#
# Dependency chain:
# libjvm → libverify → libjava → libzip, libjimage, libnet → libnio

set(BASE ${CMAKE_CURRENT_SOURCE_DIR})

# ====================== libjli ======================
file(GLOB JLI_SRC ${BASE}/share/native/libjli/*.c ${BASE}/unix/native/libjli/*.c)
add_library(jli SHARED ${JLI_SRC})
target_include_directories(jli PRIVATE ${BASE}/share/native/libjli ${BASE}/unix/native/libjli ${BASE}/share/native/launcher)
target_link_libraries(jli PRIVATE pthread dl z)
set_target_properties(jli PROPERTIES 
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/build/linux-x86_64-normal-server-slowdebug/jdk/lib/jli"
    OUTPUT_NAME jli)

# ====================== libjava ======================
file(GLOB JAVA_SRC
    ${BASE}/share/native/libjava/*.c
    ${BASE}/unix/native/libjava/*.c
    ${BASE}/linux/native/libjava/*.c
)
list(FILTER JAVA_SRC EXCLUDE REGEX "ProcessHandleImpl_aix|ProcessHandleImpl_solaris")
add_library(java SHARED ${JAVA_SRC})
target_compile_definitions(java PRIVATE VERSION_SPECIFICATION="11" VERSION_FEATURE=11 VERSION_INTERIM=0 VERSION_UPDATE=32 VERSION_PATCH=0 VERSION_BUILD=0 ARCHPROPNAME='"amd64"' LINUX _LITTLE_ENDIAN)
target_include_directories(java PRIVATE
    ${BASE}/share/native/libjava ${BASE}/unix/native/libjava ${BASE}/linux/native/libjava
)
# Match Make build flags for ABI compatibility with libjvm
target_compile_options(java PRIVATE
    -fvisibility=hidden -fno-strict-aliasing
    -fno-delete-null-pointer-checks -fno-lifetime-dse
    -fstack-protector-all --param ssp-buffer-size=1
    -fno-omit-frame-pointer
)
target_link_options(java PRIVATE
    -Wl,-z,defs -Wl,-z,relro -Wl,-z,now -Wl,-z,noexecstack
    -Wl,--hash-style=both
    -Wl,-z,origin -Wl,-rpath,\$ORIGIN -Wl,--disable-new-dtags
    -Wl,--exclude-libs,ALL
)
target_link_libraries(java PRIVATE jvm verify fdlibm dl pthread)
set_target_properties(java PROPERTIES OUTPUT_NAME java)

# ====================== libzip ======================
file(GLOB ZIP_SRC
    ${BASE}/share/native/libzip/*.c
    ${BASE}/share/native/libzip/zlib/*.c
)
add_library(zip SHARED ${ZIP_SRC})
target_include_directories(zip PRIVATE ${BASE}/share/native/libzip ${BASE}/share/native/libzip/zlib)
target_compile_options(zip PRIVATE -UDEBUG)
target_link_libraries(zip PRIVATE java z)
set_target_properties(zip PROPERTIES OUTPUT_NAME zip)

# ====================== libjimage ======================
file(GLOB JIMAGE_SRC
    ${BASE}/share/native/libjimage/*.cpp
    ${BASE}/unix/native/libjimage/*.cpp
)
add_library(jimage SHARED ${JIMAGE_SRC})
target_include_directories(jimage PRIVATE ${BASE}/share/native/libjimage)
target_link_libraries(jimage PRIVATE dl)
set_target_properties(jimage PROPERTIES OUTPUT_NAME jimage)

# ====================== libnet ======================
file(GLOB NET_SRC
    ${BASE}/share/native/libnet/*.c
    ${BASE}/unix/native/libnet/*.c
    ${BASE}/linux/native/libnet/*.c
)
list(FILTER NET_SRC EXCLUDE REGEX "macosx|solaris_close|aix")
add_library(net SHARED ${NET_SRC})
target_include_directories(net PRIVATE
    ${BASE}/share/native/libnet ${BASE}/unix/native/libnet ${BASE}/linux/native/libnet
)
target_link_libraries(net PRIVATE java dl pthread)
set_target_properties(net PROPERTIES OUTPUT_NAME net)

# ====================== libnio ======================
file(GLOB NIO_SRC
    ${BASE}/share/native/libnio/*.c ${BASE}/share/native/libnio/*/*.c
    ${BASE}/unix/native/libnio/*.c ${BASE}/unix/native/libnio/*/*.c
    ${BASE}/linux/native/libnio/*.c
)
list(FILTER NIO_SRC EXCLUDE REGEX "macosx|solaris|aix")
add_library(nio SHARED ${NIO_SRC})
target_include_directories(nio PRIVATE
    ${BASE}/share/native/libnio ${BASE}/share/native/libnio/ch ${BASE}/share/native/libnio/fs
    ${BASE}/unix/native/libnio ${BASE}/unix/native/libnio/ch ${BASE}/unix/native/libnio/fs
    ${BASE}/linux/native/libnio
)
target_link_libraries(nio PRIVATE java net pthread dl)
set_target_properties(nio PROPERTIES OUTPUT_NAME nio)
