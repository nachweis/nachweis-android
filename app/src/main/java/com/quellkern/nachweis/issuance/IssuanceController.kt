package com.quellkern.nachweis.issuance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one issuance at a time and exposes it as [state]. It is a pure state machine over
 * an [Oid4vciGateway] and an [IssuanceAllowlist][IssuerAllowlist]: resolve → evaluate →
 * (consent) → issue. All SDK and Android specifics live behind the gateway, so this class
 * is unit-tested on the JVM with a fake gateway and a test scope.
 *
 * The two-step shape is deliberate: [offer] resolves and evaluates but stops at
 * [IssuanceState.AwaitingConsent]; issuance only begins when [confirm] is called. An offer
 * that fails policy ends in [IssuanceState.Declined] without any credential-issuance request.
 */
class IssuanceController(
    private val gateway: Oid4vciGateway,
    private val allowlist: IssuerAllowlist,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<IssuanceState>(IssuanceState.Idle)
    val state: StateFlow<IssuanceState> = _state.asStateFlow()

    // The offer under consideration, retained between [offer] and [confirm].
    private var pending: PendingIssuance? = null

    /** Reset to [IssuanceState.Idle], discarding any pending offer. */
    fun reset() {
        pending = null
        _state.value = IssuanceState.Idle
    }

    /**
     * Resolve [offerUri], evaluate it against policy, and stop at consent when acceptable.
     * Ignored while an issuance is already resolving or issuing.
     */
    fun offer(offerUri: String) {
        when (_state.value) {
            IssuanceState.Resolving, IssuanceState.Issuing, IssuanceState.AwaitingUserAuth -> return
            else -> Unit
        }
        _state.value = IssuanceState.Resolving
        scope.launch {
            val resolved = try {
                gateway.resolveOffer(offerUri)
            } catch (t: Throwable) {
                pending = null
                _state.value = IssuanceState.Failed(mapResolveFailure(t))
                return@launch
            }
            when (val decision = OfferEvaluation.evaluate(resolved, allowlist)) {
                is OfferDecision.Acceptable -> {
                    pending = PendingIssuance(offerUri, decision.credential, resolved.requiresTransactionCode)
                    _state.value = IssuanceState.AwaitingConsent(
                        issuerIdentifier = resolved.issuerIdentifier,
                        credentialName = decision.credential.displayName ?: decision.credential.vct.orEmpty(),
                        vct = decision.credential.vct.orEmpty(),
                        requiresTransactionCode = resolved.requiresTransactionCode,
                    )
                }
                is OfferDecision.NotAllowlisted -> {
                    pending = null
                    _state.value = IssuanceState.Declined(IssuanceError.IssuerNotAllowed)
                }
                is OfferDecision.UnsupportedCredential -> {
                    pending = null
                    _state.value = IssuanceState.Declined(IssuanceError.UnsupportedCredential)
                }
            }
        }
    }

    /**
     * Confirm the pending offer and request issuance. [transactionCode] is required only
     * when the resolved offer said so. No-op unless the state is [IssuanceState.AwaitingConsent].
     */
    fun confirm(transactionCode: String? = null) {
        val current = pending
        if (_state.value !is IssuanceState.AwaitingConsent || current == null) return
        _state.value = IssuanceState.Issuing
        scope.launch {
            gateway.issue(current.offerUri, current.credential.configurationIdentifier, transactionCode)
                .collect { progress -> applyProgress(progress) }
        }
    }

    /** Forward an authorization-code redirect into an in-flight auth-code issuance. */
    fun onAuthorizationCallback(uri: android.net.Uri) {
        gateway.resumeWithAuthorization(uri)
    }

    private fun applyProgress(progress: IssuanceProgress) {
        when (progress) {
            IssuanceProgress.Started -> _state.value = IssuanceState.Issuing
            IssuanceProgress.AwaitingUserAuth -> _state.value = IssuanceState.AwaitingUserAuth
            is IssuanceProgress.Issued -> {
                pending = null
                _state.value = IssuanceState.Issued(progress.documentId, progress.name)
            }
            is IssuanceProgress.Failed -> {
                pending = null
                _state.value = IssuanceState.Failed(IssuanceError.fromThrowable(progress.cause))
            }
            IssuanceProgress.Finished -> {
                // Terminal only if nothing was issued; a prior Issued state is authoritative.
                if (_state.value !is IssuanceState.Issued) {
                    _state.value = IssuanceState.Failed(IssuanceError.Unexpected(IllegalStateException("no document issued")))
                }
            }
        }
    }

    private fun mapResolveFailure(t: Throwable): IssuanceError {
        val mapped = IssuanceError.fromThrowable(t)
        // A resolve failure that isn't network/auth is most usefully reported as an
        // unreadable offer rather than a generic unexpected error.
        return if (mapped is IssuanceError.Unexpected) IssuanceError.OfferUnresolvable else mapped
    }

    private data class PendingIssuance(
        val offerUri: String,
        val credential: OfferedCredential,
        val requiresTransactionCode: Boolean,
    )
}
