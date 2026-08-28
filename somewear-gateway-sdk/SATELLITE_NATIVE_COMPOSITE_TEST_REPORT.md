# Gateway v15 native Satellite composite and read-back test

Date: 2026-08-28

## Field finding addressed

Gateway v14 emitted six independently deliverable `SC3R1` messages for one
RFT JSON payload. The Somewear website received only five after 5–10+ minutes,
could not apply the SDK-owned reassembler, and the receiving phone correctly
withheld the incomplete message. Application-level Satellite fragmentation was
therefore not a compatible end-to-end transport.

## v15 correction

- SC3 queues one parent `MessagePayload` for `SATELLITE_ONLY` and the Satellite
  attempt of `RADIO_THEN_SATELLITE`.
- The retained Somewear `CompositePackager` owns native Satellite `Part`
  creation. Its `PostOffice` combines all native children before publishing the
  original parent to the gateway callback.
- Every Satellite send sets `requiresBackhaulAck=true`. Parent delivery cannot
  become successful merely because SC3 handed the message to the Node.
- SC3 Radio framing is unchanged. Legacy v14 Satellite frames remain accepted
  on receive so deployments can be upgraded without making queued traffic
  unreadable.
- Automatic `AfterTimeout` retry remains disabled because the retained
  implementation has no bounded retry count. SC3 receives terminal failure and
  can require operator approval before incurring another Satellite send.

## Automated results

The exact signed split set was clean-installed on two Android 16 emulators.
Both devices passed `info()` and `initialize()` with no gateway or test-app
crash. The advertised capabilities included `satellite_native_composite`,
`satellite_backhaul_ack`, and `satellite_fragment_reassembly_legacy`.

| Application JSON | Device A receipt | Device B receipt |
|---|---|---|
| 21 bytes (small) | `fragmentCount=1`, estimate `1`, native composite `false`, ACK `true` | `fragmentCount=1`, estimate `1`, native composite `false`, ACK `true` |
| 504 bytes (RFT) | `fragmentCount=1`, estimate `2`, native composite `true`, ACK `true` | `fragmentCount=1`, estimate `2`, native composite `true`, ACK `true` |
| 2,171 bytes (CAS) | `fragmentCount=1`, estimate `8`, native composite `true`, ACK `true` | `fragmentCount=1`, estimate `8`, native composite `true`, ACK `true` |

For both 504-byte messages, `deliveryStatus(messageId)` returned `QUEUED` with
`deliveredChannel=SATELLITE`. That is the correct non-terminal state without a
physical Node/backhaul acknowledgement. `receiveHealth()` reported an active
subscription and zero receive errors on both devices.

The clean Gradle build covers the transport policy directly: oversized Radio
uses SC3 fragmentation, oversized Satellite does not, and only Satellite
requires the retained backhaul ACK option. Existing legacy-frame tests cover
normal, duplicate, and reverse-order reassembly.

The portable handover verifier now parses each DEX class-definition table. As a
negative control it rejected an APK whose manifest named
`SomewearGatewayProvider` but whose DEX omitted the provider class; the corrected
APK passed the same verifier. This prevents a manifest-only package from being
published again.

## Physical acceptance still required

Emulators prove APK startup, IPC, routing policy, payload sizing, receipt
mapping, delivery lookup, and receive-subscription health. They cannot produce a
real Satellite uplink or downlink. Before operational deployment, send the same
504-byte and 2,171-byte fixtures through provisioned Nodes and confirm:

1. the website receives one reconstructed JSON message rather than `SC3R1`
   fragments;
2. the sender reaches a terminal acknowledged status;
3. the peer phone receives one `IncomingMessage` with the original message ID,
   byte count, and checksum; and
4. the receiver reports `lastDeliveredChannel=SATELLITE` with zero receive
   errors.
