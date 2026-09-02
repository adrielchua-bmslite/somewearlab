# SC3 Somewear Gateway SDK

Kotlin/Android client SDK for connecting SC3 to the separately installed Somewear Gateway APK.

The SDK does **not** embed ATAK or the decompiled Somewear Core. It provides a typed AAR that calls the headless gateway package over Android IPC:

```text
SC3
  -> somewear-gateway-sdk.aar
  -> ContentResolver IPC
  -> Somewear Gateway APK
  -> Somewear Core
  -> Bluetooth or USB
  -> local Somewear Node
  -> radio or satellite
  -> remote Somewear Node
```

## Status

SDK version: `0.1.0`

The SDK exposes the complete SC3-facing contract. Gateway v18 implements standalone initialization, a durable completed-message inbox with explicit local acknowledgement, automatic router/service resubscription, a state-change-only connection observer, Node hardware settings and telemetry, satellite signal, mesh-network status, message cancellation, power commands, cloud-backed file/image transfer, revisioned variable-size file-batch manifests, authenticated workspace file catalogues, SDK-owned missing-file recovery, durable selective Radio-fragment recovery and peer completion acknowledgement, a bound receive-lifetime service, receive health, gateway-hosted QR invite scanning, fresh-install workspace enrollment/synchronization, Bluetooth connection, USB connection initiation, explicit radio-only and satellite-only sending, controlled radio-then-satellite fallback, SC3 Radio fragmentation, native Somewear Satellite composite transport, legacy v14 Satellite-frame receive compatibility, cross-channel duplicate suppression, inbound router bridging, aggregate delivery-status polling, workspace listing/selection, and non-secret workspace/mesh-key readiness.

The SDK expects the separately installed gateway implementing the API-v2 contract documented below. The private handover repository includes the controlled-test gateway split set under `build/signed-splits-v2/`; see `handover/README.md` for re-signing and installation. No signing private key is committed.

The SDK intentionally refuses to use the gateway's legacy `sendMessage` method. That method lets Somewear Core use its default Radio + Satellite + Cellular channel set and is not safe when satellite cost must be controlled.

See [`FILE_API_TEST_REPORT.md`](FILE_API_TEST_REPORT.md) for the exact file,
telemetry, and two-emulator regression evidence and the remaining physical/cloud
acceptance boundary.

See [`WORKSPACE_CONTENT_SYNC_TEST_REPORT.md`](WORKSPACE_CONTENT_SYNC_TEST_REPORT.md)
for the v17 catalogue, selective recovery, atomic large-download tests, rebuilt
artifact verification, and live authenticated acceptance boundary.

See [`RADIO_SATELLITE_FALLBACK_TEST_REPORT.md`](RADIO_SATELLITE_FALLBACK_TEST_REPORT.md)
for the route-state race tests, two-emulator APK evidence, duplicate suppression,
cancellation result, and remaining over-air acceptance boundary.

See [`RELIABLE_TRANSFER_TEST_REPORT.md`](RELIABLE_TRANSFER_TEST_REPORT.md) for
the v17 durable fragment recovery, peer acknowledgement, variable file-batch,
hash verification, and two-emulator restart-style evidence.

See [`RECEIVE_RELIABILITY_TEST_REPORT.md`](RECEIVE_RELIABILITY_TEST_REPORT.md)
for the v18 durable inbox, restart/replay/acknowledgement Android evidence, large
payload persistence tests, and the remaining live Somewear downlink boundary.

## Requirements

- Android 8.0/API 26 or newer.
- AndroidX enabled in the consuming project (`android.useAndroidX=true` in the
  root `gradle.properties`).
- Kotlin application or Java application with Kotlin/coroutines dependencies.
- The Somewear Gateway APK installed on the same Android device.
- SC3 and the gateway signed with the same certificate.
- Camera permission granted to the separately installed gateway. SC3 itself does
  not need CameraX, ML Kit, or a Camera permission for workspace scanning.
- Internet access while accepting a service-token invite, synchronizing workspaces,
  uploading a file, listing workspace content, or downloading a received file.
- A provisioned Somewear Node and compatible Somewear workspace/mesh keys.
- For Bluetooth: Android Bluetooth permissions and a bonded Somewear Node.
- For USB: Android USB-host/OTG support, a data cable, and a Node model that supports USB.

## Build the AAR

From this directory:

```sh
./gradlew :sdk:assembleRelease
```

Output:

```text
sdk/build/outputs/aar/sdk-release.aar
```

Publish to Maven Local:

```sh
./gradlew :sdk:publishReleasePublicationToMavenLocal
```

Coordinates:

```kotlin
implementation("com.sc3.somewear:somewear-gateway-sdk:0.1.0")
```

Alternatively, copy `sdk-release.aar` into SC3's `app/libs/` directory:

```kotlin
dependencies {
    implementation(files("libs/sdk-release.aar"))
    implementation("androidx.activity:activity-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
```

The checked-in `dist/sc3-somewear.gradle.kts` contains that complete dependency
block. A colleague can copy it beside the AAR and add this to
`SC3/app/build.gradle.kts`:

```kotlin
apply(from = "libs/sc3-somewear.gradle.kts")
```

The SC3 root `gradle.properties` must include:

```properties
android.useAndroidX=true
```

Those Activity/coroutines lines are required when consuming the checked-in AAR as a local file because a standalone AAR cannot carry Maven transitive-dependency metadata. When consuming the SDK from Maven coordinates, the published POM supplies them. The AAR contributes the gateway permission, a dependency-free scanner proxy activity, and the Android package-visibility query through manifest merging. Camera and QR decoding run in the gateway APK using its retained offline scanner runtime.

## Create the client

Create one client for the SC3 process and keep it for the application's lifetime:

```kotlin
val somewear = SomewearGateway.create(
    applicationContext,
    SomewearSdkConfig(
        pollIntervalMillis = 500,
        operationTimeoutMillis = 30_000,
    ),
)
```

Close it when SC3 permanently shuts down:

```kotlin
somewear.close()
```

`close()` releases SDK-side resources. It does not stop the gateway or disconnect the Node. Use `disconnect()` or `shutdown()` explicitly when required.

## API overview

### Gateway lifecycle

```kotlin
suspend fun info(): SomewearResult<GatewayInfo>
suspend fun initialize(): SomewearResult<Unit>
suspend fun shutdown(): SomewearResult<Unit>
```

Call `info()` first to verify that the gateway is installed, visible, correctly signed, and has the required capability. Call `initialize()` once before connecting.

