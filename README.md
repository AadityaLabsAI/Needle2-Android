# Needle 2 Android — Browser Local Agent

This project is intentionally lightweight.

## Architecture

```text
Android APK
  ├─ localhost HTTP bridge (127.0.0.1:8765)
  ├─ Accessibility automation service
  └─ bundled browser assets
        ↓
Chrome
  ├─ Needle 2 WebAssembly engine
  ├─ bundled needle2.cact model
  └─ local tool-calling agent
```

The Android APK does **not** load the Needle native/JNI engine. This avoids device-specific native crashes and keeps Android responsible only for the automation bridge.

## User experience

1. Install the APK.
2. Open it once.
3. Enable **Needle 2 Device Automation** in Android Accessibility settings.
4. Open **Local Agent in Chrome**.
5. Use natural-language device commands locally.

No Termux, Python, cloud AI API, remote inference, or model download is required at runtime.

## Browser inference

Needle 2 officially publishes a WebAssembly browser build (`needle.js` + `needle.wasm`) and accepts the `.cact` model through `needle_load`. The release workflow bundles these files into the APK so the setup is one-install-and-run.

## Safety

The agent uses Android Accessibility only after the user explicitly enables it. Risky or irreversible operations should request confirmation in the browser agent before execution.

## Build

GitHub Actions downloads the official Needle 2 WebAssembly runtime and model, builds the APK, verifies its signature, verifies that the browser runtime/model are present, and fails if Android native libraries are packaged.
