# Gateway v12 radio-fragment duplicate-filter test

Test date: 2026-08-21

## Fault reproduced from physical logs

The physical sender queued six distinct `MessagePayload` fragments during one
second and the Node reported all six delivered over Radio. The physical
receiver received all six radio parcels, but its retained Somewear database
identified them by package type, source user, and whole-second timestamp. It
therefore exposed only the first two and discarded the remaining fragments as
duplicates before `GatewayV2` could reassemble them.

`DataPayload`/Raw is not a valid workaround in this build: the retained router
removes Radio from Raw payload channel intent.

## Implemented correction

Gateway v12 keeps the mesh-supported `MessagePayload` transport. Each fragment
gets a different whole-second timestamp. The end of each reserved range is
committed to gateway-private preferences before the fragments are queued, so a
gateway process restart cannot reuse seconds from an outstanding transfer.

The public SDK API is unchanged. `info().capabilities` now contains
`radio_fragment_dedup` so SC3 can reject an older installed gateway.

## Automated and Android results

- `:gateway-helper:testDebugUnitTest`: passed, including six-fragment,
  512-fragment, wall-clock catch-up, restart-continuation, and invalid-count
  timestamp allocation cases.
- `:sdk:testDebugUnitTest`: passed.
- `:gateway-helper:assembleDebug` and `:sdk:assembleRelease`: passed.
- Two Android 37 ARM64 emulators initialized the final signed split set without
  a gateway or retained-core crash.
- A 932-byte payload produced eight Radio-only router sends on each emulator.
  Retained-core trace timestamps were consecutive and distinct. On one runtime
  they were `1787299167` through `1787299174`; on the other they were
  `1787299237` through `1787299244`.
- A 60,000-byte payload produced 469 Radio-only fragments without a gateway,
  Binder, or retained-core crash.
- The 469-fragment reservation ended at `1787299732`. After force-stopping and
  restarting the gateway process, the next transfer began at `1787299733`,
  proving persistence across process death.
- Both emulators reassembled an eight-fragment JSON through the retained
  `MessagePayload` to `RouterPayload` receive parser. Normal and reverse-order
  delivery both produced one SDK `IncomingMessage` with the original message
  ID and content.
- The final signed base APK passed signature and manifest verification, and
  both runtimes reported `radio_fragmentation` plus
  `radio_fragment_dedup`.

## Remaining physical acceptance

The attached physical logs established the original failure inside the real
radio receive path. This environment does not have two provisioned Somewear
Nodes, so post-v12 peer-radio acceptance still needs one controlled hardware
run. Install the same v12 five-split set on both phones, verify
`radio_fragment_dedup` on both, send a payload with `fragmentCount > 2`, and
confirm that `receiveHealth().completedRadioMessageCount` increases and the
original message ID appears once at the receiver.