```kotlin
when (val result = somewear.initialize()) {
    is SomewearResult.Success -> Unit
    is SomewearResult.Failure -> showError(result.error)
}
```

### Bluetooth connection

Pass the Node's Bluetooth MAC address in colon-separated form. The SDK trims it,
normalizes lower-case hexadecimal, and rejects malformed addresses before IPC.

```kotlin
val result = somewear.connectBluetooth("AA:BB:CC:DD:EE:FF")
```

`connectBluetooth()` submits the asynchronous gateway connection operation and polls `getDeviceStatus` until connected, rejected, or timed out.

The gateway resolves an explicit valid MAC through Android Bluetooth and seeds
Somewear Core's device cache before connecting. This prevents the misleading
`NoKnownDeviceFound` result that occurred when the Node was absent from the
core's in-memory scan cache. If the Node still needs Android bonding, the core
can return `PreBondingRequired`; pair it in Android Bluetooth settings and retry.

SC3 only needs `BLUETOOTH_CONNECT` if SC3 itself enumerates bonded devices. The gateway package owns its own Bluetooth permissions because it owns the actual link.

### USB connection

USB mode is a setting stored on the Node. For the first switch, the command is normally sent while the Node is still connected over Bluetooth:

```kotlin
somewear.setNodeConnectionMode(NodeConnectionMode.USB)
```

Internally, the gateway must translate `USB` to Somewear's wire value `SettingsCommand.ConnectionMode.LineIn`.

The complete transition is:

```text
Connected over Bluetooth
  -> setNodeConnectionMode(USB)
  -> Node acknowledges and restarts
  -> Bluetooth is disabled
  -> attach a data USB cable
  -> grant Android USB-device permission to the gateway
  -> connectUsb()
```

Then call:

```kotlin
val result = somewear.connectUsb(timeoutMillis = 30_000)
```

The SDK sends an empty, non-null extras Bundle with `connectUsb()`. This is
intentional compatibility behavior for gateway builds whose legacy dispatcher
otherwise reports `Missing extras Bundle` before starting USB discovery.

To return to Bluetooth:

```text
Connected over USB
  -> setNodeConnectionMode(BLUETOOTH)
  -> Node restarts and USB disconnects
  -> reconnect over Bluetooth
```

Calling `connectUsb()` does not change the Node's stored connection mode.

### Connection status

Read once:

```kotlin
val status = somewear.deviceStatus()
```

Observe continuously:

```kotlin
lifecycleScope.launch {
    somewear.observeDeviceConnection().collect { result ->
        when (result) {
            is SomewearResult.Success -> render(result.value)
            is SomewearResult.Failure -> renderError(result.error)
        }
    }
}
```

The observer emits once immediately and then only when the complete status or
error changes. It still polls the gateway internally, but an unchanged status is
not delivered to the collector. Observing never calls `connectBluetooth()`,
`connectUsb()`, `cancelConnection()`, or `disconnect()`. Do not call those
methods unconditionally from inside the collector. `observeDeviceStatus()` is a
source-compatible alias with the same state-change-only behavior.

`DeviceStatus` reports:

- `connectionState`: disconnected, scanning, connected, or connected-and-scanning.
- `operationState`: idle, in progress, completed, or unknown.
- `operationResult`: the detailed Somewear connection result.
- `localTransport`: Bluetooth, USB, none, or unknown.

Cancel an active attempt or disconnect:

```kotlin
somewear.cancelConnection()
somewear.disconnect()
```

### Node telemetry, satellite signal, and mesh status

Read one snapshot or collect changes:

```kotlin
val telemetry = somewear.nodeTelemetry()
val mesh = somewear.meshNetworkStatus()

when (mesh) {
    is SomewearResult.Success -> {
        val rawRssi = mesh.value.signalRssi
        val quality = mesh.value.signalQuality
        // quality is UNKNOWN, FAR, SOMEWHAT_CLOSE, or CLOSE.
    }
    is SomewearResult.Failure -> showError(mesh.error)
}

lifecycleScope.launch {
    somewear.observeNodeTelemetry().collect(::renderTelemetry)
}

lifecycleScope.launch {
    somewear.observeMeshNetworkStatus().collect(::renderMeshStatus)
}
```

`NodeTelemetry.satelliteQuality` is the retained Somewear value from 0 to 5;
5 is best, and `satelliteSendable` becomes true at 2 or above. Signal quality
does not select a route. SC3 still chooses `RADIO_ONLY` or `SATELLITE_ONLY` on
each send. Battery, identity, firmware, tracking, GPS, and power fields are
nullable when the core has not received a valid value from the Node.

`MeshNetworkStatus` reports the latest peer, next hop, hop count, RSSI, and
backhaul flag. `available=false` means the core currently has only its empty
mesh snapshot; do not treat its timestamp as evidence that a peer is reachable.
`signalQuality` converts the raw Somewear mesh RSSI/link score using the same
bands as the retained core: below 100 is `UNKNOWN`, 100-147 is `FAR`, 148-213
is `SOMEWHAT_CLOSE`, and 214 or above is `CLOSE`. This measures the most recent
Node-to-Node mesh update, not the phone-to-Node Bluetooth connection.

Power commands require a connected Node:

```kotlin
somewear.powerOn()
somewear.powerOff()
```

### Hardware settings

Read the most recently reported Node settings:

```kotlin
when (val result = somewear.hardwareSettings()) {
    is SomewearResult.Success -> render(result.value)
    is SomewearResult.Failure -> showError(result.error)
}
```

Individual fields in `HardwareSettings` are nullable. `null` means the retained
Somewear core has not received that value from the Node; it does not mean
`false`. Mutations require a connected Node and complete only after Somewear's
settings command is acknowledged or fails/times out.

```kotlin
somewear.setTrackingEnabled(true)
somewear.setTrackingInterval(TrackingInterval(gpsSeconds = 30, sendingSeconds = 60))

somewear.setBackhaulEnabled(true)
somewear.setSatelliteEnabled(false)
somewear.setMeshRadioEnabled(true)
somewear.setRadioChannel(
    RadioChannel(lowSpeedFrequencyHz = 915_000_000, highSpeedFrequencyHz = 916_000_000),
)
somewear.setMeshTransmissionStrength(MeshTransmissionStrength.HIGH)

somewear.setLedLightEnabled(true)
somewear.setVibrationFeedbackEnabled(true)
somewear.setEnduranceModeEnabled(false)
somewear.setDeviceButtonFunction(DeviceButtonFunction.PUSH_TO_TALK)
somewear.setNodeConnectionMode(NodeConnectionMode.USB)
```

