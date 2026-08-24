# Complete SC3/Somewear handover

The SDK AAR is a client. It cannot communicate with a Somewear Node by itself. A working device requires two installed Android packages:

1. SC3, containing `somewear-gateway-sdk-0.1.0.aar`.
2. The standalone Somewear gateway split package in `build/signed-splits-v2/`.

The gateway owns Somewear Core, Bluetooth/USB, radio/satellite routing, storage, and the exported `ContentProvider`. SC3 calls it through Android IPC.

## Included material

- `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
- `somewear-gateway-sdk/dist/sc3-somewear.gradle.kts` (local-AAR dependency block)
- Five gateway APKs under `build/signed-splits-v2/`
- `gateway-patches/SomewearGatewayProvider.smali`
- `gateway-patches/SomewearPlugin.smali`
- `gateway-patches/PluginConfig$Companion.smali`
- `gateway-patches/ConnectionContinuation.smali`
- `somewear-gateway-sdk/gateway-helper/src/main/java/com/somewearlabs/gateway/GatewayV2.java`
- Signing, installation, and validation scripts under `handover/scripts/`
- Portable Windows verifier/signer at `handover/dist/somewear-handover-tools.jar`
- Artifact hashes in `handover/SHA256SUMS`

The full decoded vendor tree, signing private keys, SC3 source, device credentials, authentication tokens, and workspace/mesh-key material are not included.

## Prerequisites

On Windows, verification and re-signing require only Java 17 or newer. The `.bat` scripts automatically find Java from `JAVA_HOME`, `PATH`, Android Studio's bundled runtime, or common JDK installation directories. They do **not** require `apksigner`, `aapt2`, `zipalign`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`.

Installing onto a phone still requires Android SDK Platform-Tools (`adb`). Install it through Android Studio only when device installation is needed:

```text
Tools > SDK Manager > SDK Tools > Android SDK Platform-Tools
```

From Windows PowerShell or Command Prompt, start with this one command:

```bat
.\handover\scripts\verify_gateway_artifacts.bat
```

On macOS/Linux, the existing `.sh` scripts still use Android SDK Build-Tools. They automatically search the standard SDK locations; set a custom location only if necessary:

```sh
export ANDROID_SDK_ROOT='/absolute/path/to/Android/sdk'
```

## 1. Validate the checked-in artifacts

Then run:

```sh
./handover/scripts/verify_gateway_artifacts.sh
```

Windows:

```bat
.\handover\scripts\verify_gateway_artifacts.bat
```

This confirms that every split is present, every split has the same valid APK signer, and all committed hashes match. For the checked-in base APK, the provider declaration is bound to that already-inspected artifact by its committed hash.

## 2. Re-sign the gateway with the SC3 certificate

The gateway permission is signature protected. SC3 and all five gateway APKs must have the same signing-certificate SHA-256 digest.

**Only re-sign the five APKs from `build/signed-splits-v2`.** That base APK contains the standalone provider and the Somewear startup bootstrap that initializes vendor singletons such as `instanceProvider`. Do not use the original ATAK/Somewear APK, `build/signed-splits`, or an older handover build. The re-signing scripts verify every input hash and stop with `input is not the prepared standalone gateway` if the wrong build is supplied.

Do not commit or send a production keystore. For a controlled prototype, the recipient should use a dedicated team test keystore and configure their SC3 build to use it.

Set password variables without placing passwords in shell history:

```sh
export GATEWAY_KEYSTORE_PASSWORD='test-keystore-password'
export GATEWAY_KEY_PASSWORD='test-key-password'
```

Then re-sign all splits:

```sh
./handover/scripts/resign_gateway.sh \
  build/signed-splits-v2 \
  handover/out/signed-gateway \
  /absolute/path/to/sc3-team-test.jks \
  sc3-test
```

Configure SC3's Android `signingConfig` with that same keystore and alias. The script never copies or commits the keystore.

Windows PowerShell equivalent:

