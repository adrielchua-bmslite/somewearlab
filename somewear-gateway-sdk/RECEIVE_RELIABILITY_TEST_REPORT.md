# Receive reliability test report

Date: 2026-09-02
Gateway: v18 / API v2

## Finding compared with the previous build

- Completed incoming messages were kept only in the gateway process's RAM.
  Killing or replacing that process lost the pollable copy.
- The incoming sequence restarted at `1`. If SC3 retained an older high cursor,
  new messages after a gateway restart could be skipped indefinitely.
- The payload listener was considered healthy whenever a non-null subscription
  object existed, even if Somewear Core had replaced its router instance.
- Android's `onBindingDied` callback only cleared a Boolean; the SDK did not
  release and restore the dead bound-service connection.
- There was no application-level acknowledgement. SC3 could not atomically
  commit a payload and then tell the gateway that its replay copy was safe to
  delete.

## Additive fixes

- Added an app-private, atomic completed-message inbox with a wall-clock-based
  monotonic `Long` sequence and a 2,000-record bound.
- Added cumulative `acknowledgeIncomingMessagesThrough(sequence)`. It deletes
  only locally persisted gateway records and deliberately makes no network
  delivery claim.
- Kept the established RAM queue as a fallback if private storage cannot be
  initialized or written.
- Revalidates the active router and payload flow on start/poll; a stale
  subscription is disposed and replaced without changing send routing.
- Rebinds the receive service after Android reports a dead binding.
- Added safe diagnostics for inbox bounds/drops, router identity, subscription
  attempts/replacements, gateway service lifecycle, Core configuration, and the
  retained package-stream started state when that private value exists.
- Made gateway provider/service initialization idempotent after an Android test
  exposed their concurrent-start race.

## Automated build and JVM evidence

- `:gateway-helper:testDebugUnitTest`: passed.
- `:sdk:testDebugUnitTest`: passed.
- `:gateway-helper:assembleDebug`: passed.
- `:sdk:assembleRelease`: passed.
- `:sdk:assembleAndroidTest`: passed.
- `:sdk:connectedDebugAndroidTest` for
  `completedIncomingMessageRemainsUntilExplicitlyAcknowledged`: passed on the
  API 36.1 ARM64 emulator.
- `:test-app:assembleDebug`: passed.
- A 100,000-character payload persisted and read back exactly, avoiding the
  65,535-byte `DataOutput.writeUTF` limit.
- Reopen/replay, cumulative acknowledgement, monotonic sequence, bounded-drop
  reporting, future-ack rejection, and concurrent provider/service handles all
  have regression coverage.

## Android emulator evidence

One ARM64 emulator was available during this run.

- The rebuilt signed gateway split set and SC3 test app installed successfully.
- `initialize()` completed without an Android runtime crash.
- A 2.3 KB CAS JSON was injected through the same signature-protected gateway
  IPC and polled by the public SDK with its `messageId`, workspace, Satellite
  channel, and full content intact.
- Before restart, health reported:
  - `persistentInboxEnabled=true`
  - `subscriptionActive=true`
  - `subscribedRouterMatchesCurrent=true`
  - `gatewayReceiveServiceCreated=true`
  - `sdkReceiveServiceConnected=true`
  - `droppedIncomingCount=0`
  - `errorCount=0`
- The gateway package was force-stopped. `ensureReceiving()` succeeded and the
  same CAS record replayed with the same sequence.
- `acknowledgeIncomingMessagesThrough()` returned the expected sequence and
  `remainingCount=0`.
- A new poll from sequence `0` returned `count=0`; health retained the latest and
  acknowledged sequence, with zero drops and receive errors.

The first device pass caught a stale `latestSequence=0` diagnostic during a
provider/service startup race. That race was fixed, covered by a new test, and
the entire Android sequence above was repeated successfully.

## Acceptance boundary

This validates gateway APK startup, Somewear Core initialization, service/router
subscription state, completed-message persistence, Android IPC, SDK parsing,
restart replay, and local acknowledgement. The emulator injection replaces the
physical Node/radio/Satellite hop. A provisioned two-Node test is still required
to prove that Somewear Core actually emits a live peer downlink. If
`routerCallbackCount` does not increase during that test, the wrapper received no
parent payload to persist; capture both Node/Core logs and server timestamps.
