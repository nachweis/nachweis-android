package com.quellkern.nachweis.wallet

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that Android Keystore actually *enforces* user authentication for keys created the
 * way wallet-core creates credential keys ([KeyGenParameterSpec.setUserAuthenticationRequired]).
 * This is device-level evidence for the "Keystore-enforced user authentication" criterion:
 * a key that requires auth cannot be used to sign without a fresh authentication, so the
 * operation is rejected with a "user not authenticated" Keystore error (surfaced either as
 * UserNotAuthenticatedException at init, or, for a zero-timeout key on Keystore2, as a
 * SignatureException caused by a KeyStoreException at finalization).
 *
 * Requires a secure lock screen; the test suite provisions a PIN, but if the device is not
 * secured the test is skipped (assumption failure) rather than falsely passing.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreUserAuthInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "nachweis-test-userauth-key"

    @Test
    fun userAuthRequiredKey_cannotSignWithoutAuthentication() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assumeTrue("requires a secure lock screen", keyguard.isDeviceSecure)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(alias)

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val specBuilder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // timeout 0 => authentication required for every use.
            specBuilder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.initialize(specBuilder.build())
        generator.generateKeyPair()

        try {
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(entry.privateKey)
            signature.update("challenge".toByteArray())
            signature.sign()
            fail("signing must be rejected: the key requires user authentication")
        } catch (expected: GeneralSecurityException) {
            assertTrue(
                "rejection must be a user-authentication failure, was: ${describe(expected)}",
                isUserNotAuthenticated(expected),
            )
        } finally {
            keyStore.deleteEntry(alias)
        }
    }

    /** True if any exception in the cause chain is a Keystore "user not authenticated" error. */
    private fun isUserNotAuthenticated(t: Throwable): Boolean {
        var cause: Throwable? = t
        while (cause != null) {
            val name = cause.javaClass.simpleName
            val msg = (cause.message ?: "").lowercase()
            if (name == "UserNotAuthenticatedException") return true
            if (name == "KeyStoreException" && "not authenticated" in msg) return true
            cause = cause.cause
        }
        return false
    }

    private fun describe(t: Throwable): String {
        val chain = StringBuilder()
        var cause: Throwable? = t
        while (cause != null) {
            chain.append(cause.javaClass.simpleName).append(": ").append(cause.message).append(" | ")
            cause = cause.cause
        }
        return chain.toString()
    }
}
