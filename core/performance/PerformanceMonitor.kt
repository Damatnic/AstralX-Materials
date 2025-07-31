package com.astralx.browser.core.performance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface for performance monitoring system
 * Provides real-time CPU usage, memory tracking, and performance metrics
 */
interface PerformanceMonitor {
    
    /**
     * Real-time performance metrics as a SharedFlow for APEX implementation
     */
    val performanceMetrics: SharedFlow<PerformanceMetrics>
    
    /**
     * Start performance monitoring
     */
    fun startMonitoring()
    
    /**
     * Stop performance monitoring
     */
    fun stopMonitoring()
    
    /**
     * Get real-time performance metrics as a Flow (backward compatibility)
     */
    fun getPerformanceMetrics(): Flow<PerformanceMetrics>
    
    /**
     * Record generation time for specific operations
     * @param identifier Unique identifier for the operation
     * @param timeMs Time taken in milliseconds
     */
    fun recordGenerationTime(identifier: String, timeMs: Long)
    
    /**
     * Get current performance metrics snapshot
     */
    suspend fun getCurrentMetrics(): PerformanceMetrics
    
    /**
     * Check if monitoring is currently active
     */
    fun isMonitoring(): Boolean
    
    /**
     * Track logging performance impact
     */
    fun trackLogImpact()
    
    /**
     * Get current memory pressure (0.0 to 1.0)
     */
    fun getMemoryPressure(): Float
    
    /**
     * Get current CPU usage percentage
     */
    fun getCpuUsage(): Double
    
    /**
     * Get current memory usage in MB
     */
    fun getMemoryUsageMB(): Double
    
    /**
     * Check if system is under high load
     */
    fun isHighLoad(): Boolean
    
    /**
     * Record audio extraction performance
     * @param timeMs Time taken in milliseconds
     * @param method Extraction method used (ffmpeg, mediacodec, fallback)
     */
    fun recordAudioExtraction(timeMs: Long, method: String)
    
    /**
     * Record subtitle generation performance
     * @param timeMs Time taken in milliseconds
     */
    fun recordSubtitleGeneration(timeMs: Long)
    
    /**
     * Record download speed sample
     * @param bytesPerSecond Download speed in bytes per second
     */
    fun recordDownloadSpeed(bytesPerSecond: Long)
}