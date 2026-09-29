package com.carcast.mirror.discovery

import android.content.Context
import com.carcast.mirror.core.CastTarget

class DiscoveryCoordinator(context: Context) {
    private val nsd = DiscoveryManager(context)
    private val ssdp = SsdpDiscoveryProvider(context)
    fun start(onChanged: (List<DiscoveredReceiver>) -> Unit) {
        val all = linkedMapOf<String, DiscoveredReceiver>()
        nsd.start { native -> native.forEach { all["native:${it.host.hostAddress}:${it.port}"] = it.copy(protocol = "CarCast Native") }; onChanged(all.values.toList()) }
        ssdp.start { media -> media.forEach { target -> if (target.host != null) { val item = DiscoveredReceiver(target.name, target.host, target.port ?: 1900, null, "media-only", "DLNA") ; all["dlna:${target.host.hostAddress}"] = item } }; onChanged(all.values.toList()) }
    }
    fun stop() { nsd.stop(); ssdp.stop() }
}
