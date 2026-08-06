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

The SDK exposes the complete SC3-facing contract. Gateway v7 implements standalone initialization, QR invite scanning, fresh-install workspace enrollment/synchronization, Bluetooth connection, USB connection initiation, explicit radio-only and satellite-only sending, inbound router bridging, delivery-status polling, workspace listing/selection, and non-secret workspace/mesh-key readiness. Automatic radio-then-satellite fallback remains unsupported.

The SDK expects the separately installed gateway implementing the API-v2 contract documented below. The private handover repository includes the controlled-test gateway split set under `build/signed-splits-v2/`; see `handover/README.md` for re-signing and installation. No signing private key is committed.

The SDK intentionally refuses to use the gateway's legacy `sendMessage` method. That method lets Somewear Core use its default Radio + Satellite + Cellular channel set and is not safe when satellite cost must be controlled.

## Requirements

- Android 8.0/API 26 or newer.
- AndroidX enabled in the consuming project (`android.useAndroidX=true` in the
  root `gradle.properties`).
- Kotlin application or Java application with Kotlin/coroutines dependencies.
- The Somewear Gateway APK installed on the same Android device.
- SC3 and the gateway signed with the same certificate.
- Camera permission in SC3 for the SDK-owned QR scanner. The barcode model is bundled and works offline.
- Internet access while accepting a service-token invite or synchronizing workspaces.
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
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
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

Those AndroidX/ML Kit lines are required when consuming the checked-in AAR as a local file because a standalone AAR cannot carry Maven transitive-dependency metadata. When consuming the SDK from Maven coordinates, the published POM supplies them. The AAR contributes camera/gateway permissions, the scanner activity, and the Android package-visibility query through manifest merging.

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
    somewear.observeDeviceStatus().collect { result ->
        when (result) {
            is SomewearResult.Success -> render(result.value)
            is SomewearResult.Failure -> renderError(result.error)
        }
    }
}
```

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

Keep the payload small. `bodyJson` must be valid JSON and is inserted into the envelope without additional quoting.

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

Available policies:

| Policy | Required gateway behavior |
|---|---|
| `RADIO_ONLY` | Build `SendOptions` with only `DevicePayloadChannel.Radio`; disable backhaul. |
| `RADIO_THEN_SATELLITE` | Send radio-only, wait for terminal status/timeout, then make one satellite-only attempt if needed. |
| `SATELLITE_ONLY` | Build `SendOptions` with only `DevicePayloadChannel.Satellite`. |

For cost control, the gateway must implement radio-then-satellite as two controlled sends. It must not pass `{Radio, Satellite}` together as one channel set.

The SDK never silently upgrades `RADIO_ONLY` to satellite and never falls back to legacy `sendMessage`.

### Delivery status

Read once:

```kotlin
val result = somewear.deliveryStatus(messageId)
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

An accepted send is not proof of peer delivery. Treat `SendReceipt` as queue acceptance and `DeliveryUpdate` as delivery evidence.

### Incoming messages

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
        persistBeforeAcknowledgingToTheUi(message)
    }
}
```

Persist the highest received sequence in SC3. Message UUIDs should also be deduplicated because a radio retry may redeliver application content.

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

The scanner accepts the same decoded Somewear invitation URI used by the retained Somewear flow: either a service `token`, or a `meshKey` plus `workspaceId`. It returns the decoded invite only to SC3's result callback. Call `joinWorkspace()` immediately; do not log, persist, or place the invite in analytics. The gateway consumes it in memory, calls Somewear's retained repository, activates the joined workspace, and refreshes the shared cache.

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
| `cancelConnection` | none | common result |
| `setConnectionMode` | `connection_mode: BLUETOOTH or USB` | accepted setting command |
| `disconnect` | none | common result |
| `shutdown` | none | common result |
| `sendMessageV2` | `message_id`, `message`, `workspace_id`, optional `target_user_id`, `route_policy`, `radio_timeout_ms` | `message_id`, optional `parcel_id`, `accepted_at_ms` |
| `getDeliveryStatus` | `message_id` | `delivery_status`, `delivered_channel`, optional `error_reason`, `updated_at_ms` |
| `pollIncomingMessages` | `after_sequence`, `limit` | `items: ArrayList<Bundle>` |
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

## Gateway compatibility

| Capability | Gateway v7 | Validation/work remaining |
|---|---:|---:|
| Information and activation | Yes | Emulator validated |
| Bluetooth connect/status/cancel/disconnect | Yes | Physical Node validation |
| USB connection initiation | Yes | Hardware validation |
| Change Node to USB/LineIn mode | Yes | Hardware validation; Bluetooth restore not exported |
| Explicit radio/satellite `SendOptions` | Yes | Hardware validation |
| Inbound `SomewearRouter.getPayload()` bridge | Yes | Hardware validation |
| Delivery status and actual channel | Yes | Hardware validation |
| Automatic radio-then-satellite fallback | No, safely rejected | Implement terminal timeout policy |
| QR scanner and invite validation | Yes | Scanner activity/camera launch emulator validated; parser has unit coverage |
| Workspace join/sync/list/selection/readiness | Yes | Authenticated empty-cache sync and invalid-invite backend rejection emulator validated; a real issued invite and key transfer require account/hardware acceptance |
| Foreground/bound service lifetime | No | Recommended |

Unsupported calls return `SomewearErrorCode.UNSUPPORTED`; they never fall back to the unsafe legacy all-channel send.

## Gateway implementation rules

1. Keep `SomewearCoreConfig`, `SomewearDevice`, `SomewearRouter`, `MessagePayload`, `SendOptions`, workspace storage, authentication, and protobuf models private to the gateway APK.
2. Use `MessagePayload` for SC3 mesh traffic. Generic raw `DataPayload` is not in the retained router's mesh-supported package list.
3. Preserve the SC3 UUID and map it to the Somewear parcel ID.
4. Subscribe to `SomewearRouter.getPayload()` for inbound payloads and status updates.
5. Export `RouterPayload.summaryStatus` and `RouterPayload.deliveredDeviceChannel`.
6. Make `RADIO_ONLY` the safe default and never widen its channel set.
7. Keep USB/Bluetooth permissions and connection ownership in the gateway package.
8. Run long-lived connection and inbound collection work from a foreground or bound service. The ContentProvider may remain as the command/polling compatibility surface.
9. Do not export authentication secrets, traffic keys, or mesh-key bytes through IPC.

## Security and deployment

The gateway provider is protected by a signature-level permission. SC3 and the gateway must be signed with the same certificate. A mismatched signature produces `PERMISSION_DENIED`.

The provided gateway build is reverse-engineered and intended for controlled prototype testing. Validate Somewear licensing, provisioning, firmware compatibility, and operational approval before deployment.

## Deliberately not exposed

- ATAK APIs or ATAK Core.
- DDS or MQTT transports.
- Generic raw radio payloads.
- Mesh-key or authentication secret material.
- An automatic satellite fallback outside an explicit SC3 route policy.
