package com.quellkern.nachweis.deeplink

import android.net.Uri

/**
 * Classifies an inbound deep link into the action the app should take. The routing decision
 * is a pure function of the link's scheme (and, for the authorization callback, its host),
 * so [classify] with a raw string is unit-tested without an Activity or the wallet SDK; the
 * [Uri] overload is a thin adapter for production intents.
 *
 * The scheme vocabulary is fixed by the OpenID4VCI / OpenID4VP specifications and by our own
 * registered redirect scheme; it mirrors the intent filters declared in the manifest. A
 * registered scheme maps to exactly one action; unregistered input maps to
 * [DeepLinkAction.Unknown] rather than being misrouted.
 */
object DeepLinkIntake {

    /** OpenID4VCI credential-offer schemes (issuance entry points). */
    val OFFER_SCHEMES: Set<String> = setOf("openid-credential-offer", "haip-vci")

    /** OpenID4VP presentation schemes (handled by the B5 presentation slice). */
    val PRESENTATION_SCHEMES: Set<String> =
        setOf("openid4vp", "eudi-openid4vp", "mdoc-openid4vp", "haip-vp")

    /** Our own auth-code redirect scheme; never the EU reference app's `eu.europa.ec.euidi`. */
    const val REDIRECT_SCHEME: String = "com.quellkern.nachweis"

    /** Host segment of [REDIRECT_SCHEME] that carries the authorization response. */
    const val REDIRECT_HOST: String = "authorization"

    /** Adapter for production intents. */
    fun classify(uri: Uri?): DeepLinkAction = classify(uri?.toString())

    /** Pure classification of a raw deep-link string. */
    fun classify(raw: String?): DeepLinkAction {
        if (raw.isNullOrBlank()) return DeepLinkAction.Unknown
        val scheme = schemeOf(raw)?.lowercase() ?: return DeepLinkAction.Unknown
        return when {
            scheme in OFFER_SCHEMES -> DeepLinkAction.CredentialOffer(raw)
            scheme in PRESENTATION_SCHEMES -> DeepLinkAction.Presentation(raw)
            scheme == REDIRECT_SCHEME && hostOf(raw)?.lowercase() == REDIRECT_HOST ->
                DeepLinkAction.AuthorizationCallback(raw)
            else -> DeepLinkAction.Unknown
        }
    }

    /** The scheme component, i.e. everything before the first ':'. */
    private fun schemeOf(raw: String): String? {
        val colon = raw.indexOf(':')
        if (colon <= 0) return null
        return raw.substring(0, colon)
    }

    /** The host after "scheme://", up to the first '/', '?', or '#'; null if not hierarchical. */
    private fun hostOf(raw: String): String? {
        val marker = "://"
        val start = raw.indexOf(marker)
        if (start < 0) return null
        val rest = raw.substring(start + marker.length)
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (end < 0) rest else rest.substring(0, end)
        // Strip any userinfo@ and :port, leaving the host.
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.ifBlank { null }
    }
}

/** The disjoint set of things an inbound deep link can ask the app to do. */
sealed interface DeepLinkAction {
    /** An OpenID4VCI credential offer to resolve and (subject to allowlist) issue. */
    data class CredentialOffer(val offerUri: String) : DeepLinkAction

    /**
     * An OpenID4VP presentation request. Registered so the app owns the scheme, but the
     * handling lands with the B5 presentation slice; until then the UI states it plainly.
     */
    data class Presentation(val requestUri: String) : DeepLinkAction

    /** The authorization-code redirect back into the app during an auth-code issuance. */
    data class AuthorizationCallback(val uri: String) : DeepLinkAction

    /** Anything not matching a registered scheme. */
    data object Unknown : DeepLinkAction
}
