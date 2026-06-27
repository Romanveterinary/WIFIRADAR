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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var btnStartScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvWifiResults: TextView
    private var countDownTimer: CountDownTimer? = null
    private var isScanning = false

    // Приймач, який спрацьовує, коли Android завершує сканування Wi-Fi
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                displayWifiResults()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ініціалізуємо сервіс Wi-Fi
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Зв'язуємо змінні з елементами дизайну XML
        btnStartScan = findViewById(R.id.btnStartScan)
        tvStatus = findViewById(R.id.tvStatus)
        tvWifiResults = findViewById(R.id.tvWifiResults)

        btnStartScan.setOnClickListener {
            if (isScanning) {
                stopRadar()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    // Перевірка дозволів у реальному часі (вимога сучасного Android)
    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, 
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 
                101
            )
        } else {
            startRadar()
        }
    }

    // Запуск 1-хвилинної роботи радара
    private fun startRadar() {
        isScanning = true
        btnStartScan.text = "Зупинити радар"
        tvWifiResults.text = "Сканування ефіру..."
        
        // Реєструємо приймач результатів сканування
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        // Запускаємо перше сканування
        wifiManager.startScan()

        // Створюємо таймер: 60000 мс (1 хвилина), крок оновлення 1000 мс (1 секунда)
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                tvStatus.text = "Радар працює. Залишилось: $secondsLeft сек."
                
                // Оскільки сканування разове, кожні 5 секунд даємо команду шукати знову
                if (secondsLeft % 5 == 0L) {
                    wifiManager.startScan()
                }
            }

            override fun onFinish() {
                tvStatus.text = "Час вийшов (1 хвилина). Радар вимкнено."
                stopRadar()
            }
        }.start()
    }

    // Зупинка радара та очищення процесів
    private fun stopRadar() {
        isScanning = false
        btnStartScan.text = "Запустити радар (1 хв)"
        countDownTimer?.cancel()
        try {
            unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {
            // Приймач міг бути вже відключений
        }
    }

    // Обробка та виведення списку мереж на екран
    private fun displayWifiResults() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            
            val results = wifiManager.scanResults
            val sb = StringBuilder()
            sb.append("Знайдені точки доступу (${results.size}):\n\n")
            
            for (result in results) {
                val ssid = if (result.SSID.isEmpty()) "[Прихована мережа]" else result.SSID
                sb.append("SSID: $ssid\n")
                  .append("BSSID: ${result.BSSID}\n")
                  .append("Потужність сигналу: ${result.level} dBm\n")
                  .append("-------------------------\n")
            }
            tvWifiResults.text = sb.toString()
        }
    }

    // Обробка відповіді користувача на запит дозволу геолокації
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRadar()
        } else {
            Toast.makeText(this, "Потрібен дозвіл на геолокацію для сканування Wi-Fi!", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Помилка: немає дозволів."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRadar()
    }
}