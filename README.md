# Needle 2 — Android Life Points Monitor

Needle 2 is a background notification monitor for Android.

## What it does

- Waits a random **8–10 minutes** between screen checks.
- Uses Android AccessibilityService to inspect accessible text from the active app.
- Recognizes the target values **40, 65, 90, 100**.
- Recognizes **Sorry**.
- Treats Life Points/logo/loading text as a waiting state.
- Sends a high-priority notification and plays the device alarm ringtone when a target is detected.
- Runs as a foreground service so Android can keep the monitoring process alive.
- Does **not** click buttons, answer surveys, submit anything, log into an account, or collect rewards.

## Android 9 setup

1. Install the debug APK.
2. Open Needle 2.
3. Tap **Enable Accessibility**.
4. Enable **Needle 2** in Android Accessibility settings.
5. Return to Needle 2 and tap **Start Monitor**.
6. Keep the Life Points app available when you want it monitored.

### Important limitation

AccessibilityService reads the accessibility tree exposed by an app. It is not a general-purpose pixel/OCR engine on Android 9. If the Life Points app renders a value only as pixels and exposes no accessibility text/content description, that value cannot be detected by this implementation without a separate user-authorized screen-capture/OCR flow.

The service deliberately performs no clicks or gesture automation.
