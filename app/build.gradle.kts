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
        // The SDK's own floor is 24; 26 (Android 8.0) is kept because it is what
        // this app has shipped and run on, not because anything requires it.
        // Dropping to 24 would mean qualifying the foreground service,
        // notification and Keystore paths on two API levels the app has never run on.
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppAuth's own manifest declares a RedirectUriReceiverActivity whose
        // intent-filter scheme is this placeholder. The app never uses that
        // activity — AndroidManifest.xml replaces it (tools:node="replace")
        // with an https App Link — but the merger still resolves placeholders
        // in every manifest it merges, including the unit-test variant's
        // (isIncludeAndroidResources pulls Robolectric into a real manifest
        // merge). Left unresolved, that merge fails before any test runs.
        // The value is inert: nothing reads it.
        manifestPlaceholders["appAuthRedirectScheme"] = "unused"

        // Empty means "the production gateway", which is what the SDK resolves an
        // empty gatewayAddresses list to. A debug build overrides it with a dev
        // address. This is no longer a WebSocket URL — the SDK takes host:port.
        buildConfigField("String", "GATEWAY_URL", "\"\"")
        // Account portal (OAuth provider + the /api/v1/me publisher-id lookup).
        // Same default/override pattern as GATEWAY_URL.
        buildConfigField("String", "ACCOUNT_BASE_URL", "\"\"")
        // Read from the version catalog rather than duplicated as a literal, so
        // the Settings/diagnostics display can't drift from the dependency
        // actually on the classpath (libs.versions.toml is the source of
        // truth). Resolved once at configuration time, so this stays
        // configuration-cache compatible.
        buildConfigField("String", "SDK_VERSION", "\"${libs.versions.meerklySdk.get()}\"")
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

    buildTypes {
        debug {
            // A dev gateway on the host's LAN IP, reachable from a physical
            // device on the same Wi-Fi and from the emulator. host:port, not a
            // ws:// URL — the SDK speaks QUIC, not WebSocket.
            buildConfigField("String", "GATEWAY_URL", "\"192.168.1.10:8443\"")
            // Rails dev server on the same host (cleartext allowed for this IP by
            // the debug network_security_config; Rails allows IP-literal hosts).
            buildConfigField("String", "ACCOUNT_BASE_URL", "\"http://192.168.1.10:3000\"")
            // Must byte-match both the dev Doorkeeper's seeded redirect_uri
            // (derived from that server's own APP_HOST) and the plain intent
            // filter debug/AndroidManifest.xml registers for it. A
            // debug-keystore build can never have verified an https App Link,
            // so this stays the dev server's plain http URL — not the
            // release value below.
            buildConfigField("String", "OAUTH_REDIRECT_URI", "\"http://192.168.1.10:3000/oauth2redirect\"")
        }
        release {
            // Empty on purpose: the SDK's own default is the production gateway,
            // and duplicating the address here would be a second place to get it
            // wrong.
            buildConfigField("String", "GATEWAY_URL", "\"\"")
            buildConfigField("String", "ACCOUNT_BASE_URL", "\"https://dashboard.meerkly.com\"")
            // Must byte-match both the redirect_uri the dashboard seeds
            // (derived from its own APP_HOST) and the verified App Link intent
            // filter in the release manifest. Do not change this without
            // updating both of those in lockstep.
            buildConfigField("String", "OAUTH_REDIRECT_URI", "\"https://dashboard.meerkly.com/oauth2redirect\"")

            // R8 stays off. The SDK's public surface is uniffi-generated bindings
            // dispatching through JNA reflection, and the size win on an app this
            // small does not justify that risk.
            optimization {
                enable = false
            }

            // Unsigned when no keystore is configured, so a plain `assembleRelease`
            // still works for anyone without the signing material.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    compileOptions {
        // Java 17 is the toolchain this project targets. With AGP 9 built-in Kotlin,
        // the Kotlin jvmTarget defaults to targetCompatibility, so this also moves
        // Kotlin bytecode to 17.
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
    implementation(libs.okhttp)
    implementation(libs.appauth)
    implementation(libs.meerkly.sdk)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // Compose under Robolectric: renders on the JVM (no device), which is how
    // the adaptive branches get asserted at specific widths via
    // @Config(qualifiers = "w840dp-...") and how design renders are captured.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}