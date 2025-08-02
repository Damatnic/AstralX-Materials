package com.astralx.browser.presentation.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.commit
import com.astralx.browser.R
import com.astralx.browser.core.privacy.PrivacyManager
import com.astralx.browser.presentation.browser.ModernBrowserFragment
import com.astralx.browser.presentation.downloads.DownloadActivity
import com.astralx.browser.presentation.performance.PerformanceActivity
import com.astralx.browser.presentation.performance.PerformanceIntegrationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var performanceIntegrationManager: PerformanceIntegrationManager
    
    
    private lateinit var privacyManager: PrivacyManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_main)
        
        // Launch modern browser fragment
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, ModernBrowserFragment.newInstance())
            }
        }
        
        initializeComponents()
        setupPrivacy()
        setupPerformanceMonitoring()
    }
    
    private fun initializeComponents() {
        privacyManager = PrivacyManager(this)
    }
    
    private fun setupPrivacy() {
        // Initialize secure browsing mode
        privacyManager.initializeSecureBrowsingMode(this, this)
        
        // Check if biometric auth is required for adult content
        if (privacyManager.isAuthRequired()) {
            privacyManager.authenticateForAdultContent(
                activity = this,
                onSuccess = {
                    // Continue with normal app flow
                },
                onError = { error ->
                    // Handle authentication error
                    finish()
                }
            )
        }
    }
    

    
    private fun setupPerformanceMonitoring() {
        // Integrate performance monitoring into the main activity
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        performanceIntegrationManager.integrateIntoActivity(
            activity = this,
            lifecycleOwner = this,
            rootViewGroup = rootView,
            showOverlay = false // Initially hidden, can be toggled via menu
        )
    }
    

    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_downloads -> {
                openDownloadsActivity()
                true
            }
            R.id.action_settings -> {
                openSettingsActivity()
                true
            }
            R.id.action_performance -> {
                openPerformanceActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openDownloadsActivity() {
        val intent = Intent(this, DownloadActivity::class.java)
        startActivity(intent)
    }
    
    private fun openSettingsActivity() {
        // This would open the settings activity if it exists
        // For now, just show a toast or do nothing
    }
    
    private fun openPerformanceActivity() {
        val intent = Intent(this, PerformanceActivity::class.java)
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up performance monitoring
        performanceIntegrationManager.cleanup()
    }
}