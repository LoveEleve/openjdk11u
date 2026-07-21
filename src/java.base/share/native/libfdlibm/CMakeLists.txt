# libfdlibm — static math library
# Source: src/java.base/share/native/libfdlibm/
# 57 .c files, no platform-specific code

file(GLOB FDLIBM_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/*.c")

add_library(fdlibm STATIC ${FDLIBM_SOURCES})
target_include_directories(fdlibm PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(fdlibm PRIVATE -ffp-contract=off)
set_target_properties(fdlibm PROPERTIES OUTPUT_NAME fdlibm)
