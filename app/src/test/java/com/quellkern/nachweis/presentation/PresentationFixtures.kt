package com.quellkern.nachweis.presentation

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Mints throwaway, clearly test-only WRPAC-style certificates and signed OpenID4VP requests
 * (JARs) for the B5 validator tests. Nothing here is a real trust anchor: every key is
 * generated per test run with BouncyCastle, so no private key material is committed. The
 * validator under test is what matters; these fixtures only exercise its checks.
 */
object PresentationFixtures {

    init {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A minted certificate authority: its self-signed cert and signing key. */
    data class Ca(val certificate: X509Certificate, val key: KeyPair)

    /** A minted leaf (WRPAC-style) certificate, its key, and its issuing chain (leaf..ca). */
    data class Leaf(
        val certificate: X509Certificate,
        val key: KeyPair,
        val chain: List<X509Certificate>,
    )

    fun generateEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC", "BC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    /** Mint a self-signed CA valid around now. */
    fun newCa(commonName: String = "nachweis Test WRPAC CA"): Ca {
        val key = generateEcKeyPair()
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=$commonName, O=nachweis Test, C=DE")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            Date(now - DAY_MS),
            Date(now + 3650 * DAY_MS),
            subject,
            key.public,
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(key.private)
        val certificate = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
        return Ca(certificate, key)
    }

    /**
     * Mint a leaf signed by [ca], with the given SAN dNSName and validity window (offsets in
     * days from now). Defaults describe a currently-valid verifier certificate.
     */
    fun newLeaf(
        ca: Ca,
        sanDns: String = "verifier-sandbox.nachweis.tech",
        commonName: String = sanDns,
        notBeforeDays: Long = -1,
        notAfterDays: Long = 365,
        organizationIdentifier: String? = null,
    ): Leaf {
        val key = generateEcKeyPair()
        val now = System.currentTimeMillis()
        // Use the CA's exact encoded subject as the issuer, so the leaf's issuer DN byte-matches
        // the CA's subject DN (rebuilding it from the RFC2253 string reverses attribute order).
        val issuer = JcaX509CertificateHolder(ca.certificate).subject
        val subject = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, "DE")
            addRDN(BCStyle.O, "nachweis Test Verifier")
            addRDN(BCStyle.CN, commonName)
            // The WRP identifier lives in organizationIdentifier (OID 2.5.4.97); this is the
            // WRPAC side of the D1 binding to the WRPRC's wrp.id[].identifier.
            if (organizationIdentifier != null) addRDN(BCStyle.ORGANIZATION_IDENTIFIER, organizationIdentifier)
        }.build()
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(now + 1),
            Date(now + notBeforeDays * DAY_MS),
            Date(now + notAfterDays * DAY_MS),
            subject,
            key.public,
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            .addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(GeneralName(GeneralName.dNSName, sanDns)),
            )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(ca.key.private)
        val certificate = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
        return Leaf(certificate, key, listOf(certificate, ca.certificate))
    }

    /** PEM-encode a certificate (for TrustStore.fromPem tests and anchor bundles). */
    fun toPem(certificate: X509Certificate): String {
        val b64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(certificate.encoded)
        return "-----BEGIN CERTIFICATE-----\n$b64\n-----END CERTIFICATE-----\n"
    }

    /** A minimal DCQL query for exactly the SD-JWT PID and the given claim paths. */
    fun pidDcql(vct: String = PresentationRequestValidator.SUPPORTED_VCT, claims: List<String> = listOf("given_name", "family_name")): String {
        val claimObjects = claims.joinToString(",") { """{"path":["$it"]}""" }
        return """
            {"credentials":[{"id":"pid","format":"dc+sd-jwt",
            "meta":{"vct_values":["$vct"]},
            "claims":[$claimObjects]}]}
        """.trimIndent().replace("\n", "")
    }

    /**
     * Build a signed request object (JAR) with [leaf]'s key and its chain in `x5c`. [dcqlJson]
     * and the client-id fields let each test target one check; [extraPayload] overrides or adds
     * top-level members verbatim.
     */
    fun signedRequest(
        leaf: Leaf,
        clientId: String = "x509_san_dns:verifier-sandbox.nachweis.tech",
        responseUri: String = "https://verifier-sandbox.nachweis.tech/response",
        nonce: String = "n-0S6_WzA2Mj",
        dcqlJson: String = pidDcql(),
        purpose: String? = "To confirm your name",
        verifierInfoJson: String? = null,
        extraPayload: Map<String, String> = emptyMap(),
    ): String {
        val members = LinkedHashMap<String, String>()
        members["client_id"] = jsonString(clientId)
        members["response_type"] = jsonString("vp_token")
        members["response_mode"] = jsonString("direct_post")
        members["response_uri"] = jsonString(responseUri)
        members["nonce"] = jsonString(nonce)
        members["dcql_query"] = dcqlJson
        if (purpose != null) members["purpose"] = jsonString(purpose)
        if (verifierInfoJson != null) members["verifier_info"] = verifierInfoJson
        extraPayload.forEach { (k, v) -> members[k] = v }
        val payloadJson = members.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .x509CertChain(leaf.chain.map { Base64.encode(it.encoded) })
            .build()
        val jws = JWSObject(header, Payload(payloadJson))
        jws.sign(ECDSASigner(leaf.key.private as ECPrivateKey))
        return jws.serialize()
    }

    /** Corrupt the signature segment of a compact JWS so verification fails. */
    fun corruptSignature(compactJws: String): String {
        val parts = compactJws.split(".")
        val sig = parts[2]
        val flipped = if (sig.first() == 'A') "B" + sig.substring(1) else "A" + sig.substring(1)
        return parts[0] + "." + parts[1] + "." + flipped
    }

    /** An unsecured (`alg=none`) token — structurally not a signed request. */
    fun unsignedToken(): String {
        val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"client_id":"x509_san_dns:x"}""".toByteArray())
        return "$header.$payload."
    }

    fun statusGood(leaf: Leaf): RequestStatusSource =
        CachedStatusSource(good = setOf(CachedStatusSource.keyOf(leaf.certificate)))

    fun statusRevoked(leaf: Leaf): RequestStatusSource =
        CachedStatusSource(revoked = setOf(CachedStatusSource.keyOf(leaf.certificate)))

    fun statusUnknown(): RequestStatusSource = CachedStatusSource()

    fun x509HashClientId(leaf: Leaf): String {
        val hash = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(leaf.certificate.encoded))
        return "x509_hash:$hash"
    }

    // --- D1 / WRPRC fixtures -------------------------------------------------------------------

    /** A WRP identifier as it appears in both the WRPAC organizationIdentifier and the WRPRC. */
    const val WRP_ID: String = "LEIXG-743700EB2QF2J1WRO915"

    /** A different WRP identifier for the negative (unrelated-identifier) binding fixture. */
    const val OTHER_WRP_ID: String = "LEIXG-529900T8BM49AURSDO55"

    /**
     * Build a conforming JWT WRPRC (dev-plan.md D1 supported profile): `typ=rc-wrp+jwt`, an AdES
     * ECDSA signature, the JAdES B-B markers (`x5c` signing-cert inclusion and a signed `sigT`
     * claimed signing time), and the required policy/profile payload fields. Signed by
     * [provider]. Options let each test target one check.
     */
    fun wrprcJwt(
        provider: Leaf,
        wrpIds: List<String> = listOf(WRP_ID),
        registeredClaims: List<String> = listOf("given_name", "family_name"),
        vct: String = PresentationRequestValidator.SUPPORTED_VCT,
        statusUri: String = "https://registrar.invalid/status-lists/1",
        statusIdx: Int = 7,
        iatEpochSeconds: Long = System.currentTimeMillis() / 1000 - 3600,
        expEpochSeconds: Long? = null,
        typ: String = "rc-wrp+jwt",
        includeSigT: Boolean = true,
        includeX5c: Boolean = true,
        includePolicyId: Boolean = true,
        includeStatus: Boolean = true,
        includeCredentials: Boolean = true,
    ): String {
        val idsJson = wrpIds.joinToString(",") {
            """{"type":"http://data.europa.eu/eudi/id/LEI","identifier":"$it"}"""
        }
        val claimsJson = registeredClaims.joinToString(",") { """{"path":["$it"]}""" }
        val members = LinkedHashMap<String, String>()
        members["name"] = jsonString("Test Webshop")
        members["wrp"] = """{"legal_name":["Test Webshop Ltd"],"id":[$idsJson]}"""
        if (includePolicyId) members["policy_id"] = """["0.4.0.19475.3.1"]"""
        members["iat"] = iatEpochSeconds.toString()
        if (expEpochSeconds != null) members["exp"] = expEpochSeconds.toString()
        if (includeStatus) members["status"] = """{"status_list":{"idx":$statusIdx,"uri":"$statusUri"}}"""
        if (includeCredentials) {
            members["credentials"] =
                """[{"format":"dc+sd-jwt","meta":{"vct_values":["$vct"]},"claims":[$claimsJson]}]"""
        }
        val payloadJson = members.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

        val headerBuilder = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType(typ))
        if (includeX5c) headerBuilder.x509CertChain(provider.chain.map { Base64.encode(it.encoded) })
        if (includeSigT) {
            headerBuilder.customParam("sigT", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(iatEpochSeconds)))
        }
        val jws = JWSObject(headerBuilder.build(), Payload(payloadJson))
        jws.sign(ECDSASigner(provider.key.private as ECPrivateKey))
        return jws.serialize()
    }

    /** Wrap a JWT WRPRC as a `verifier_info` array entry (JSON string), as B5 carries it. */
    fun verifierInfoJwt(compactJws: String): String =
        """[{"format":"rc-wrp+jwt","data":${jsonString(compactJws)}}]"""

    /** A `verifier_info` array whose registration certificate is a CWT (unsupported profile). */
    fun verifierInfoCwt(): String =
        """[{"format":"rc-wrp+cwt","data":"0oRDoQEmoQRBMWFhWEC...cbor..."}]"""

    fun wrprcStatusGood(uri: String = "https://registrar.invalid/status-lists/1", idx: Int = 7): WrprcStatusSource =
        CachedWrprcStatusSource(good = setOf(CachedWrprcStatusSource.keyOf(WrprcStatusRef(uri, idx))))

    fun wrprcStatusRevoked(uri: String = "https://registrar.invalid/status-lists/1", idx: Int = 7): WrprcStatusSource =
        CachedWrprcStatusSource(revoked = setOf(CachedWrprcStatusSource.keyOf(WrprcStatusRef(uri, idx))))

    fun wrprcStatusUnknown(): WrprcStatusSource = CachedWrprcStatusSource()

    private fun jsonString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
