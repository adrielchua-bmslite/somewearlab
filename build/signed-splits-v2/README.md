# Standalone gateway APK set

These five APKs form one Android split-package installation for the standalone Somewear gateway. Install or re-sign all five together:

- `com.somewearlabs.swtak.plugin.apk` — base package containing `SomewearGatewayProvider`, the application-level Realm bootstrap, retained Somewear runtime, API-v2 workspace invite enrollment, Node telemetry/settings, mesh status, message cancellation, power, and cloud-backed file/image bridges, signature-protected QR scanner, and bound receive-lifetime service.
- `config.arm64_v8a.apk` — ARM64 native libraries.
- `config.en.apk` — English resources.
- `config.fr.apk` — French resources.
- `config.mdpi.apk` — MDPI resources.

Package: `com.somewearlabs.swtak.plugin`

Provider authority: `com.somewearlabs.swtak.plugin.somewear.gateway`

Provider permission: `com.somewearlabs.swtak.plugin.permission.SOMEWEAR_GATEWAY`

The permission uses Android's `signature` protection level. The checked-in APKs are signed consistently with a build-machine debug certificate, but that private key is deliberately not committed. Before handing this package to another developer, use `handover/scripts/resign_gateway.sh` to sign every split with the same keystore used for their SC3 build.

This set is ARM64-only. It is suitable for a compatible physical Android device, not an x86/x86_64 emulator.

See `handover/README.md` before installing. Do not install only the base APK.
