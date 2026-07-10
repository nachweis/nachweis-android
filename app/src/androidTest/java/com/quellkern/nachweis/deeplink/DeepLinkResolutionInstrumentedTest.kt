package com.quellkern.nachweis.deeplink

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the manifest intent filters actually resolve to this app: every registered scheme
 * (issuance offers, presentation requests, and our own auth-code redirect) is handled, and
 * the EU reference app's scheme is not. This exercises the real merged manifest on a device,
 * complementing the pure [DeepLinkIntakeTest] on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkResolutionInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun resolvesInThisApp(uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    @Test
    fun offerSchemesResolve() {
        assertTrue(resolvesInThisApp("openid-credential-offer://issue?credential_offer_uri=https%3A%2F%2Fx"))
        assertTrue(resolvesInThisApp("haip-vci://issue?x=1"))
    }

    @Test
    fun presentationSchemesResolve() {
        for (scheme in listOf("openid4vp", "eudi-openid4vp", "mdoc-openid4vp", "haip-vp")) {
            assertTrue("$scheme should resolve", resolvesInThisApp("$scheme://authorize?request=1"))
        }
    }

    @Test
    fun ownRedirectResolves() {
        assertTrue(resolvesInThisApp("com.quellkern.nachweis://authorization?code=abc&state=xyz"))
    }

    @Test
    fun euReferenceSchemeDoesNotResolveInThisApp() {
        assertFalse(resolvesInThisApp("eu.europa.ec.euidi://authorization?code=abc"))
    }
}
