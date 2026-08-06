// Copy this file and somewear-gateway-sdk-0.1.0.aar into SC3/app/libs/, then add:
// apply(from = "libs/sc3-somewear.gradle.kts")
// to SC3/app/build.gradle.kts after applying the Android plugin.
// SC3's root gradle.properties must contain: android.useAndroidX=true
dependencies {
    add("implementation", files("libs/somewear-gateway-sdk-0.1.0.aar"))
    add("implementation", "androidx.activity:activity-ktx:1.12.0")
    add("implementation", "androidx.camera:camera-camera2:1.6.1")
    add("implementation", "androidx.camera:camera-lifecycle:1.6.1")
    add("implementation", "androidx.camera:camera-view:1.6.1")
    add("implementation", "com.google.mlkit:barcode-scanning:17.3.0")
    add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
