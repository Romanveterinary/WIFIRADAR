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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment
    
    // Словник для зберігання посилань на 3D об'єкти та їхні View
    private val activeMarkers = mutableMapOf<String, Node>()
    private val markerViews = mutableMapOf<String, View>()

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
                wifiManager.scanResults.forEach { result ->
                    updateMarker(result.SSID, result.BSSID, result.level)
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        wifiManager.startScan()
    }

    private fun updateMarker(ssid: String, bssid: String, level: Int) {
        val marker = activeMarkers[bssid]
        
        if (marker == null) {
            // Створюємо нову View з XML
            val customView = View.inflate(this, R.layout.ar_marker, null)
            markerViews[bssid] = customView

            // Будуємо 3D-рендер
            ViewRenderable.builder()
                .setView(this, customView)
                .build()
                .thenAccept { renderable ->
                    val newNode = Node()
                    newNode.setParent(arFragment.arSceneView.scene)
                    // Ставимо прямо перед камерою (1 метр вперед)
                    newNode.localPosition = Vector3(0f, 0f, -1f)
                    newNode.localScale = Vector3(0.1f, 0.1f, 0.1f)
                    newNode.renderable = renderable
                    activeMarkers[bssid] = newNode
                }
        }

        // Оновлюємо текст у View
        markerViews[bssid]?.let { view ->
            view.findViewById<TextView>(R.id.tvSsid)?.text = ssid
            view.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "$level dBm"
        }
    }
}