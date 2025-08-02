package com.astralx.browser.core.privacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BiometricAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BiometricAuthManager"
        private const val PREFS_NAME = "biometric_auth_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTH_TIMEOUT = "auth_timeout"
        private const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastAuthTime = 0L
    
    /**
     * Check if biometric authentication is available on the device
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "No biometric hardware available")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "Biometric hardware unavailable")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "No biometric credentials enrolled")
                false
            }
            else -> false
        }
    }
    
    /**
     * Enable biometric authentication
     */
    fun enableBiometricAuth() {
        preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
        Log.d(TAG, "Biometric authentication enabled")
    }
    
    /**
     * Disable biometric authentication
     */
    fun disableBiometricAuth() {
        preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply()
        lastAuthTime = 0L
        Log.d(TAG, "Biometric authentication disabled")
    }
    
    /**
     * Check if biometric authentication is enabled
     */
    fun isBiometricEnabled(): Boolean {
        return preferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
    
    /**
     * Check if authentication is required (timeout expired)
     */
    fun isAuthRequired(): Boolean {
        if (!isBiometricEnabled()) return false
        
        val timeout = preferences.getLong(KEY_AUTH_TIMEOUT, DEFAULT_TIMEOUT_MS)
        val currentTime = System.currentTimeMillis()
        
        return (currentTime - lastAuthTime) > timeout
    }
    
    /**
     * Authenticate user with biometric
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock AstralX",
        subtitle: String = "Use your fingerprint or face to access adult content",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailure: () -> Unit
    ) {
        if (!isBiometricAvailable()) {
            onError("Biometric authentication not available")
            return
        }
        
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, 
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    lastAuthTime = System.currentTimeMillis()
                    Log.d(TAG, "Biometric authentication succeeded")
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Biometric authentication error: $errString")
                    onError(errString.toString())
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed")
                    onFailure()
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()
            
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * Authenticate for adult content access
     */
    fun authenticateForAdultContent(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        authenticate(
            activity = activity,
            title = "Adult Content Access",
            subtitle = "Verify your identity to access adult content",
            onSuccess = onSuccess,
            onError = onError,
            onFailure = onFailure
        )
    }
    
    /**
     * Set authentication timeout
     */
    fun setAuthTimeout(timeoutMs: Long) {
        preferences.edit().putLong(KEY_AUTH_TIMEOUT, timeoutMs).apply()
        Log.d(TAG, "Auth timeout set to ${timeoutMs}ms")
    }
    
    /**
     * Get current authentication timeout
     */
    fun getAuthTimeout(): Long {
        return preferences.getLong(KEY_AUTH_TIMEOUT, DEFAULT_TIMEOUT_MS)
    }
    
    /**
     * Clear authentication state (force re-auth)
     */
    fun clearAuthState() {
        lastAuthTime = 0L
        Log.d(TAG, "Authentication state cleared")
    }
}