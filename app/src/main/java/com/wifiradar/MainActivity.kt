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
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment
    
    // Зберігаємо окремо і модель, і саму View для оновлення тексту
    private var markerRenderable: ViewRenderable? = null
    private val markerViews = mutableMapOf<String, View>()
    private val activeMarkers = mutableMapOf<String, Node>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        ViewRenderable.builder()
            .setView(this, R.layout.ar_marker)
            .build()
            .thenAccept { renderable -> 
                markerRenderable = renderable 
                checkPermissionsAndStart()
            }
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
        val renderable = markerRenderable ?: return
        
        // Якщо маркера ще немає — створюємо
        if (activeMarkers[bssid] == null) {
            // Створюємо нову View для цього конкретного маркера
            val customView = View.inflate(this, R.layout.ar_marker, null)
            markerViews[bssid] = customView

            val newNode = Node()
            newNode.setParent(arFragment.arSceneView.scene)
            newNode.localPosition = Vector3(Random.nextFloat() * 2 - 1, 0f, -2f)
            newNode.localScale = Vector3(0.1f, 0.1f, 0.1f)
            
            // Встановлюємо наш View у рендер-об'єкт
            ViewRenderable.builder().setView(this, customView).build().thenAccept { newRenderable ->
                newNode.renderable = newRenderable
            }
            
            activeMarkers[bssid] = newNode
        }

        // Оновлюємо текст у нашому збереженому View
        markerViews[bssid]?.let { view ->
            view.findViewById<TextView>(R.id.tvSsid)?.text = ssid
            view.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "$level dBm"
        }
    }
}