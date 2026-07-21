# libjsig — Signal Chaining Library
file(GLOB JSIG_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/*.c")
add_library(jsig SHARED ${JSIG_SOURCES})
target_include_directories(jsig PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(jsig PRIVATE dl)
set_target_properties(jsig PROPERTIES OUTPUT_NAME jsig)