Supported button functions are `NONE`, `SATELLITE`, `TRACKING`, `SENSOR`, and
`PUSH_TO_TALK`. Transmission strengths are `LOW`, `MEDIUM`, and `HIGH`.
`UNKNOWN` is read-only and is rejected by the setters.

Only set radio-channel frequencies supplied for that Node/workspace by Somewear
and permitted in the operating region. The SDK validates positive Hz values but
cannot validate spectrum authorization or firmware compatibility.

Changing connection mode normally restarts the Node and disconnects the current
transport. Factory reset is deliberately harder to call:

```kotlin
somewear.factoryReset(FactoryResetConfirmation.ERASE_NODE)
```

This invokes Somewear's retained device-management reset workflow, clears the
stored bond/device, and normally terminates the connection. Never expose it as a
single-tap action; require an operator confirmation in SC3.

### Message format

The recommended payload is a compact SC3 envelope carried inside a Somewear `MessagePayload`:

```kotlin
val envelope = Sc3MessageEnvelope(
    type = "position",
    sender = "SC3-01",
    bodyJson = """{"lat":1.3521,"lon":103.8198}""",
)
```

Encoded form:

```json
{
  "v": 1,
  "id": "application-uuid",
  "type": "position",
  "sentAt": 1785741000000,
  "sender": "SC3-01",
  "target": "workspace",
  "body": {
    "lat": 1.3521,
    "lon": 103.8198
  }
}
```

`bodyJson` must be valid JSON and is inserted into the envelope without additional quoting.

### Send a message

Radio-only should be SC3's default:

```kotlin
val request = SendRequest(
    workspaceId = workspaceId,
    content = envelope.encode(),
    routePolicy = RoutePolicy.RADIO_ONLY,
    messageId = envelope.id,
)

val receipt = somewear.send(request)
```

Gateway v17 measures the fully encoded Somewear package before queueing it, but
the responsible fragmentation layer depends on the route:

- `RADIO_ONLY`: SC3 splits an oversized message into checksummed ordinary
  `MessagePayload` records. The retained core does not accept native composite
  children over Radio.
- `SATELLITE_ONLY`: SC3 queues exactly one parent `MessagePayload`. The retained
  Somewear `CompositePackager` splits it into native `Part` records and
  `PostOffice` reassembles the parent before the gateway callback. This is also
  the format understood by the Somewear service/web path. Satellite
  `SendOptions` require backhaul acknowledgement for every native child.

The retained Somewear receiver considers messages with the same source and
whole-second timestamp to be duplicates even when their bytes differ. Gateway
v12 therefore gives every fragment a distinct timestamp and persists the last
reservation so timestamps are not reused after process restart. Generic Raw
payloads are not used because the retained router removes Radio from that type.

Both devices should run gateway v15 or newer for native-composite Satellite
messages. Gateway v15 still receives v14 SC3-framed Satellite messages during a
rolling handover. Gateway v12 or newer remains sufficient for Radio-fragmented
messages. Unfragmented small messages remain compatible with older gateways.

`SendReceipt.fragmentCount` reports SC3-owned parent/message records, not the
retained core's private native children. `estimatedTransmissionCount` reports
the core's low-speed estimate for the original parent.
`satelliteNativeComposite=true` means the Satellite parent is oversized and the
retained core owns its splitting/reassembly. For that case `fragmentCount` stays
`1`, `satelliteFragmented` stays `false`, and the estimate is greater than one.
`backhaulAckRequired=true` confirms that a Satellite send requires server/
backhaul read-back rather than treating Node handoff as terminal evidence.
`transportFragmented`/`radioFragmented` remain true for SC3 Radio framing.

The SC3 Radio framing limit is 64 KiB for message ID plus content. Compress
application JSON where practical and use file/image APIs for binary content.

Available policies:

| Policy | Required gateway behavior |
|---|---|
| `RADIO_ONLY` | Build `SendOptions` with only `DevicePayloadChannel.Radio`; disable backhaul. |
| `RADIO_THEN_SATELLITE` | Send radio-only, wait for terminal status/timeout, then make one satellite-only attempt if needed. |
| `SATELLITE_ONLY` | Build `SendOptions` with only `DevicePayloadChannel.Satellite` and require backhaul acknowledgement. |

For cost control, the gateway must implement radio-then-satellite as two controlled sends. It must not pass `{Radio, Satellite}` together as one channel set.

The SDK never silently upgrades `RADIO_ONLY` to satellite and never falls back to legacy `sendMessage`.

For an operator-approved fallback send:

```kotlin
val result = somewear.send(
    SendRequest(
        workspaceId = workspaceId,
        content = envelope.encode(),
        messageId = envelope.id,
        routePolicy = RoutePolicy.RADIO_THEN_SATELLITE,
        radioTimeoutMillis = 30_000L,
        satelliteTimeoutMillis = 300_000L,
    ),
)
```

Gateway v15 queues only Radio first. If every Radio parcel is delivered, it
removes the fallback. If the retained core reports an unsuccessful terminal
state (`ERROR`, `CANCELED`, or `COLLAPSED`) or the radio delivery timer expires,
the gateway atomically cancels the old Radio parcels and makes exactly one
Satellite-only parent send. If that parent needs multiple transmissions, the
retained Somewear native composite transport owns them.
An explicit `cancelMessage()` removes the plan before canceling its parcels, so
it never spends satellite later.

`SendReceipt.satelliteFallbackArmed` is true when the Radio attempt is active
and a future Satellite attempt is armed. After handover,
`deliveryStatus(messageId).deliveredChannel` changes to `SATELLITE`. The
Satellite attempt uses `satelliteTimeoutMillis` (five minutes by default), not
the short Radio timeout.

Both route attempts carry the SC3 `messageId`. The receiver keeps the first
copy and suppresses a late duplicate from the other channel for 24 hours. Both
devices must run gateway v13 or newer for `RADIO_THEN_SATELLITE`; gateway v15
is required for oversized native-composite Satellite attempts. This does not change
the compatibility of ordinary unfragmented `RADIO_ONLY` messages.

### Delivery status

Read once:

```kotlin
val result = somewear.deliveryStatus(messageId)
```

Cancel the active Somewear parent belonging to an SC3 message. The retained core
cancels incomplete native Satellite children belonging to that parent; SC3
cancels each Radio fragment it owns:

```kotlin
somewear.cancelMessage(messageId)
```

Observe until terminal:

