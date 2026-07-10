import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // Compose plugin only. Kotlin compilation uses AGP 9.2.1's built-in Kotlin
    // toolchain (2.2.10); org.jetbrains.kotlin.android is intentionally NOT applied.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.quellkern.nachweis"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Toolchain floor for wallet-core 0.28.1.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Product flavors carry the environment split: distinct applicationIds,
    // endpoints, and trust roots. Build types stay debug/release.
    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationId = "com.quellkern.nachweis.demo"
            // Canonical nachweis.tech sandbox hosts (public demo surface).
            buildConfigField("String", "ISSUER_BASE_URL", "\"https://api-sandbox.nachweis.tech\"")
            buildConfigField("String", "VERIFIER_BASE_URL", "\"https://verifier-sandbox.nachweis.tech\"")
            // Trust anchors bundled in the demo flavor (public demo trust anchor only).
            buildConfigField("String", "TRUST_ANCHORS_RES", "\"demo_trust_anchors\"")
        }
        create("production") {
            dimension = "environment"
            applicationId = "com.quellkern.nachweis"
            // Placeholder production hosts: NOT deployed, names not yet fixed.
            // Kept deliberately distinct from demo so the two configs never share
            // endpoints or trust roots.
            buildConfigField("String", "ISSUER_BASE_URL", "\"https://api.nachweis.tech\"")
            buildConfigField("String", "VERIFIER_BASE_URL", "\"https://verifier.nachweis.tech\"")
            buildConfigField("String", "TRUST_ANCHORS_RES", "\"production_trust_anchors\"")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Built-in Kotlin toolchain: align the JVM target with the Java compile target (17).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Foundation dependency: proves wallet-core 0.28.1 resolves on the raised
    // floor. No wallet-core API is referenced yet (that begins in B2).
    implementation(libs.eudi.wallet.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
