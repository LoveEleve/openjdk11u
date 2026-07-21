# Launcher executables — all share src/java.base/share/native/launcher/main.c
# Each linked against libjli with RPATH $ORIGIN/../lib/jli
# Suffix "_launcher" avoids name collision with .so libraries (java, jimage)

set(LAUNCHER_SRC "${CMAKE_SOURCE_DIR}/src/java.base/share/native/launcher/main.c")
set(LAUNCHER_LIBS jli pthread dl)

function(add_jdk_launcher NAME)
    cmake_parse_arguments(ARG "" "MAIN_CLASS" "" ${ARGN})

    add_executable(${NAME}_launcher ${LAUNCHER_SRC})

    # Common defines for all launchers
    target_compile_definitions(${NAME}_launcher PRIVATE
        PROGNAME="${NAME}"
        LAUNCHER_NAME="openjdk"
        VERSION_STRING="11.0.32-internal")

    if(ARG_MAIN_CLASS)
        # JAVA_ARGS format: { "-J-ms8m", "-m", "module/class" }
        # Must use target_compile_options because braces and quotes confuse CMake's
        # target_compile_definitions parser
        target_compile_options(${NAME}_launcher PRIVATE
            "-DJAVA_ARGS={ \"-J-ms8m\", \"-m\", \"${ARG_MAIN_CLASS}\" }")
    endif()

    target_include_directories(${NAME}_launcher PRIVATE
        ${CMAKE_SOURCE_DIR}/src/java.base/share/native/launcher
        ${CMAKE_SOURCE_DIR}/src/java.base/share/native/libjli
        ${CMAKE_SOURCE_DIR}/src/java.base/unix/native/libjli
    )
    target_link_libraries(${NAME}_launcher PRIVATE ${LAUNCHER_LIBS})
    set_target_properties(${NAME}_launcher PROPERTIES
        OUTPUT_NAME ${NAME}
        INSTALL_RPATH "$ORIGIN/../lib/jli"
        BUILD_WITH_INSTALL_RPATH TRUE
    )
endfunction()

# ==== java.base ====
add_jdk_launcher(java)
add_jdk_launcher(keytool  MAIN_CLASS java.base/sun.security.tools.keytool.Main)
add_jdk_launcher(jexec)
add_jdk_launcher(jspawnhelper)

# ==== jdk.compiler ====
add_jdk_launcher(javac     MAIN_CLASS jdk.compiler/com.sun.tools.javac.Main)
add_jdk_launcher(serialver MAIN_CLASS jdk.compiler/sun.tools.serialver.SerialVer)

# ==== jdk.jartool ====
add_jdk_launcher(jar       MAIN_CLASS jdk.jartool/sun.tools.jar.Main)
add_jdk_launcher(jarsigner MAIN_CLASS jdk.jartool/sun.security.tools.jarsigner.Main)

# ==== jdk.jcmd ====
add_jdk_launcher(jcmd   MAIN_CLASS jdk.jcmd/sun.tools.jcmd.JCmd)
add_jdk_launcher(jps    MAIN_CLASS jdk.jcmd/sun.tools.jps.Jps)
add_jdk_launcher(jstat  MAIN_CLASS jdk.jcmd/sun.tools.jstat.Jstat)
add_jdk_launcher(jinfo  MAIN_CLASS jdk.jcmd/sun.tools.jinfo.JInfo)
add_jdk_launcher(jmap   MAIN_CLASS jdk.jcmd/sun.tools.jmap.JMap)
add_jdk_launcher(jstack MAIN_CLASS jdk.jcmd/sun.tools.jstack.JStack)

# ==== jdk.jlink ====
add_jdk_launcher(jlink  MAIN_CLASS jdk.jlink/jdk.tools.jlink.internal.Main)
add_jdk_launcher(jmod   MAIN_CLASS jdk.jlink/jdk.tools.jmod.Main)
add_jdk_launcher(jimage MAIN_CLASS jdk.jlink/jdk.tools.jimage.Main)

# ==== jdk.jfr ====
add_jdk_launcher(jfr    MAIN_CLASS jdk.jfr/jdk.jfr.internal.tool.Main)

# ==== jdk.hotspot.agent ====
add_jdk_launcher(jhsdb  MAIN_CLASS jdk.hotspot.agent/sun.jvm.hotspot.SALauncher)
