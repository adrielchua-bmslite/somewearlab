import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.sc3.somewear"
version = "0.1.0"

android {
    namespace = "com.sc3.somewear.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api("androidx.activity:activity-ktx:1.12.0")
    api("androidx.camera:camera-camera2:1.6.1")
    api("androidx.camera:camera-lifecycle:1.6.1")
    api("androidx.camera:camera-view:1.6.1")
    // Bundled model: QR scanning works offline and on devices without Play Services.
    api("com.google.mlkit:barcode-scanning:17.3.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation("junit:junit:4.12")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "somewear-gateway-sdk"
                version = project.version.toString()
            }
        }
    }
}