```kotlin
lifecycleScope.launch {
    somewear.observeDeliveryStatus(messageId).collect { update ->
        renderDelivery(update.status, update.deliveredChannel)
    }
}
```

Terminal states are `DELIVERED`, `ERROR`, `CANCELED`, and `COLLAPSED`. `deliveredChannel` reports the channel actually used, including Radio or Satellite.

An accepted send is not proof of peer delivery. Treat `SendReceipt` as queue acceptance and `DeliveryUpdate` as Node/service delivery evidence. For SC3-fragmented Radio, `DELIVERED` is reported only after every SC3 fragment is delivered. `receiverConfirmed=true` is stronger: the peer v17 gateway rebuilt and accepted the complete logical message. For native-composite Satellite, the retained core emits the parent terminal status after its children complete or fail.

### Reliable fragmented Radio messages

Gateway v17 keeps each outbound fragmented Radio message in its app-private
journal for up to 24 hours. The receiving gateway also journals every valid
fragment before reassembly. After a quiet period it automatically sends compact
Radio requests containing only the missing indexes; the sender selectively
requeues those retained fragments. When reassembly succeeds, the receiver sends
a completion acknowledgement and the sender removes its retained copy.

Normal SC3 code does not need to drive that loop. It can expose progress and an
operator retry button with these APIs:

```kotlin
val pending = somewear.listIncompleteMessageTransfers()

// Receiver: ask immediately instead of waiting for the next automatic round.
somewear.requestMissingMessageFragments(transferId)

// Sender: resend all retained fragments for this logical message.
somewear.retryFragmentedMessage(messageId)
```

`SendReceipt.transferId` and `receiverAcknowledgementAvailable` are populated
only for SC3-fragmented Radio sends. Read `DeliveryUpdate.receiverConfirmed` for
peer reassembly confirmation. `receiveHealth()` also exposes recovery-request,
retransmission, peer-acknowledgement, and recovery-error counters.

This selective request protocol is for v17-to-v17 SC3 Radio framing. Satellite
native `Part` records remain private to Somewear Core, so the SDK cannot request
one missing Satellite child. For a terminal Satellite failure, SC3 must make an
operator-approved retry of the complete logical message. The file-batch API
below uses the workspace catalogue instead of message fragments and can recover
individual missing files by ID.

### Incoming messages

`initialize()` binds the gateway receive service. Keep the same `SomewearClient`
open and start the collector before asking the peer to transmit. For an
application-wide receiver, use an application/service-owned coroutine scope
rather than a short-lived screen scope.

Poll explicitly:

```kotlin
val result = somewear.pollIncomingMessages(
    afterSequence = lastStoredSequence,
    limit = 50,
)
```

Or collect continuously:

```kotlin
lifecycleScope.launch {
    somewear.incomingMessages(afterSequence = lastStoredSequence).collect { message ->
        // One SC3 database transaction: insert/deduplicate the payload and save
        // message.sequence as the new contiguous cursor.
        sc3Database.persistIncomingAndCursor(message)

        // Only after that transaction commits, release the gateway's local copy.
        somewear.acknowledgeIncomingMessagesThrough(message.sequence)
    }
}
```

Completed messages are now stored in the gateway's app-private durable inbox,
not only RAM. The monotonic sequence survives gateway process restarts and an
unacknowledged message is returned again. Acknowledgement is cumulative: never
acknowledge a later sequence until SC3 has durably stored every earlier message.
It deletes only the gateway's local copy; it is not a radio, Satellite, or
Somewear-server delivery receipt. Message UUIDs should also be deduplicated
because a transport retry can redeliver application content.

The inbox retains at most 2,000 unacknowledged messages. Monitor
`droppedIncomingCount`; any non-zero increase means the consumer fell behind and
the oldest local records had to be discarded. `persistentInboxEnabled=false`
means private storage initialization failed and the gateway has fallen back to
the pre-existing RAM queue.

Use `receiveHealth()` when Satellite appears silent. A healthy v15 native
Satellite receive reaches the gateway as one parent `MessagePayload` with
`lastDeliveredChannel=SATELLITE` and `lastPayloadOutbound=false`. If
`routerCallbackCount` stays at zero, the retained core has not completed the
downlink parent. If callbacks rise but `ignoredInboundCount` rises, inspect
`lastPayloadType`: this SDK accepts SC3 `MessagePayload` traffic, not ATAK
`TakMessagePayload`/CoT traffic. `activeTransportReassemblies` applies to SC3
Radio framing and legacy v14 Satellite frames, not native v15 Satellite parts.

Inspect the physical receive path without exposing message contents or keys:

```kotlin
when (val health = somewear.receiveHealth()) {
    is SomewearResult.Success -> logSafeReceiveHealth(health.value)
    is SomewearResult.Failure -> showError(health.error)
}
```

- `subscriptionActive=false`: the matching gateway service is not bound/started.
- `sdkReceiveServiceConnected=false`: SC3's service anchor is not currently
  connected. Android package replacement is rebound automatically; call
  `ensureReceiving()` to revalidate it explicitly after an unusual lifecycle event.
- `subscribedRouterMatchesCurrent=false`: the retained core replaced its router;
  the next poll or `ensureReceiving()` replaces the stale subscription.
- `coreConfigured=false`: Somewear Core has not completed setup.
- `packageStreamStarted=false`: the retained Core reports that its cloud package
  stream is stopped. `null` means this private diagnostic is unavailable in that
  vendor build; it does not itself prove a receive failure.
- `routerCallbackCount=0` after a peer transmission: the retained core saw no
  traffic; check both Nodes, active workspace, mesh key, radio channel, and range.
- `ignoredInboundCount>0`: traffic arrived but was not a Somewear `MessagePayload`
  (for example ATAK CoT/location traffic is not an SC3 message).
- `errorCount>0`: inspect `lastError`, which contains only a stage and exception
  class, never payload data.
- `queuedIncomingCount>0` while SC3 shows nothing: fix SC3's Flow/cursor/UI path.
- `oldestSequence`, `latestSequence`, and `acknowledgedThroughSequence` show the
  exact local-inbox window; `droppedIncomingCount` must remain zero.

This hardening protects messages after Somewear Core invokes the gateway
callback. If `routerCallbackCount` does not increase, no wrapper can reconstruct
that missing downlink; capture the health snapshot and vendor Node/Core logs.

### Files and images

Use `sendFile()` for images, documents, audio, or other files. Give it a
caller-readable `content://` URI from Android's photo picker or document picker:

