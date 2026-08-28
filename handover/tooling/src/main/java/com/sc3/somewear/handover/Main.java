package com.sc3.somewear.handover;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Main {
    private static final List<String> APK_NAMES = List.of(
            "com.somewearlabs.swtak.plugin.apk",
            "config.arm64_v8a.apk",
            "config.en.apk",
            "config.fr.apk",
            "config.mdpi.apk"
    );

    private Main() {}

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                usage();
            } else if ("verify".equals(args[0]) && args.length == 4) {
                verify(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            } else if ("resign".equals(args[0]) && args.length == 6) {
                resign(
                        Path.of(args[1]),
                        Path.of(args[2]),
                        Path.of(args[3]),
                        Path.of(args[4]),
                        args[5]
                );
            } else {
                usage();
            }
        } catch (Exception error) {
            System.err.println("error: " + error.getMessage());
            if ("1".equals(System.getenv("HANDOVER_DEBUG"))) {
                error.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }

    private static void usage() {
        throw new IllegalArgumentException(
                "usage: verify REPO_ROOT APK_DIR AAR | "
                        + "resign REPO_ROOT INPUT_DIR OUTPUT_DIR KEYSTORE KEY_ALIAS"
        );
    }

    private static void verify(Path repoRoot, Path apkDir, Path aar) throws Exception {
        repoRoot = repoRoot.toAbsolutePath().normalize();
        apkDir = apkDir.toAbsolutePath().normalize();
        aar = aar.toAbsolutePath().normalize();
        requireFile(aar, "SDK AAR");
        verifySdkAar(aar);
        System.out.println("sdk_enrollment_contract=OK");

        Path committedApkDir = repoRoot.resolve("build/signed-splits-v2").normalize();
        boolean committedArtifacts = Files.isSameFile(apkDir, committedApkDir);
        if (committedArtifacts) {
            verifyGatewayProviderSource(repoRoot);
            verifyCommittedHashes(repoRoot);
            System.out.println("gateway_source_contract=OK");
            System.out.println("committed_hashes=OK");
        } else {
            System.out.println("committed_hashes=SKIPPED (custom APK directory)");
        }

        String expectedSigner = null;
        for (String apkName : APK_NAMES) {
            Path apk = apkDir.resolve(apkName);
            requireFile(apk, "gateway split");
            String signer = verifyApk(apk);
            if ("com.somewearlabs.swtak.plugin.apk".equals(apkName)) {
                verifyGatewayApkContract(apk);
            }
            if (expectedSigner == null) {
                expectedSigner = signer;
            } else if (!expectedSigner.equalsIgnoreCase(signer)) {
                throw new IllegalStateException("gateway split signer mismatch: " + apkName);
            }
            System.out.println(sha256(apk) + "  " + apkName);
        }

        System.out.println(sha256(aar) + "  " + aar.getFileName());
        System.out.println("signer_sha256=" + expectedSigner);
        if (committedArtifacts) {
            System.out.println("provider=com.somewearlabs.gateway.SomewearGatewayProvider");
            System.out.println("authority=com.somewearlabs.swtak.plugin.somewear.gateway");
            System.out.println("provider_check=BOUND_TO_COMMITTED_HASH");
        }
        System.out.println("verification=OK");
    }

    private static void verifyCommittedHashes(Path repoRoot) throws Exception {
        Path sumsFile = repoRoot.resolve("handover/SHA256SUMS");
        requireFile(sumsFile, "SHA256SUMS");
        for (String line : Files.readAllLines(sumsFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.trim().split("\\s+", 2);
            if (fields.length != 2) {
                throw new IllegalStateException("invalid SHA256SUMS line: " + line);
            }
            Path artifact = repoRoot.resolve(fields[1]).normalize();
            requireFile(artifact, "committed artifact");
            String actual = sha256(artifact);
            if (!actual.equalsIgnoreCase(fields[0])) {
                throw new IllegalStateException("SHA-256 mismatch: " + fields[1]);
            }
        }
    }

    private static void verifyGatewayProviderSource(Path repoRoot) throws Exception {
        Path provider = repoRoot.resolve("gateway-patches/SomewearGatewayProvider.smali");
        Path helper = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/GatewayV2.java"
        );
        Path fallbackCoordinator = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/RouteFallbackCoordinator.java"
        );
        Path fallbackEnvelope = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/FallbackMessageEnvelope.java"
        );
        Path inboundDeduplicator = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/InboundDeduplicator.java"
        );
        Path transportPolicy = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/TransportFragmentationPolicy.java"
        );
        Path scanner = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/WorkspaceQrScannerActivity.java"
        );
        Path receiveService = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/java/"
                        + "com/somewearlabs/gateway/SomewearGatewayService.java"
        );
        Path helperManifest = repoRoot.resolve(
                "somewear-gateway-sdk/gateway-helper/src/main/AndroidManifest.xml"
        );
        Path sdkBuild = repoRoot.resolve("somewear-gateway-sdk/sdk/build.gradle.kts");
        Path sdkClient = repoRoot.resolve(
                "somewear-gateway-sdk/sdk/src/main/kotlin/com/sc3/somewear/sdk/SomewearClient.kt"
        );
        Path sdkClientImpl = repoRoot.resolve(
                "somewear-gateway-sdk/sdk/src/main/kotlin/com/sc3/somewear/sdk/"
                        + "ContentProviderSomewearClient.kt"
        );
        Path dependencyScript = repoRoot.resolve(
                "somewear-gateway-sdk/dist/sc3-somewear.gradle.kts"
        );
        requireFile(provider, "gateway provider patch");
        requireFile(helper, "gateway v2 helper");
        requireFile(transportPolicy, "gateway transport policy");
        requireFile(fallbackCoordinator, "radio-to-satellite fallback coordinator");
        requireFile(fallbackEnvelope, "fallback message envelope");
        requireFile(inboundDeduplicator, "cross-channel inbound deduplicator");
        requireFile(scanner, "gateway QR scanner source");
        requireFile(receiveService, "gateway bound receive service source");
        requireFile(helperManifest, "gateway helper manifest");
        requireFile(sdkBuild, "SDK build file");
        requireFile(sdkClient, "SDK client contract");
        requireFile(sdkClientImpl, "SDK client implementation");
        requireFile(dependencyScript, "SDK local dependency script");
        String providerSource = Files.readString(provider, StandardCharsets.UTF_8);
        String helperSource = Files.readString(helper, StandardCharsets.UTF_8);
        String scannerSource = Files.readString(scanner, StandardCharsets.UTF_8);
        String receiveServiceSource = Files.readString(receiveService, StandardCharsets.UTF_8);
        String scannerManifest = Files.readString(helperManifest, StandardCharsets.UTF_8);
        String sdkClientSource = Files.readString(sdkClient, StandardCharsets.UTF_8);
        String sdkClientImplSource = Files.readString(sdkClientImpl, StandardCharsets.UTF_8);
        String sdkDependencies = Files.readString(sdkBuild, StandardCharsets.UTF_8)
                + Files.readString(dependencyScript, StandardCharsets.UTF_8);
        if (providerSource.contains(":read_raw_payload")
                || providerSource.contains(
                        "Landroid/os/Bundle;->getByteArray(Ljava/lang/String;)[B"
                )) {
            throw new IllegalStateException(
                    "gateway provider no longer matches the last-known-good dispatcher"
            );
        }
        if (!providerSource.contains(
                "Landroid/os/BaseBundle;->getByteArray(Ljava/lang/String;)[B"
        )) {
            throw new IllegalStateException("last-known-good provider dispatcher is missing");
        }
        if (!helperSource.contains("isLegacyProviderMethod")
                || !helperSource.contains("\"sendRaw\".equals(method)")
                || !helperSource.contains("\"sendRawToWorkspace\".equals(method)")
                || !helperSource.contains("\"sendRawWithParcel\".equals(method)")
                || !helperSource.contains("Unknown gateway method: ")) {
            throw new IllegalStateException(
                    "GatewayV2 must intercept legacy raw and unknown methods before provider dispatch"
            );
        }
        if (!helperSource.contains("radio_then_satellite")
                || !helperSource.contains("satellite_timeout")
                || !helperSource.contains("satellite_timeout_ms")
                || !helperSource.contains("satellite_native_composite")
                || !helperSource.contains("satellite_backhaul_ack")
                || !helperSource.contains("satellite_fragment_reassembly_legacy")
                || !helperSource.contains("buildTransportFragmentPayloads")
                || !helperSource.contains("TransportFragmentationPolicy.shouldFragment")
                || !helperSource.contains("TransportFragmentationPolicy.requiresBackhaulAck")
                || !helperSource.contains("ROUTE_FALLBACKS")
                || !helperSource.contains("performFallbackLocked")
                || !helperSource.contains("sendOptions(\"Satellite\"")) {
            throw new IllegalStateException(
                    "GatewayV2 must implement controlled fallback and native Satellite composites"
            );
        }
        if (!helperSource.contains("if (\"listWorkspaces\".equals(method)) return listWorkspaces()")
                || !helperSource.contains(
                        "if (\"activateWorkspace\".equals(method)) return activateWorkspace(extras)"
                )
                || !helperSource.contains("SharedWorkspaceCache")
                || !helperSource.contains("GenericUserSource")
                || !helperSource.contains("getActiveWorkspaceOrNull")) {
            throw new IllegalStateException(
                    "GatewayV2 must expose retained workspace listing and activation APIs"
            );
        }
        if (!helperSource.contains("if (\"joinWorkspace\".equals(method))")
                || !helperSource.contains("return joinWorkspace(extras)")
                || !helperSource.contains("if (\"syncWorkspaces\".equals(method))")
                || !helperSource.contains("joinWorkspaceByInviteToken")
                || !helperSource.contains("createWorkspaceFromMeshKey")
                || !helperSource.contains("invokeSuspend")
                || !helperSource.contains("workspace_qr_invite")
                || !helperSource.contains("workspace_qr_scanner")
                || !helperSource.contains("receive_health")
                || !helperSource.contains("getReceiveHealth")
                || !helperSource.contains("startReceiving")
                || !helperSource.contains("recordReceiveError")) {
            throw new IllegalStateException(
                    "GatewayV2 must expose retained QR-invite enrollment and synchronization"
            );
        }
        if (!helperSource.contains(
                        "if (\"listWorkspaceFiles\".equals(method)) return listWorkspaceFiles(extras)"
                )
                || !helperSource.contains("workspace_file_catalog")
                || !helperSource.contains("getGetFilesMethod")
                || !sdkClientSource.contains(" listWorkspaceFiles(")
                || !sdkClientSource.contains(" syncWorkspaceContent(")
                || !sdkClientSource.contains(" cachedWorkspaceFiles(")) {
            throw new IllegalStateException(
                    "Gateway/SDK must expose workspace catalogue and SDK-owned missing-file recovery"
            );
        }
        if (!scannerSource.contains("com.budiyev.android.codescanner.CodeScannerView")
                || !scannerSource.contains("com.google.zxing.BarcodeFormat")
                || !scannerSource.contains("EXTRA_INVITE_CODE")
                || !scannerManifest.contains("WorkspaceQrScannerActivity")
                || !scannerManifest.contains("permission.SOMEWEAR_GATEWAY")) {
            throw new IllegalStateException(
                    "Gateway must host the signature-protected offline QR scanner"
            );
        }
        if (!receiveServiceSource.contains("GatewayV2.startReceiving()")
                || !scannerManifest.contains("SomewearGatewayService")
                || !scannerManifest.contains("android:exported=\"true\"")
                || !scannerManifest.contains("permission.SOMEWEAR_GATEWAY")) {
            throw new IllegalStateException(
                    "Gateway must host the signature-protected bound receive service"
            );
        }
        List<String> hardwareGatewayMethods = List.of(
                "getHardwareSettings",
                "setTrackingEnabled",
                "setTrackingInterval",
                "setBackhaulEnabled",
                "setSatelliteEnabled",
                "setMeshRadioEnabled",
                "setRadioChannel",
                "setMeshTransmissionStrength",
                "setLedLightEnabled",
                "setVibrationFeedbackEnabled",
                "setEnduranceModeEnabled",
                "setDeviceButtonFunction",
                "setConnectionMode",
                "factoryReset"
        );
        for (String method : hardwareGatewayMethods) {
            if (!helperSource.contains("\"" + method + "\"")) {
                throw new IllegalStateException("GatewayV2 is missing hardware method " + method);
            }
            String sdkMethod;
            if ("setConnectionMode".equals(method)) {
                sdkMethod = "setNodeConnectionMode";
            } else if ("getHardwareSettings".equals(method)) {
                sdkMethod = "hardwareSettings";
            } else {
                sdkMethod = method;
            }
            if (!sdkClientSource.contains(" " + sdkMethod + "(")) {
                throw new IllegalStateException("SDK contract is missing hardware method " + sdkMethod);
            }
        }
        if (!helperSource.contains("DeviceManagementRepositoryImpl")
                || !helperSource.contains("factoryReset-gIAlu-s")
                || !helperSource.contains("updateSettings")
                || !helperSource.contains("updateConnectionMode")) {
            throw new IllegalStateException(
                    "Gateway hardware APIs must use the retained Somewear settings/reset workflows"
            );
        }
        if (!sdkClientSource.contains("observeDeviceConnection()")
                || !sdkClientImplSource.contains(
                        ".distinctUntilChanged(::sameDeviceObservation)"
                )) {
            throw new IllegalStateException(
                    "SDK connection observer must emit state changes instead of every poll"
            );
        }
        if (sdkDependencies.contains("com.google.mlkit")
                || sdkDependencies.contains("androidx.camera")) {
            throw new IllegalStateException(
                    "SC3 SDK distribution must not depend on ML Kit or CameraX"
            );
        }
    }

    private static void verifySdkAar(Path aar) throws Exception {
        Set<String> classes = new HashSet<>();
        String somewearClientSymbols = null;
        String sendRequestSymbols = null;
        String fileSendRequestSymbols = null;
        String sendReceiptSymbols = null;
        String receiveHealthSymbols = null;
        String manifest;
        try (ZipFile archive = new ZipFile(aar.toFile())) {
            ZipEntry classesJar = archive.getEntry("classes.jar");
            ZipEntry manifestEntry = archive.getEntry("AndroidManifest.xml");
            if (classesJar == null || manifestEntry == null) {
                throw new IllegalStateException("SDK AAR is missing classes.jar or AndroidManifest.xml");
            }
            manifest = new String(
                    archive.getInputStream(manifestEntry).readAllBytes(),
                    StandardCharsets.UTF_8
            );
            try (JarInputStream jar = new JarInputStream(archive.getInputStream(classesJar))) {
                JarEntry entry;
                while ((entry = jar.getNextJarEntry()) != null) {
                    classes.add(entry.getName());
                    String classBytes = new String(jar.readAllBytes(), StandardCharsets.ISO_8859_1);
                    if ("com/sc3/somewear/sdk/SomewearClient.class".equals(entry.getName())) {
                        somewearClientSymbols = classBytes;
                    }
                    if ("com/sc3/somewear/sdk/SendRequest.class".equals(entry.getName())) {
                        sendRequestSymbols = classBytes;
                    }
                    if ("com/sc3/somewear/sdk/FileSendRequest.class".equals(entry.getName())) {
                        fileSendRequestSymbols = classBytes;
                    }
                    if ("com/sc3/somewear/sdk/SendReceipt.class".equals(entry.getName())) {
                        sendReceiptSymbols = classBytes;
                    }
                    if ("com/sc3/somewear/sdk/ReceiveHealth.class".equals(entry.getName())) {
                        receiveHealthSymbols = classBytes;
                    }
                    if (classBytes.contains("com/google/mlkit")
                            || classBytes.contains("androidx/camera")) {
                        throw new IllegalStateException(
                                "SDK AAR still references an app-local scanner runtime"
                        );
                    }
                }
            }
        }
        List<String> requiredClasses = List.of(
                "com/sc3/somewear/sdk/SomewearClient.class",
                "com/sc3/somewear/sdk/ReceiveHealth.class",
                "com/sc3/somewear/sdk/HardwareSettings.class",
                "com/sc3/somewear/sdk/TrackingInterval.class",
                "com/sc3/somewear/sdk/RadioChannel.class",
                "com/sc3/somewear/sdk/MeshTransmissionStrength.class",
                "com/sc3/somewear/sdk/DeviceButtonFunction.class",
                "com/sc3/somewear/sdk/FactoryResetConfirmation.class",
                "com/sc3/somewear/sdk/WorkspaceInviteCode.class",
                "com/sc3/somewear/sdk/WorkspaceQrScanContract.class",
                "com/sc3/somewear/sdk/WorkspaceQrScannerActivity.class",
                "com/sc3/somewear/sdk/WorkspaceContentActivity.class",
                "com/sc3/somewear/sdk/WorkspaceFile.class",
                "com/sc3/somewear/sdk/WorkspaceFilePage.class",
                "com/sc3/somewear/sdk/WorkspaceContentSyncRequest.class",
                "com/sc3/somewear/sdk/SendRequest.class",
                "com/sc3/somewear/sdk/FileSendRequest.class",
                "com/sc3/somewear/sdk/SendReceipt.class"
        );
        for (String requiredClass : requiredClasses) {
            if (!classes.contains(requiredClass)) {
                throw new IllegalStateException("SDK AAR is missing " + requiredClass);
            }
        }
        if (somewearClientSymbols == null) {
            throw new IllegalStateException("SDK AAR is missing SomewearClient symbols");
        }
        if (sendRequestSymbols == null
                || fileSendRequestSymbols == null
                || sendReceiptSymbols == null
                || receiveHealthSymbols == null
                || !sendRequestSymbols.contains("satelliteTimeoutMillis")
                || !fileSendRequestSymbols.contains("satelliteTimeoutMillis")
                || !sendReceiptSymbols.contains("satelliteFallbackArmed")
                || !sendReceiptSymbols.contains("transportFragmented")
                || !sendReceiptSymbols.contains("satelliteFragmented")
                || !sendReceiptSymbols.contains("estimatedTransmissionCount")
                || !sendReceiptSymbols.contains("satelliteNativeComposite")
                || !sendReceiptSymbols.contains("backhaulAckRequired")
                || !receiveHealthSymbols.contains("inboundTransportFragmentCount")
                || !receiveHealthSymbols.contains("lastDeliveredChannel")) {
            throw new IllegalStateException(
                    "SDK AAR is missing the Satellite transport/read-back contract"
            );
        }
        List<String> requiredMethods = List.of(
                "observeDeviceConnection",
                "hardwareSettings",
                "setTrackingEnabled",
                "setTrackingInterval",
                "setBackhaulEnabled",
                "setSatelliteEnabled",
                "setMeshRadioEnabled",
                "setRadioChannel",
                "setMeshTransmissionStrength",
                "setLedLightEnabled",
                "setVibrationFeedbackEnabled",
                "setEnduranceModeEnabled",
                "setDeviceButtonFunction",
                "setNodeConnectionMode",
                "listWorkspaceFiles",
                "downloadWorkspaceFile",
                "cachedWorkspaceFiles",
                "syncWorkspaceContent",
                "factoryReset"
        );
        for (String method : requiredMethods) {
            if (!somewearClientSymbols.contains(method)) {
                throw new IllegalStateException("SDK AAR is missing method " + method);
            }
        }
        if (manifest.contains("android.permission.CAMERA")
                || !manifest.contains("WorkspaceQrScannerActivity")
                || !manifest.contains("WorkspaceContentActivity")
                || !manifest.contains("android.permission.INTERNET")) {
            throw new IllegalStateException(
                    "SDK AAR is missing its QR/content activity or network manifest contract"
            );
        }
    }

    private static void verifyGatewayApkContract(Path apk) throws Exception {
        boolean coordinator = false;
        boolean envelope = false;
        boolean deduplicator = false;
        boolean capability = false;
        boolean timeout = false;
        boolean satelliteNativeComposite = false;
        boolean satelliteBackhaulAck = false;
        boolean legacySatelliteReassembly = false;
        boolean transmissionEstimate = false;
        boolean workspaceFileCatalog = false;
        boolean providerClass = false;
        boolean gatewayV2Class = false;
        try (ZipFile archive = new ZipFile(apk.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                byte[] dexBytes = archive.getInputStream(entry).readAllBytes();
                String dex = new String(dexBytes, StandardCharsets.ISO_8859_1);
                Set<String> definedClasses = dexClassDescriptors(dexBytes);
                providerClass |= definedClasses.contains(
                        "Lcom/somewearlabs/gateway/SomewearGatewayProvider;"
                );
                gatewayV2Class |= definedClasses.contains(
                        "Lcom/somewearlabs/gateway/GatewayV2;"
                );
                coordinator |= dex.contains("Lcom/somewearlabs/gateway/RouteFallbackCoordinator;");
                envelope |= dex.contains("Lcom/somewearlabs/gateway/FallbackMessageEnvelope;");
                deduplicator |= dex.contains("Lcom/somewearlabs/gateway/InboundDeduplicator;");
                capability |= dex.contains("radio_then_satellite");
                timeout |= dex.contains("satellite_timeout_ms");
                satelliteNativeComposite |= dex.contains("satellite_native_composite");
                satelliteBackhaulAck |= dex.contains("satellite_backhaul_ack");
                legacySatelliteReassembly |= dex.contains(
                        "satellite_fragment_reassembly_legacy"
                );
                transmissionEstimate |= dex.contains("estimated_transmission_count");
                workspaceFileCatalog |= dex.contains("workspace_file_catalog")
                        && dex.contains("listWorkspaceFiles")
                        && dex.contains("getGetFilesMethod");
            }
        }
        if (!providerClass
                || !gatewayV2Class
                || !coordinator
                || !envelope
                || !deduplicator
                || !capability
                || !timeout
                || !satelliteNativeComposite
                || !satelliteBackhaulAck
                || !legacySatelliteReassembly
                || !transmissionEstimate
                || !workspaceFileCatalog) {
            throw new IllegalStateException(
                    "base APK is missing its provider/helper class definition, controlled fallback,"
                            + " native Satellite composite, or workspace file catalogue contract"
            );
        }
        System.out.println("gateway_radio_satellite_content_contract=OK");
    }

    /** Returns the class descriptors that are defined, rather than merely referenced, by a DEX. */
    private static Set<String> dexClassDescriptors(byte[] dex) {
        if (dex.length < 0x70
                || dex[0] != 'd'
                || dex[1] != 'e'
                || dex[2] != 'x'
                || dex[3] != '\n') {
            throw new IllegalStateException("invalid DEX header in base APK");
        }
        ByteBuffer buffer = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        int stringIdsSize = positiveDexValue(buffer.getInt(0x38), "string_ids_size");
        int stringIdsOffset = positiveDexValue(buffer.getInt(0x3c), "string_ids_off");
        int typeIdsSize = positiveDexValue(buffer.getInt(0x40), "type_ids_size");
        int typeIdsOffset = positiveDexValue(buffer.getInt(0x44), "type_ids_off");
        int classDefsSize = positiveDexValue(buffer.getInt(0x60), "class_defs_size");
        int classDefsOffset = positiveDexValue(buffer.getInt(0x64), "class_defs_off");
        requireDexRange(dex, stringIdsOffset, stringIdsSize, 4, "string IDs");
        requireDexRange(dex, typeIdsOffset, typeIdsSize, 4, "type IDs");
        requireDexRange(dex, classDefsOffset, classDefsSize, 32, "class definitions");

        Set<String> descriptors = new HashSet<>();
        for (int index = 0; index < classDefsSize; index++) {
            int classIndex = buffer.getInt(classDefsOffset + index * 32);
            if (classIndex < 0 || classIndex >= typeIdsSize) {
                throw new IllegalStateException("invalid DEX class index");
            }
            int descriptorIndex = buffer.getInt(typeIdsOffset + classIndex * 4);
            if (descriptorIndex < 0 || descriptorIndex >= stringIdsSize) {
                throw new IllegalStateException("invalid DEX descriptor index");
            }
            int stringOffset = buffer.getInt(stringIdsOffset + descriptorIndex * 4);
            descriptors.add(readDexString(dex, stringOffset));
        }
        return descriptors;
    }

    private static int positiveDexValue(int value, String field) {
        if (value < 0) throw new IllegalStateException("invalid DEX " + field);
        return value;
    }

    private static void requireDexRange(
            byte[] dex,
            int offset,
            int count,
            int itemSize,
            String label
    ) {
        long end = (long) offset + (long) count * itemSize;
        if (offset < 0 || count < 0 || end > dex.length) {
            throw new IllegalStateException("invalid DEX " + label + " range");
        }
    }

    private static String readDexString(byte[] dex, int offset) {
        if (offset < 0 || offset >= dex.length) {
            throw new IllegalStateException("invalid DEX string offset");
        }
        int cursor = offset;
        do {
            if (cursor >= dex.length) throw new IllegalStateException("truncated DEX string length");
        } while ((dex[cursor++] & 0x80) != 0);
        int start = cursor;
        while (cursor < dex.length && dex[cursor] != 0) cursor++;
        if (cursor == dex.length) throw new IllegalStateException("unterminated DEX string");
        return new String(dex, start, cursor - start, StandardCharsets.UTF_8);
    }

    private static void resign(
            Path repoRoot,
            Path inputDir,
            Path outputDir,
            Path keystore,
            String alias
    ) throws Exception {
        repoRoot = repoRoot.toAbsolutePath().normalize();
        inputDir = inputDir.toAbsolutePath().normalize();
        outputDir = outputDir.toAbsolutePath().normalize();
        keystore = keystore.toAbsolutePath().normalize();
        verifyPreparedGatewayInputs(repoRoot, inputDir);
        requireFile(keystore, "keystore");

        char[] storePassword = requiredPassword("GATEWAY_KEYSTORE_PASSWORD");
        String keyPasswordValue = System.getenv("GATEWAY_KEY_PASSWORD");
        char[] keyPassword = keyPasswordValue == null ? storePassword : keyPasswordValue.toCharArray();

        for (String apkName : APK_NAMES) {
            requireFile(inputDir.resolve(apkName), "gateway split");
            if (Files.exists(outputDir.resolve(apkName))) {
                throw new IllegalStateException("refusing to overwrite: " + outputDir.resolve(apkName));
            }
        }

        KeyMaterial material = loadKey(keystore, alias, storePassword, keyPassword);
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                alias,
                material.privateKey(),
                material.certificates()
        ).build();

        Files.createDirectories(outputDir);
        List<Path> created = new ArrayList<>();
        try {
            String expectedSigner = null;
            for (String apkName : APK_NAMES) {
                Path input = inputDir.resolve(apkName);
                Path output = outputDir.resolve(apkName);
                Path temporary = outputDir.resolve(apkName + ".signing.tmp");
                Files.deleteIfExists(temporary);
                try {
                    new ApkSigner.Builder(List.of(signerConfig))
                            .setInputApk(input.toFile())
                            .setOutputApk(temporary.toFile())
                            .setMinSdkVersion(28)
                            .setV1SigningEnabled(false)
                            .setV2SigningEnabled(false)
                            .setV3SigningEnabled(true)
                            .setV4SigningEnabled(false)
                            .setAlignmentPreserved(true)
                            .setOtherSignersSignaturesPreserved(false)
                            .build()
                            .sign();
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                created.add(output);

                String signer = verifyApk(output);
                if (expectedSigner == null) {
                    expectedSigner = signer;
                } else if (!expectedSigner.equalsIgnoreCase(signer)) {
                    throw new IllegalStateException("re-signed split signer mismatch: " + apkName);
                }
                System.out.println("signed " + apkName);
            }
            System.out.println("signer_sha256=" + expectedSigner);
            System.out.println("output=" + outputDir);
            System.out.println("Re-sign SC3 with this same keystore and alias before installation.");
        } catch (Exception error) {
            for (Path output : created) {
                Files.deleteIfExists(output);
            }
            throw error;
        } finally {
            Arrays.fill(storePassword, '\0');
            if (keyPassword != storePassword) {
                Arrays.fill(keyPassword, '\0');
            }
        }
    }

    private static void verifyPreparedGatewayInputs(Path repoRoot, Path inputDir) throws Exception {
        Path sumsFile = repoRoot.resolve("handover/SHA256SUMS");
        requireFile(sumsFile, "SHA256SUMS");
        List<String> lines = Files.readAllLines(sumsFile, StandardCharsets.UTF_8);

        for (String apkName : APK_NAMES) {
            String artifactName = "build/signed-splits-v2/" + apkName;
            String expectedHash = null;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.trim().split("\\s+", 2);
                if (fields.length == 2 && artifactName.equals(fields[1])) {
                    expectedHash = fields[0];
                    break;
                }
            }
            if (expectedHash == null) {
                throw new IllegalStateException("missing committed hash for " + artifactName);
            }

            Path inputApk = inputDir.resolve(apkName);
            requireFile(inputApk, "gateway split");
            if (!expectedHash.equalsIgnoreCase(sha256(inputApk))) {
                throw new IllegalStateException(
                        "input is not the prepared standalone gateway: " + apkName + ". "
                                + "Do not re-sign the original ATAK/Somewear APK or an older gateway build; "
                                + "use build/signed-splits-v2 from this repository."
                );
            }
        }
        System.out.println("prepared_gateway_check=OK");
    }

    private static KeyMaterial loadKey(
            Path keystorePath,
            String alias,
            char[] storePassword,
            char[] keyPassword
    ) throws Exception {
        Exception lastFailure = null;
        for (String type : List.of("JKS", "PKCS12")) {
            try {
                KeyStore keyStore = KeyStore.getInstance(type);
                try (InputStream input = Files.newInputStream(keystorePath)) {
                    keyStore.load(input, storePassword);
                }
                Key key = keyStore.getKey(alias, keyPassword);
                if (!(key instanceof PrivateKey privateKey)) {
                    throw new IllegalStateException("alias is not a private key: " + alias);
                }
                Certificate[] chain = keyStore.getCertificateChain(alias);
                if (chain == null || chain.length == 0) {
                    throw new IllegalStateException("certificate chain is missing for alias: " + alias);
                }
                List<X509Certificate> certificates = Arrays.stream(chain)
                        .map(certificate -> (X509Certificate) certificate)
                        .toList();
                return new KeyMaterial(privateKey, certificates);
            } catch (Exception error) {
                lastFailure = error;
            }
        }
        throw new IllegalStateException(
                "could not load JKS/PKCS12 keystore or alias '" + alias + "': " + lastFailure.getMessage(),
                lastFailure
        );
    }

    private static String verifyApk(Path apk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk.toFile())
                .setMinCheckedPlatformVersion(28)
                .build()
                .verify();
        if (!result.isVerified()) {
            throw new IllegalStateException("APK signature verification failed: " + apk.getFileName()
                    + formatIssues(result));
        }
        List<X509Certificate> signers = result.getSignerCertificates();
        if (signers.size() != 1) {
            throw new IllegalStateException("expected one APK signer: " + apk.getFileName());
        }
        return sha256(signers.get(0).getEncoded());
    }

    private static String formatIssues(ApkVerifier.Result result) {
        if (result.getErrors().isEmpty()) {
            return "";
        }
        return ": " + result.getErrors().get(0);
    }

    private static char[] requiredPassword(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("set " + name);
        }
        return value.toCharArray();
    }

    private static void requireFile(Path path, String description) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(description + " is missing: " + path);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value))
                .toLowerCase(Locale.ROOT);
    }

    private record KeyMaterial(PrivateKey privateKey, List<X509Certificate> certificates) {}
}
