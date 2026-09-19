# Needle2 Android

Needle2 is the Android frontend and portable Rust foundation derived from the Airgorah project.

## Implemented

- Native Android Wi-Fi inventory using supported Android Wi-Fi APIs.
- Device capability detection for Wi-Fi, location, USB host and connected USB devices.
- Portable Rust audit/domain layer in `crates/mobile-core`.
- Passive audit findings and CSV export.
- USB external-adapter architecture that inventories attached devices without assuming unsupported kernel capabilities.
- Debug and release APK builds in GitHub Actions.
- Airgorah's official Linux app icon is fetched from the upstream project during the Android build.

## Platform boundary

The upstream Airgorah application is Linux-only and requires a wireless adapter capable of monitor mode and packet injection. Android does not expose those Linux interfaces to ordinary applications. Needle2 therefore uses Android-native APIs where available and reports unsupported low-level capabilities instead of pretending they work.

Active interference, deauthentication and credential-cracking functionality are not enabled by the Android frontend. Passive inventory and authorized configuration auditing are supported where the platform permits them.

## USB architecture

USB host support is detected and attached devices are inventoried. A future privileged/custom-kernel backend can implement a compatible adapter driver without changing the UI or portable audit layer.

## Reports

The app exports a CSV inventory through Android's document picker. JSON report generation is available in the report layer for future share/export surfaces.
