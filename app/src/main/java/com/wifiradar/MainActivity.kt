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
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment
    
    // Зберігаємо Якорі, щоб вони не губилися
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
                wifiManager.scanResults.forEach { result ->
                    updateMarker(result.SSID, result.BSSID, result.level)
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        wifiManager.startScan()
    }

    private fun updateMarker(ssid: String, bssid: String, level: Int) {
        val frame = arFragment.arSceneView?.arFrame ?: return
        
        // Чекаємо, поки камера зрозуміє простір
        if (frame.camera.trackingState != com.google.ar.core.TrackingState.TRACKING) return

        val marker = activeMarkers[bssid]
        
        if (marker == null) {
            // МАТЕМАТИКА З BLUETOOTH ПРОЕКТУ: Рахуємо точку на 1 метр попереду
            val posArray = frame.camera.pose.translation
            val cameraPose = frame.camera.pose
            val forward = floatArrayOf(0f, 0f, -1.0f) // 1 метр від камери
            val transformed = FloatArray(3)
            cameraPose.rotateVector(forward, 0, transformed, 0)
            
            val anchorPose = Pose.makeTranslation(
                posArray[0] + transformed[0], 
                posArray[1] + transformed[1], 
                posArray[2] + transformed[2]
            )
            
            // Створюємо залізний Якір
            val anchor = arFragment.arSceneView?.session?.createAnchor(anchorPose)
            anchor?.let {
                val anchorNode = AnchorNode(it)
                anchorNode.setParent(arFragment.arSceneView?.scene)

                val customView = View.inflate(this, R.layout.ar_marker, null)
                customView.findViewById<TextView>(R.id.tvSsid)?.text = ssid
                customView.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "$level dBm"

                ViewRenderable.builder()
                    .setView(this, customView)
                    .build()
                    .thenAccept { renderable ->
                        val labelNode = Node()
                        labelNode.setParent(anchorNode)
                        labelNode.renderable = renderable
                        // Ваш ar_marker.xml вже має правильний розмір, тому додатковий scale не робимо
                    }

                activeMarkers[bssid] = anchorNode
            }
        } else {
            // Оновлюємо існуючий маркер
            val node = marker.children.firstOrNull() as? Node
            val viewRenderable = node?.renderable as? ViewRenderable
            viewRenderable?.view?.findViewById<TextView>(R.id.tvMacAndSignal)?.text = "$level dBm"
        }
    }
}