plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

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
        versionCode = 1
        versionName = "1.0"

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
            optimization {
                enable = false
            }
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