```kotlin
val result = somewear.sendFile(
    FileSendRequest(
        workspaceId = workspaceId,
        sourceUri = selectedImageUri,
        fileName = "recon-01.jpg",       // optional; inferred when omitted
        mimeType = "image/jpeg",         // optional; inferred when omitted
        routePolicy = RoutePolicy.RADIO_ONLY,
        // For an approved fallback instead:
        // routePolicy = RoutePolicy.RADIO_THEN_SATELLITE,
        // satelliteTimeoutMillis = 300_000L,
    ),
)

when (result) {
    is SomewearResult.Success -> {
        val fileId = result.value.fileId
        val metadataMessageId = result.value.metadataDelivery.messageId
    }
    is SomewearResult.Failure -> showError(result.error)
}
```

The SDK hashes and counts the URI, obtains an authenticated signed upload ticket
from Somewear, streams the bytes directly to that URL, and then sends a native
`FileMetadataPayload` through the selected Node channel. File bytes never enter
an Android IPC `Bundle`, so large images do not hit Binder's transaction limit.
The upload URL is private to the SDK/gateway exchange and is not returned to SC3.

Important transport distinction: Radio or Satellite carries the small file
announcement, not the image bytes. The sender needs data access to upload before
sending; the receiver needs data access to download. This is the file workflow
present in the retained Somewear core. It is not peer-to-peer multi-megabyte
image transfer over the mesh radio. For small JSON sent directly over radio,
the existing bounded message limit remains 64 KiB.

Receive file announcements using a cursor just like messages:

```kotlin
lifecycleScope.launch {
    somewear.incomingFiles(afterSequence = lastFileSequence).collect { file ->
        persistFileMetadata(file)
        // Download only after operator/policy approval.
    }
}

val page = somewear.pollIncomingFiles(afterSequence = lastFileSequence, limit = 50)
```

Download into a URI that SC3 can write:

```kotlin
when (val downloaded = somewear.downloadFile(incomingFile, destinationUri)) {
    is SomewearResult.Success -> verifySize(downloaded.value.bytesWritten)
    is SomewearResult.Failure -> showError(downloaded.error)
}
```

#### Tell the receiver how many files belong to one operation

Use `sendFileBatch()` when several files form one logical report. The list is
variable-size; `5` is not hard-coded. The SDK uploads every file first and then
uploads and announces one authoritative manifest last. That manifest contains:

- `batchId` and `revision`;
- `expectedFileCount`;
- each file's zero-based index, Somewear `fileId`, name, MIME type, byte count,
  and SHA-256 hash;
- `finalRevision`, which tells SC3 whether the sender plans a later complete
  revision for the same batch ID.

Sender example with five files (the same call accepts more):

```kotlin
val result = somewear.sendFileBatch(
    FileBatchSendRequest(
        workspaceId = workspaceId,
        files = selectedUris.map { uri ->
            FileBatchItemRequest(sourceUri = uri)
        },
        routePolicy = RoutePolicy.RADIO_ONLY,
    ),
)

if (result is SomewearResult.Success) {
    check(result.value.manifest.expectedFileCount == selectedUris.size)
    saveBatchId(result.value.manifest.batchId)
}
```

On the receiver, ordinary file announcements may arrive in any order. The
manifest announcement is identified by `IncomingFile.isFileBatchManifest`.
Reading it tells SC3 the exact expected set, and reconciliation uses the remote
workspace catalogue so a missed Radio/Satellite announcement does not lose the
file:

```kotlin
lifecycleScope.launch {
    somewear.incomingFiles(afterSequence = lastFileSequence).collect { incoming ->
        if (!incoming.isFileBatchManifest) return@collect

        when (val decoded = somewear.readFileBatchManifest(incoming)) {
            is SomewearResult.Failure -> showError(decoded.error)
            is SomewearResult.Success -> {
                val manifest = decoded.value
                val state = somewear.reconcileFileBatch(manifest)
                renderExpectedCount(manifest.expectedFileCount)
                renderBatchState(state)

                somewear.syncFileBatch(manifest).collect { event ->
                    renderContentSync(event)
                }
            }
        }
    }
}
```

`reconcileFileBatch()` reports exact remote `missingFileIds` and locally
verified `cachedFileIds`. `syncFileBatch()` downloads only the manifest IDs,
retries with fresh signed tickets, and verifies both size and SHA-256 before an
atomic publish. A manifest can be found later through `listWorkspaceFiles()` by
its MIME type `application/vnd.sc3.file-batch+json`, even if its channel
announcement was missed. Each higher revision is a complete replacement list,
not an unbounded delta; receivers should keep the highest revision seen for a
`batchId`.

Missing a Radio/Satellite metadata announcement no longer makes a cloud file
undiscoverable. Read the authenticated workspace catalogue directly:

```kotlin
when (val page = somewear.listWorkspaceFiles(workspaceId, offset = 0, limit = 100)) {
    is SomewearResult.Success -> renderFiles(page.value.files)
    is SomewearResult.Failure -> showError(page.error)
}
```

Let the SDK find and recover every missing file into its app-private cache:

```kotlin
lifecycleScope.launch {
    somewear.syncWorkspaceContent(
        WorkspaceContentSyncRequest(
            workspaceId = workspaceId,
            maxDownloadAttempts = 3,
        ),
    ).collect { event ->
        when (event) {
            is WorkspaceContentSyncEvent.Downloaded -> use(event.file.cachedUri!!)
            is WorkspaceContentSyncEvent.FileFailed -> showError(event.error)
            is WorkspaceContentSyncEvent.Completed -> renderSummary(event.summary)
            else -> renderProgress(event)
        }
    }
}
```

If SC3 knows exact file IDs that are missing, request only those IDs. Unknown IDs
are reported through `WorkspaceContentSyncEvent.NotFound`:

```kotlin
somewear.syncWorkspaceContent(
    WorkspaceContentSyncRequest(
        workspaceId = workspaceId,
        fileIds = setOf("file-id-17", "file-id-22"),
    ),
).collect(::renderContentSync)
```

The sync operation obtains a fresh signed URL for every retry, streams into a
temporary file, verifies the byte count from Somewear metadata, and atomically
publishes only a complete file. It uses at most three attempts by default. The
last catalogue remains available offline through `cachedWorkspaceFiles()`.

SC3 can launch the SDK-owned content page instead of implementing this UI:

```kotlin
startActivity(WorkspaceContentActivity.createIntent(this, workspaceId))
```

The page lists remote content, shows local status, downloads one selected file,
or downloads all missing files. It uses app-private storage and requires no
storage permission or C2 transport/reassembly code.

