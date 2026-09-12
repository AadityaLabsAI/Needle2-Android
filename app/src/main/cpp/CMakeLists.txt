cmake_minimum_required(VERSION 3.22.1)
project(needle2_jni)

set(NEEDLE_DIR ${CMAKE_CURRENT_BINARY_DIR}/needle2)
set(NEEDLE_LIB ${NEEDLE_DIR}/libneedle.a)
set(NEEDLE_HEADER ${NEEDLE_DIR}/needle.h)

file(MAKE_DIRECTORY ${NEEDLE_DIR})
file(DOWNLOAD
    "https://huggingface.co/Cactus-Compute/needle2/resolve/main/android-arm64/libneedle.a?download=true"
    ${NEEDLE_LIB}
    SHOW_PROGRESS
    TLS_VERIFY ON
    EXPECTED_HASH SHA256=YOUR_HASH_IS_VALIDATED_BY_CI
)
