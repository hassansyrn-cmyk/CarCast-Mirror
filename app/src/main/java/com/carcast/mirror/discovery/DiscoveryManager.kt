package com.carcast.mirror.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress

const val SERVICE_TYPE = "_carcast._tcp."
data class DiscoveredReceiver(val name: String, val host: InetAddress, val port: Int, val sessionId: String? = null, val capabilities: String = "", val protocol: String = "CarCast Native")

class DiscoveryManager(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    fun start(onChanged: (List<DiscoveredReceiver>) -> Unit) {
        val found = mutableListOf<DiscoveredReceiver>()
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        val sessionId = service.attributes["session"]?.toString(Charsets.UTF_8)
                        val capabilities = service.attributes["capabilities"]?.toString(Charsets.UTF_8) ?: ""
                        val item = DiscoveredReceiver(service.serviceName, service.host, service.port, sessionId, capabilities)
                        if (found.none { it.name == item.name }) { found += item; onChanged(found.toList()) }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) { found.removeAll { it.name == info.serviceName }; onChanged(found.toList()) }
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) { nsd.stopServiceDiscovery(this) }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {}
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }
    fun stop() { listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }; listener = null }
}
