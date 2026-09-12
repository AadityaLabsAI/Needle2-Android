#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "needle.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Needle2", __VA_ARGS__)

static std::mutex g_mutex;

extern "C" JNIEXPORT jint JNICALL
Java_com_aadityasamriya_needle2_NativeNeedle_load(JNIEnv* env, jclass, jbyteArray model) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!model) return -100;
    const jsize size = env->GetArrayLength(model);
    std::vector<unsigned char> bytes(static_cast<size_t>(size));
    env->GetByteArrayRegion(model, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
    return needle_load(bytes.data(), static_cast<uint64_t>(bytes.size()));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aadityasamriya_needle2_NativeNeedle_init(JNIEnv* env, jclass,
                                                  jstring system, jstring tools) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* systemChars = system ? env->GetStringUTFChars(system, nullptr) : nullptr;
    const char* toolsChars = tools ? env->GetStringUTFChars(tools, nullptr) : nullptr;
    const int rc = needle_init(systemChars, toolsChars, nullptr);
    if (systemChars) env->ReleaseStringUTFChars(system, systemChars);
    if (toolsChars) env->ReleaseStringUTFChars(tools, toolsChars);
    return rc;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aadityasamriya_needle2_NativeNeedle_complete(JNIEnv* env, jclass,
                                                       jstring input, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!input) return env->NewStringUTF("{\"error\":\"empty input\"}");
    const char* inputChars = env->GetStringUTFChars(input, nullptr);
    std::vector<char> output(1 << 20);
    const int rc = needle_complete(inputChars, maxTokens, output.data(), static_cast<int>(output.size()));
    env->ReleaseStringUTFChars(input, inputChars);
    if (rc < 0) {
        std::string error = "{\"error\":\"needle_complete failed: " + std::to_string(rc) + "\"}";
        return env->NewStringUTF(error.c_str());
    }
    output.back() = '\0';
    return env->NewStringUTF(output.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_aadityasamriya_needle2_NativeNeedle_reset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    needle_reset();
}
