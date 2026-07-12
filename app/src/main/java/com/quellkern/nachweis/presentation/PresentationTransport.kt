package com.quellkern.nachweis.presentation

import android.net.Uri
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.Response
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import org.multipaz.securearea.KeyUnlockData

/**
 * The wallet-core operations [DefaultOid4vpGateway] needs to run one OpenID4VP presentation:
 * listen for transfer events, start the remote presentation, dispatch the response, reject, and
 * fetch a document's key-unlock data. Extracted so the gateway's obtain/validate/consent/send
 * choreography is JVM-unit-testable with a fake transport — this file is the *only* one on the
 * presentation path that touches [EudiWallet], its [DocumentExtensions], and [android.net.Uri],
 * confining the un-fakeable SDK surface to the single delegating implementation below.
 *
 * The request URI is passed as a plain [String]; [WalletPresentationTransport] parses it into an
 * [android.net.Uri] internally, keeping that Android type out of the gateway and its tests.
 */
interface PresentationTransport {

    /** Register a transfer-event listener with the underlying transport. */
    fun addTransferEventListener(listener: TransferEvent.Listener)

    /** Remove a previously registered transfer-event listener. */
    fun removeTransferEventListener(listener: TransferEvent.Listener)

    /** Start the remote presentation for the signed request reachable at [requestUri]. */
    fun startRemotePresentation(requestUri: String)

    /** Dispatch a built [response] to the verifier (fire-and-forget; outcome arrives as an event). */
    fun sendResponse(response: Response)

    /** Tell the verifier the user declined, where the transport supports it. */
    fun rejectRemotePresentation()

    /**
     * The default key-unlock data for the document [documentId], or null when its signing key needs
     * no unlock. Returned as the [KeyUnlockData] supertype; the concrete
     * [org.multipaz.securearea.AndroidKeystoreKeyUnlockData] is what actually flows through on-device.
     */
    fun keyUnlockDataFor(documentId: String): KeyUnlockData?
}

/**
 * The real [PresentationTransport], six one-line delegations to the [EudiWallet] (which itself
 * implements wallet-core's presentation manager). Holds no state of its own.
 */
class WalletPresentationTransport(private val wallet: EudiWallet) : PresentationTransport {

    override fun addTransferEventListener(listener: TransferEvent.Listener) {
        wallet.addTransferEventListener(listener)
    }

    override fun removeTransferEventListener(listener: TransferEvent.Listener) {
        wallet.removeTransferEventListener(listener)
    }

    override fun startRemotePresentation(requestUri: String) {
        wallet.startRemotePresentation(Uri.parse(requestUri), null)
    }

    override fun sendResponse(response: Response) {
        wallet.sendResponse(response)
    }

    override fun rejectRemotePresentation() {
        wallet.rejectRemotePresentation()
    }

    override fun keyUnlockDataFor(documentId: String): KeyUnlockData? =
        wallet.getDefaultKeyUnlockData(documentId)
}
