# Mesh signal API test report

Date: 2026-08-24

## Public API

`MeshNetworkStatus.signalRssi` continues to expose the raw value reported by the
latest Somewear `DeviceMeshNetworkUpdate`. The new computed
`MeshNetworkStatus.signalQuality` property exposes the matching retained-core
quality band without changing the existing model constructor.

| Raw mesh value | Public quality |
|---:|---|
| missing, `-1`, or below 100 | `UNKNOWN` |
| 100-147 | `FAR` |
| 148-213 | `SOMEWHAT_CLOSE` |
| 214 or above | `CLOSE` |

This is receive-side Node-to-Node mesh quality. It is separate from Bluetooth
RSSI and from `setMeshTransmissionStrength(LOW/MEDIUM/HIGH)`, which controls the
Node's configured transmit strength.

## Automated validation

The boundary mapping, computed model property, existing SDK unit suite, release
lint, and release AAR build passed. Live non-empty RSSI values still require two
physical Nodes on a compatible mesh workspace; emulators only provide the safe
empty mesh state.

The rebuilt gateway and SDK test client were then installed on two API-37 ARM64
emulators. Both initialized and invoked the public mesh API successfully:

```text
emulator-5554 COMMAND_SUCCESS command=mesh_status available=false
signal_rssi=null signal_quality=UNKNOWN peer_user_id=null

emulator-5556 COMMAND_SUCCESS command=mesh_status available=false
signal_rssi=null signal_quality=UNKNOWN peer_user_id=null
```

No `AndroidRuntime` fatal exception was observed. This verifies the deployed
empty-state path and the new computed property on Android; it does not replace
the remaining physical two-Node test for non-empty radio measurements.
