package com.carcast.mirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Provider
import java.security.Security
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

class TlsIdentityTest {
    @Test
    fun generatedIdentityCompletesTlsHandshakeWithMatchingPairingCode() {
        val originalBc = Security.getProvider("BC")
        val originalBcPosition = Security.getProviders().indexOfFirst { it.name == "BC" } + 1
        Security.removeProvider("BC")

        try {
            // Simulate Android's built-in BC provider, which may not include X.509 conversion.
            val androidLikeBc = object : Provider("BC", 1.0, "Test provider without CertificateFactory.X.509") {}
            assertTrue(Security.insertProviderAt(androidLikeBc, 1) > 0)

            val identity = TlsIdentity.serverIdentity()
            assertTrue(identity.sas.matches(Regex("\\d{6}")))

            val server = TlsIdentity.serverSocket(identity).apply { soTimeout = 5_000 }
            val executor = Executors.newSingleThreadExecutor()
            try {
                val serverSas = executor.submit<String> {
                    (server.accept() as SSLSocket).use { accepted ->
                        accepted.startHandshake()
                        TlsIdentity.sas(accepted.session.localCertificates.first())
                    }
                }

                TlsIdentity.clientSocket("127.0.0.1", server.localPort).use { client ->
                    assertEquals(identity.sas, TlsIdentity.sas(client.session.peerCertificates.first()))
                }
                assertEquals(identity.sas, serverSas.get(5, TimeUnit.SECONDS))
            } finally {
                server.close()
                executor.shutdownNow()
            }
        } finally {
            Security.removeProvider("BC")
            originalBc?.let { Security.insertProviderAt(it, originalBcPosition) }
        }
    }
}
