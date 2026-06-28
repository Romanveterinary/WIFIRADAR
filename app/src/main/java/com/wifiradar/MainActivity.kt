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
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // ТЕСТ 2: Малюємо нашу табличку з трикутником по кліку
        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            // Завантажуємо ваш XML дизайн
            val customView = View.inflate(this, R.layout.ar_marker, null)
            customView.findViewById<TextView>(R.id.tvSsid)?.text = "Тестова Мережа"
            customView.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "-45 dBm"

            ViewRenderable.builder()
                .setView(this, customView)
                .build()
                .thenAccept { renderable ->
                    val labelNode = Node()
                    labelNode.setParent(anchorNode)
                    labelNode.renderable = renderable
                    
                    Toast.makeText(this, "Табличку встановлено!", Toast.LENGTH_SHORT).show()
                }
                .exceptionally {
                    Toast.makeText(this, "Помилка рендеру XML", Toast.LENGTH_LONG).show()
                    null
                }
        }

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
                if (results.isNotEmpty()) {
                    Toast.makeText(context, "Wi-Fi працює: ${results.size} мереж", Toast.LENGTH_SHORT).show()
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        wifiManager.startScan()
    }
}