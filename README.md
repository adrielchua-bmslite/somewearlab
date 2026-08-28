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

Gateway API v2 passed bidirectional Android contract tests for initialization, explicit routing, inbound polling/Flow delivery, message-ID preservation, delivery lookup, cancellation, and controlled Radio-to-Satellite handover. Gateway v17 adds durable receiver-requested Radio fragment recovery, peer completion acknowledgement, and revisioned variable-size file-batch manifests with exact file IDs/counts/hashes. On two Android runtimes, a deliberately missing fragment was completed from the private journal after volatile assembly state was cleared, and a 17-file manifest round-tripped exactly. Gateway v17 retains the v15 native Satellite correction: oversized Satellite sends remain one parent `MessagePayload`, allowing the retained Somewear `CompositePackager` and `PostOffice` to split and reassemble native `Part` records. Every Satellite send requires the retained backhaul acknowledgement flag, so Node handoff alone is not reported as sufficient. SC3 framing remains enabled only for Radio, where the retained core explicitly does not accept composite children. Legacy v14 Satellite frames can still be received during handover. The v17 API also exposes the authenticated workspace file catalogue and SDK-owned selective recovery/cache, alongside Node/satellite telemetry, mesh topology and signal quality, hardware settings, workspace QR enrollment, file/image transfer, and receive-health diagnostics.

Physical Bluetooth/USB connection, live setting acknowledgements/factory reset, peer-radio delivery, and terminal over-air Satellite delivery still require provisioned Somewear Nodes for hardware acceptance testing.

## Artifacts

- Kotlin AAR: `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
- Local-AAR dependency block: `somewear-gateway-sdk/dist/sc3-somewear.gradle.kts`
- SDK AAR SHA-256: see `handover/SHA256SUMS` (updated with every release artifact).
- Gateway base APK: `build/signed-splits-v2/com.somewearlabs.swtak.plugin.apk`
- Gateway ABI/configuration splits: the other four APKs in `build/signed-splits-v2/`.

No signing private key is committed. A recipient must re-sign all gateway splits and SC3 with the same controlled prototype certificate before installation.

Re-sign only the prepared APKs in `build/signed-splits-v2`. The original ATAK/Somewear plugin does not contain the complete standalone provider bootstrap and can fail with `lateinit property instanceProvider has not been initialized`. The handover scripts now reject an incorrect or older input set before signing.

The gateway APKs contain vendor-derived Somewear runtime code and are for authorized, controlled prototype testing. They must not be redistributed outside an approved team or used as a substitute for an official Somewear SDK/license.
