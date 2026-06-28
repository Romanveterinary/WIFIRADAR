package com.wifiradar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // БЕЗВІДМОВНИЙ ТЕСТ: Створення м'ячика по кліку на розпізнану площину
        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            // Створюємо якір саме там, куди ви тапнули
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            // Малюємо червону кулю
            MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
                .thenAccept { material ->
                    val sphereNode = Node()
                    sphereNode.setParent(anchorNode)
                    sphereNode.renderable = ShapeFactory.makeSphere(0.1f, Vector3.zero(), material)
                    
                    Toast.makeText(this, "М'ячик успішно встановлено!", Toast.LENGTH_SHORT).show()
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
                    // Тост для контролю, що Wi-Fi працює
                    Toast.makeText(context, "Wi-Fi: Знайдено ${results.size} мереж", Toast.LENGTH_SHORT).show()
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        wifiManager.startScan()
    }
}