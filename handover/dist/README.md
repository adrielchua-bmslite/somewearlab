# Portable handover tool

`somewear-handover-tools.jar` verifies and re-signs the five gateway APKs without an Android SDK Build-Tools installation. It requires Java 17 or newer and is invoked by the Windows `.bat` scripts.

The JAR is reproducibly built from `handover/tooling/`:

```sh
./somewear-gateway-sdk/gradlew -p handover/tooling clean portableJar
```

It embeds Android's `apksig` library. The corresponding Apache 2.0 license is included as `LICENSE` inside the JAR.