`FileSendReceipt.metadataDelivery` is queue acceptance for the small
announcement. Continue with `deliveryStatus()` or `observeDeliveryStatus()` to
confirm its terminal channel/status. A successful upload alone does not prove
that the peer received the announcement. For Satellite metadata,
`metadataDelivery.backhaulAckRequired` and `satelliteNativeComposite` expose the
same reliability contract as `send()`. Failed announcement sends can leave an
uploaded but unannounced file in the Somewear service.

The catalogue/recovery path fixes that missed-announcement case. It still needs
authenticated IP connectivity because the retained Somewear file service owns
the bytes. It does not turn a large image into an offline mesh-radio transfer.
Native Satellite composite children remain private to Somewear Core: the SDK
cannot ask for one native child such as “part 4”; a terminal Satellite failure
requires retrying the complete logical message under operator policy.

### Workspace and mesh-key readiness

#### Fresh installation: scan and join

Register the scanner once in an SC3 `ComponentActivity` or Fragment:

```kotlin
private val scanSomewearWorkspace = registerForActivityResult(
    WorkspaceQrScanContract(),
) { result ->
    when (result) {
        is WorkspaceQrScanResult.Success -> lifecycleScope.launch {
            when (val joined = somewear.joinWorkspace(result.inviteCode)) {
                is SomewearResult.Success -> {
                    val workspaceId = joined.value.workspace.workspaceId
                    persistWorkspaceId(workspaceId)
                    renderWorkspace(joined.value.workspace)
                }
                is SomewearResult.Failure -> showError(joined.error)
            }
        }
        is WorkspaceQrScanResult.Failure -> showError(result.message)
        WorkspaceQrScanResult.Cancelled -> Unit
    }
}
```

After `initialize()` succeeds, launch it:

```kotlin
scanSomewearWorkspace.launch(Unit)
```

The SDK proxy opens a signature-protected activity in the gateway package; SC3 does not load a barcode library. The gateway scanner accepts the same decoded Somewear invitation URI used by the retained Somewear flow: either a service `token`, or a `meshKey` plus `workspaceId`. It returns the decoded invite only to SC3's result callback. Call `joinWorkspace()` immediately; do not log, persist, or place the invite in analytics. The gateway consumes it in memory, calls Somewear's retained repository, activates the joined workspace, and refreshes the shared cache.

To support a paste-code screen as well as the camera, validate and submit the pasted value directly:

```kotlin
val metadata = WorkspaceInviteCode.inspect(pastedCode) // no token/key in metadata
if (metadata != null) {
    val joined = somewear.joinWorkspace(pastedCode)
}
```

A service-token invite can be invalid, expired, revoked, offline, or target a different Somewear environment. Handle `INVALID_INVITE`, `NETWORK_UNAVAILABLE`, `TIMEOUT`, and `ENVIRONMENT_MISMATCH` explicitly. `ENVIRONMENT_MISMATCH` is intentionally not switched silently because changing the Somewear backend is an operational/security decision.

#### Existing installation and refresh

Force a network/authentication refresh before reading the local cache when needed:

```kotlin
val provisioning = somewear.workspaceProvisioningStatus()
val synchronized = somewear.syncWorkspaces()
```

```kotlin
when (val workspaces = somewear.listWorkspaces()) {
    is SomewearResult.Failure -> showError(workspaces.error)
    is SomewearResult.Success -> {
        // Success(emptyList()) means no numeric workspace is currently cached.
        val selected = workspaces.value.firstOrNull { it.member && it.ready }
        if (selected == null) {
            showNoReadyWorkspace()
        } else {
            when (val activated = somewear.activateWorkspace(selected.id)) {
                is SomewearResult.Failure -> showError(activated.error)
                is SomewearResult.Success -> {
                    check(activated.value.active)
                    persistWorkspaceId(activated.value.workspaceId)
                }
            }
        }
    }
}

val active = somewear.activeWorkspace()
val workspace = somewear.workspaceStatus(workspaceId)
val meshKey = somewear.meshKeyStatus(workspaceId)
```

`initialize()` boots Somewear Core and resumes the gateway's retained external identity. `listWorkspaces()` is deliberately local-only; `Success(emptyList())` means the cache is empty, not that synchronization ran. On a fresh installation, scan/paste an invite and call `joinWorkspace()`. On an already enrolled installation, call `syncWorkspaces()` before treating the list as current. `UNSUPPORTED` means an old gateway APK is installed.

The SDK now joins and activates a workspace from an approved Somewear invite; it still does not create Somewear accounts or expose credentials. Once joined/synchronized, `listWorkspaces()` is the source of the numeric `workspaceId` that SC3 passes to `activateWorkspace()` and `SendRequest`.

Workspace activation is not a Bluetooth/USB connection. It selects the signed-in Somewear identity's routing/key context. The gateway refuses to activate a cached workspace when `member == false`. `ready` means the identity is a member and synchronized mesh-key material is present. Both peer Nodes still need compatible workspace/traffic keys for radio communication. `meshKeyStatus()` exposes only a 16-character SHA-256 fingerprint; it never exports key material.

The SC3 API uses positive `Long` workspace IDs because Somewear `MessagePayload` routes using a numeric workspace ID. Client-generated/non-numeric ad-hoc workspaces are omitted from `listWorkspaces()` and cannot be used by this gateway build.

## Recommended SC3 startup sequence

```kotlin
suspend fun startSomewear(
    client: SomewearClient,
    workspaceId: Long,
    bluetoothAddress: String,
): SomewearResult<DeviceStatus> {
    when (val initialized = client.initialize()) {
        is SomewearResult.Failure -> return initialized
        is SomewearResult.Success -> Unit
    }

    when (val workspace = client.activateWorkspace(workspaceId)) {
        is SomewearResult.Failure -> return workspace
        is SomewearResult.Success -> if (!workspace.value.active || !workspace.value.ready) {
            return SomewearResult.Failure(
                SomewearError(
                    SomewearErrorCode.GATEWAY_REJECTED,
                    "Somewear workspace is not ready",
                ),
            )
        }
    }

    return client.connectBluetooth(bluetoothAddress)
}
```

## Errors

All one-shot calls return `SomewearResult<T>`:

```kotlin
when (val result = somewear.send(request)) {
    is SomewearResult.Success -> saveReceipt(result.value)
    is SomewearResult.Failure -> when (result.error.code) {
        SomewearErrorCode.PERMISSION_DENIED -> showSigningProblem()
        SomewearErrorCode.UNSUPPORTED -> showGatewayUpgradeRequired()
        SomewearErrorCode.TIMEOUT -> queueForOperatorReview()
        else -> showError(result.error.message)
    }
}
```

