file(GLOB SRC "../share/native/libj2gss/*.c")
add_library(j2gss SHARED ${SRC})
target_include_directories(j2gss PRIVATE ../share/native/libj2gss)
set_target_properties(j2gss PROPERTIES OUTPUT_NAME j2gss)
