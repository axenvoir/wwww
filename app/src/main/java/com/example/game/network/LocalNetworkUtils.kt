package com.example.game.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class LocalInterfaceAddress(
    val interfaceName: String,
    val ipAddress: String,
    val isHotspotLikely: Boolean
)

object LocalNetworkUtils {

    /**
     * Scans all up non-loopback network interfaces to find Wi-Fi and Android Mobile Hotspot IPv4 addresses.
     */
    fun getLocalIpv4Addresses(): List<LocalInterfaceAddress> {
        val result = mutableListOf<LocalInterfaceAddress>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                val name = ni.name.lowercase()
                // Skip cellular interfaces (rmnet/ccmni) so we prioritize Wi-Fi & Hotspot interfaces
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun")) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val hotspot = name.contains("ap") || name.contains("swlan") || ip.startsWith("192.168.43.") || ip.startsWith("192.168.137.")
                        result.add(LocalInterfaceAddress(ni.name, ip, hotspot))
                    }
                }
            }
        }
        return result.sortedByDescending { it.interfaceName.startsWith("wlan") || it.isHotspotLikely }
    }

    fun getBroadcastAddresses(): List<InetAddress> {
        val broadcasts = linkedSetOf<InetAddress>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (ni in interfaces) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) {
                        ia.broadcast?.let { broadcasts.add(it) }
                    }
                }
            }
        }
        runCatching { broadcasts.add(InetAddress.getByName("255.255.255.255")) }
        return broadcasts.toList()
    }

    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? {
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.createMulticastLock("MatchdayLanDiscoveryLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }
}
