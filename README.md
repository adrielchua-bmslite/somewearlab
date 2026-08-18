# SC3 Somewear Gateway

Prototype Android/Kotlin integration that lets SC3 communicate with a separately installed, headless Somewear gateway without ATAK Core.

```text
SC3 application
  -> Kotlin gateway SDK AAR
  -> signature-protected ContentProvider IPC
  -> standalone Somewear gateway
  -> Bluetooth or USB
  -> Somewear Node
  -> radio or satellite
```

## Repository contents

- `somewear-gateway-sdk/sdk`: typed Kotlin SDK used by SC3.
- `somewear-gateway-sdk/dist`: prebuilt SDK AAR.
- `somewear-gateway-sdk/README.md`: complete API and integration reference.
- `build/signed-splits-v2`: installable standalone gateway APK split set.
- `gateway-patches`: injected `ContentProvider` patch sources.
- `somewear-gateway-sdk/gateway-helper`: readable API-v2 gateway adapter source.
- `handover`: signing, installation, verification scripts, and recipient instructions.

Start with the [SDK/API README](somewear-gateway-sdk/README.md).
For a complete transfer to another developer, follow [the handover guide](handover/README.md).

## Verified status

Gateway API v2 passed a bidirectional two-emulator contract test for initialization, explicit radio-only routing, inbound polling/Flow delivery, message-ID preservation, delivery lookup, and safe rejection of automatic satellite fallback. Gateway v10 also makes the connection observer state-change-only and exposes typed hardware settings plus a guarded factory reset. On Android, an unchanged disconnected state produced exactly one observer emission over 2.5 seconds; every new settings call traversed SDK/IPC safely, the retained settings/reset reflection bridge was verified, and disconnected mutations returned `NOT_CONNECTED` without a crash. A retained `MessagePayload` also passed through the real `RouterPayload` parser into the SDK Flow. The fresh-install path includes a gateway-hosted offline QR scanner, Somewear invite enrollment, remote workspace synchronization, provisioning status, activation, readiness, and non-secret mesh-key status.

Physical Bluetooth/USB connection, live setting acknowledgements/factory reset, and real radio/satellite delivery still require provisioned Somewear Nodes for hardware acceptance testing.

## Artifacts

- Kotlin AAR: `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
- Local-AAR dependency block: `somewear-gateway-sdk/dist/sc3-somewear.gradle.kts`
- SDK AAR SHA-256: `e0ba8e553e24a90587f8514c049ab3a49284284cb522b44913d4babb602f00d4`
- Gateway base APK: `build/signed-splits-v2/com.somewearlabs.swtak.plugin.apk`
- Gateway ABI/configuration splits: the other four APKs in `build/signed-splits-v2/`.

No signing private key is committed. A recipient must re-sign all gateway splits and SC3 with the same controlled prototype certificate before installation.

Re-sign only the prepared APKs in `build/signed-splits-v2`. The original ATAK/Somewear plugin does not contain the complete standalone provider bootstrap and can fail with `lateinit property instanceProvider has not been initialized`. The handover scripts now reject an incorrect or older input set before signing.

The gateway APKs contain vendor-derived Somewear runtime code and are for authorized, controlled prototype testing. They must not be redistributed outside an approved team or used as a substitute for an official Somewear SDK/license.
