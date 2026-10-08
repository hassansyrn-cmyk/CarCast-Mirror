package com.carcast.mirror.core

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.*
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.*

object TlsIdentity {
    init { if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider()) }
    data class ServerIdentity(val context: SSLContext, val sas: String)
    fun serverIdentity(): ServerIdentity {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = Date()
        val subject = X500Name("CN=CarCast ephemeral")
        val certificateHolder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(64, SecureRandom()),
            now,
            Date(now.time + 120_000),
            subject,
            kp.public
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        // Android's built-in BC provider may not expose CertificateFactory.X.509.
        // Let the platform's default X.509 provider convert the generated certificate.
        val cert = JcaX509CertificateConverter().getCertificate(certificateHolder)
        val store = KeyStore.getInstance("JKS").apply { load(null, null); setKeyEntry("carcast", kp.private, CharArray(0), arrayOf(cert)) }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        return ServerIdentity(SSLContext.getInstance("TLSv1.3").apply { init(km.keyManagers, null, SecureRandom()) }, sas(cert))
    }
    fun serverSocket(identity: ServerIdentity): SSLServerSocket { return identity.context.serverSocketFactory.createServerSocket(0) as SSLServerSocket }
    fun clientSocket(host: String, port: Int): SSLSocket {
        val trust = arrayOf<TrustManager>(object : X509TrustManager { override fun getAcceptedIssuers() = arrayOf<X509Certificate>(); override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}; override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {} })
        val context = SSLContext.getInstance("TLSv1.3").apply { init(null, trust, SecureRandom()) }
        return (context.socketFactory.createSocket() as SSLSocket).apply { connect(InetSocketAddress(host, port), 15_000); startHandshake() }
    }
    fun sas(certificate: java.security.cert.Certificate): String { val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded); val n = ((digest[0].toInt() and 0xff) shl 16) or ((digest[1].toInt() and 0xff) shl 8) or (digest[2].toInt() and 0xff); return "%06d".format(n % 1_000_000) }
}
