package com.carcast.mirror.discovery

import android.content.Context
import com.carcast.mirror.core.CastDiscoveryProvider
import com.carcast.mirror.core.CastProtocol
import com.carcast.mirror.core.CastTarget
import com.carcast.mirror.core.ReceiverCapability
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SsdpDiscoveryProvider(private val context: Context) : CastDiscoveryProvider {
    override val protocol = CastProtocol.DLNA
    @Volatile private var running = false
    private var worker: Thread? = null
    override fun start(onTargets: (List<CastTarget>) -> Unit) {
        if (running) return; running = true
        worker = Thread {
            val found = mutableListOf<CastTarget>(); runCatching {
                DatagramSocket().use { socket -> socket.soTimeout = 1500; val request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n".toByteArray(); socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900)); val buffer = ByteArray(8192); while (running) { val packet = DatagramPacket(buffer, buffer.size); runCatching { socket.receive(packet) }.onFailure { return@use }; val text = packet.data.decodeToString(0, packet.length); val location = text.lines().firstOrNull { it.startsWith("LOCATION:", true) }?.substringAfter(':')?.trim(); if (location != null && found.none { it.details == location }) { found += CastTarget("dlna-${packet.address.hostAddress}", "${packet.address.hostAddress} media renderer", CastProtocol.DLNA, packet.address, 1900, setOf(ReceiverCapability.MEDIA_ONLY), details = location); onTargets(found.toList()) } } }
            }
        }.also { it.start() }
    }
    override fun stop() { running = false; worker?.interrupt(); worker = null }
}
