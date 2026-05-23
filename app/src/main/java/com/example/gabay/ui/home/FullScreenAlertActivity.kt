package com.example.gabay.ui.home

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.gabay.MainActivity
import com.example.gabay.databinding.ActivityFullScreenAlertBinding

class FullScreenAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenAlertBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibration: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val disasterType = intent.getStringExtra("disaster_type") ?: "EARTHQUAKE"
        val magnitude = intent.getDoubleExtra("magnitude", 0.0)
        val location = intent.getStringExtra("location") ?: "Unknown location"
        val distance = intent.getDoubleExtra("distance", 0.0)
        val reportedTime = intent.getStringExtra("reportedTime") ?: ""
        val actionText = intent.getStringExtra("actionText") ?: "ALERT"
        val windSpeed = intent.getIntExtra("wind_speed", 0)
        val signalNo = intent.getStringExtra("signal_no") ?: ""

        binding.tvAlertTitle.text = actionText

        if (disasterType == "TYPHOON") {
            binding.tvMagnitude.text = "$location — $signalNo"
            binding.tvLocation.text = "Max winds: $windSpeed km/h"
        } else {
            binding.tvMagnitude.text = "Magnitude ${String.format("%.1f", magnitude)}"
            binding.tvLocation.text = location
        }

        binding.tvDistance.text = "~${String.format("%.1f", distance)} km from you"
        binding.tvReportedTime.text = reportedTime

        startAlarm()

        binding.btnWalking.setOnClickListener { launchEvacuation(true) }
        binding.btnVehicle.setOnClickListener { launchEvacuation(false) }
        binding.btnDismiss.setOnClickListener {
            stopAlarm()
            finish()
        }
    }

    private fun startAlarm() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("FullScreenAlert", "Alarm error: ${e.message}")
        }

        // Vibrates in SOS pattern: ... --- ...
        try {
            vibration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(
                0,
                200, 100, 200, 100, 200,   // ... (short short short)
                300,
                500, 100, 500, 100, 500,   // --- (long long long)
                300,
                200, 100, 200, 100, 200,   // ... (short short short)
                1000
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibration?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibration?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.e("FullScreenAlert", "Vibrate error: ${e.message}")
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibration?.cancel()
        vibration = null
    }

    private fun launchEvacuation(isWalking: Boolean) {
        stopAlarm()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to_evacuation", true)
            putExtra("travel_mode_walking", isWalking)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent accidental dismissal
    }
}