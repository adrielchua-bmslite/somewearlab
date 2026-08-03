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

Start with the [SDK/API README](somewear-gateway-sdk/README.md).

## Verified status

Gateway API v2 passed a bidirectional two-emulator contract test for initialization, explicit radio-only routing, inbound polling/Flow delivery, message-ID preservation, delivery lookup, and safe rejection of automatic satellite fallback.

Physical Bluetooth/USB connection and real radio/satellite delivery still require two provisioned Somewear Nodes for hardware acceptance testing.

## Artifacts

- Kotlin AAR: `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
- SDK AAR SHA-256: `b69b90361d7270c8630a3e900123a1be7e8e2d46687377c8e59e4fb391943b25`

Gateway APKs, vendor material, decompiler output, signing keys, test harnesses, and generated Android build directories are intentionally excluded from this SDK repository.
