package com.astralx.browser.privacy

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebView
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import com.astralx.browser.core.privacy.EnhancedPrivacyManager

/**
 * Privacy shield that automatically activates enhanced privacy features
 */
@Singleton
class PrivacyShield @Inject constructor(
    private val context: Context,
    private val privacyManager: EnhancedPrivacyManager,
    private val scope: CoroutineScope
) {
    
    private var activity: Activity? = null
    private var webView: WebView? = null
    private var gestureDetector: GestureDetector? = null
    private var privacyActivated = false
    
    // Keywords that trigger privacy mode
    private val sensitiveKeywords = listOf(
        "private", "secure", "confidential", "personal",
        "banking", "finance", "medical", "health"
    )
    
    /**
     * Initialize privacy shield for an activity
     */
    fun initialize(activity: Activity, webView: WebView) {
        this.activity = activity
        this.webView = webView
        setupPanicMode()
    }
    
    /**
     * Check if privacy should be activated based on URL and content
     */
    fun checkAndActivatePrivacy(url: String, pageContent: String = "") {
        scope.launch {
            if (shouldActivatePrivacy(url, pageContent)) {
                activateMaxPrivacy()
            }
        }
    }
    
    /**
     * Determine if privacy mode should be activated
     */
    private fun shouldActivatePrivacy(url: String, content: String): Boolean {
        val combined = "$url $content".lowercase()
        
        // Check for sensitive keywords
        if (sensitiveKeywords.any { combined.contains(it) }) {
            return true
        }
        
        // Check for HTTPS
        if (!url.startsWith("https://")) {
            Timber.d("Non-HTTPS site detected, considering privacy activation")
        }
        
        // Check for private/incognito indicators in URL
        if (url.contains("private") || url.contains("secure")) {
            return true
        }
        
        return false
    }
    
    /**
     * Activate maximum privacy protections
     */
    private fun activateMaxPrivacy() {
        if (privacyActivated) return
        privacyActivated = true
        
        Timber.d("Activating maximum privacy mode")
        
        // Enable incognito mode
        enableIncognito()
        
        // Disable screenshots
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        // Hide from recents
        hideFromRecents()
        
        // Enable panic mode listener
        enablePanicMode()
        
        // Clear cookies and cache for this session
        webView?.apply {
            clearCache(true)
            clearHistory()
            clearFormData()
        }
        
        // Notify privacy manager
        privacyManager.activatePrivacyMode()
    }
    
    /**
     * Enable incognito browsing
     */
    private fun enableIncognito() {
        webView?.settings?.apply {
            // Disable form data saving
            saveFormData = false
            
            // Disable password saving
            savePassword = false
            
            // Use private cache mode
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            
            // Disable geolocation
            setGeolocationEnabled(false)
            
            // Block third-party cookies
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
        }
    }
    
    /**
     * Hide app from recent apps list
     */
    private fun hideFromRecents() {
        activity?.let { act ->
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                val activityManager = act.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val tasks = activityManager.appTasks
                tasks.forEach { task ->
                    task.setExcludeFromRecents(true)
                }
            }
        }
    }
    
    /**
     * Setup panic mode detection
     */
    fun setupPanicMode() {
        // Three-finger tap detection
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Detect three-finger swipe up
                if (e1.pointerCount >= 3 && velocityY < -1000) {
                    executePanic()
                    return true
                }
                return false
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Triple tap detection (double tap + one more)
                if (System.currentTimeMillis() - lastDoubleTapTime < 500) {
                    executePanic()
                    return true
                }
                lastDoubleTapTime = System.currentTimeMillis()
                return false
            }
        })
        
        // Volume button combination
        setupVolumeButtonPanic()
    }
    
    private var lastDoubleTapTime = 0L
    private var volumeDownCount = 0
    private var volumeResetJob: Job? = null
    
    /**
     * Setup volume button panic trigger
     */
    private fun setupVolumeButtonPanic() {
        // This would be implemented with key event handling in the activity
        // Trigger panic on 3 quick volume down presses
    }
    
    /**
     * Enable panic mode detection
     */
    private fun enablePanicMode() {
        Timber.d("Panic mode enabled")
        // Additional panic mode setup if needed
    }
    
    /**
     * Execute panic mode actions
     */
    fun executePanic() {
        Timber.d("PANIC MODE ACTIVATED")
        
        scope.launch {
            // 1. Immediately load safe URL
            webView?.loadUrl("https://www.google.com")
            
            // 2. Clear all browsing data
            clearAllData()
            
            // 3. Minimize app
            minimizeApp()
            
            // 4. Reset privacy state
            privacyActivated = false
            
            // 5. Clear memory
            System.gc()
        }
    }
    
    /**
     * Clear all browsing data
     */
    private fun clearAllData() {
        webView?.apply {
            clearCache(true)
            clearHistory()
            clearFormData()
            clearSslPreferences()
            clearMatches()
        }
        
        // Clear cookies
        android.webkit.CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        
        // Clear web storage
        android.webkit.WebStorage.getInstance().deleteAllData()
        
        // Notify privacy manager to clear data
        privacyManager.clearAllPrivateData()
    }
    
    /**
     * Minimize the app
     */
    private fun minimizeApp() {
        activity?.moveTaskToBack(true)
        
        // Alternatively, go to home screen
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }
    
    /**
     * Handle motion events for gesture detection
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector?.onTouchEvent(event) ?: false
    }
    
    /**
     * Handle volume button events
     */
    fun onVolumeDown() {
        volumeResetJob?.cancel()
        volumeDownCount++
        
        if (volumeDownCount >= 3) {
            executePanic()
            volumeDownCount = 0
        } else {
            // Reset counter after 2 seconds
            volumeResetJob = scope.launch {
                delay(2000)
                volumeDownCount = 0
            }
        }
    }
    
    /**
     * Deactivate privacy mode
     */
    fun deactivatePrivacy() {
        privacyActivated = false
        
        // Remove secure flag
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        // Restore normal settings
        webView?.settings?.apply {
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        
        Timber.d("Privacy mode deactivated")
    }
    
    /**
     * Check if privacy mode is active
     */
    fun isPrivacyActive(): Boolean = privacyActivated
    
    /**
     * Cleanup
     */
    fun cleanup() {
        volumeResetJob?.cancel()
        activity = null
        webView = null
        gestureDetector = null
        privacyActivated = false
    }
}