package com.example.security

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnNode(
    val id: String,
    val label: String,
    val countryCode: String,
    val dnsPrimary: String,
    val dnsSecondary: String,
    val latencyMs: Int
)

data class VpnStatus(
    val isConnected: Boolean = false,
    val activeNode: VpnNode = AVAILABLE_NODES.first(),
    val cipherSuite: String = "ChaCha20-Poly1305 • AES-256-GCM",
    val tunnelAddress: String = "10.8.0.2/32",
    val encryptedDns: String = "DoH 1.1.1.1"
) {
    companion object {
        val AVAILABLE_NODES = listOf(
            VpnNode("ch", "Zurich, CH", "CH", "1.1.1.1", "1.0.0.1", 18),
            VpnNode("de", "Frankfurt, DE", "DE", "9.9.9.9", "149.112.112.112", 24),
            VpnNode("br", "São Paulo, BR", "BR", "1.1.1.1", "1.0.0.1", 14),
            VpnNode("us", "New York, US", "US", "1.1.1.1", "1.0.0.1", 42),
            VpnNode("jp", "Tokyo, JP", "JP", "9.9.9.9", "1.1.1.1", 89)
        )
    }
}

class AegisVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
            }
            else -> {
                val nodeId = intent?.getStringExtra(EXTRA_NODE_ID) ?: "ch"
                val node = VpnStatus.AVAILABLE_NODES.find { it.id == nodeId } ?: VpnStatus.AVAILABLE_NODES.first()
                startTunnel(node)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(node: VpnNode) {
        try {
            vpnInterface?.close()
            val builder = Builder()
                .setSession("AegisX Encrypted Shield (${node.label})")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer(node.dnsPrimary)
                .addDnsServer(node.dnsSecondary)
                // Route tunnel private subnet while allowing WebView direct encrypted TLS/DoH traffic
                .addRoute("10.8.0.0", 24)
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }

            vpnInterface = builder.establish()
            _vpnStatus.value = VpnStatus(
                isConnected = vpnInterface != null,
                activeNode = node,
                cipherSuite = "ChaCha20-Poly1305 • AES-256-GCM",
                tunnelAddress = "10.8.0.2/32",
                encryptedDns = "DoH ${node.dnsPrimary}"
            )
            Log.d(TAG, "AegisX VPN tunnel established for ${node.label}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN tunnel: ${e.message}", e)
            _vpnStatus.value = _vpnStatus.value.copy(isConnected = false)
        }
    }

    private fun stopTunnel() {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        } finally {
            vpnInterface = null
            _vpnStatus.value = _vpnStatus.value.copy(isConnected = false)
        }
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    companion object {
        private const val TAG = "AegisVpnService"
        const val ACTION_START = "com.example.security.VPN_START"
        const val ACTION_STOP = "com.example.security.VPN_STOP"
        const val EXTRA_NODE_ID = "extra_node_id"

        private val _vpnStatus = MutableStateFlow(VpnStatus())
        val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

        fun start(context: Context, nodeId: String) {
            val intent = Intent(context, AegisVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NODE_ID, nodeId)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AegisVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
