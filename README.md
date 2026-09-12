# Needle2-Android

A small Android 9+ application that runs Cactus Compute Needle 2 locally on ARM64 devices.

## Target device

Designed first for the OPPO A11k (CPH2083): Android 9, `arm64-v8a`.

## Architecture

- Official Cactus Needle 2 Android ARM64 static engine (`libneedle.a`)
- Official `needle2.cact` weights (~14 MB)
- Small JNI bridge
- Plain Android Java UI; no Termux, Python, server, or API key
- Model and inference run locally after installation

Needle 2 is specifically a tool-calling / structured-extraction model rather than a general chat model. It returns structured tool calls and can return a final response after tool results are fed back. See the official Cactus documentation for the model contract.

## Build

GitHub Actions downloads the official Cactus ARM64 engine and model at build time, verifies the model SHA-256, and produces a debug APK artifact. The large binary files are intentionally **not committed to this repository**.

Run the `Build Needle 2 Android` workflow from GitHub Actions, or push to `main` to trigger it.

## Current local tools

The demo app declares two safe device tools:

- `get_battery`
- `get_time`

The app executes those tools locally and feeds their JSON results back into Needle 2.

## License

This wrapper project is Apache-2.0 compatible. The Cactus Needle 2 engine/model remain subject to their upstream Apache-2.0 license and notices.
