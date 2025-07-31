package com.astralx.browser.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class PerformanceMetrics(
    val cpuUsagePercent: Double,
    val memoryUsageMB: Long,
    val availableMemoryMB: Long,
    val gcCount: Long,
    val activeThreads: Int,
    val downloadSpeedMbps: Double,
    val jsHeapSizeMB: Long = 0,
    val domNodesCount: Int = 0,
    val networkLatencyMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class CpuStats(
    val total: Long,
    val idle: Long,
    val user: Long,
    val system: Long,
    val ioWait: Long,
    val irq: Long,
    val softIrq: Long
)

@Singleton
class RealTimePerformanceMonitor @Inject constructor(
    private val context: Context
) : PerformanceMonitor {
    
    private val monitoringJob = SupervisorJob()
    private val monitoringScope = CoroutineScope(Dispatchers.IO + monitoringJob)
    
    private val _performanceMetrics = MutableSharedFlow<PerformanceMetrics>()
    override val performanceMetrics: SharedFlow<PerformanceMetrics> = _performanceMetrics.asSharedFlow()
    
    private val performanceMetricsState = MutableStateFlow(createDefaultMetrics())
    private val generationTimes = ConcurrentHashMap<String, MutableList<Long>>()
    
    private var lastCpuStats: CpuStats? = null
    private var lastAppCpuTime: Long = 0
    private var lastTotalCpuTime: Long = 0
    
    private val cpuSamples = mutableListOf<Double>()
    private val maxCpuSamples = 5
    private val logImpactCount = AtomicLong(0)
    
    private var isMonitoringActive = false
    
    override fun startMonitoring() {
        Timber.d("Starting performance monitoring")
        isMonitoringActive = true
        
        monitoringScope.launch {
            while (isActive) {
                try {
                    val metrics = collectPerformanceMetrics()
                    performanceMetricsState.emit(metrics)
                    _performanceMetrics.emit(metrics)
                } catch (e: Exception) {
                    Timber.e(e, "Error collecting performance metrics")
                }
                delay(1000) // Update every second
            }
        }
    }
    
    override fun stopMonitoring() {
        Timber.d("Stopping performance monitoring")
        isMonitoringActive = false
        monitoringJob.cancel()
    }
    
    override fun getPerformanceMetrics(): Flow<PerformanceMetrics> = performanceMetricsState.asStateFlow()
    
    override fun recordGenerationTime(identifier: String, timeMs: Long) {
        generationTimes.getOrPut(identifier) { mutableListOf() }.apply {
            add(timeMs)
            // Keep only last 100 samples
            if (size > 100) {
                removeAt(0)
            }
        }
        Timber.d("Recorded generation time for $identifier: ${timeMs}ms")
    }
    
    override suspend fun getCurrentMetrics(): PerformanceMetrics {
        return performanceMetricsState.value
    }
    
    override fun isMonitoring(): Boolean {
        return isMonitoringActive
    }
    
    override fun trackLogImpact() {
        logImpactCount.incrementAndGet()
    }
    
    override fun getMemoryPressure(): Float {
        val currentMetrics = performanceMetrics.value
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024 // MB
        return (currentMetrics.memoryUsageMB.toFloat() / maxMemory).coerceIn(0f, 1f)
    }
    
    override fun getCpuUsage(): Double {
        return performanceMetrics.value.cpuUsagePercent
    }
    
    override fun getMemoryUsageMB(): Double {
        return performanceMetrics.value.memoryUsageMB.toDouble()
    }
    
    override fun isHighLoad(): Boolean {
        val metrics = performanceMetrics.value
        return metrics.cpuUsagePercent > 80.0 || getMemoryPressure() > 0.8f
    }
    
    override fun recordAudioExtraction(timeMs: Long, method: String) {
        monitoringScope.launch {
            Timber.d("Audio extraction completed in ${timeMs}ms using $method")
            recordGenerationTime("audio_extraction_$method", timeMs)
        }
    }
    
    override fun recordSubtitleGeneration(timeMs: Long) {
        monitoringScope.launch {
            Timber.d("Subtitle generation completed in ${timeMs}ms")
            recordGenerationTime("subtitle_generation", timeMs)
        }
    }
    
    override fun recordDownloadSpeed(bytesPerSecond: Long) {
        monitoringScope.launch {
            // Convert bytes per second to Mbps
            val mbps = bytesPerSecond * 8.0 / (1024 * 1024)
            performanceMetrics.update { current ->
                current.copy(
                    downloadSpeedMbps = mbps
                )
            }
        }
    }
    
    fun getAverageGenerationTime(identifier: String): Long? {
        return generationTimes[identifier]?.let { times ->
            if (times.isEmpty()) null else times.average().toLong()
        }
    }
    
    private suspend fun collectPerformanceMetrics(): PerformanceMetrics = withContext(Dispatchers.IO) {
        val memoryInfo = getMemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val cpuUsage = calculateCpuUsage()
        val threadCount = getActiveThreadCount()
        val gcCount = getGarbageCollectionCount()
        val currentDownloadSpeed = performanceMetrics.value.downloadSpeedMbps
        
        PerformanceMetrics(
            cpuUsagePercent = cpuUsage,
            memoryUsageMB = memoryInfo.totalPss / 1024,
            availableMemoryMB = memInfo.availMem / 1024 / 1024,
            gcCount = gcCount,
            activeThreads = threadCount,
            downloadSpeedMbps = currentDownloadSpeed,
            jsHeapSizeMB = performanceMetrics.value.jsHeapSizeMB,
            domNodesCount = performanceMetrics.value.domNodesCount,
            networkLatencyMs = performanceMetrics.value.networkLatencyMs
        )
    }
    
    private fun getMemoryInfo(): Debug.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo
    }
    
    private fun calculateCpuUsage(): Double {
        try {
            // Read total CPU stats
            val currentCpuStats = readCpuStats()
            val currentAppCpuTime = readAppCpuTime()
            val currentTotalCpuTime = currentCpuStats.total
            
            // Calculate CPU usage if we have previous readings
            val cpuUsage = if (lastCpuStats != null && lastTotalCpuTime > 0) {
                val appCpuDelta = currentAppCpuTime - lastAppCpuTime
                val totalCpuDelta = currentTotalCpuTime - lastTotalCpuTime
                
                if (totalCpuDelta > 0) {
                    ((appCpuDelta.toDouble() / totalCpuDelta) * 100).coerceIn(0.0, 100.0)
                } else {
                    0.0
                }
            } else {
                0.0
            }
            
            // Update last readings
            lastCpuStats = currentCpuStats
            lastAppCpuTime = currentAppCpuTime
            lastTotalCpuTime = currentTotalCpuTime
            
            // Add to samples for smoothing
            cpuSamples.add(cpuUsage)
            if (cpuSamples.size > maxCpuSamples) {
                cpuSamples.removeAt(0)
            }
            
            // Return average of samples
            return if (cpuSamples.isNotEmpty()) {
                cpuSamples.average().roundToInt().toDouble()
            } else {
                0.0
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating CPU usage")
            return 0.0
        }
    }
    
    private fun readCpuStats(): CpuStats {
        try {
            BufferedReader(FileReader("/proc/stat")).use { reader ->
                val line = reader.readLine()
                val tokens = line.split(" ").filter { it.isNotEmpty() }
                
                if (tokens.isNotEmpty() && tokens[0] == "cpu") {
                    val user = tokens.getOrNull(1)?.toLongOrNull() ?: 0
                    val nice = tokens.getOrNull(2)?.toLongOrNull() ?: 0
                    val system = tokens.getOrNull(3)?.toLongOrNull() ?: 0
                    val idle = tokens.getOrNull(4)?.toLongOrNull() ?: 0
                    val ioWait = tokens.getOrNull(5)?.toLongOrNull() ?: 0
                    val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0
                    val softIrq = tokens.getOrNull(7)?.toLongOrNull() ?: 0
                    
                    val total = user + nice + system + idle + ioWait + irq + softIrq
                    
                    return CpuStats(
                        total = total,
                        idle = idle,
                        user = user + nice,
                        system = system,
                        ioWait = ioWait,
                        irq = irq,
                        softIrq = softIrq
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading CPU stats")
        }
        
        return CpuStats(0, 0, 0, 0, 0, 0, 0)
    }
    
    private fun readAppCpuTime(): Long {
        try {
            val pid = Process.myPid()
            BufferedReader(FileReader("/proc/$pid/stat")).use { reader ->
                val line = reader.readLine()
                val tokens = line.split(" ")
                
                // utime (14th token) + stime (15th token)
                val utime = tokens.getOrNull(13)?.toLongOrNull() ?: 0
                val stime = tokens.getOrNull(14)?.toLongOrNull() ?: 0
                
                return utime + stime
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading app CPU time")
            return 0
        }
    }
    
    private fun getActiveThreadCount(): Int {
        return Thread.activeCount()
    }
    
    private fun getGarbageCollectionCount(): Long {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }
    
    private fun createDefaultMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            cpuUsagePercent = 0.0,
            memoryUsageMB = 0,
            availableMemoryMB = 0,
            gcCount = 0,
            activeThreads = 0,
            downloadSpeedMbps = 0.0,
            jsHeapSizeMB = 0,
            domNodesCount = 0,
            networkLatencyMs = 0
        )
    }
    
    // Update specific metrics from external sources
    fun updateWebMetrics(jsHeapSizeMB: Long, domNodesCount: Int) {
        monitoringScope.launch {
            performanceMetrics.update { current ->
                current.copy(
                    jsHeapSizeMB = jsHeapSizeMB,
                    domNodesCount = domNodesCount
                )
            }
        }
    }
    
    fun updateNetworkMetrics(latencyMs: Long, downloadSpeedBps: Double) {
        monitoringScope.launch {
            // Convert bytes per second to Mbps
            val mbps = downloadSpeedBps * 8.0 / (1024 * 1024)
            performanceMetrics.update { current ->
                current.copy(
                    networkLatencyMs = latencyMs,
                    downloadSpeedMbps = mbps
                )
            }
        }
    }
}