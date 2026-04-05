package com.example.gabay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.example.gabay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: androidx.navigation.NavController  // ADD THIS
    private val LOCATION_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestLocationPermission()
        setupNavigation()

        // Handle navigation from FullScreenAlertActivity
        if (intent.getBooleanExtra("navigate_to_evacuation", false)) {
            val isWalking = intent.getBooleanExtra("travel_mode_walking", true)
            val bundle = Bundle().apply {
                putBoolean("auto_travel_mode_walking", isWalking)
                putBoolean("skip_safety_check", true)
            }
            navController.navigate(R.id.evacuationFragment, bundle)
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController  // ASSIGN TO CLASS PROPERTY

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

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                } else {
                    // Permission denied - you can show a message to the user
                }
            }
        }
    }
}