package nl.moukafih.ike_vpn_demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.Ikev2VpnProfile
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnManager
import android.net.VpnService
import android.net.eap.EapSessionConfig
import android.net.ipsec.ike.ChildSaProposal
import android.net.ipsec.ike.IkeFqdnIdentification
import android.net.ipsec.ike.IkeSaProposal
import android.net.ipsec.ike.IkeSessionParams
import android.net.ipsec.ike.IkeTunnelConnectionParams
import android.net.ipsec.ike.SaProposal
import android.net.ipsec.ike.TunnelModeChildSessionParams
import android.os.Build
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyVpnService : VpnService() {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var vpnManager: VpnManager

    // Network callback to detect VPN disconnection
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                Log.d(TAG, "VPN Connected")
            }
        }

        override fun onLost(network: Network) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                Log.d(TAG, "VPN Disconnected")
                stopVpn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Start the VPN connection when the service is started
        vpnManager = applicationContext.getSystemService(VPN_MANAGEMENT_SERVICE) as VpnManager

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        val serviceChannel = NotificationChannel(
            "vpn_channel",
            "VPN Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "DISCONNECT") {
            stopVpn()
        } else {
            sendBroadcast(Intent(VpnStatusReceiver.ACTION_VPN_CONNECTED))
            establishVpn()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
        // Unregister the network callback when the service is destroyed
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun sendVpnStatusBroadcast(isConnected: Boolean) {
        val action = if (isConnected) {
            VpnStatusReceiver.ACTION_VPN_CONNECTED
        } else {
            VpnStatusReceiver.ACTION_VPN_DISCONNECTED
        }
        sendBroadcast(Intent(action))
    }

    private fun stopVpn() {
        sendVpnStatusBroadcast(false)
        vpnManager.stopProvisionedVpnProfile()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN connection stopped.")
    }

    private fun updateNotification(status: String) {
        Log.i(TAG, "createNotification")

        val disconnectIntent = Intent(this, MyVpnService::class.java).apply {
            action = "DISCONNECT"
        }

        val disconnectPendingIntent: PendingIntent = PendingIntent.getService(this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification =  NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("VPN Status")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vpn)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Keeps the notification persistent
            .addAction(R.drawable.ic_disconnect, "Disconnect", disconnectPendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun establishVpn() {

        val vpn_server = getString(R.string.vpn_server)
        val username = getString(R.string.username)
        val password = getString(R.string.password)

        updateNotification("Connecting...")
        // Launch a coroutine for network operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Define an SA Proposal with encryption, integrity, and DH group
                val saProposal = IkeSaProposal.Builder()
                    .addEncryptionAlgorithm(
                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC,
                        256
                    ) // Specify key length (256 bits)
                    .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
                    .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256)
                    .addDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP)
                    .build()

                val childSaProposal = ChildSaProposal.Builder()
                    .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, 256)
                    .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
                    .addDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP)
                    .build()

                val remoteIdentification = IkeFqdnIdentification(vpn_server)
                val localIdentification = IkeFqdnIdentification(username)

                // Create EAP configuration (customize according to your requirements)
                val eapConfig = EapSessionConfig.Builder()
                    .setEapMsChapV2Config(username, password)
                    //.setEapIdentity(username.toByteArray())
                    .build()

                val ikeSessionParams = IkeSessionParams.Builder()
                    .setServerHostname(vpn_server)
                    .addIkeSaProposal(saProposal)  // Security association proposal
                    .setLocalIdentification(localIdentification)
                    .setRemoteIdentification(remoteIdentification)
                    .setAuthEap(null, eapConfig) // Use EAP for authentication with CA certificate
                    .setDpdDelaySeconds(30)
                    .setNattKeepAliveDelaySeconds(20)
                    .build()

                val childSessionParams = TunnelModeChildSessionParams.Builder()
                    .addChildSaProposal(childSaProposal)
                    .addInternalAddressRequest(OsConstants.AF_INET)
                    .build()

                val tunnelConnectionParams = IkeTunnelConnectionParams(ikeSessionParams, childSessionParams)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val ikev2VpnProfile = Ikev2VpnProfile.Builder(tunnelConnectionParams)
                    .build()
                vpnManager.provisionVpnProfile(ikev2VpnProfile)
                vpnManager.startProvisionedVpnProfileSession()
            }

                Log.i("VPN", "VPN established")
                updateNotification("Connected")
                sendVpnStatusBroadcast(true)
            } catch (e: Exception) {
                Log.e("VPN Error", "Failed to establish VPN: ${e.message}")
                // Handle additional error processing here
            }
        }
    }

    companion object {
        private val TAG : String = MyVpnService::class.java.simpleName
    }
}

