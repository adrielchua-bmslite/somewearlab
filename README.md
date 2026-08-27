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

Gateway API v2 passed bidirectional Android contract tests for initialization, explicit routing, inbound polling/Flow delivery, message-ID preservation, delivery lookup, cancellation, and controlled Radio-to-Satellite handover. Gateway v14 keeps the v13 one-attempt fallback controls and adds independently deliverable Satellite fragmentation/reassembly. On two Android runtimes, 21-byte JSON remained one Satellite parcel; exact 504-byte RFT and 2,171-byte CAS JSON queued 5 and 18 Satellite parcels and reassembled through the provider/SDK into one message each, including reverse order. Receive health reported the Satellite channel, 28 fragment callbacks, three completed messages, zero invalid fragments, zero receive errors, and zero incomplete assemblies. The earlier Radio fragmentation protections remain, including checksummed ordinary messages, distinct persisted timestamps, reverse-order reassembly, and a 60,000-byte stress case without a gateway/core crash. The API also exposes Node/satellite telemetry, mesh topology and signal quality, hardware settings, workspace QR enrollment, file/image transfer, and receive-health diagnostics.

Physical Bluetooth/USB connection, live setting acknowledgements/factory reset, peer-radio delivery, and terminal over-air Satellite delivery still require provisioned Somewear Nodes for hardware acceptance testing.

## Artifacts

- Kotlin AAR: `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
- Local-AAR dependency block: `somewear-gateway-sdk/dist/sc3-somewear.gradle.kts`
- SDK AAR SHA-256: `d2a309c9cf79e31133c596b34e958a065681c6201d4e75d675f78d7f316fd499`
- Gateway base APK: `build/signed-splits-v2/com.somewearlabs.swtak.plugin.apk`
- Gateway ABI/configuration splits: the other four APKs in `build/signed-splits-v2/`.

No signing private key is committed. A recipient must re-sign all gateway splits and SC3 with the same controlled prototype certificate before installation.

Re-sign only the prepared APKs in `build/signed-splits-v2`. The original ATAK/Somewear plugin does not contain the complete standalone provider bootstrap and can fail with `lateinit property instanceProvider has not been initialized`. The handover scripts now reject an incorrect or older input set before signing.

The gateway APKs contain vendor-derived Somewear runtime code and are for authorized, controlled prototype testing. They must not be redistributed outside an approved team or used as a substitute for an official Somewear SDK/license.
