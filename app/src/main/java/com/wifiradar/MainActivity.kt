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
import com.google.ar.core.Pose
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
    private val activeMarkers = mutableMapOf<String, AnchorNode>()

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
                
                // ДІАГНОСТИКА №1: Чи взагалі спрацьовує Wi-Fi?
                Toast.makeText(context, "Wi-Fi сканування успішне! Знайдено мереж: ${results.size}", Toast.LENGTH_LONG).show()

                results.forEach { result ->
                    // Малюємо тестову кулю тільки для найсильніших мереж (щоб не захаращувати екран)
                    if (result.level > -80) {
                        drawRedSphere(result.BSSID)
                    }
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        val success = wifiManager.startScan()
        if (!success) {
            Toast.makeText(this, "Помилка: Android заблокував Wi-Fi сканування (Throttling)", Toast.LENGTH_LONG).show()
        }
    }

    // ДІАГНОСТИКА №2: Малюємо найпростішу базову фігуру ARCore
    private fun drawRedSphere(bssid: String) {
        val frame = arFragment.arSceneView?.arFrame ?: return
        if (frame.camera.trackingState != com.google.ar.core.TrackingState.TRACKING) return

        if (activeMarkers[bssid] == null) {
            val cameraPose = frame.camera.pose
            val forward = floatArrayOf(0f, 0f, -1.0f) // 1 метр вперед
            val transformed = FloatArray(3)
            cameraPose.rotateVector(forward, 0, transformed, 0)
            
            val anchorPose = Pose.makeTranslation(
                cameraPose.translation[0] + transformed[0], 
                cameraPose.translation[1] + transformed[1], 
                cameraPose.translation[2] + transformed[2]
            )
            
            val anchor = arFragment.arSceneView?.session?.createAnchor(anchorPose)
            anchor?.let {
                val anchorNode = AnchorNode(it)
                anchorNode.setParent(arFragment.arSceneView?.scene)

                // Малюємо суцільну червону кулю радіусом 10 см
                MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
                    .thenAccept { material ->
                        val sphereNode = Node()
                        sphereNode.setParent(anchorNode)
                        sphereNode.renderable = ShapeFactory.makeSphere(0.1f, Vector3.zero(), material)
                    }

                activeMarkers[bssid] = anchorNode
            }
        }
    }
}