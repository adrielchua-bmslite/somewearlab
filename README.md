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

Gateway API v2 passed a bidirectional two-emulator contract test for initialization, explicit radio-only routing, inbound polling/Flow delivery, message-ID preservation, delivery lookup, and safe rejection of automatic satellite fallback. Gateway v12 measures the real encoded package and carries oversized content as checksummed ordinary Radio messages. It also assigns each fragment a persisted, distinct whole-second timestamp so the retained receiver cannot misclassify fragment three onward as duplicate chat messages. Both emulators queued a 932-byte payload as eight Radio-only fragments with distinct retained-core trace IDs and reassembled the JSON through the real `MessagePayload`/`RouterPayload` receive parser in normal and reverse order. A 60,000-byte payload queued 469 fragments without a gateway/core crash, and the timestamp allocation continued without reuse after process restart. The connection observer is state-change-only, hardware settings and guarded factory reset are exposed, and the fresh-install path includes gateway-hosted offline QR scanning, workspace enrollment/synchronization, provisioning status, activation, readiness, and non-secret mesh-key status. The current API also exposes Node/satellite telemetry, mesh topology and readable Node-to-Node signal quality, message cancellation, power commands, and cloud-backed file/image send, receive, and download. Native 25 MB and 50 MB image metadata records were received as Radio payloads on two Android runtimes without placing image bytes in Binder.

Physical Bluetooth/USB connection, live setting acknowledgements/factory reset, and post-v12 peer-radio delivery still require provisioned Somewear Nodes for hardware acceptance testing.

## Artifacts

- Kotlin AAR: `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
- Local-AAR dependency block: `somewear-gateway-sdk/dist/sc3-somewear.gradle.kts`
- SDK AAR SHA-256: `229d4b42c536e221290ca308f514100ba1f8df82a0441e307f50a56ef2c3cb87`
- Gateway base APK: `build/signed-splits-v2/com.somewearlabs.swtak.plugin.apk`
- Gateway ABI/configuration splits: the other four APKs in `build/signed-splits-v2/`.

No signing private key is committed. A recipient must re-sign all gateway splits and SC3 with the same controlled prototype certificate before installation.

Re-sign only the prepared APKs in `build/signed-splits-v2`. The original ATAK/Somewear plugin does not contain the complete standalone provider bootstrap and can fail with `lateinit property instanceProvider has not been initialized`. The handover scripts now reject an incorrect or older input set before signing.

The gateway APKs contain vendor-derived Somewear runtime code and are for authorized, controlled prototype testing. They must not be redistributed outside an approved team or used as a substitute for an official Somewear SDK/license.
