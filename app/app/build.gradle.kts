import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself; the root build pins the Kotlin version GeckoView needs.
    id("com.android.application")
}

// Package id and version; the display name is app_name in res/values/strings.xml.
val appId = "app.zerobrowser"
val appVersion = "0.1.1"
val geckoviewVersion = "159.0.20261022150525"

// Release signing comes from ../../keystore.properties (gitignored), next to keystore/release.jks.
val keystoreProps = Properties().apply {
    val f = rootProject.file("../keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.zerobrowser"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = appId
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = appVersion
    }

    // One APK per ABI: Fire TV sticks may run a 32-bit userland on 64-bit CPUs.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file("../" + keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Compress GeckoView's native libraries: roughly halves the download for Fire TV sticks.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")
}

// Copies release APKs to ../dist as ZeroBrowser-v<version>-<abi>.apk for GitHub releases.
tasks.register<Copy>("dist") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*-release.apk")
    }
    into(rootProject.file("../dist"))
    rename { name ->
        val abi = Regex("app-(.+)-release\\.apk").find(name)?.groupValues?.get(1) ?: name
        "ZeroBrowser-v$appVersion-$abi.apk"
    }
}
