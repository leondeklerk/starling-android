package com.leondeklerk.starling

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.leondeklerk.starling.databinding.ActivityMainBinding

/**
 * Main activity used to handle the navigation component.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup general navigation
        val navFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
        val navController = navFragment!!.findNavController()
        NavigationUI.setupWithNavController(binding.navView, navController)

//        navController.addOnDestinationChangedListener { _, destination, _ ->
//            when (destination.id) {
//                R.id.navigation_test -> {
//                    binding.navView.animate().alpha(0f).setDuration(50L).withEndAction {
//                        binding.navView.visibility = View.GONE
//                    }
//                }
//                else -> {
//                    binding.navView.animate().alpha(1f)
//                    binding.navView.visibility = View.VISIBLE
//                }
//            }
//        }
    }

    var onReenter: ((resultCode: Int, data: Intent?) -> Unit)? = null

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        onReenter?.invoke(resultCode, data)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