```powershell
$env:GATEWAY_KEYSTORE_PASSWORD = "test-keystore-password"
$env:GATEWAY_KEY_PASSWORD = "test-key-password"
.\handover\scripts\resign_gateway.bat `
  build\signed-splits-v2 `
  handover\out\signed-gateway `
  C:\absolute\path\to\sc3-team-test.jks `
  sc3-test
```

The Windows signer is self-contained in the repository and preserves the APKs' existing alignment. No Android SDK Build-Tools setup is involved.

## 3. Resolve package conflicts

The standalone gateway uses package ID `com.somewearlabs.swtak.plugin`. An official/Play-signed copy of that package cannot be upgraded with a differently signed prototype build.

If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, first preserve any data you are authorized to retain. Uninstalling the existing package erases its local application data and credentials. The installation script does not uninstall anything automatically.

## 4. Install the gateway

Connect one ARM64 Android device with USB debugging enabled, then run:

```sh
./handover/scripts/install_gateway.sh handover/out/signed-gateway
```

Windows:

```bat
.\handover\scripts\install_gateway.bat handover\out\signed-gateway
```

For more than one connected device, pass the serial:

```sh
./handover/scripts/install_gateway.sh handover/out/signed-gateway DEVICE_SERIAL
```

The gateway has no launcher activity. The script installs all five splits and attempts to grant the transport permissions needed for prototype testing.

## 5. Install SC3 and run the preflight call

When SC3 consumes the checked-in AAR as `implementation(files(...))`, copy
`somewear-gateway-sdk/dist/sc3-somewear.gradle.kts` beside the AAR and apply it
from SC3's app module. Also set `android.useAndroidX=true` in SC3's root
`gradle.properties`. The script adds Activity 1.12.0 and coroutines. QR camera
and decoding now run inside the separately installed gateway, so SC3 must not
package CameraX or ML Kit for this SDK.

Install the SC3 APK signed with the same certificate. In SC3, create the client and call `info()` before initialization:

```kotlin
val somewear = SomewearGateway.create(applicationContext)

when (val result = somewear.info()) {
    is SomewearResult.Success -> {
        check(result.value.apiVersion >= 2)
    }
    is SomewearResult.Failure -> error(result.error.toString())
}
```

Expected provider authority:

```text
content://com.somewearlabs.swtak.plugin.somewear.gateway
```

Expected API version: `2`.

Only after `info()` succeeds should SC3 call `initialize()`. The current SDK
binds the gateway receive service during this call; keep the `SomewearClient`
open for the entire communications session. Then enroll/synchronize a workspace,
start `incomingMessages()`, connect Bluetooth/USB, and send messages.

Use `observeDeviceConnection()` for the UI connection stream. It emits the
initial state and then only real state/error changes. `observeDeviceStatus()` is
a compatibility alias. Neither method connects or disconnects the Node, so SC3
must not restart connection logic for every observed value.

After the Node is connected, `hardwareSettings()` returns its current nullable
settings and the typed setters cover tracking, interval, backhaul, satellite,
mesh radio, radio channel, mesh strength, LED, vibration, endurance, device
button, and Bluetooth/USB connection mode. See
`somewear-gateway-sdk/README.md#hardware-settings` for the exact calls. Treat
`factoryReset(FactoryResetConfirmation.ERASE_NODE)` as destructive: it clears
the Node and stored local bond/device and normally disconnects immediately.

`nodeTelemetry()` exposes the retained 0..5 satellite-quality value (sendable
at 2 or above), battery/firmware/tracking state, and other non-secret Node
health. `meshNetworkStatus()` exposes the latest mesh peer/next-hop/hops/RSSI
snapshot. Its `signalQuality` property converts the Node's raw mesh value to
`UNKNOWN`, `FAR`, `SOMEWHAT_CLOSE`, or `CLOSE` using the retained Somewear
thresholds. This is Node-to-Node mesh strength, not Bluetooth strength. These
reads do not select a transport. SC3 must still use an explicit `RoutePolicy`
for every send.

For images/documents, call `sendFile(FileSendRequest)` with an Android content
URI. The SDK streams the file to a Somewear signed upload URL, then sends only
native file metadata through Radio or Satellite. The receiver collects
`incomingFiles()` and calls `downloadFile()`. Both sides need data access for
file bytes; the Node channel carries the announcement, not a multi-megabyte
image. See `somewear-gateway-sdk/README.md#files-and-images`.

