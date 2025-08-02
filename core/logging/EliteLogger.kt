package com.astralx.browser.core.logging

import android.util.Log
import com.astralx.browser.BuildConfig
import com.astralx.browser.core.analytics.AnalyticsEngine
import com.astralx.browser.core.performance.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Elite Logging System with smart filtering and structured analytics
 * Zero performance impact in production with intelligent context-aware logging
 */
@Singleton
class EliteLogger @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val performanceMonitor: PerformanceMonitor,
    private val loggingScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    
    private val logCount = AtomicLong(0)
    private val startTime = System.currentTimeMillis()
    private val contextualFilters = mutableMapOf<String, LogFilter>()
    
    fun plant() {
        if (BuildConfig.DEBUG || isInternalTester()) {
            Timber.plant(DebugEliteTree())
        } else {
            Timber.plant(ProductionEliteTree())
        }
        
        initializeContextualFilters()
    }
    
    private fun initializeContextualFilters() {
        contextualFilters.apply {
            put("WebView", LogFilter(priority = Log.WARN, enabled = true))
            put("Network", LogFilter(priority = Log.INFO, enabled = true))
            put("Performance", LogFilter(priority = Log.DEBUG, enabled = BuildConfig.DEBUG))
            put("Privacy", LogFilter(priority = Log.VERBOSE, enabled = true))
            put("Download", LogFilter(priority = Log.INFO, enabled = true))
            put("Audio", LogFilter(priority = Log.DEBUG, enabled = true))
        }
    }
    
    private fun isInternalTester(): Boolean {
        return BuildConfig.APPLICATION_ID.contains("debug") || 
               BuildConfig.BUILD_TYPE == "internal"
    }
    
    private fun shouldLog(priority: Int, tag: String?, context: LogContext): Boolean {
        val filter = tag?.let { contextualFilters[extractCategory(it)] }
        
        if (filter != null && !filter.enabled) return false
        if (filter != null && priority < filter.priority) return false
        
        // Smart throttling based on current system load
        if (performanceMonitor.isHighLoad() && priority < Log.WARN) return false
        
        // Context-aware filtering
        return when (context.operation) {
            LogContext.Operation.STARTUP -> priority >= Log.INFO
            LogContext.Operation.USER_INTERACTION -> priority >= Log.DEBUG
            LogContext.Operation.BACKGROUND -> priority >= Log.WARN
            LogContext.Operation.CRITICAL -> true
        }
    }
    
    private fun extractCategory(tag: String): String {
        return when {
            tag.contains("WebView", ignoreCase = true) -> "WebView"
            tag.contains("Network", ignoreCase = true) -> "Network"
            tag.contains("Performance", ignoreCase = true) -> "Performance"
            tag.contains("Privacy", ignoreCase = true) -> "Privacy"
            tag.contains("Download", ignoreCase = true) -> "Download"
            tag.contains("Audio", ignoreCase = true) -> "Audio"
            else -> "General"
        }
    }
    
    private fun getCurrentContext(): LogContext {
        return LogContext(
            operation = detectCurrentOperation(),
            threadName = Thread.currentThread().name,
            memoryPressure = performanceMonitor.getMemoryPressure(),
            cpuLoad = performanceMonitor.getCpuUsage()
        )
    }
    
    private fun detectCurrentOperation(): LogContext.Operation {
        val uptime = System.currentTimeMillis() - startTime
        return when {
            uptime < 5000 -> LogContext.Operation.STARTUP
            Thread.currentThread().name.contains("main", ignoreCase = true) -> 
                LogContext.Operation.USER_INTERACTION
            Thread.currentThread().name.contains("background", ignoreCase = true) -> 
                LogContext.Operation.BACKGROUND
            else -> LogContext.Operation.USER_INTERACTION
        }
    }
    
    private fun getContextualData(): Map<String, Any> {
        return mapOf(
            "timestamp" -> System.currentTimeMillis(),
            "thread" -> Thread.currentThread().name,
            "memory_mb" -> performanceMonitor.getMemoryUsageMB(),
            "cpu_percent" -> performanceMonitor.getCpuUsage(),
            "log_count" -> logCount.get(),
            "uptime_ms" -> System.currentTimeMillis() - startTime
        )
    }
    
    inner class DebugEliteTree : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val context = getCurrentContext()
            
            if (shouldLog(priority, tag, context)) {
                super.log(priority, tag, message, t)
                
                // Performance impact tracking
                performanceMonitor.trackLogImpact()
                logCount.incrementAndGet()
                
                // Structured logging for analytics (async)
                if (priority >= Log.WARN) {
                    loggingScope.launch {
                        analyticsEngine.logStructured(
                            StructuredLog(
                                priority = priority,
                                tag = tag,
                                message = message,
                                throwable = t,
                                context = getContextualData()
                            )
                        )
                    }
                }
            }
        }
        
        override fun createStackElementTag(element: StackTraceElement): String {
            return "${super.createStackElementTag(element)}:${element.lineNumber}"
        }
    }
    
    inner class ProductionEliteTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Production logging - only errors and critical warnings
            if (priority >= Log.ERROR || (priority == Log.WARN && isCriticalWarning(message))) {
                loggingScope.launch {
                    analyticsEngine.logStructured(
                        StructuredLog(
                            priority = priority,
                            tag = tag,
                            message = sanitizeMessage(message),
                            throwable = t,
                            context = getContextualData()
                        )
                    )
                }
            }
        }
        
        private fun isCriticalWarning(message: String): Boolean {
            val criticalKeywords = listOf(
                "memory", "crash", "security", "privacy", 
                "permission", "network", "timeout"
            )
            return criticalKeywords.any { 
                message.contains(it, ignoreCase = true) 
            }
        }
        
        private fun sanitizeMessage(message: String): String {
            // Remove sensitive data patterns
            val sensitivePatterns = listOf(
                Regex("password=\\w+", RegexOption.IGNORE_CASE),
                Regex("token=\\w+", RegexOption.IGNORE_CASE),
                Regex("key=\\w+", RegexOption.IGNORE_CASE),
                Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), // Credit cards
                Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b") // Emails
            )
            
            var sanitized = message
            sensitivePatterns.forEach { pattern ->
                sanitized = pattern.replace(sanitized, "[REDACTED]")
            }
            return sanitized
        }
    }
    
    data class LogContext(
        val operation: Operation,
        val threadName: String,
        val memoryPressure: Float,
        val cpuLoad: Double
    ) {
        enum class Operation {
            STARTUP, USER_INTERACTION, BACKGROUND, CRITICAL
        }
    }
    
    data class LogFilter(
        val priority: Int,
        val enabled: Boolean
    )
    
    data class StructuredLog(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
        val context: Map<String, Any>
    )
}