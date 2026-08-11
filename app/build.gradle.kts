import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing credentials, kept out of the repo (both sources are gitignored).
// Either a keystore.properties beside settings.gradle.kts:
//     storeFile=meerkly-release.jks   (path relative to the project root)
//     storePassword=…
//     keyAlias=…
//     keyPassword=…
// or the env vars MEERKLY_KEYSTORE_{FILE,PASSWORD} / MEERKLY_KEY_{ALIAS,PASSWORD} on CI.
// Read through the provider API so the configuration cache tracks the file.
val keystoreProps = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: providers.environmentVariable(env).orNull

android {
    namespace = "com.meerkly.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.meerkly.android"
        // GeckoView 152's AAR declares minSdkVersion 26, so 26 (Android 8.0) is the real floor.
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default: no gateway. The debug build overrides this with a dev URL; a
        // production URL will be set on release once a prod gateway exists.
        buildConfigField("String", "GATEWAY_URL", "\"\"")
        // Account portal (OAuth provider + device registration API). Same
        // default/override pattern as GATEWAY_URL.
        buildConfigField("String", "ACCOUNT_BASE_URL", "\"\"")

        // AppAuth's RedirectUriReceiverActivity binds this scheme (manifest merge)
        // to catch the OAuth redirect com.meerkly.android:/oauth2redirect — must
        // byte-match the redirect_uri seeded for the meerkly-android client.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.meerkly.android"
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "MEERKLY_KEYSTORE_FILE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword", "MEERKLY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MEERKLY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MEERKLY_KEY_PASSWORD")
            }
        }
    }

    // GeckoView ships a ~75 MB native library per ABI, so a universal APK is
    // ~530 MB — unusable as a download. Release builds pass `-PabiSplits` to
    // emit one ~100 MB APK per ABI instead. Opt-in so the local dev loop keeps
    // packaging a single APK. (Bundles split themselves; this doesn't affect
    // bundleRelease, which is still what goes to Play.)
    splits {
        abi {
            isEnable = providers.gradleProperty("abiSplits").isPresent
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // Same opt-in: on the direct-download path the APK IS the download,
            // and GeckoView's uncompressed .so files dominate it. Compressing
            // them costs install time and disk (the system extracts them) but
            // roughly halves what someone pulls over mobile data. Play builds
            // (bundleRelease) never take this branch and keep the modern
            // uncompressed-and-aligned layout.
            useLegacyPackaging = providers.gradleProperty("abiSplits").isPresent
        }
    }

    buildTypes {
        debug {
            // Dev gateway on the host's LAN IP so a physical device on the same
            // Wi-Fi can reach it (the emulator can reach this IP too). Change this
            // to match your machine's address if it differs.
            buildConfigField("String", "GATEWAY_URL", "\"ws://192.168.1.10:8080/v1/connect\"")
            // Rails dev server on the same host (cleartext allowed for this IP by
            // the debug network_security_config; Rails allows IP-literal hosts).
            buildConfigField("String", "ACCOUNT_BASE_URL", "\"http://192.168.1.10:3000\"")
        }
        release {
            // Production endpoints — must match @meerkly/sdk's defaults.ts, which
            // is the cross-platform source of truth for these URLs.
            buildConfigField("String", "GATEWAY_URL", "\"wss://gateway.meerkly.com/v1/connect\"")
            buildConfigField("String", "ACCOUNT_BASE_URL", "\"https://account.meerkly.com\"")

            // R8 stays off. It only shrinks Java/Kotlin bytecode, which is a rounding
            // error next to GeckoView's native libraries — the actual size win comes
            // from Play's per-ABI splits in the AAB. Not worth the reflection risk
            // across GeckoView, AppAuth and the WebExtension bridge for that.
            optimization {
                enable = false
            }

            // Unsigned when no keystore is configured, so a plain `assembleRelease`
            // still works for anyone without the signing material.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    compileOptions {
        // GeckoView 152 requires Java 17. With AGP 9 built-in Kotlin, the Kotlin jvmTarget
        // defaults to targetCompatibility, so this also moves Kotlin bytecode to 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.geckoview)
    implementation(libs.okhttp)
    implementation(libs.appauth)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}