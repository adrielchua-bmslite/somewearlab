# Gateway v14 Satellite fragmentation and receive test

Date: 2026-08-27

## Result

Gateway v14 no longer hands an oversized SC3 JSON message to the retained core
as one opaque Satellite parcel. It creates ordinary, independently deliverable
Satellite-only `MessagePayload` records and reassembles them on the receiving
gateway before exposing one `IncomingMessage` to SC3.

## Payload cases

The regression used the application sizes reported from field testing:

| Application JSON | Core preflight | Gateway parcels | Receipt |
|---:|---:|---:|---|
| 21 bytes | 1 transmission | 1 | `fragmentCount=1`, `satelliteFragmented=false` |
| 504 bytes (RFT) | 2 transmissions | 5 | `fragmentCount=5`, `satelliteFragmented=true` |
| 2,171 bytes (CAS) | 8 transmissions | 18 | `fragmentCount=18`, `satelliteFragmented=true` |

The gateway count is intentionally larger than the core estimate. Each parcel
contains a versioned SC3 frame, message ID, index/count, and checksum so it can
be retried and validated independently. That costs more Satellite airtime, but
removes the prior one-parcel failure mode.

## Automated coverage

The clean Gradle build passed gateway-helper and SDK unit tests. The framing
test reconstructs exact 504-byte and 2,171-byte JSON strings through a
`SATELLITE` receive channel and compares the resulting message ID and UTF-8
content byte-for-byte. Existing duplicate, checksum-failure, out-of-order,
delivery aggregation, cancellation, and fallback-race tests also passed.

## Installed Android regression

The rebuilt signed base APK, all four matching configuration splits, and the
SDK test app were installed on two ARM64 Android API 37 virtual devices.

- Both `initialize()` calls completed without a crash.
- Direct `SATELLITE_ONLY` sends for 21, 504, and 2,171 bytes returned successful
  `SendReceipt` values with parcel counts 1, 5, and 18.
- The 504-byte and 2,171-byte payloads were delivered through the gateway
  provider callback with `delivered_channel=SATELLITE`; the CAS frames were
  deliberately reversed.
- The SDK emitted exactly one message for each original message ID with the
  complete original JSON.
- Reciprocal Satellite reassembly also passed on the other Android runtime.
- Combined receive diagnostics reported 28 fragment callbacks, three completed
  transport messages, zero invalid fragments, zero receive errors, and zero
  active/incomplete assemblies.
- `lastDeliveredChannel=SATELLITE`, `lastPayloadStatus=Delivered`, and
  `lastPayloadOutbound=false` were exposed through `receiveHealth()`.
- No `AndroidRuntime` fatal exception was recorded.

## Acceptance boundary

Virtual devices cannot emulate a Somewear Node or the satellite network. These
tests prove SDK preflight/framing, provider IPC, retained-core queue acceptance,
Satellite channel intent, out-of-order reassembly, Flow/poll delivery, and
diagnostics. Final acceptance still requires two provisioned Nodes and a
clear-sky over-air test that observes every fragment's terminal delivery.

A stricter disconnected-core run started with zero router callbacks, queued the
504-byte and 2,171-byte cases as 5 and 18 Satellite-only parcels, then still
reported zero callbacks and aggregate `QUEUED` after ten seconds. This is
expected without a Node, but it means the emulator result must not be described
as core terminal delivery or an over-air Satellite pass.