Flow APIs throw `SomewearSdkException` when polling cannot continue. Collect them within the SC3 lifecycle and use normal coroutine retry/backoff if appropriate.

USB-specific errors are represented separately:

- `USB_NO_DEVICE`
- `USB_NO_DRIVER`
- `USB_PERMISSION_DENIED`
- `USB_OPEN_DEVICE_FAILED`
- `USB_OPEN_PORT_FAILED`
- `USB_INCORRECT_DEVICE`

File-specific errors are:

- `FILE_READ_FAILED`: SC3's URI permission expired or the source cannot be reopened.
- `FILE_LIST_FAILED`: the authenticated workspace catalogue could not be read.
- `FILE_UPLOAD_FAILED`: the upload ticket or signed-URL PUT failed.
- `FILE_DOWNLOAD_FAILED`: the download ticket, GET, or destination URI failed.
- `FILE_INTEGRITY_FAILED`: the completed byte count or manifest SHA-256 did not match.
- `FILE_CACHE_FAILED`: the SDK could not update its app-private catalogue/cache.
- `FRAGMENT_RECOVERY_FAILED`: the private Radio recovery journal was unavailable.

## Low-level gateway IPC contract

Authority:

```text
content://com.somewearlabs.swtak.plugin.somewear.gateway
```

Permission:

```text
com.somewearlabs.swtak.plugin.permission.SOMEWEAR_GATEWAY
```

Every method returns a `Bundle` with:

| Key | Type | Meaning |
|---|---|---|
| `ok` | Boolean | Whether the gateway accepted/completed the IPC request. |
| `message` | String | Human-readable result or error. |
| `error_code` | String? | Stable machine-readable failure code. |

### Methods

| Method | Request extras | Response extras |
|---|---|---|
| `info` | none | `api_version`, `capabilities` |
| `activate` | none | common result |
| `connectBle` | `address: String` | accepted operation |
| `connectUsb` | none | accepted operation or USB error |
| `getDeviceStatus` | none | `connection_state`, `operation_state`, `operation_result`, `local_transport` |
| `getNodeTelemetry` | none | battery, charge/power/activity, satellite quality/sendable, firmware/identity/GPS/tracking fields |
| `getMeshNetworkStatus` | none | mesh availability, peer/next-hop, hops, RSSI, backhaul, timestamps |
| `cancelConnection` | none | common result |
| `setConnectionMode` | `connection_mode: BLUETOOTH or USB` | accepted setting command |
| `getHardwareSettings` | none | nullable tracking, network, radio, feedback, button, and connection-mode fields |
| `setTrackingEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setTrackingInterval` | `tracking_gps_seconds`, `tracking_sending_seconds` | acknowledged setting command |
| `setBackhaulEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setSatelliteEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setMeshRadioEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setRadioChannel` | `low_speed_frequency_hz`, `high_speed_frequency_hz` | acknowledged setting command |
| `setMeshTransmissionStrength` | `mesh_transmission_strength: LOW, MEDIUM, or HIGH` | acknowledged setting command |
| `setLedLightEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setVibrationFeedbackEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setEnduranceModeEnabled` | `enabled: Boolean` | acknowledged setting command |
| `setDeviceButtonFunction` | `device_button_function` | acknowledged setting command |
| `factoryReset` | `factory_reset_confirmation: ERASE_NODE` | destructive reset result |
| `disconnect` | none | common result |
| `shutdown` | none | common result |
| `powerOn` / `powerOff` | none | accepted connected-Node power command |
| `sendMessageV2` | `message_id`, `message`, `workspace_id`, optional `target_user_id`, `route_policy`, `radio_timeout_ms`, `satellite_timeout_ms` | `message_id`, optional `parcel_id`/`transfer_id`, `receiver_ack_available`, fragment/transport fields, `satellite_fallback_armed`, `accepted_at_ms` |
| `cancelMessage` | `message_id` | canceled fragment count |
| `getDeliveryStatus` | `message_id` | delivery/channel/error/time plus `receiver_confirmed` and optional confirmation time |
| `pollIncomingMessages` | `after_sequence`, `limit` | `items: ArrayList<Bundle>` |
| `acknowledgeIncomingMessages` | `through_sequence` | `acknowledged_through_sequence`, `remaining_count` |
| `listIncompleteMessageTransfers` | none | incomplete Radio transfers with counts, exact missing indexes, and request state |
| `requestMissingMessageFragments` | `transfer_id` | requested fragment count and recovery round |
| `retryFragmentedMessage` | `message_id` | retained transfer ID and requeued fragment count |
| `prepareFileUpload` | name, MIME, SHA-256, byte count, workspace | internal signed-upload ticket and file metadata |
| `sendFileMetadata` | prepared file metadata, message ID, workspace, route policy, radio/satellite timeouts | parcel/message/file acceptance fields and `satellite_fallback_armed` |
| `pollIncomingFiles` | `after_sequence`, `limit` | file-metadata `items: ArrayList<Bundle>` |
| `listWorkspaceFiles` | `workspace_id`, `offset`, `limit` | bounded file-metadata page, `total_count`, `next_offset` |
| `getFileDownloadUrl` | `file_id`, `workspace_id` | internal signed-download ticket |
| `getReceiveHealth` | none | subscription, receive/reassembly/queue state, last event, recovery request/retransmit/peer-ack/error counters |
| `joinWorkspace` | `invite_code`, optional `workspace_timeout_ms` | joined workspace fields, `workspace_sync_completed` |
| `syncWorkspaces` | optional `workspace_timeout_ms` | `workspaces: ArrayList<Bundle>` |
| `getWorkspaceProvisioningStatus` | none | `authenticated`, `auth_state`, `workspace_count`, `has_active_workspace` |
| `listWorkspaces` | none | `workspaces: ArrayList<Bundle>` |
| `getActiveWorkspace` | none | `has_active_workspace`; when true, workspace fields |
| `activateWorkspace` | `workspace_id` | workspace fields with `workspace_active=true` |
| `getWorkspaceStatus` | `workspace_id` | `workspace_name`, `workspace_ready`, `workspace_active`, `workspace_member`, `mesh_key_installed` |
| `getMeshKeyStatus` | `workspace_id` | `mesh_key_installed`, optional `mesh_key_id` |

An incoming-message bundle contains:

