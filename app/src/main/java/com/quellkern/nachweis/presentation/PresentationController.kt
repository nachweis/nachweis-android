package com.quellkern.nachweis.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Drives one presentation at a time and exposes it as [state]. A pure state machine over an
 * [Oid4vpGateway], a [PresentationRequestValidator], and a [clock]: obtain → validate →
 * (consent) → send. All SDK, network, and time specifics are injected, so this is unit-tested
 * on the JVM with a fake gateway and a fixed clock.
 *
 * The shape mirrors [com.quellkern.nachweis.issuance.IssuanceController]: [onRequest]
 * validates and stops at [PresentationState.AwaitingConsent]; disclosure begins only when
 * [confirm] is called. A request that fails validation ends in [PresentationState.Rejected]
 * with no consent step and no disclosure.
 */
class PresentationController(
    private val gateway: Oid4vpGateway,
    private val validator: PresentationRequestValidator,
    private val scope: CoroutineScope,
    private val clock: () -> Date = { Date() },
) {
    private val _state = MutableStateFlow<PresentationState>(PresentationState.Idle)
    val state: StateFlow<PresentationState> = _state.asStateFlow()

    private var pending: ValidatedPresentationRequest? = null

    /** Reset to [PresentationState.Idle], discarding any pending request. */
    fun reset() {
        pending = null
        _state.value = PresentationState.Idle
    }

    /**
     * Obtain and validate the signed request at [requestUri], stopping at consent when valid.
     * Ignored while already resolving or sending, so a re-delivered intent cannot restart it.
     */
    fun onRequest(requestUri: String) {
        when (_state.value) {
            PresentationState.Resolving, PresentationState.Sending -> return
            else -> Unit
        }
        _state.value = PresentationState.Resolving
        scope.launch {
            val signed = try {
                gateway.obtainSignedRequest(requestUri)
            } catch (t: Throwable) {
                pending = null
                _state.value = PresentationState.Rejected(PresentationError.Unreadable)
                return@launch
            }
            when (val result = validator.validate(signed, clock())) {
                is PresentationValidation.Valid -> {
                    pending = result.request
                    _state.value = PresentationState.AwaitingConsent(result.request)
                }
                is PresentationValidation.Invalid -> {
                    pending = null
                    _state.value = PresentationState.Rejected(result.error)
                }
            }
        }
    }

    /**
     * Confirm the pending request and disclose exactly its requested claims. No-op unless the
     * state is [PresentationState.AwaitingConsent].
     */
    fun confirm() {
        val request = pending
        if (_state.value !is PresentationState.AwaitingConsent || request == null) return
        _state.value = PresentationState.Sending
        scope.launch {
            try {
                gateway.sendResponse(request, request.requestedClaims)
                pending = null
                _state.value = PresentationState.Sent
            } catch (t: Throwable) {
                pending = null
                _state.value = PresentationState.Rejected(PresentationError.Unexpected(t))
            }
        }
    }

    /** Decline a request awaiting consent: notify the verifier and end without disclosure. */
    fun decline() {
        if (_state.value !is PresentationState.AwaitingConsent) return
        pending = null
        gateway.reject()
        _state.value = PresentationState.Declined
    }
}
