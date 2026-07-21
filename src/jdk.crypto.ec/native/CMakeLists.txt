file(GLOB SRC "../share/native/libsunec/*.cpp" "../share/native/libsunec/impl/*.cpp")
add_library(sunec SHARED ${SRC})
target_include_directories(sunec PRIVATE ../share/native/libsunec ../share/native/libsunec/impl)
set_target_properties(sunec PROPERTIES OUTPUT_NAME sunec)
