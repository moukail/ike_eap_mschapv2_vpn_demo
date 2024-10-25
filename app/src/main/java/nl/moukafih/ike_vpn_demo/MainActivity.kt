package nl.moukafih.ike_vpn_demo

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var vpnManager: VpnManager
    private val isConnected = MutableLiveData(false)
    private lateinit var vpnStatusReceiver: VpnStatusReceiver

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startService(Intent(this, MyVpnService::class.java))
        } else {
            // Handle the case where permission is denied
            Log.e("VPN", "User denied VPN permission")
        }
    }

    private val notificationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true -> Log.i(TAG, "Notification permissions are granted")
            else -> Toast.makeText(this@MainActivity, "Notification permissions are required.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vpnManager = applicationContext.getSystemService(VPN_MANAGEMENT_SERVICE) as VpnManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissions.launch(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        }

        // Receiver
        vpnStatusReceiver = VpnStatusReceiver { connected -> isConnected.value = connected }

        val filter = IntentFilter().apply {
            addAction("android.net.VpnService")
            addAction(VpnStatusReceiver.ACTION_VPN_CONNECTED)
            addAction(VpnStatusReceiver.ACTION_VPN_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStatusReceiver, filter, RECEIVER_EXPORTED)
        }

        // UI
        enableEdgeToEdge()
        // Observe the isConnected value and update UI
        isConnected.observe(this) { connected ->
            setContent {
                VpnConnectButton(isConnected = connected)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(vpnStatusReceiver)
    }

    private fun startVpnService() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startService(Intent(this, MyVpnService::class.java))
        }
    }

    private fun disconnectVpn() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                vpnManager.stopProvisionedVpnProfile()  // Disconnect the VPN
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "VPN Disconnected", Toast.LENGTH_SHORT).show()
                }

                val disconnectIntent = Intent(applicationContext, MyVpnService::class.java)
                disconnectIntent.action = "DISCONNECT"
                startService(disconnectIntent)

            } catch (e: Exception) {
                Log.e("VPN", "Failed to disconnect: ${e.message}")
            }
        }
    }

    @Composable
    fun VpnConnectButton(isConnected: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            //Text(text = isConnected, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (!isConnected) {
                    startVpnService()
                } else {
                    disconnectVpn()
                }
            }) {
                Text(text = if (isConnected) "Disconnect" else "Connect to VPN")
            }
        }
    }

    companion object {
        private val TAG : String = MainActivity::class.java.simpleName
    }
}