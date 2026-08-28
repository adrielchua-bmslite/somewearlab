# Gateway v16 workspace content recovery test report

Date: 2026-08-28

## Implemented contract

- `listWorkspaceFiles(workspaceId, offset, limit)` exposes a bounded page from
  the retained authenticated `FileService/GetFiles` operation.
- `downloadWorkspaceFile()` downloads a catalogue entry to a caller URI.
- `syncWorkspaceContent()` owns full-catalogue comparison, exact-ID selection,
  fresh-ticket bounded retry, app-private caching, byte-count verification, and
  atomic completion inside the SDK.
- `cachedWorkspaceFiles()` reads the last local catalogue without a network call.
- `WorkspaceContentActivity` provides an optional SDK-owned list/download page.

The gateway returns metadata only through Binder. Signed URLs remain internal,
and downloaded bytes stream over HTTP directly into the SDK-managed cache.

## Automated validation

These commands completed successfully:

```text
:gateway-helper:compileDebugJavaWithJavac
:gateway-helper:testDebugUnitTest
:gateway-helper:assembleDebug
:sdk:compileDebugKotlin
:sdk:testDebugUnitTest
:sdk:assembleRelease
```

New tests verify:

- an empty selection means every catalogue entry;
- exact file IDs select only the requested files and report unknown IDs;
- blank IDs and unbounded retry counts are rejected before IPC;
- a streamed 2 MiB + 17 byte download is published byte-for-byte only after its
  expected size is verified;
- a truncated/mismatched download produces no completed file and leaves no
  temporary `.part` file.

The rebuilt AAR and five prepared gateway splits passed the portable handover
verifier. It confirmed matching signatures and hashes, the content activity and
network manifest contract, and these strings/classes inside the base APK DEX:

```text
workspace_file_catalog
listWorkspaceFiles
getGetFilesMethod
gateway_radio_satellite_content_contract=OK
verification=OK
```

The signed gateway splits and SDK instrumentation APK were also exercised on a
Pixel 9 Pro XL Android API 37 virtual device. The connected test passed, and its
filtered log was:

```text
info capability workspace_file_catalog=true
initialize result=SUCCESS
catalogue result=FAILURE(PERMISSION_DENIED)
content activity launch=OK
```

`PERMISSION_DENIED` is the expected typed result for this unenrolled virtual
device. It proves the catalogue request reached the retained authenticated gRPC
path instead of failing as `UNSUPPORTED`, on missing classes, or during provider
bootstrap.

## Acceptance boundary

The local environment did not have a provisioned, authenticated Somewear
workspace containing downloadable files. Therefore this report does not claim
a successful live `GetFiles` service response or physical Node delivery.

The retained APK was inspected to confirm that `FileService/GetFiles`, its
request/response protobufs, and the authenticated internal gRPC `makeCall`
implementation are present. Final acceptance still requires a signed-in gateway
on a phone with data access:

1. call `info()` and require `workspace_file_catalog`;
2. call `listWorkspaceFiles()` for a real joined workspace;
3. interrupt one download to confirm a later sync obtains a fresh URL and
   replaces no completed file with partial data;
4. miss or suppress one Node metadata announcement and confirm catalogue sync
   still discovers and downloads that file.

This recovery path is cloud-backed. It does not request individual hidden
Somewear Satellite composite children and does not carry large file bytes over
the mesh radio.
