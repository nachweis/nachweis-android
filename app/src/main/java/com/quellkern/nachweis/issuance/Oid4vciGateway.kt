package com.quellkern.nachweis.issuance

import kotlinx.coroutines.flow.Flow

/**
 * The seam between the issuance controller and wallet-core's OpenID4VCI manager. Every
 * wallet-core and Android type stays behind this interface so [IssuanceController] is a
 * pure state machine, unit-tested on the JVM with a fake implementation.
 *
 * The real implementation ([com.quellkern.nachweis.issuance.DefaultOid4vciGateway]) also
 * owns the device-authentication step: when a credential key requires user authentication,
 * it drives the prompt itself and emits [IssuanceProgress.AwaitingUserAuth] only as a
 * UI-facing signal, so the controller never touches BiometricPrompt or Keystore types.
 */
interface Oid4vciGateway {

    /** Resolve an offer URI to its issuer and offered credentials, or throw. */
    suspend fun resolveOffer(offerUri: String): ResolvedOffer

    /**
     * Issue the credential identified by [configurationIdentifier] from the offer at
     * [offerUri]. Emits progress until it terminates in
     * [IssuanceProgress.Issued]+[IssuanceProgress.Finished] or [IssuanceProgress.Failed].
     */
    fun issue(
        offerUri: String,
        configurationIdentifier: String,
        transactionCode: String?,
    ): Flow<IssuanceProgress>

    /** Feed an authorization-code redirect back into an in-flight auth-code issuance. */
    fun resumeWithAuthorization(uri: android.net.Uri)
}

/** SDK-agnostic issuance progress, mapped from wallet-core's `IssueEvent` sequence. */
sealed interface IssuanceProgress {
    /** Issuance has started for the requested credential. */
    data object Started : IssuanceProgress

    /** A key requires device authentication; the gateway is driving the prompt. */
    data object AwaitingUserAuth : IssuanceProgress

    /** A document was issued and stored. */
    data class Issued(val documentId: String, val name: String) : IssuanceProgress

    /** Issuance failed. */
    data class Failed(val cause: Throwable) : IssuanceProgress

    /** The issuance session finished (all requested documents processed). */
    data object Finished : IssuanceProgress
}
