# Standalone gateway provider patch

`SomewearGatewayProvider.smali` is the injected provider used by gateway v6. It:

- initializes Realm from `ContentProvider.onCreate()`;
- initializes Realm and completes standalone `PluginConfig.setup(context)` before exposing the API-v2 helper;
- initializes the API-v2 helper;
- delegates API-v2 calls to `GatewayV2` before the legacy API-v1 dispatcher;
- retains the legacy Bluetooth and router entry points.
- normalizes explicit Bluetooth MAC addresses and asks `GatewayV2` to seed the
  vendor core's private BLE device cache before `toggleScan()`.
- rejects unknown provider methods before reading raw-payload extras and invokes
  `getByteArray` on `android.os.Bundle` (where Android declares it), not
  `android.os.BaseBundle`.

`SomewearPlugin.smali` initializes Realm defensively in `Application.onCreate()`
but deliberately leaves vendor-core configuration to the provider. This avoids
configuring the singleton graph twice and prevents IPC from racing application startup.

`PluginConfig$Companion.smali` registers `RealmBuilderModule` immediately after
Somewear Core configuration. This supplies the Realm singleton used by metric,
message, and database background tasks without depending on ATAK setup.

`GatewayV2` itself is maintained as readable Java under:

```text
somewear-gateway-sdk/gateway-helper/src/main/java/com/somewearlabs/gateway/GatewayV2.java
```

The helper module builds a temporary APK so its DEX can be converted to smali and inserted into the separately decoded gateway. The original vendor APK and full decoded tree are intentionally not committed.
