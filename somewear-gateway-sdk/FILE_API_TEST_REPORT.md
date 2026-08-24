# File/image API test report

Date: 2026-08-24

## Automated build and unit tests

The following completed successfully:

```text
:gateway-helper:testDebugUnitTest
:gateway-helper:assembleDebug
:sdk:testDebugUnitTest
:sdk:assembleRelease
```

Coverage added in this revision includes fragment-aware message cancellation and
telemetry/mesh observation deduplication. The checked-in AAR and five gateway
splits also passed `handover/scripts/verify_gateway_artifacts.sh`, including
hash, signer, manifest provider, QR activity, and bound service checks.

## Android runtime checks

Two API-37 ARM64 emulators ran the retained Somewear core and the rebuilt
signature-matched gateway/test client.

Observed results:

```text
GatewayInfo capabilities=[..., node_telemetry, satellite_signal,
mesh_network_status, message_cancel, device_power, file_upload,
file_metadata_send, incoming_files, file_download, ...]

COMMAND_SUCCESS command=telemetry
NodeTelemetry(batteryPercent=null, satelliteQuality=0,
satelliteSendable=false, ...)

COMMAND_SUCCESS command=dispatch_file_metadata
COMMAND_SUCCESS command=poll_files count=1
FILE_RECEIVED file_id=image-75mb-final file_name=final-field-image.jpg
mime_type=image/jpeg file_size_bytes=75000000 workspace_id=1 channel=RADIO

COMMAND_SUCCESS command=dispatch_framed_router fragment_count=157
COMMAND_SUCCESS command=poll count=1
MESSAGE_RECEIVED message_id=large-json-001 workspace_id=1 channel=RADIO

COMMAND_FAILED command=file_upload_probe code=FILE_UPLOAD_FAILED
message=Somewear did not return a file upload ticket
```

There was no `AndroidRuntime` fatal exception or retained-core crash. The final
upload probe used an unenrolled emulator intentionally: it verifies that URI
hashing/reading reaches the retained file service and an unavailable ticket is
returned as a typed failure rather than an exception.

## Remaining acceptance test

The tests validate native large-image metadata receive without placing bytes in
Binder; they do not claim that 75 MB crossed the Node radio. Live file bytes use
Somewear's signed cloud upload/download URLs. A provisioned Somewear account,
real workspace, data access, and two physical Nodes are still required to verify:

1. signed upload of actual image bytes;
2. Radio- or Satellite-only delivery of its metadata announcement;
3. peer receipt and signed download with byte count/hash comparison.
