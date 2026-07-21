# libverify — bytecode verifier
file(GLOB VERIFY_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/*.c")
add_library(verify SHARED ${VERIFY_SOURCES})
target_include_directories(verify PRIVATE 
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/src/hotspot/share/include
    ${CMAKE_SOURCE_DIR}/src/hotspot/os/posix/include
)
target_link_libraries(verify PRIVATE dl)
set_target_properties(verify PROPERTIES OUTPUT_NAME verify)
