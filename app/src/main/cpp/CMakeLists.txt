cmake_minimum_required(VERSION 3.22.1)
project(needle2_jni)

set(NEEDLE_DIR ${CMAKE_CURRENT_BINARY_DIR}/needle2)
set(NEEDLE_LIB ${NEEDLE_DIR}/libneedle.a)
set(NEEDLE_HEADER ${NEEDLE_DIR}/needle.h)
set(CACTUS_REF b739205bcda01f71ebf0ba180f75a885d515ad96)

file(MAKE_DIRECTORY ${NEEDLE_DIR})
file(DOWNLOAD
    "https://huggingface.co/Cactus-Compute/needle2/resolve/${CACTUS_REF}/android-arm64/libneedle.a?download=true"
    ${NEEDLE_LIB}
    SHOW_PROGRESS
    TLS_VERIFY ON
    EXPECTED_HASH SHA256=93738ae3a9488cbc3104eb65bf49093683c26eb01b0d257e980e499d1d06a9f4
)
file(DOWNLOAD
    "https://huggingface.co/Cactus-Compute/needle2/resolve/${CACTUS_REF}/android-arm64/needle.h?download=true"
    ${NEEDLE_HEADER}
    TLS_VERIFY ON
)

if(NOT EXISTS ${NEEDLE_LIB} OR NOT EXISTS ${NEEDLE_HEADER})
    message(FATAL_ERROR "Failed to download the official Cactus Needle 2 Android ARM64 engine")
endif()

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES IMPORTED_LOCATION ${NEEDLE_LIB})

add_library(needle2_jni SHARED needle_jni.cpp)
set_target_properties(needle2_jni PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED YES)
target_include_directories(needle2_jni PRIVATE ${NEEDLE_DIR})
target_link_libraries(needle2_jni needle log android c++_shared)
