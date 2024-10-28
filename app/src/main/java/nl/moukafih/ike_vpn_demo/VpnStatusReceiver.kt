package nl.moukafih.ike_vpn_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

class VpnStatusReceiver(private val onVpnStatusChanged: (Boolean) -> Unit) : BroadcastReceiver() {

    companion object {
        private val TAG : String = VpnStatusReceiver::class.java.simpleName
        const val ACTION_VPN_CONNECTED = "nl.moukafih.ike_vpn_demo.ACTION_VPN_CONNECTED"
        const val ACTION_VPN_DISCONNECTED = "nl.moukafih.ike_vpn_demo.ACTION_VPN_DISCONNECTED"
    }

    private lateinit var connectivityManager: ConnectivityManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val isConnected = connectivityManager.activeNetwork?.let { isVpnNetwork(it) } == true
                onVpnStatusChanged(isConnected)
            }
            ACTION_VPN_CONNECTED -> {
                onVpnStatusChanged(true)
            }
            ACTION_VPN_DISCONNECTED -> {
                onVpnStatusChanged(false)
            }
            else -> {
                Log.w("VpnStatusReceiver", "Unknown action received")
            }
        }
    }

    private fun isVpnNetwork(network: Network): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}