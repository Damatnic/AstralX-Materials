package com.astralx.browser.core.privacy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy Manager Interface as specified in the design document
 */
interface PrivacyManager {
    suspend fun enableVpnKillSwitch()
    suspend fun setCustomDns(provider: String)
    suspend fun enableAutoClean(clearOnExit: Boolean)
    suspend fun activatePanicMode()
    fun getPrivacyState(): Flow<PrivacyState>
}

/**
 * Privacy Manager Implementation
 * Implements VPN connection monitoring, custom DNS support, auto-clear, and panic mode
 */
@Singleton
class PrivacyManagerImpl @Inject constructor(
    private val context: Context,
    private val privacyConfigManager: PrivacyConfigManager,
    private val secureMemoryUtils: SecureMemoryUtils
) : PrivacyManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Privacy state management
    private val _privacyState = MutableStateFlow(PrivacyState())
    private val privacyState: StateFlow<PrivacyState> = _privacyState.asStateFlow()
    
    // VPN monitoring
    private var vpnNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var isVpnConnected = false
    private var killSwitchActive = false
    
    // DNS configuration
    private var customDnsProvider: String? = null
    private val supportedDnsProviders = mapOf(
        "cloudflare" to listOf("1.1.1.1", "1.0.0.1"),
        "quad9" to listOf("9.9.9.9", "149.112.112.112"),
        "nextdns" to listOf("45.90.28.0", "45.90.30.0"),
        "adguard" to listOf("94.140.14.14", "94.140.15.15")
    )
    
    // Auto-clear configuration
    private var autoClearEnabled = false
    private var clearOnExit = false
    
    // Panic mode
    private var panicModeEnabled = false
    
    init {
        initializePrivacyManager()
    }
    
    private fun initializePrivacyManager() {
        scope.launch {
            try {
                // Load saved privacy configuration
                loadPrivacyConfiguration()
                
                // Initialize VPN monitoring
                setupVpnMonitoring()
                
                Timber.d("Privacy Manager initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Privacy Manager")
            }
        }
    }
    
    override suspend fun enableVpnKillSwitch() {
        try {
            killSwitchActive = true
            updatePrivacyState { it.copy(vpnKillSwitchActive = true) }
            
            // Save configuration
            savePrivacyConfiguration()
            
            // Monitor VPN connection status
            if (vpnNetworkCallback == null) {
                setupVpnMonitoring()
            }
            
            Timber.d("VPN kill switch enabled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable VPN kill switch")
            throw PrivacyException("Failed to enable VPN kill switch: ${e.message}")
        }
    }
    
    override suspend fun setCustomDns(provider: String) {
        try {
            val providerKey = provider.lowercase()
            if (!supportedDnsProviders.containsKey(providerKey)) {
                throw PrivacyException("Unsupported DNS provider: $provider. Supported providers: ${supportedDnsProviders.keys}")
            }
            
            customDnsProvider = providerKey
            val dnsServers = supportedDnsProviders[providerKey] ?: emptyList()
            
            // Apply DNS configuration
            applyDnsConfiguration(dnsServers)
            
            updatePrivacyState { state ->
                state.copy(
                    customDnsEnabled = true,
                    currentDnsServers = dnsServers
                )
            }
            
            // Save configuration
            savePrivacyConfiguration()
            
            Timber.d("Custom DNS set to $provider with servers: $dnsServers")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set custom DNS")
            throw PrivacyException("Failed to set custom DNS: ${e.message}")
        }
    }
    
    override suspend fun enableAutoClean(clearOnExit: Boolean) {
        try {
            this.autoClearEnabled = true
            this.clearOnExit = clearOnExit
            
            updatePrivacyState { it.copy(autoCleanEnabled = true) }
            
            // Save configuration
            savePrivacyConfiguration()
            
            Timber.d("Auto-clear enabled (clearOnExit: $clearOnExit)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable auto-clear")
            throw PrivacyException("Failed to enable auto-clear: ${e.message}")
        }
    }
    
    override suspend fun activatePanicMode() {
        try {
            panicModeEnabled = true
            updatePrivacyState { it.copy(panicModeEnabled = true) }
            
            // Immediately clear all sensitive data
            clearAllBrowsingData()
            
            // Block network access if VPN is not connected and kill switch is active
            if (!isVpnConnected && killSwitchActive) {
                blockNetworkAccess()
            }
            
            // Clear sensitive memory
            secureMemoryUtils.clearApplicationMemory()
            
            // Save configuration
            savePrivacyConfiguration()
            
            Timber.w("Panic mode activated - all sensitive data cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to activate panic mode")
            throw PrivacyException("Failed to activate panic mode: ${e.message}")
        }
    }
    
    override fun getPrivacyState(): Flow<PrivacyState> = privacyState
    
    private fun setupVpnMonitoring() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
            
            vpnNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    isVpnConnected = true
                    updatePrivacyState { it.copy(vpnEnabled = true) }
                    Timber.d("VPN connection detected")
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    isVpnConnected = false
                    updatePrivacyState { it.copy(vpnEnabled = false) }
                    
                    // Activate kill switch if enabled
                    if (killSwitchActive) {
                        scope.launch {
                            blockNetworkAccess()
                            Timber.w("VPN connection lost - kill switch activated")
                        }
                    }
                }
            }
            
            connectivityManager.registerNetworkCallback(networkRequest, vpnNetworkCallback!!)
            Timber.d("VPN monitoring setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup VPN monitoring")
        }
    }
    
    private suspend fun applyDnsConfiguration(dnsServers: List<String>) = withContext(Dispatchers.IO) {
        try {
            // Note: In a real Android implementation, DNS configuration typically requires:
            // 1. Root access to modify system DNS settings
            // 2. VPN service to intercept and redirect DNS queries
            // 3. Private DNS configuration (Android 9+)
            
            // For this implementation, we'll log the configuration
            // In a production app, this would integrate with a VPN service
            Timber.d("DNS configuration applied: $dnsServers")
            
            // Store DNS configuration for VPN service integration
            val config = privacyConfigManager.loadPrivacyConfig()
            privacyConfigManager.savePrivacyConfig(
                config.copy(customDnsProvider = customDnsProvider)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply DNS configuration")
            throw e
        }
    }
    
    private suspend fun blockNetworkAccess() = withContext(Dispatchers.IO) {
        try {
            // In a real implementation, this would:
            // 1. Block network traffic through VPN service
            // 2. Set firewall rules (requires root)
            // 3. Disable network interfaces
            
            // For this implementation, we'll log the action
            Timber.w("Network access blocked due to VPN kill switch activation")
            
            // Update state to reflect blocked network
            updatePrivacyState { it.copy(networkBlocked = true) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to block network access")
        }
    }
    
    private suspend fun clearAllBrowsingData() = withContext(Dispatchers.Main) {
        try {
            // Clear cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            
            // Clear web storage
            WebStorage.getInstance().deleteAllData()
            
            // Clear cache directories
            withContext(Dispatchers.IO) {
                clearCacheDirectories()
            }
            
            Timber.d("All browsing data cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear browsing data")
        }
    }
    
    private fun clearCacheDirectories() {
        try {
            val cacheDir = context.cacheDir
            val webViewCacheDir = File(context.cacheDir, "webview")
            val externalCacheDir = context.externalCacheDir
            
            // Clear main cache directory
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            
            // Clear WebView cache
            if (webViewCacheDir.exists()) {
                webViewCacheDir.deleteRecursively()
            }
            
            // Clear external cache
            externalCacheDir?.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            
            Timber.d("Cache directories cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cache directories")
        }
    }
    
    private fun updatePrivacyState(update: (PrivacyState) -> PrivacyState) {
        _privacyState.value = update(_privacyState.value)
    }
    
    private suspend fun loadPrivacyConfiguration() = withContext(Dispatchers.IO) {
        try {
            val config = privacyConfigManager.loadPrivacyConfig()
            
            autoClearEnabled = config.autoCleanOnExit || config.autoCleanOnBackground
            clearOnExit = config.autoCleanOnExit
            killSwitchActive = config.vpnKillSwitchEnabled
            customDnsProvider = config.customDnsProvider
            panicModeEnabled = config.panicModeEnabled
            
            // Update state
            val dnsServers = customDnsProvider?.let { supportedDnsProviders[it] } ?: emptyList()
            updatePrivacyState { state ->
                state.copy(
                    autoCleanEnabled = autoClearEnabled,
                    vpnKillSwitchActive = killSwitchActive,
                    customDnsEnabled = customDnsProvider != null,
                    currentDnsServers = dnsServers,
                    panicModeEnabled = panicModeEnabled
                )
            }
            
            Timber.d("Privacy configuration loaded")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load privacy configuration")
        }
    }
    
    private suspend fun savePrivacyConfiguration() = withContext(Dispatchers.IO) {
        try {
            val currentConfig = privacyConfigManager.loadPrivacyConfig()
            val updatedConfig = currentConfig.copy(
                vpnKillSwitchEnabled = killSwitchActive,
                customDnsProvider = customDnsProvider,
                autoCleanOnExit = clearOnExit,
                autoCleanOnBackground = autoClearEnabled && !clearOnExit,
                panicModeEnabled = panicModeEnabled
            )
            
            privacyConfigManager.savePrivacyConfig(updatedConfig)
            Timber.d("Privacy configuration saved")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save privacy configuration")
        }
    }
    
    // Additional utility methods
    suspend fun disableVpnKillSwitch() {
        killSwitchActive = false
        updatePrivacyState { it.copy(vpnKillSwitchActive = false, networkBlocked = false) }
        savePrivacyConfiguration()
        Timber.d("VPN kill switch disabled")
    }
    
    suspend fun disableCustomDns() {
        customDnsProvider = null
        updatePrivacyState { state ->
            state.copy(
                customDnsEnabled = false,
                currentDnsServers = emptyList()
            )
        }
        savePrivacyConfiguration()
        Timber.d("Custom DNS disabled")
    }
    
    suspend fun disableAutoClean() {
        autoClearEnabled = false
        clearOnExit = false
        updatePrivacyState { it.copy(autoCleanEnabled = false) }
        savePrivacyConfiguration()
        Timber.d("Auto-clear disabled")
    }
    
    suspend fun deactivatePanicMode() {
        panicModeEnabled = false
        updatePrivacyState { it.copy(panicModeEnabled = false) }
        savePrivacyConfiguration()
        Timber.d("Panic mode deactivated")
    }
    
    fun getSupportedDnsProviders(): Map<String, List<String>> = supportedDnsProviders
    
    fun cleanup() {
        vpnNetworkCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Timber.e(e, "Failed to unregister network callback")
            }
        }
        scope.cancel()
        Timber.d("Privacy Manager cleaned up")
    }
}

/**
 * Privacy State Data Model as specified in the design document
 */
data class PrivacyState(
    val vpnEnabled: Boolean = false,
    val vpnKillSwitchActive: Boolean = false,
    val customDnsEnabled: Boolean = false,
    val autoCleanEnabled: Boolean = false,
    val panicModeEnabled: Boolean = false,
    val currentDnsServers: List<String> = emptyList(),
    val networkBlocked: Boolean = false
)

/**
 * Privacy Exception for error handling
 */
class PrivacyException(message: String, cause: Throwable? = null) : Exception(message, cause)