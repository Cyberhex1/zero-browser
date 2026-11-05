import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself; the root build pins the Kotlin version GeckoView needs.
    id("com.android.application")
}

// Package id and version; the display name is app_name in res/values/strings.xml.
val appId = "app.zerobrowser"
val appVersion = "0.3.2"
// Bump once per release. Modern builds get +1000 so they always outrank legacy ones,
// letting a device that later moves to a newer Fire OS upgrade from legacy to modern.
val buildNumber = 9

// "modern": current GeckoView, Android 8+ (Fire OS 7/8).
// "legacy": GeckoView 144, the last release supporting Android 5–7 (Fire OS 5/6).
// Mozilla raised GeckoView's minSdk to 26 in 145 and publishes no ESR GeckoView,
// so the legacy engine gets no further security updates.
val geckoviewModern = "159.0.20261102144711"
val geckoviewLegacy = "144.0.20251027123126"

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
        targetSdk = 36
        versionName = appVersion
    }

    buildFeatures {
        resValues = true
    }

    flavorDimensions += "engine"
    productFlavors {
        create("modern") {
            dimension = "engine"
            minSdk = 26
            versionCode = 1000 + buildNumber
            resValue("string", "engine_version", geckoviewModern)
            // Release file name prefix the in-app updater downloads, e.g. ZeroBrowser-armeabi-v7a.apk.
            resValue("string", "update_asset_prefix", "ZeroBrowser-")
        }
        create("legacy") {
            dimension = "engine"
            minSdk = 21
            versionCode = buildNumber
            versionNameSuffix = "-legacy"
            resValue("string", "engine_version", geckoviewLegacy)
            resValue("string", "update_asset_prefix", "ZeroBrowser-legacy-")
        }
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
    "modernImplementation"("org.mozilla.geckoview:geckoview:$geckoviewModern")
    "legacyImplementation"("org.mozilla.geckoview:geckoview:$geckoviewLegacy")
    // FileProvider, to hand downloaded updates to Android's installer.
    implementation("androidx.core:core:1.13.1")
}

// Copies release APKs to ../dist for GitHub releases:
// ZeroBrowser-v<version>-<abi>.apk (modern) and ZeroBrowser-legacy-v<version>-armeabi-v7a.apk.
tasks.register<Copy>("dist") {
    dependsOn("assembleModernRelease", "assembleLegacyRelease")
    from(layout.buildDirectory.dir("outputs/apk/modern/release")) {
        include("*-release.apk")
        rename(Regex("app-modern-(.+)-release\\.apk").toPattern(), "ZeroBrowser-v$appVersion-$1.apk")
    }
    from(layout.buildDirectory.dir("outputs/apk/legacy/release")) {
        include("*-armeabi-v7a-release.apk")
        rename(Regex("app-legacy-(.+)-release\\.apk").toPattern(), "ZeroBrowser-legacy-v$appVersion-$1.apk")
    }
    into(rootProject.file("../dist"))
}
