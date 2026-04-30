package com.example.gabay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.example.gabay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: androidx.navigation.NavController
    private val PERMISSION_REQUEST_CODE = 100
    private var evacuationIntentHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestAllPermissions()
        setupNavigation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEvacuationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!evacuationIntentHandled) {
            handleEvacuationIntent(intent)
        }

        // Check full screen intent permission on Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notifManager.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle("Enable Alert Overlay")
                    .setMessage("Gabay needs permission to show emergency alerts over other apps and on the lock screen. This is critical for earthquake warnings.")
                    .setPositiveButton("Allow") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    private fun handleEvacuationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("navigate_to_evacuation", false) == true) {
            evacuationIntentHandled = true
            val isWalking = intent.getBooleanExtra("travel_mode_walking", true)
            val bundle = Bundle().apply {
                putBoolean("auto_travel_mode_walking", isWalking)
                putBoolean("skip_safety_check", true)
            }
            // Small delay to ensure navController is ready
            binding.root.post {
                navController.navigate(R.id.evacuationFragment, bundle)
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.menu.findItem(destination.id)?.isChecked = true
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navController.navigate(
                item.itemId,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, false)
                    .setLaunchSingleTop(true)
                    .build()
            )
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener { }
    }

    private fun requestAllPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Permissions handled silently — app degrades gracefully if denied
    }
}