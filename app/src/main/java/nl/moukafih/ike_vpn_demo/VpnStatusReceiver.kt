package nl.moukafih.ike_vpn_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class VpnStatusReceiver(private val onVpnStatusChanged: (Boolean) -> Unit) : BroadcastReceiver() {

    companion object {
        private val TAG : String = VpnStatusReceiver::class.java.simpleName
        const val ACTION_VPN_CONNECTED = "nl.moukafih.ike_vpn_demo.ACTION_VPN_CONNECTED"
        const val ACTION_VPN_DISCONNECTED = "nl.moukafih.ike_vpn_demo.ACTION_VPN_DISCONNECTED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received action: ${intent.action}")

        when (intent.action) {
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
}