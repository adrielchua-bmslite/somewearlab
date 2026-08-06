# Complete SC3/Somewear handover

The SDK AAR is a client. It cannot communicate with a Somewear Node by itself. A working device requires two installed Android packages:

1. SC3, containing `somewear-gateway-sdk-0.1.0.aar`.
2. The standalone Somewear gateway split package in `build/signed-splits-v2/`.

The gateway owns Somewear Core, Bluetooth/USB, radio/satellite routing, storage, and the exported `ContentProvider`. SC3 calls it through Android IPC.

## Included material

- `somewear-gateway-sdk/dist/somewear-gateway-sdk-0.1.0.aar`
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

Only after `info()` succeeds should SC3 call `initialize()`, connect Bluetooth/USB, and send messages.

## Error guide

| Result | Meaning | Resolution |
|---|---|---|
| `GATEWAY_NOT_INSTALLED` | The gateway base package/provider is absent or not visible. | Install all five splits and ensure the AAR manifest merged its `<queries>` entry. |
| `PERMISSION_DENIED` | SC3 and the gateway have different signing certificates. | Re-sign every gateway split and SC3 with the same keystore. |
| `lateinit property instanceProvider has not been initialized` | An original or older ATAK plugin was re-signed/installed without the standalone Somewear bootstrap. | Pull the latest repository, re-sign only `build/signed-splits-v2`, and install all five resulting APKs together. |
| `Call Realm.init(Context) before creating a RealmConfiguration` | The installed gateway predates the application-level Realm bootstrap. | Pull the latest repository, re-sign `build/signed-splits-v2`, and reinstall all five gateway splits. |
| `UNSUPPORTED` | The gateway lacks that API-v2 capability. | Check `info().capabilities`; do not fall back to legacy all-channel sending. |
| Native-library/ABI failure | The device is not ARM64 or its required split was omitted. | Use a compatible ARM64 physical device and install `config.arm64_v8a.apk`. |
| Bluetooth failure | Gateway permissions, bonding, provisioning, or Node reachability is incomplete. | Grant gateway permissions, bond the Node, then inspect `deviceStatus()`. |
| `NoKnownDeviceFound` for a valid Bluetooth MAC | The installed SDK/gateway predates explicit-MAC cache seeding, or the gateway lacks Nearby devices permission. | Pull the latest repository, re-sign/reinstall all five prepared gateway splits, update the AAR, grant Nearby devices permission, and retry. A Node requiring pairing may then report `PreBondingRequired`. |
| `Missing extras Bundle` from `connectUsb()` | SC3 is using an older AAR that sends a null provider extras Bundle, or the installed base gateway does not match the handover set. | Update the AAR and re-sign/reinstall all five APKs from `build/signed-splits-v2/`. The current SDK always sends an empty Bundle for USB. |
| `No virtual method getByteArray` mentioning `BaseBundle` | The installed base gateway contains the older provider dispatcher, which could send an unknown USB-style method into the raw-payload branch and invoke `getByteArray` on the wrong Android class. | Pull the latest repository, run the verifier, re-sign all five prepared splits, and reinstall them together. |

## Operational limitations

- Physical radio/satellite delivery still requires provisioned Somewear hardware and compatible workspace/traffic keys.
- `RADIO_THEN_SATELLITE` and workspace/key readiness remain unsupported in gateway v5.
- The gateway artifacts contain vendor-derived code. Keep the repository and downstream artifacts access-controlled and obtain the required Somewear licensing/approval before deployment or redistribution.
