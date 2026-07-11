import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Developer-local issuer override for testing against a local EUDIPLO. Read from
// local.properties (git-ignored) key `nachweis.localIssuerOverride`; empty by default so
// committed builds never point anywhere but the canonical hosts. Not a secret (a localhost
// origin), just machine-specific, so it stays out of the tree.
val localIssuerOverride: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("nachweis.localIssuerOverride", "")

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
            // WRPRC-provider trust anchor (public only; the provider key is offline, provisioned
            // by Workstream A). Empty placeholder until published: registration checks fail closed.
            buildConfigField("String", "WRPRC_TRUST_ANCHORS_RES", "\"demo_wrprc_trust_anchors\"")
            // Public signed WRPRC status lists to refresh out of band (comma-separated). The demo
            // publisher serves a valid and a revoked list; refreshing both lets a revoked WRPRC
            // resolve to Revoked rather than only fail-closed-unknown. Empty until published.
            buildConfigField(
                "String",
                "WRPRC_STATUS_LIST_URLS",
                "\"https://verifier-sandbox.nachweis.tech/trust/status/wrprc-valid.jwt," +
                    "https://verifier-sandbox.nachweis.tech/trust/status/wrprc-revoked.jwt\"",
            )
            // Public signed WRPAC revocation list (CRL) refreshed out of band, plus the public
            // WRPAC-provider issuer cert used to verify it (chains to the bundled demo root).
            // Empty on production: the access-cert revocation check then fails closed.
            buildConfigField(
                "String",
                "WRPAC_CRL_URL",
                "\"https://verifier-sandbox.nachweis.tech/trust/wrpac/wrpac-provider.crl.der\"",
            )
            buildConfigField("String", "WRPAC_ISSUER_CERT_RES", "\"demo_wrpac_provider\"")
            // Public OpenID4VCI client id for the demo issuer tenant.
            buildConfigField("String", "OID4VCI_CLIENT_ID", "\"nachweis-demo\"")
            // Developer-local issuer override (empty unless set in local.properties). Only the
            // demo flavor honors it; production never accepts a local override.
            buildConfigField("String", "LOCAL_ISSUER_OVERRIDE", "\"$localIssuerOverride\"")
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
            buildConfigField("String", "WRPRC_TRUST_ANCHORS_RES", "\"production_wrprc_trust_anchors\"")
            // No status lists published for the placeholder production hosts: fail closed.
            buildConfigField("String", "WRPRC_STATUS_LIST_URLS", "\"\"")
            // No WRPAC CRL published for the placeholder production hosts: fail closed.
            buildConfigField("String", "WRPAC_CRL_URL", "\"\"")
            buildConfigField("String", "WRPAC_ISSUER_CERT_RES", "\"\"")
            buildConfigField("String", "OID4VCI_CLIENT_ID", "\"nachweis\"")
            // Production never honors a local override.
            buildConfigField("String", "LOCAL_ISSUER_OVERRIDE", "\"\"")
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

    // B3/B4 issuance: QR scanning and device authentication.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.biometric)

    // B5 presentation: signed-request (JAR) parsing/verification and JSON handling.
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    // B5 fixtures only: mint a throwaway WRPAC test CA and signed requests on the JVM.
    testImplementation(libs.bouncycastle.bcpkix)
    testImplementation(libs.bouncycastle.bcprov)
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
