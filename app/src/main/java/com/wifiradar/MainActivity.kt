package com.wifiradar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var tvArTimer: TextView
    private var countDownTimer: CountDownTimer? = null
    private lateinit var arFragment: ArFragment

    // Приймач Wi-Fi працює у фоні, збираючи дані для майбутніх трикутників
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                // Тут ми пізніше додамо логіку промальовування 3D фігур
                val results = wifiManager.scanResults 
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        tvArTimer = findViewById(R.id.tvArTimer)

        // Ініціалізуємо AR-камеру
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment

        // Одразу при запуску перевіряємо дозволи
        checkPermissionsAndStart()
    }

    // Тепер нам потрібні два дозволи: Геолокація (для Wi-Fi) та Камера (для AR)
    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        
        val needsPermission = permissions.any { 
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED 
        }

        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, 101)
        } else {
            startRadar()
        }
    }

    // Запуск таймера та сканування
    private fun startRadar() {
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        wifiManager.startScan()

        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                // Форматуємо час як 00:XX
                val formattedTime = String.format("00:%02d", secondsLeft)
                tvArTimer.text = "TIME REMAINING: $formattedTime"
                
                if (secondsLeft % 5 == 0L) {
                    wifiManager.startScan()
                }
            }

            override fun onFinish() {
                tvArTimer.text = "SCAN COMPLETE"
                stopRadar()
            }
        }.start()
    }

    private fun stopRadar() {
        countDownTimer?.cancel()
        try {
            unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {}
    }

    // Обробка дозволів
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Перевіряємо, чи користувач дав УСІ запитані дозволи
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startRadar()
        } else {
            Toast.makeText(this, "Потрібні дозволи на камеру та геолокацію!", Toast.LENGTH_LONG).show()
            tvArTimer.text = "NO PERMISSION"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRadar()
    }
}