```text
sequence: Long
message_id: String
message: String
workspace_id: Long
sender_id: String?
received_at_ms: Long
delivered_channel: String
```

The signed upload/download methods are internal implementation details of the
typed SDK. Applications should call `sendFile()` and `downloadFile()` so URLs
are not logged, persisted, or exposed to UI code.

## Gateway compatibility

| Capability | Gateway v18 | Validation/work remaining |
|---|---:|---:|
| Information and activation | Yes | Emulator validated |
| Bluetooth connect/status/cancel/disconnect | Yes | Physical Node validation |
| USB connection initiation | Yes | Hardware validation |
| Change Node connection mode between Bluetooth and USB/LineIn | Yes | Physical restart/reconnect validation |
| State-change-only connection observer | Yes | Unit tested and Android regression validated with one unchanged emission over 2.5 seconds |
| Read/update Node hardware settings | Yes | Retained settings types and disconnected safety validated on Android; live Node acknowledgement still requires hardware acceptance |
| Factory reset with explicit confirmation | Yes | Retained device-management bridge verified on Android; destructive live-Node test intentionally not run |
| Explicit radio/satellite `SendOptions` | Yes | Hardware validation |
| Oversized JSON over Radio without Satellite | Yes | Durable journal/protocol unit tested; two v17 Android runtimes each omitted one fragment, cleared volatile assembly state, restored/reassembled the exact JSON from the journal, delivered it once, and removed the incomplete record; physical peer-radio acceptance remains |
| Oversized JSON over Satellite | Yes | On two clean Android emulator installations, exact 504-byte RFT and 2,171-byte CAS JSON each queued one acknowledged native parent with estimates of 2/8 transmissions; clear-sky website and peer-phone acceptance remains |
| Inbound `SomewearRouter.getPayload()` bridge | Yes | Retained `MessagePayload`→`RouterPayload` parser→SDK Flow validated on Android; physical peer validation remains |
| Durable completed-message replay and local acknowledgement | Yes | 2.3 KB CAS JSON replayed after forced gateway-process restart with the same sequence and was removed only after acknowledgement; 100 KB persistence and concurrent provider/service startup race unit tested |
| Delivery status and actual channel | Yes | Multi-fragment aggregation unit tested; live Node terminal acknowledgements require hardware validation |
| Message cancellation | Yes | Fragment cancellation aggregation unit tested; live Node cancellation acknowledgement requires hardware validation |
| Node telemetry and satellite quality | Yes | Two Android runtimes returned stable disconnected snapshots without core crashes; connected values require hardware validation |
| Mesh-network snapshot | Yes | Empty-state Android validation passed; live peer RSSI/topology requires hardware validation |
| File/image metadata receive | Yes | Native 25 MB and 50 MB image metadata records parsed and polled as Radio on two Android runtimes without transferring bytes through Binder |
| File/image upload and download | Yes | SDK/gateway build and signed-ticket paths implemented; live authenticated Somewear service upload/download remains account acceptance testing |
| Workspace file catalogue, batches, and selective recovery | Yes | Variable 17-file manifest round trip passed on two Android runtimes; exact-ID planning, bounded retry, atomic 2 MiB download, truncation/hash rejection, and offline catalogue cache unit tested; live authenticated service acceptance remains |
| Automatic radio-then-satellite fallback | Yes | Race/timeout/cancel/dedup unit tested; the signed APK queued Radio then Satellite on two Android runtimes and exposed `SATELLITE` delivery status; live over-air terminal delivery still requires provisioned Nodes |
| QR scanner and invite validation | Yes | Clean SC3 runtime without ML Kit/CameraX launched gateway camera on emulator; parser has unit coverage |
| Workspace join/sync/list/selection/readiness | Yes | Authenticated empty-cache sync and invalid-invite backend rejection emulator validated; a real issued invite and key transfer require account/hardware acceptance |
| Bound service lifetime | Yes | Android idle/frozen-provider regression validated; keep `SomewearClient` open |

Unsupported calls return `SomewearErrorCode.UNSUPPORTED`; they never fall back to the unsafe legacy all-channel send.

## Gateway implementation rules

1. Keep `SomewearCoreConfig`, `SomewearDevice`, `SomewearRouter`, `MessagePayload`, `SendOptions`, workspace storage, authentication, and protobuf models private to the gateway APK.
2. Use `MessagePayload` for SC3 mesh traffic. Generic raw `DataPayload` is not in the retained router's mesh-supported package list.
3. Preserve the SC3 UUID and map it to the Somewear parcel ID.
4. Subscribe to `SomewearRouter.getPayload()` for inbound payloads and status updates.
5. Export `RouterPayload.summaryStatus` and `RouterPayload.deliveredDeviceChannel`.
6. Make `RADIO_ONLY` the safe default and never widen its channel set.
7. Preflight the fully encoded package. Use SC3 checksummed fragments only for
   oversized Radio messages. Hand an oversized Satellite message to the retained
   core as one parent so its native `CompositePackager`/`PostOffice` protocol is
   preserved for the Node, service, website, and peer gateway.
8. Implement `RADIO_THEN_SATELLITE` as two serialized attempts: Radio first,
   then at most one Satellite-only send after a failed terminal state or timer.
   Never pass `{Radio, Satellite}` to the retained core together.
9. Require retained backhaul acknowledgement for every Satellite parent/native
   child. Do not enable unbounded automatic Satellite retry; expose failure so
   SC3 can request an operator-approved retry.
10. Split oversized Radio traffic above Somewear Core into ordinary `MessagePayload` records; never use the retained Radio `PackageType.Part` path.
11. Persist SC3 Radio fragments privately, request only missing indexes, and retain outbound frames until peer acknowledgement or bounded expiry.
12. Keep USB/Bluetooth permissions and connection ownership in the gateway package.
13. Run long-lived connection and inbound collection work from a foreground or bound service. The ContentProvider may remain as the command/polling compatibility surface.
14. Do not export authentication secrets, traffic keys, or mesh-key bytes through IPC.

## Security and deployment

The gateway provider is protected by a signature-level permission. SC3 and the gateway must be signed with the same certificate. A mismatched signature produces `PERMISSION_DENIED`.

The provided gateway build is reverse-engineered and intended for controlled prototype testing. Validate Somewear licensing, provisioning, firmware compatibility, and operational approval before deployment.

## Deliberately not exposed

- ATAK APIs or ATAK Core.
- DDS or MQTT transports.
- Generic raw radio payloads.
- Mesh-key or authentication secret material.
- An automatic satellite fallback outside an explicit SC3 route policy.
