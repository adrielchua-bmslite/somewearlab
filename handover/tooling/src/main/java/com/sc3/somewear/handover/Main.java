package com.sc3.somewear.handover;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

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
        requireFile(provider, "gateway provider patch");
        String source = Files.readString(provider, StandardCharsets.UTF_8);
        String invalidDescriptor =
                "Landroid/os/BaseBundle;->getByteArray(Ljava/lang/String;)[B";
        String validDescriptor =
                "Landroid/os/Bundle;->getByteArray(Ljava/lang/String;)[B";
        if (source.contains(invalidDescriptor)) {
            throw new IllegalStateException(
                    "gateway provider invokes getByteArray on BaseBundle instead of Bundle"
            );
        }
        if (!source.contains(validDescriptor)) {
            throw new IllegalStateException("gateway provider Bundle.getByteArray call is missing");
        }
        if (!source.contains(":read_raw_payload")
                || !source.contains("if-eqz v0, :unknown_method")) {
            throw new IllegalStateException(
                    "gateway provider must reject unknown methods before reading raw payload extras"
            );
        }
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
