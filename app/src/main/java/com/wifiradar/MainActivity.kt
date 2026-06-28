package com.wifiradar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment
    
    private val activeMarkers = mutableMapOf<String, AnchorNode>()
    private val markerViews = mutableMapOf<String, View>()
    private var verticalOffset = -0.5f // Початкова висота (нижче рівня очей)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startRadar()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA), 101)
        }
    }

    private fun startRadar() {
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val results = wifiManager.scanResults
                
                if (results.isEmpty()) {
                    Toast.makeText(context, "0 мереж: Увімкніть GPS (Локацію) у шторці телефону", Toast.LENGTH_LONG).show()
                    return
                }

                Toast.makeText(context, "Обробка ${results.size} мереж...", Toast.LENGTH_SHORT).show()

                // Фільтрація: беремо 5 найсильніших сигналів
                val topNetworks = results.sortedByDescending { it.level }.take(5)
                
                topNetworks.forEach { result ->
                    updateMarker(result.SSID, result.BSSID, result.level)
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        wifiManager.startScan()
    }

    private fun updateMarker(ssid: String, bssid: String, level: Int) {
        val frame = arFragment.arSceneView.arFrame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        if (activeMarkers[bssid] == null) {
            val cameraPose = frame.camera.pose
            
            // Математика вертикального стеку: x=0 (центр), z=-1.5 (глибина), y=змінна висота
            val offset = floatArrayOf(0f, verticalOffset, -1.5f)
            verticalOffset += 0.35f 
            if (verticalOffset > 1.0f) verticalOffset = -0.5f // Зациклення висоти
            
            val transformed = FloatArray(3)
            cameraPose.rotateVector(offset, 0, transformed, 0)
            
            val anchorPose = Pose.makeTranslation(
                cameraPose.translation[0] + transformed[0], 
                cameraPose.translation[1] + transformed[1], 
                cameraPose.translation[2] + transformed[2]
            )
            
            val anchor = arFragment.arSceneView.session?.createAnchor(anchorPose) ?: return
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            val customView = View.inflate(this@MainActivity, R.layout.ar_marker, null)
            markerViews[bssid] = customView
            
            val displaySsid = if (ssid.isEmpty()) "[Прихована]" else ssid
            customView.findViewById<TextView>(R.id.tvSsid)?.text = displaySsid
            customView.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "$level dBm"

            ViewRenderable.builder()
                .setView(this@MainActivity, customView)
                .build()
                .thenAccept { renderable ->
                    val labelNode = Node()
                    labelNode.setParent(anchorNode)
                    labelNode.renderable = renderable
                }

            activeMarkers[bssid] = anchorNode
        } else {
            markerViews[bssid]?.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "$level dBm"
        }
    }
}