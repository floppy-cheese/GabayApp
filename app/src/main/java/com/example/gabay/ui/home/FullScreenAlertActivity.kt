package com.example.gabay.ui.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.gabay.MainActivity
import com.example.gabay.databinding.ActivityFullScreenAlertBinding

class FullScreenAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenAlertBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make it show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Override silent/DND
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Populate alert data from intent
        val magnitude = intent.getDoubleExtra("magnitude", 0.0)
        val location = intent.getStringExtra("location") ?: "Unknown location"
        val distance = intent.getDoubleExtra("distance", 0.0)
        val reportedTime = intent.getStringExtra("reportedTime") ?: ""
        val actionText = intent.getStringExtra("actionText") ?: "EARTHQUAKE ALERT"

        binding.tvAlertTitle.text = actionText
        binding.tvMagnitude.text = "Magnitude ${String.format("%.1f", magnitude)}"
        binding.tvLocation.text = location
        binding.tvDistance.text = "~${String.format("%.1f", distance)} km from you"
        binding.tvReportedTime.text = reportedTime

        binding.btnWalking.setOnClickListener {
            launchEvacuation(isWalking = true)
        }

        binding.btnVehicle.setOnClickListener {
            launchEvacuation(isWalking = false)
        }

        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun launchEvacuation(isWalking: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_evacuation", true)
            putExtra("travel_mode_walking", isWalking)
        }
        startActivity(intent)
        finish()
    }
}