On a fresh install, `listWorkspaces()` can correctly return an empty cache. Register the SDK scanner and submit the result to the new enrollment API:

```kotlin
private val workspaceScanner = registerForActivityResult(
    WorkspaceQrScanContract(),
) { scan ->
    if (scan is WorkspaceQrScanResult.Success) {
        lifecycleScope.launch {
            when (val joined = somewear.joinWorkspace(scan.inviteCode)) {
                is SomewearResult.Success -> persistWorkspaceId(
                    joined.value.workspace.workspaceId,
                )
                is SomewearResult.Failure -> showError(joined.error)
            }
        }
    }
}
```

Call `somewear.syncWorkspaces()` to force remote synchronization on an existing installation; `listWorkspaces()` itself reads the retained local cache. Use `workspaceProvisioningStatus()` to distinguish authentication state from an empty cache. The gateway retains authentication and key material; SC3 must not log or store the QR invite.

## Error guide

| Result | Meaning | Resolution |
|---|---|---|
| `GATEWAY_NOT_INSTALLED` | The gateway base package/provider is absent or not visible. | Install all five splits and ensure the AAR manifest merged its `<queries>` entry. |
| `PERMISSION_DENIED` | SC3 and the gateway have different signing certificates. | Re-sign every gateway split and SC3 with the same keystore. |
| `lateinit property instanceProvider has not been initialized` | An original or older ATAK plugin was re-signed/installed without the standalone Somewear bootstrap. | Pull the latest repository, re-sign only `build/signed-splits-v2`, and install all five resulting APKs together. |
| `Call Realm.init(Context) before creating a RealmConfiguration` | The installed gateway predates the application-level Realm bootstrap. | Pull the latest repository, re-sign `build/signed-splits-v2`, and reinstall all five gateway splits. |
| `UNSUPPORTED` | The gateway lacks that API-v2 capability. | Check `info().capabilities`; do not fall back to legacy all-channel sending. |
| `PAYLOAD_TOO_LARGE_FOR_RADIO` | The message exceeds the v12 bounded radio-framing limit or cannot fit its framing header in one Node transmission. | Reduce/compress the application payload or use an explicitly approved transport; the gateway will not silently enable satellite. |
| `INVALID_INVITE` | The QR/pasted invite is malformed, expired, revoked, or rejected. | Scan a newly issued Somewear workspace invite and submit it once. Do not log it. |
| `NETWORK_UNAVAILABLE` or `TIMEOUT` | Workspace join/sync could not reach the Somewear service. | Restore internet access and retry the same operator-approved operation. |
| `ENVIRONMENT_MISMATCH` | The invite targets a different Somewear backend. | Obtain an invite for the deployed environment; do not silently change production/gov/custom endpoints. |
| `NoClassDefFoundError` mentioning `com.google.mlkit` while opening the scanner | SC3 still has the older SDK-owned scanner AAR. | Pull the latest repository, update the AAR, re-sign/reinstall the current five gateway splits, and remove any scanner-specific ML Kit/CameraX workaround. The scanner now runs in the gateway. |
| `NOT_FOUND` from a workspace call | Workspace synchronization has not populated that numeric ID, or the signed-in Somewear identity cannot see it. | Call `initialize()`, then `syncWorkspaces()`, and select an ID returned by `listWorkspaces()`; on a fresh install call `joinWorkspace()` first. |
| `NOT_MEMBER` from `activateWorkspace()` | The synchronized cache contains the workspace but the current Somewear identity is not a member. | Join/provision the identity through approved Somewear tooling, then initialize and synchronize again. |
| Native-library/ABI failure | The device is not ARM64 or its required split was omitted. | Use a compatible ARM64 physical device and install `config.arm64_v8a.apk`. |
| Bluetooth failure | Gateway permissions, bonding, provisioning, or Node reachability is incomplete. | Grant gateway permissions, bond the Node, then inspect `deviceStatus()`. |
| `NoKnownDeviceFound` for a valid Bluetooth MAC | The installed SDK/gateway predates explicit-MAC cache seeding, or the gateway lacks Nearby devices permission. | Pull the latest repository, re-sign/reinstall all five prepared gateway splits, update the AAR, grant Nearby devices permission, and retry. A Node requiring pairing may then report `PreBondingRequired`. |
| `Missing extras Bundle` from `connectUsb()` | SC3 is using an older AAR that sends a null provider extras Bundle, or the installed base gateway does not match the handover set. | Update the AAR and re-sign/reinstall all five APKs from `build/signed-splits-v2/`. The current SDK always sends an empty Bundle for USB. |
| `No virtual method getByteArray` mentioning `BaseBundle` | The caller reached an unsupported legacy raw-payload method, normally because the installed base gateway and AAR are from different handover versions. | Pull the latest repository, run the verifier, re-sign all five prepared splits, and reinstall them together. API v2 now blocks legacy raw and unknown methods before vendor-provider dispatch. |
| Connection observer repeatedly fires and connection logic disconnects/restarts | SC3 is using an older AAR, or the collector calls connect/disconnect for each value. | Update the AAR and all five gateway splits. Collect `observeDeviceConnection()` only for state changes and start a connection only from an explicit user/state-machine transition. |
| Hardware setter returns `NOT_CONNECTED` | Settings are written through the connected Node and no Bluetooth/USB Node is currently connected. | Wait for an observed connected state, then submit one setting operation and handle its acknowledgement/timeout. |
| Hardware call returns `UNSUPPORTED` or is missing at runtime | The AAR and installed base APK are from different handover revisions. | Pull one repository revision, run the verifier, then re-sign/reinstall all five splits and rebuild SC3 with that revision's AAR. |
| File call returns `FILE_READ_FAILED` | SC3 lost URI permission or the picker URI cannot be reopened. | Retain URI permission when applicable and keep the URI readable until `sendFile()` completes. |
| File call returns `FILE_UPLOAD_FAILED` or `FILE_DOWNLOAD_FAILED` | The signed-ticket request, data connection, HTTP transfer, or destination URI failed. | Confirm Somewear authentication/workspace membership, phone data access, and URI access; retry under application policy. |
| Connected but no incoming messages and no error | An older gateway had no receive-lifetime service and swallowed router callback exceptions, or SC3 is not collecting `incomingMessages()`. | Update both AAR and all five gateway splits. Start the Flow before peer transmission and inspect `receiveHealth()`: zero callbacks points to Node/workspace/radio delivery; ignored callbacks indicate a non-`MessagePayload`; queued messages point to an SC3 cursor/UI issue. |
| Small text works but larger JSON changes to Satellite or returns `ChannelDisabled` | The installed gateway predates v11 and allowed the retained Somewear composite path to rewrite split parts to Satellite. | Update the AAR and re-sign/reinstall all five prepared v12 gateway splits on both devices. Confirm `info().capabilities` contains `radio_fragmentation`. |
| Sender reports every fragment delivered over Radio, but the receiver exposes only the first two and reports no SDK error | Gateway v11 used the same whole-second timestamp for every `MessagePayload`, so the retained receiver discarded fragment three onward as duplicates. | Install/re-sign the prepared v12 split set on both devices and confirm `info().capabilities` contains `radio_fragment_dedup`. Do not substitute Raw/DataPayload; this retained router removes Radio from Raw traffic. |

## Operational limitations

- Physical radio/satellite delivery and successful live hardware-setting/reset acknowledgements still require provisioned Somewear hardware and compatible workspace/traffic keys.
- File/image upload and download require the authenticated Somewear service and
  phone data access. Only the small metadata announcement uses the selected Node
  channel; this build does not send multi-megabyte file bytes over mesh radio.
- `RADIO_THEN_SATELLITE` remains unsupported. QR enrollment, workspace synchronization, and cache selection are exposed, but a real issued invite, key transfer, and peer delivery still require account/hardware acceptance testing.
- The gateway artifacts contain vendor-derived code. Keep the repository and downstream artifacts access-controlled and obtain the required Somewear licensing/approval before deployment or redistribution.
