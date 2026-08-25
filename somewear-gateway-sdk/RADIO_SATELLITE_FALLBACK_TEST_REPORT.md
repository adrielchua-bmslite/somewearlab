# Gateway v13 radio-to-satellite fallback test

Date: 2026-08-25

## Result

The checked-in gateway v13 APK and Kotlin SDK implement
`RADIO_THEN_SATELLITE`. The gateway sends only Radio first and permits exactly
one Satellite-only attempt after an unsuccessful terminal Radio state or the
caller-supplied Radio delivery timeout.

## Automated coverage

The clean Gradle build passed all SDK and gateway-helper unit tests. The new
tests cover:

- Radio delivery before timeout suppresses Satellite;
- timeout and terminal failure each claim fallback once;
- simultaneous timeout/failure produces exactly one winning attempt;
- stale timers cannot claim a replacement message;
- explicit SC3 cancellation disarms fallback both before and after the plan is armed;
- retained-core `ERROR`, `CANCELED`, and `COLLAPSED` terminal states trigger
  fallback when SC3 did not explicitly cancel;
- the fallback envelope preserves Unicode message IDs and JSON bytes exactly;
- the bounded 24-hour receiver cache suppresses duplicate route copies;
- Radio and Satellite have independent 30-second and five-minute defaults.

## Installed Android regression

The final signed base APK, all four matching configuration splits, and the SDK
test app were installed on two ARM64 Android API 37 virtual devices.

On both devices:

- `info()` returned `radio_then_satellite` and `satellite_timeout`;
- `initialize()` completed without an Android crash;
- `RADIO_THEN_SATELLITE` returned a receipt with
  `satelliteFallbackArmed=true`;
- after a 1,000 ms Radio timeout, `deliveryStatus()` reported
  `status=QUEUED` and `deliveredChannel=SATELLITE`;
- direct `SATELLITE_ONLY` returned success and immediately reported
  `deliveredChannel=SATELLITE`;
- two copies of the same fallback-framed inbound message produced exactly one
  `IncomingMessage`, with the original SC3 message ID and content;
- no `AndroidRuntime` fatal exception was recorded.

An additional cancellation test armed a fallback with a 15-second Radio
timeout, called `cancelMessage()`, waited beyond that timeout, and still
reported `status=CANCELED`, `deliveredChannel=RADIO`, and
`errorReason=Canceled by SC3`. Satellite was not started.

## Acceptance boundary

Virtual devices do not emulate a Somewear Node or the satellite network. These
tests prove SDK-to-provider IPC, retained-router queue selection, timeout
handover, cancellation, delivery-state exposure, receiver parsing, and duplicate
suppression. Final operational acceptance still requires two provisioned Nodes
for peer Radio delivery and a clear-sky, enabled Satellite test that reaches a
terminal over-air acknowledgement.
