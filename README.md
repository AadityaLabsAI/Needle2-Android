# Needle 2 Local AI Device Agent

An Android 9+ ARM64 local AI device-agent built around Cactus Compute Needle 2. Designed first for the OPPO A11k (CPH2083).

## What it does

- Runs the Needle 2 model locally on-device.
- Uses Android AccessibilityService for user-enabled UI automation.
- Can read visible screen text, tap visible labels, type text, tap coordinates, swipe, press Back/Home, launch installed apps and open URLs.
- Provides local battery/time tools.
- Hosts an offline playground at `http://127.0.0.1:8765/` that can be opened in Chrome.
- No Termux, Python, cloud API or remote inference required.

## First-time setup

1. Install the signed ARM64 APK.
2. Open the app.
3. Tap **Enable Device Automation**.
4. In Android Accessibility settings, enable **Needle 2 Device Automation**.
5. Return to the app.
6. Tap **Open Playground in Chrome**.
7. Give the agent natural-language commands.

AccessibilityService is explicitly user-enabled. Android controls the service lifecycle and permission; the app cannot silently enable it.

## Architecture

The UI/server process and native Needle engine run separately. The engine process calls the main-process automation bridge only through loopback. This isolates a native engine failure from the main UI as much as practical.

The official Needle 2 model supports structured function calls and is intended for tool calling/device use rather than unrestricted general chat. The app follows the documented `function_calls[].arguments` contract.

## Safety

The agent is limited to declared tools. The system prompt instructs it to ask for confirmation before risky or irreversible actions such as purchases, message sending, deletion or account changes. Android privileged operations still require the relevant OS permission and may not be automatable.

## Build

GitHub Actions downloads the official Cactus ARM64 engine and model at build time, verifies the model SHA-256, builds a signed APK, and verifies the APK contents, native libraries and accessibility-service resource before publishing the release package.

## License

This wrapper project is Apache-2.0 compatible. The Cactus Needle 2 engine/model remain subject to their upstream Apache-2.0 license and notices.
