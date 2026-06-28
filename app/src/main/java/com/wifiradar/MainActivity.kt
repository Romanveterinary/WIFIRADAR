package com.wifiradar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import kotlin.math.abs
import kotlin.random.Random

data class WifiSignalRecord(
    var currentNode: AnchorNode? = null,
    var labelView: ViewRenderable? = null,
    var lastRssi: Int = -100
)

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var arFragment: ArFragment
    private lateinit var wifiListContainer: LinearLayout
    
    private val wifiRecords = mutableMapOf<String, WifiSignalRecord>()
    // Черга для мереж, які чекають, поки ARCore завантажиться
    private val pendingNetworks = mutableListOf<ScanResult>() 
    
    private val colorPalette = arrayOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
        Color.CYAN, Color.MAGENTA, Color.parseColor("#FF9800"), Color.parseColor("#9C27B0")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiListContainer = findViewById(R.id.wifiListContainer)

        // Слухаємо оновлення кадрів ARCore
        arFragment.arSceneView.scene.addOnUpdateListener {
            val frame = arFragment.arSceneView.arFrame ?: return@addOnUpdateListener
            
            // Якщо камера готова і є мережі в черзі — малюємо їх!
            if (frame.camera.trackingState == TrackingState.TRACKING && pendingNetworks.isNotEmpty()) {
                val networksToRender = pendingNetworks.toList()
                pendingNetworks.clear() // Очищаємо чергу
                
                networksToRender.forEach { result ->
                    if (result.level > -85) {
                        updateSignalInAR(result.SSID, result.BSSID, result.level)
                    }
                }
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
                
                wifiListContainer.removeAllViews()

                if (results.isEmpty()) {
                    val tvEmpty = TextView(context).apply {
                        text = "Список мереж порожній. Перевірте статус GPS/Локації."
                        setTextColor(Color.RED)
                        textSize = 14f
                    }
                    wifiListContainer.addView(tvEmpty)
                    return
                }

                // Оновлюємо UI список і додаємо мережі в чергу для AR
                pendingNetworks.clear()
                pendingNetworks.addAll(results)

                results.forEach { result ->
                    val tvNetwork = TextView(context).apply {
                        val displaySsid = if (result.SSID.isEmpty()) "[Прихована]" else result.SSID
                        text = "$displaySsid (${result.BSSID}) | ${result.level} dBm"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setPadding(0, 4, 0, 4)
                    }
                    wifiListContainer.addView(tvNetwork)
                }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        wifiManager.startScan()
    }

    private fun getColorForMac(mac: String): Int {
        val index = abs(mac.hashCode()) % colorPalette.size
        return colorPalette[index]
    }

    private fun updateSignalInAR(ssid: String, bssid: String, level: Int) {
        val record = wifiRecords[bssid] ?: WifiSignalRecord().also { wifiRecords[bssid] = it }
        record.lastRssi = level

        val frame = arFragment.arSceneView.arFrame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val displaySsid = if (ssid.isEmpty()) "[Прихована]" else ssid

        if (record.currentNode == null) {
            val baseSize = when {
                level > -50 -> 0.15f
                level > -70 -> 0.08f
                else -> 0.04f
            }

            val androidColorInt = getColorForMac(bssid)
            val r = Color.red(androidColorInt) / 255f
            val g = Color.green(androidColorInt) / 255f
            val b = Color.blue(androidColorInt) / 255f
            val arColor = com.google.ar.sceneform.rendering.Color(r, g, b)

            MaterialFactory.makeOpaqueWithColor(this, arColor).thenAccept { material ->
                ViewRenderable.builder()
                    .setView(this@MainActivity, R.layout.ar_label)
                    .build()
                    .thenAccept { viewRenderable ->
                        record.labelView = viewRenderable
                        val textView = viewRenderable.view.findViewById<TextView>(R.id.tv_ar_label)
                        textView.text = "$displaySsid\n$level dBm"

                        val shapeNode = Node()
                        shapeNode.renderable = ShapeFactory.makeSphere(baseSize, Vector3.zero(), material)

                        val cameraPose = frame.camera.pose
                        val posArray = cameraPose.translation
                        
                        // ДОДАНО: Випадковий розкид координат (від -1.5 до 1.5 метра по ширині, від 0.5 до 2.5 метрів у глибину)
                        val randomX = Random.nextFloat() * 3f - 1.5f 
                        val randomZ = -(Random.nextFloat() * 2f + 0.5f) 
                        val randomY = Random.nextFloat() * 1f - 0.5f // Трохи вгору/вниз

                        val forward = floatArrayOf(randomX, randomY, randomZ)
                        val transformed = FloatArray(3)
                        cameraPose.rotateVector(forward, 0, transformed, 0)

                        val anchorPose = Pose.makeTranslation(
                            posArray[0] + transformed[0],
                            posArray[1] + transformed[1],
                            posArray[2] + transformed[2]
                        )

                        val anchor = arFragment.arSceneView.session?.createAnchor(anchorPose)
                        anchor?.let {
                            val anchorNode = AnchorNode(it)
                            anchorNode.setParent(arFragment.arSceneView.scene)

                            shapeNode.setParent(anchorNode)

                            val labelNode = Node()
                            labelNode.setParent(anchorNode)
                            labelNode.renderable = viewRenderable
                            labelNode.localScale = Vector3(0.6f, 0.6f, 0.6f)
                            labelNode.localPosition = Vector3(0f, baseSize + 0.10f, 0f)

                            record.currentNode = anchorNode
                        }
                    }
            }
        } else {
            record.labelView?.view?.findViewById<TextView>(R.id.tv_ar_label)?.text = "$displaySsid\n$level dBm"
        }
    }
}