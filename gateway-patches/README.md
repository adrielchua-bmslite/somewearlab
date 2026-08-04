# Standalone gateway provider patch

`SomewearGatewayProvider.smali` is the injected provider used by gateway v5. It:

- initializes Realm from `ContentProvider.onCreate()`;
- runs the modified standalone `PluginConfig.setup(context)` before core startup;
- initializes the API-v2 helper;
- delegates API-v2 calls to `GatewayV2` before the legacy API-v1 dispatcher;
- retains the legacy Bluetooth and router entry points.

`GatewayV2` itself is maintained as readable Java under:

```text
somewear-gateway-sdk/gateway-helper/src/main/java/com/somewearlabs/gateway/GatewayV2.java
```

The helper module builds a temporary APK so its DEX can be converted to smali and inserted into the separately decoded gateway. The original vendor APK and full decoded tree are intentionally not committed.
