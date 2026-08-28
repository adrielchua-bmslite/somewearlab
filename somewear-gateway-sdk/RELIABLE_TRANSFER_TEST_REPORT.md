# Reliable transfer validation

Date: 2026-08-28

## What changed

- Fragmented `RADIO_ONLY` messages are journaled in the gateway's private files
  before queueing.
- A receiver journals fragments, automatically requests missing indexes with
  bounded backoff, and sends a completion acknowledgement after exact
  checksum-safe reassembly.
- The sender selectively retransmits requested indexes and deletes its retained
  copy after the peer acknowledgement. Records expire after 24 hours and both
  incoming/outgoing journals are bounded to 128 transfers.
- `sendFileBatch()` uploads a variable non-empty list, then publishes an
  authoritative revisioned manifest last. The manifest contains the exact
  expected count and per-file ID, position, size, MIME type, and SHA-256.
- `syncFileBatch()` uses the workspace catalogue to recover exact missing IDs
  and validates both new and previously cached files against the manifest hash.

## Automated evidence

- Clean gateway-helper and SDK JVM tests passed, including protocol
  encode/parse/batching, journal reopen/expiry/acknowledgement, out-of-order
  completion, 2 MiB atomic download, size rejection, and SHA-256 rejection.
- The release AAR and gateway v17 base APK were rebuilt from the tested sources.
- On both `emulator-5554` and `emulator-5556`, the signed Android suite passed:
  provider/bootstrap startup, catalogue capability, a 17-file manifest exact
  round trip, and a missing-fragment recovery scenario.
- The Android recovery scenario delivered every frame except index 1, confirmed
  the incomplete API returned exactly `[1]`, cleared all volatile reassembly
  state, supplied only fragment 1, rebuilt the original 2,824-byte JSON from the
  durable journal, and delivered it once. After another volatile-state clear,
  a late duplicate was acknowledged and ignored from the durable completion
  tombstone; no ghost incomplete transfer or duplicate application message was
  created.

## Acceptance boundary

The Android tests exercise the actual rebuilt provider, retained Somewear
`MessagePayload` parser, SDK IPC, durable journal, and reassembler. They do not
emulate RF loss inside a physical Somewear Node. Final field acceptance still
requires two provisioned Nodes to confirm the compact recovery request and peer
completion acknowledgement traverse the selected mesh channel. Live file-batch
upload/catalogue/download also requires an authenticated Somewear service.

Selective fragment recovery applies to SC3 Radio framing. Native Satellite
composite children remain internal to Somewear Core; a failed Satellite parent
must be retried as a complete logical message under operator policy.
