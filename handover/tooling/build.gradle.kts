plugins {
    application
}

group = "com.sc3.somewear"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.android.tools.build:apksig:8.13.2")
}

application {
    mainClass.set("com.sc3.somewear.handover.Main")
}

tasks.register<Jar>("portableJar") {
    group = "distribution"
    description = "Builds the SDK-independent handover verifier and signer."
    archiveFileName.set("somewear-handover-tools.jar")
    destinationDirectory.set(layout.projectDirectory.dir("../dist"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
