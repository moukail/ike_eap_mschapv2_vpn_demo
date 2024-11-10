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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("vpninfo")
    }

    private external fun getIpAddress(interfaceName: String): String

    private external fun getVpnLocalIp(): String
    external fun stringFromJNI(): String
    external fun getNetworkInfo(): String

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
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
            addAction(VpnStatusReceiver.ACTION_VPN_CONNECTED)
            addAction(VpnStatusReceiver.ACTION_VPN_DISCONNECTED)
        }
        registerReceiver(vpnStatusReceiver, filter, RECEIVER_EXPORTED)

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

    private fun getPublicIpAddress(): JSONObject? {

        val statusObject = JSONObject();
        statusObject.put("query", "")
        statusObject.put("isp", "")

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://myvpn.moukafih.nl/api/v1/status")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return statusObject.put("status", "Failed to fetch public IP")

                response.body?.string()?.let {
                    val jsonObject = JSONObject(it)
                    return jsonObject
                }
            }
            return statusObject.put("status", "Public IP not available")

        } catch (e: Exception) {
            // Log the error (optional) and return a fallback message
            e.printStackTrace()
            return statusObject.put("status", "Error retrieving public IP")
        }
    }

    @Composable
    fun VpnConnectButton(isConnected: Boolean) {

        val networkInfo = remember { mutableStateOf("") }
        val mobileNetworkInfo = remember { mutableStateOf("") }
        val vpnLocalIp = remember { mutableStateOf("") }
        val publicIp = remember { mutableStateOf("Fetching...") }
        val internetProvider = remember { mutableStateOf("Fetching...") }
        val proxy = remember { mutableStateOf("Fetching...") }
        val continent = remember { mutableStateOf("Fetching...") }
        val country = remember { mutableStateOf("Fetching...") }
        val city = remember { mutableStateOf("Fetching...") }

        val scope = rememberCoroutineScope()

        LaunchedEffect(isConnected) {
                Log.d(TAG, "LaunchedEffect")

                vpnLocalIp.value = getIpAddress("ipsec")
                networkInfo.value = getIpAddress("wlan0")
                mobileNetworkInfo.value = getIpAddress("rmnet")

                scope.launch(Dispatchers.IO) {
                    publicIp.value = getPublicIpAddress()?.getString("query") ?: ""
                    internetProvider.value = getPublicIpAddress()?.getString("isp") ?: ""
                    proxy.value = getPublicIpAddress()?.getString("proxy") ?: ""
                    continent.value = getPublicIpAddress()?.getString("continent") ?: ""
                    country.value = getPublicIpAddress()?.getString("country") ?: ""
                    city.value = getPublicIpAddress()?.getString("city") ?: ""
                }
        }

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

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Public IP: ${publicIp.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Internet Provider: ${internetProvider.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Continent: ${continent.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Country: ${country.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "City: ${city.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Local VPN IP: ${vpnLocalIp.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Mobile IP: ${mobileNetworkInfo.value}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Wifi local IP: ${networkInfo.value}")
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    companion object {
        private val TAG : String = MainActivity::class.java.simpleName
    }
}