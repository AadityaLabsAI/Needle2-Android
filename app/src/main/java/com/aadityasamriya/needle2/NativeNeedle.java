package com.aadityasamriya.needle2;

public final class NativeNeedle {
    static { System.loadLibrary("needle2_jni"); }
    private NativeNeedle() {}
    public static native int load(byte[] model);
    public static native int init(String system, String toolsJson);
    public static native String complete(String input, int maxTokens);
    public static native void reset();
}
