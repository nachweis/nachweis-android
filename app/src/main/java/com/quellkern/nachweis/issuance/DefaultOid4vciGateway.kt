package com.quellkern.nachweis.issuance

import android.net.Uri
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultCreateDocumentSettings
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * The real [Oid4vciGateway], backed by wallet-core's [OpenId4VciManager]. It maps
 * wallet-core's callback events onto the SDK-agnostic [IssuanceProgress] the controller
 * consumes, and owns the device-authentication step via an injected [UserAuthenticator] so
 * the controller never sees BiometricPrompt or Keystore types.
 *
 * The manager [config] fixes our own auth-code redirect ([REDIRECT_URI]); a wrong redirect
 * scheme would make the auth-code issuance unable to return into the app (dev-plan.md B3).
 *
 * Note [verified — build only]: this path compiles against wallet-core 0.28.1 but is
 * exercised end-to-end only against a live SD-JWT PID issuer. The controller's state machine
 * (which this feeds) is what the unit tests cover, through a fake gateway.
 */
class DefaultOid4vciGateway(
    private val wallet: EudiWallet,
    private val issuerUrl: String,
    private val clientId: String,
    private val authenticator: UserAuthenticator,
    private val authScope: CoroutineScope,
) : Oid4vciGateway {

    private val directExecutor = Executor(Runnable::run)

    private val manager: OpenId4VciManager by lazy {
        val config = OpenId4VciManager.Config(
            issuerUrl,
            OpenId4VciManager.ClientAuthenticationType.None(clientId),
            REDIRECT_URI,
        )
        wallet.createOpenId4VciManager(config)
    }

    override suspend fun resolveOffer(offerUri: String): ResolvedOffer =
        suspendCoroutine { cont ->
            manager.resolveDocumentOffer(offerUri, directExecutor) { result: OfferResult ->
                when (result) {
                    is OfferResult.Success -> cont.resume(result.offer.toResolvedOffer())
                    is OfferResult.Failure -> cont.resumeWithException(result.cause)
                    else -> cont.resumeWithException(IllegalStateException("unknown offer result"))
                }
            }
        }

    override fun issue(
        offerUri: String,
        configurationIdentifier: String,
        transactionCode: String?,
    ): Flow<IssuanceProgress> = callbackFlow {
        val listener = OpenId4VciManager.OnIssueEvent { event: IssueEvent ->
            when (event) {
                is IssueEvent.Started -> trySend(IssuanceProgress.Started)
                is IssueEvent.DocumentRequiresUserAuth -> {
                    trySend(IssuanceProgress.AwaitingUserAuth)
                    authScope.launch {
                        try {
                            val unlocked = authenticator.unlock(event.keysRequireAuth, event.signingAlgorithm)
                            event.resume(unlocked)
                        } catch (t: Throwable) {
                            event.cancel(t.message ?: "authentication cancelled")
                            trySend(IssuanceProgress.Failed(t))
                        }
                    }
                }
                is IssueEvent.DocumentRequiresCreateSettings -> {
                    // wallet-core requires the app to supply the per-document key-creation
                    // settings for each offered credential. We derive them from the wallet's
                    // configured posture (WalletConfigFactory.configureDocumentKeyCreation, i.e.
                    // WalletSecurityPolicy: user-auth required, no reuse window, StrongBox
                    // preferred), so the key is minted under the same guarantee everywhere and
                    // the posture stays centralized rather than duplicated here. If deriving the
                    // settings fails, we refuse rather than mint a key with an unverified posture.
                    try {
                        event.resume(wallet.getDefaultCreateDocumentSettings(event.offeredDocument))
                    } catch (t: Throwable) {
                        event.cancel(t.message ?: "could not derive key-creation settings")
                        trySend(IssuanceProgress.Failed(t))
                    }
                }
                is IssueEvent.DocumentIssued ->
                    trySend(IssuanceProgress.Issued(event.documentId, event.name))
                is IssueEvent.DocumentFailed ->
                    trySend(IssuanceProgress.Failed(event.cause))
                is IssueEvent.DocumentDeferred ->
                    trySend(IssuanceProgress.Failed(UnsupportedOperationException("deferred issuance not supported")))
                is IssueEvent.Failure -> {
                    trySend(IssuanceProgress.Failed(event.cause))
                    close()
                }
                is IssueEvent.Finished -> {
                    trySend(IssuanceProgress.Finished)
                    close()
                }
                else -> Unit
            }
        }
        // Issue from the offer itself, not from the manager's configured issuer. A
        // pre-authorized offer carries its own credential-issuer identifier (here the EUDIPLO
        // tenant path .../issuers/nachweis) and pre-authorized-code grant; issuing by
        // configuration identifier would instead resolve metadata against the manager's
        // configured origin and miss the tenant path, so the offer's grant is never used.
        // resolveOffer already validated the issuer against the allowlist before we get here.
        manager.issueDocumentByOfferUri(
            offerUri,
            transactionCode,
            directExecutor,
            listener,
        )
        awaitClose { }
    }

    override fun resumeWithAuthorization(uri: Uri) {
        manager.resumeWithAuthorization(uri)
    }

    private fun Offer.toResolvedOffer(): ResolvedOffer = ResolvedOffer(
        issuerIdentifier = issuerMetadata.credentialIssuerIdentifier.toString(),
        offeredCredentials = offeredDocuments.map { doc ->
            OfferedCredential(
                configurationIdentifier = doc.configurationIdentifier.value,
                vct = (doc.documentFormat as? SdJwtVcFormat)?.vct,
                docType = (doc.documentFormat as? MsoMdocFormat)?.docType,
                displayName = null,
            )
        },
        requiresTransactionCode = txCodeSpec != null,
    )

    companion object {
        /** Our own auth-code redirect target; never the EU reference app's scheme. */
        const val REDIRECT_URI: String = "com.quellkern.nachweis://authorization"
    }
}
