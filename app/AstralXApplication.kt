package com.astralx.browser

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.work.Configuration
import androidx.work.WorkManager
import com.astralx.browser.core.logging.EliteLogger
import com.astralx.browser.monitoring.PerformanceMonitor
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltAndroidApp
class AstralXApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var eliteLogger: EliteLogger

    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging first - critical for debugging
        initializeLogging()
        
        // Initialize crash reporting
        initializeCrashReporting()
        
        // Initialize other components
        initializeComponents()
        
        // Setup StrictMode in debug builds
        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }
    }
    
    private fun initializeCrashReporting() {
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            
            // Configure Crashlytics
            val crashlytics = FirebaseCrashlytics.getInstance()
            
            // Enable/disable based on build type - disable in debug for better development experience
            crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            
            // Set custom keys for better crash analysis
            crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            crashlytics.setCustomKey("version_name", BuildConfig.VERSION_NAME)
            crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE.toString())
            crashlytics.setCustomKey("debug_mode", BuildConfig.DEBUG)
            
            // Set user identifier (anonymous)
            crashlytics.setUserId(getAnonymousUserId())
            
            // Log initialization success
            if (BuildConfig.DEBUG) {
                Timber.d("Crashlytics initialized successfully (disabled in debug)")
            } else {
                Timber.i("Crashlytics initialized successfully (enabled in production)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Crashlytics - continuing without crash reporting")
            // Don't crash the app if Crashlytics fails to initialize
        }
    }
    
    private fun initializeLogging() {
        // Use Elite Logger with smart filtering and structured analytics
        eliteLogger.plant()
        Timber.i("Elite logging system initialized with smart filtering and analytics")
    }

    private fun initializeComponents() {
        try {
            // Initialize Performance Monitoring
            try {
                PerformanceMonitor.getInstance(this).apply {
                    initialize()
                    trackAppStartup()
                }
                Timber.d("Performance monitoring initialized")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize performance monitoring - continuing without it")
            }
            
            // Initialize WorkManager with custom configuration
            try {
                WorkManager.initialize(this, workManagerConfiguration)
                Timber.d("WorkManager initialized")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize WorkManager")
            }
            
            // Initialize other SDK components
            initializeAnalytics()
            initializeNetworkSecurity()
            
            Timber.d("AstralX Application core components initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing application components")
            // Record exception if Crashlytics is available
            try {
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (crashlyticsError: Exception) {
                Timber.e(crashlyticsError, "Failed to record exception to Crashlytics")
            }
        }
    }
    
    private fun initializeAnalytics() {
        // Analytics initialization would go here
        // Example: Firebase Analytics, custom analytics, etc.
    }
    
    private fun initializeNetworkSecurity() {
        // Network security initialization
        // Example: Certificate pinning, custom trust managers, etc.
    }
    
    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }
    
    private fun getAnonymousUserId(): String {
        // Generate or retrieve anonymous user ID
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var userId = prefs.getString("anonymous_user_id", null)
        
        if (userId == null) {
            userId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("anonymous_user_id", userId).apply()
        }
        
        return userId
    }
    
    // WorkManager configuration
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .setExecutor(Executors.newFixedThreadPool(2))
            .build()
    
    /**
     * Custom Timber tree that logs to Crashlytics in production
     * Only logs warnings, errors, and exceptions to avoid spam
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Only log warnings and above in production
            if (priority < android.util.Log.WARN) {
                return
            }
            
            try {
                val crashlytics = FirebaseCrashlytics.getInstance()
                
                // Log the message with priority level
                val priorityString = when (priority) {
                    android.util.Log.WARN -> "WARN"
                    android.util.Log.ERROR -> "ERROR"
                    android.util.Log.ASSERT -> "ASSERT"
                    else -> "LOG"
                }
                crashlytics.log("[$priorityString] $tag: $message")
                
                // Record exception if present
                if (t != null) {
                    crashlytics.recordException(t)
                }
            } catch (e: Exception) {
                // Fallback to system logging if Crashlytics fails
                android.util.Log.e("CrashlyticsTree", "Failed to log to Crashlytics", e)
                android.util.Log.println(priority, tag ?: "AstralX", message)
                t?.let { android.util.Log.e(tag ?: "AstralX", message, it) }
            }
        }
    }
    
    companion object {
        private const val TAG = "AstralXApplication"
    }
} 