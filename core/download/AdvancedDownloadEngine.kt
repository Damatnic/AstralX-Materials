package com.astralx.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AdvancedDownloadEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedDownloadEngine"
        private const val ADULT_CONTENT_DIR = "AstralX_Private"
        private const val REGULAR_CONTENT_DIR = "AstralX_Downloads"
    }
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val activeDownloads = ConcurrentHashMap<Long, DownloadInfo>()
    private val downloadHistory = mutableListOf<DownloadInfo>()
    
    private val _downloadProgress = MutableLiveData<Map<Long, DownloadProgress>>()
    val downloadProgress: LiveData<Map<Long, DownloadProgress>> = _downloadProgress
    
    private val _downloadQueue = MutableLiveData<List<DownloadInfo>>()
    val downloadQueue: LiveData<List<DownloadInfo>> = _downloadQueue
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Start periodic cleanup of old tracking data
        startPeriodicCleanup()
    }
    
    /**
     * Start periodic cleanup of old tracking data
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (true) {
                delay(60 * 60 * 1000L) // Run every hour
                cleanupOldTrackingData()
            }
        }
    }
    
    /**
     * Download video with intelligent detection and optimization
     */
    fun downloadVideo(
        url: String,
        title: String = "Video",
        isAdultContent: Boolean = false,
        quality: VideoQuality = VideoQuality.AUTO,
        enableNotifications: Boolean = true
    ): Long {
        Log.d(TAG, "Starting video download: $url")
        
        val videoInfo = analyzeVideoUrl(url)
        val optimizedUrl = selectBestQuality(url, quality, videoInfo)
        val fileName = generateFileName(title, videoInfo.format)
        
        val request = DownloadManager.Request(Uri.parse(optimizedUrl)).apply {
            setTitle(title)
            setDescription("Downloading ${videoInfo.format.uppercase()} video...")
            
            // Set destination based on content type
            val downloadDir = if (isAdultContent) {
                createSecureDownloadDirectory()
            } else {
                Environment.DIRECTORY_DOWNLOADS
            }
            
            setDestinationInExternalPublicDir(downloadDir, fileName)
            
            // Adult content optimizations
            if (isAdultContent) {
                configureAdultContentDownload(this, url)
            }
            
            // Network policy
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            
            // Notifications
            if (enableNotifications) {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            } else {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            }
            
            // Headers for better compatibility
            addRequestHeader("User-Agent", getOptimizedUserAgent(isAdultContent))
            if (isAdultContent) {
                addRequestHeader("Referer", extractDomain(url))
            }
        }
        
        val downloadId = downloadManager.enqueue(request)
        
        // Track download
        val downloadInfo = DownloadInfo(
            id = downloadId,
            url = url,
            title = title,
            fileName = fileName,
            isAdultContent = isAdultContent,
            videoInfo = videoInfo,
            quality = quality,
            startTime = System.currentTimeMillis(),
            status = DownloadStatus.QUEUED
        )
        
        activeDownloads[downloadId] = downloadInfo
        updateDownloadQueue()
        
        // Start monitoring
        monitorDownload(downloadId)
        
        Log.d(TAG, "Download queued with ID: $downloadId")
        return downloadId
    }
    
    /**
     * Analyze video URL to extract metadata
     */
    private fun analyzeVideoUrl(url: String): VideoInfo {
        val format = when {
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".webm", ignoreCase = true) -> "webm"
            url.contains(".mkv", ignoreCase = true) -> "mkv"
            url.contains(".avi", ignoreCase = true) -> "avi"
            url.contains(".mov", ignoreCase = true) -> "mov"
            url.contains("m3u8", ignoreCase = true) -> "m3u8"
            url.contains("mpd", ignoreCase = true) -> "mpd"
            else -> "mp4" // Default
        }
        
        val isStreamingFormat = format in listOf("m3u8", "mpd")
        val estimatedSize = estimateVideoSize(url, format)
        
        return VideoInfo(
            format = format,
            isStreaming = isStreamingFormat,
            estimatedSizeMB = estimatedSize,
            detectedQuality = detectQualityFromUrl(url)
        )
    }
    
    /**
     * Select best quality URL based on preference
     */
    private fun selectBestQuality(url: String, quality: VideoQuality, videoInfo: VideoInfo): String {
        // For streaming formats, we might need to parse manifest
        if (videoInfo.isStreaming) {
            return selectStreamingQuality(url, quality)
        }
        
        // For direct video files, return as-is
        return url
    }
    
    /**
     * Handle streaming video quality selection
     */
    private fun selectStreamingQuality(url: String, quality: VideoQuality): String {
        // In a real implementation, this would parse HLS/DASH manifests
        // and select the appropriate quality stream
        return url
    }
    
    /**
     * Configure download request for adult content
     */
    private fun configureAdultContentDownload(request: DownloadManager.Request, url: String) {
        // Adult content specific headers
        request.addRequestHeader("Accept", "video/mp4,video/webm,video/*;q=0.9,*/*;q=0.8")
        request.addRequestHeader("Accept-Language", "en-US,en;q=0.5")
        request.addRequestHeader("Cache-Control", "no-cache")
        
        // Some adult sites require specific referrers
        val domain = extractDomain(url)
        request.addRequestHeader("Referer", "https://$domain/")
    }
    
    /**
     * Monitor download progress
     */
    private fun monitorDownload(downloadId: Long) {
        scope.launch {
            while (activeDownloads.containsKey(downloadId)) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    
                    // Calculate enhanced progress metrics
                    val downloadSpeed = calculateDownloadSpeed(downloadId, bytesDownloaded)
                    val eta = calculateETA(downloadId, bytesDownloaded, totalBytes)
                    val progressPercent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                    
                    val progress = DownloadProgress(
                        downloadId = downloadId,
                        status = mapDownloadStatus(status),
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        progress = progressPercent,
                        downloadSpeed = downloadSpeed,
                        estimatedTimeRemaining = eta
                    )
                    
                    updateProgress(downloadId, progress)
                    
                    // Update download info status
                    activeDownloads[downloadId]?.let { downloadInfo ->
                        downloadInfo.status = progress.status
                        downloadInfo.progress = progress.progress
                    }
                    
                    // Check if download is complete or failed
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            handleDownloadCompleted(downloadId, cursor)
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            handleDownloadFailed(downloadId, reason)
                            break
                        }
                    }
                }
                
                cursor.close()
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Handle successful download completion with enhanced cleanup
     */
    private fun handleDownloadCompleted(downloadId: Long, cursor: android.database.Cursor) {
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        
        activeDownloads[downloadId]?.let { downloadInfo ->
            downloadInfo.status = DownloadStatus.COMPLETED
            downloadInfo.completedTime = System.currentTimeMillis()
            downloadInfo.localPath = localUri
            
            // Calculate final download statistics
            val speedData = downloadSpeedTracking[downloadId]
            if (speedData != null) {
                val totalTime = (downloadInfo.completedTime!! - downloadInfo.startTime) / 1000.0
                val averageSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.average() else 0.0
                
                Log.d(TAG, "Download completed: ${downloadInfo.title}")
                Log.d(TAG, "Total time: ${totalTime}s, Average speed: ${averageSpeed / 1024}KB/s")
            }
            
            // Move to history
            downloadHistory.add(downloadInfo)
            activeDownloads.remove(downloadId)
            
            // Enhanced cleanup of tracking data
            cleanupDownloadTracking(downloadId)
            
            // Generate thumbnail for video
            scope.launch {
                generateVideoThumbnail(downloadInfo)
            }
            
            updateDownloadQueue()
            updateProgressCleanup(downloadId)
        }
    }
    
    /**
     * Handle download failure with enhanced cleanup
     */
    private fun handleDownloadFailed(downloadId: Long, reason: Int) {
        activeDownloads[downloadId]?.let { downloadInfo ->
            downloadInfo.status = DownloadStatus.FAILED
            downloadInfo.failureReason = mapFailureReason(reason)
            
            // Log failure details with speed data if available
            val speedData = downloadSpeedTracking[downloadId]
            if (speedData != null) {
                val downloadTime = (System.currentTimeMillis() - downloadInfo.startTime) / 1000.0
                val lastSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.last() else 0.0
                
                Log.e(TAG, "Download failed: ${downloadInfo.title}")
                Log.e(TAG, "Failure reason: ${downloadInfo.failureReason}")
                Log.e(TAG, "Download time: ${downloadTime}s, Last speed: ${lastSpeed / 1024}KB/s")
            }
            
            // Move to history
            downloadHistory.add(downloadInfo)
            activeDownloads.remove(downloadId)
            
            // Enhanced cleanup of tracking data
            cleanupDownloadTracking(downloadId)
            
            updateDownloadQueue()
            updateProgressCleanup(downloadId)
        }
    }
    
    /**
     * Generate video thumbnail
     */
    private suspend fun generateVideoThumbnail(downloadInfo: DownloadInfo) = withContext(Dispatchers.IO) {
        try {
            downloadInfo.localPath?.let { path ->
                val thumbnailPath = ThumbnailGenerator.generateThumbnail(
                    videoPath = path,
                    outputDir = getThumbnailDirectory(downloadInfo.isAdultContent),
                    timeMs = 5000 // 5 seconds into video
                )
                downloadInfo.thumbnailPath = thumbnailPath
                Log.d(TAG, "Thumbnail generated: $thumbnailPath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail", e)
        }
    }
    
    /**
     * Calculate download speed with 10-sample smoothing for stable readings
     */
    private fun calculateDownloadSpeed(downloadId: Long, bytesDownloaded: Long): Double {
        val downloadInfo = activeDownloads[downloadId] ?: return 0.0
        
        // Get or create speed tracking data
        val speedData = downloadSpeedTracking.getOrPut(downloadId) {
            DownloadSpeedData(
                startTime = System.currentTimeMillis(),
                lastUpdateTime = System.currentTimeMillis(),
                lastBytesDownloaded = 0L,
                speeds = mutableListOf(),
                speedSamples = mutableListOf(),
                totalBytesHistory = mutableListOf()
            )
        }
        
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - speedData.lastUpdateTime
        
        // Calculate speed only if enough time has passed (at least 100ms)
        if (timeDiff >= 100) {
            val bytesDiff = bytesDownloaded - speedData.lastBytesDownloaded
            val speedBytesPerMs = bytesDiff.toDouble() / timeDiff
            val speedBytesPerSecond = speedBytesPerMs * 1000
            
            // Add current speed sample to the 10-sample buffer
            speedData.speedSamples.add(speedBytesPerSecond)
            if (speedData.speedSamples.size > 10) {
                speedData.speedSamples.removeAt(0)
            }
            
            // Add to total bytes history for trend analysis
            speedData.totalBytesHistory.add(Pair(currentTime, bytesDownloaded))
            if (speedData.totalBytesHistory.size > 20) {
                speedData.totalBytesHistory.removeAt(0)
            }
            
            // Calculate smoothed speed using weighted average
            val smoothedSpeed = calculateSmoothedSpeed(speedData.speedSamples)
            
            // Add smoothed speed to main speed history
            speedData.speeds.add(smoothedSpeed)
            if (speedData.speeds.size > 10) {
                speedData.speeds.removeAt(0)
            }
            
            // Update tracking data
            speedData.lastUpdateTime = currentTime
            speedData.lastBytesDownloaded = bytesDownloaded
            
            return smoothedSpeed
        }
        
        // Return last known smoothed speed
        return if (speedData.speeds.isNotEmpty()) speedData.speeds.last() else 0.0
    }
    
    /**
     * Calculate smoothed speed using weighted average of 10 samples
     * Recent samples have higher weight for better responsiveness
     */
    private fun calculateSmoothedSpeed(samples: List<Double>): Double {
        if (samples.isEmpty()) return 0.0
        if (samples.size == 1) return samples[0]
        
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        // Apply weights: more recent samples get higher weight
        samples.forEachIndexed { index, speed ->
            val weight = (index + 1).toDouble() / samples.size // Weight from 0.1 to 1.0
            weightedSum += speed * weight
            totalWeight += weight
        }
        
        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }
    
    /**
     * Calculate accurate ETA based on speed trends and historical data
     */
    private fun calculateETA(downloadId: Long, bytesDownloaded: Long, totalBytes: Long): Long {
        if (bytesDownloaded >= totalBytes) return 0L
        
        val speedData = downloadSpeedTracking[downloadId] ?: return -1L
        
        // Use trend-based calculation if we have enough history
        val trendBasedETA = calculateTrendBasedETA(speedData, bytesDownloaded, totalBytes)
        if (trendBasedETA > 0) return trendBasedETA
        
        // Fallback to current speed calculation
        val currentSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.last() else 0.0
        if (currentSpeed <= 0) return -1L // Unknown ETA
        
        val remainingBytes = totalBytes - bytesDownloaded
        val remainingSeconds = (remainingBytes / currentSpeed).toLong()
        
        // Return ETA in milliseconds
        return remainingSeconds * 1000
    }
    
    /**
     * Calculate ETA based on download speed trends
     */
    private fun calculateTrendBasedETA(
        speedData: DownloadSpeedData,
        bytesDownloaded: Long,
        totalBytes: Long
    ): Long {
        val history = speedData.totalBytesHistory
        if (history.size < 3) return -1L // Need at least 3 data points
        
        // Calculate average speed over different time windows
        val recentHistory = history.takeLast(5) // Last 5 measurements
        val olderHistory = history.takeLast(10).take(5) // Previous 5 measurements
        
        if (recentHistory.size < 2) return -1L
        
        // Calculate recent speed trend
        val recentSpeed = calculateSpeedFromHistory(recentHistory)
        val olderSpeed = if (olderHistory.size >= 2) calculateSpeedFromHistory(olderHistory) else recentSpeed
        
        // Determine if speed is increasing, decreasing, or stable
        val speedTrend = when {
            recentSpeed > olderSpeed * 1.1 -> SpeedTrend.INCREASING
            recentSpeed < olderSpeed * 0.9 -> SpeedTrend.DECREASING
            else -> SpeedTrend.STABLE
        }
        
        // Project future speed based on trend
        val projectedSpeed = when (speedTrend) {
            SpeedTrend.INCREASING -> {
                // Assume speed will continue to increase but cap the growth
                val growthRate = (recentSpeed / olderSpeed).coerceAtMost(1.5)
                recentSpeed * growthRate.coerceAtMost(1.2) // Max 20% increase
            }
            SpeedTrend.DECREASING -> {
                // Assume speed will stabilize at recent level
                recentSpeed * 0.95 // Slight decrease
            }
            SpeedTrend.STABLE -> recentSpeed
        }
        
        if (projectedSpeed <= 0) return -1L
        
        val remainingBytes = totalBytes - bytesDownloaded
        val remainingSeconds = (remainingBytes / projectedSpeed).toLong()
        
        return remainingSeconds * 1000
    }
    
    /**
     * Calculate speed from historical data points
     */
    private fun calculateSpeedFromHistory(history: List<Pair<Long, Long>>): Double {
        if (history.size < 2) return 0.0
        
        val first = history.first()
        val last = history.last()
        
        val timeDiff = last.first - first.first
        val bytesDiff = last.second - first.second
        
        return if (timeDiff > 0) (bytesDiff.toDouble() / timeDiff) * 1000 else 0.0
    }
    
    // Enum for speed trend analysis
    private enum class SpeedTrend {
        INCREASING, DECREASING, STABLE
    }
    
    // Data class for tracking download speeds with enhanced smoothing
    private data class DownloadSpeedData(
        var startTime: Long,
        var lastUpdateTime: Long,
        var lastBytesDownloaded: Long,
        val speeds: MutableList<Double>, // Smoothed speeds for display
        val speedSamples: MutableList<Double>, // Raw speed samples for smoothing
        val totalBytesHistory: MutableList<Pair<Long, Long>> // (timestamp, totalBytes) for trend analysis
    )
    
    // Map to track download speeds
    private val downloadSpeedTracking = mutableMapOf<Long, DownloadSpeedData>()
    
    /**
     * Update download progress
     */
    private fun updateProgress(downloadId: Long, progress: DownloadProgress) {
        val currentProgress = _downloadProgress.value?.toMutableMap() ?: mutableMapOf()
        currentProgress[downloadId] = progress
        _downloadProgress.postValue(currentProgress)
    }
    
    /**
     * Update download queue
     */
    private fun updateDownloadQueue() {
        _downloadQueue.postValue(activeDownloads.values.toList())
    }
    
    /**
     * Create secure download directory for adult content
     */
    private fun createSecureDownloadDirectory(): String {
        val secureDir = File(context.getExternalFilesDir(null), ADULT_CONTENT_DIR)
        if (!secureDir.exists()) {
            secureDir.mkdirs()
        }
        return secureDir.absolutePath
    }
    
    /**
     * Get thumbnail directory
     */
    private fun getThumbnailDirectory(isAdultContent: Boolean): String {
        val dir = if (isAdultContent) {
            File(context.getExternalFilesDir(null), "$ADULT_CONTENT_DIR/thumbnails")
        } else {
            File(context.getExternalFilesDir(null), "$REGULAR_CONTENT_DIR/thumbnails")
        }
        
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        return dir.absolutePath
    }
    
    /**
     * Generate optimized file name
     */
    private fun generateFileName(title: String, format: String): String {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50) // Limit length
        val timestamp = System.currentTimeMillis()
        return "${cleanTitle}_${timestamp}.${format}"
    }
    
    /**
     * Get optimized user agent
     */
    private fun getOptimizedUserAgent(isAdultContent: Boolean): String {
        return if (isAdultContent) {
            "Mozilla/5.0 (Android 11; Mobile; rv:68.0) Gecko/68.0 Firefox/88.0"
        } else {
            System.getProperty("http.agent") ?: "AstralX/1.0"
        }
    }
    
    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Estimate video size based on URL analysis
     */
    private fun estimateVideoSize(url: String, format: String): Long {
        // Basic estimation based on format
        return when (format) {
            "mp4" -> 50L // 50MB average
            "webm" -> 30L
            "mkv" -> 100L
            else -> 50L
        }
    }
    
    /**
     * Detect quality from URL patterns
     */
    private fun detectQualityFromUrl(url: String): String {
        return when {
            url.contains("1080p", ignoreCase = true) -> "1080p"
            url.contains("720p", ignoreCase = true) -> "720p"
            url.contains("480p", ignoreCase = true) -> "480p"
            url.contains("4K", ignoreCase = true) -> "4K"
            else -> "Unknown"
        }
    }
    
    /**
     * Map DownloadManager status to our enum
     */
    private fun mapDownloadStatus(status: Int): DownloadStatus {
        return when (status) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.QUEUED
            DownloadManager.STATUS_RUNNING -> DownloadStatus.DOWNLOADING
            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
            else -> DownloadStatus.UNKNOWN
        }
    }
    
    /**
     * Map failure reason to readable string
     */
    private fun mapFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File system error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed"
        }
    }
    
    /**
     * Get download statistics
     */
    fun getDownloadStats(): DownloadStats {
        val completed = downloadHistory.count { it.status == DownloadStatus.COMPLETED }
        val failed = downloadHistory.count { it.status == DownloadStatus.FAILED }
        val totalSize = downloadHistory.filter { it.status == DownloadStatus.COMPLETED }
            .sumOf { it.videoInfo.estimatedSizeMB }
        
        return DownloadStats(
            totalDownloads = downloadHistory.size,
            completedDownloads = completed,
            failedDownloads = failed,
            activeDownloads = activeDownloads.size,
            totalSizeMB = totalSize,
            adultContentDownloads = downloadHistory.count { it.isAdultContent }
        )
    }
    
    /**
     * Pause download
     */
    fun pauseDownload(downloadId: Long): Boolean {
        // DownloadManager doesn't support pause/resume directly
        // Would need custom implementation
        return false
    }
    
    /**
     * Cancel download with enhanced cleanup
     */
    fun cancelDownload(downloadId: Long): Boolean {
        val removed = downloadManager.remove(downloadId)
        if (removed > 0) {
            activeDownloads.remove(downloadId)
            cleanupDownloadTracking(downloadId)
            updateDownloadQueue()
            updateProgressCleanup(downloadId)
            Log.d(TAG, "Download cancelled: $downloadId")
        }
        return removed > 0
    }
    
    /**
     * Enhanced cleanup of download tracking data
     */
    private fun cleanupDownloadTracking(downloadId: Long) {
        downloadSpeedTracking.remove(downloadId)
        
        // Clean up any orphaned progress data
        val currentProgress = _downloadProgress.value?.toMutableMap() ?: mutableMapOf()
        currentProgress.remove(downloadId)
        _downloadProgress.postValue(currentProgress)
    }
    
    /**
     * Update progress tracking with cleanup
     */
    private fun updateProgressCleanup(downloadId: Long) {
        val currentProgress = _downloadProgress.value?.toMutableMap() ?: mutableMapOf()
        currentProgress.remove(downloadId)
        _downloadProgress.postValue(currentProgress)
    }
    
    /**
     * Get detailed download speed statistics
     */
    fun getDownloadSpeedStats(downloadId: Long): DownloadSpeedStats? {
        val speedData = downloadSpeedTracking[downloadId] ?: return null
        val downloadInfo = activeDownloads[downloadId] ?: return null
        
        val currentTime = System.currentTimeMillis()
        val totalTime = (currentTime - speedData.startTime) / 1000.0
        
        return DownloadSpeedStats(
            downloadId = downloadId,
            averageSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.average() else 0.0,
            currentSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.last() else 0.0,
            maxSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.maxOrNull() ?: 0.0 else 0.0,
            minSpeed = if (speedData.speeds.isNotEmpty()) speedData.speeds.minOrNull() ?: 0.0 else 0.0,
            totalTimeSeconds = totalTime,
            speedSamples = speedData.speedSamples.size,
            isSpeedStable = isSpeedStable(speedData.speeds)
        )
    }
    
    /**
     * Check if download speed is stable (low variance)
     */
    private fun isSpeedStable(speeds: List<Double>): Boolean {
        if (speeds.size < 3) return false
        
        val average = speeds.average()
        val variance = speeds.map { (it - average) * (it - average) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        // Consider stable if standard deviation is less than 20% of average
        return standardDeviation < (average * 0.2)
    }
    
    /**
     * Cleanup old download tracking data to prevent memory leaks
     */
    fun cleanupOldTrackingData() {
        val currentTime = System.currentTimeMillis()
        val maxAge = 24 * 60 * 60 * 1000L // 24 hours
        
        downloadSpeedTracking.entries.removeAll { (downloadId, speedData) ->
            val age = currentTime - speedData.startTime
            val isOld = age > maxAge
            val isNotActive = !activeDownloads.containsKey(downloadId)
            
            if (isOld && isNotActive) {
                Log.d(TAG, "Cleaning up old tracking data for download: $downloadId")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Get download history
     */
    fun getDownloadHistory(): List<DownloadInfo> {
        return downloadHistory.toList()
    }
    
    /**
     * Clear download history
     */
    fun clearDownloadHistory() {
        downloadHistory.clear()
        Log.d(TAG, "Download history cleared")
    }
    
    /**
     * Get current download speeds for all active downloads
     */
    fun getAllActiveDownloadSpeeds(): Map<Long, Double> {
        return activeDownloads.keys.associateWith { downloadId ->
            downloadSpeedTracking[downloadId]?.let { speedData ->
                if (speedData.speeds.isNotEmpty()) speedData.speeds.last() else 0.0
            } ?: 0.0
        }
    }
    
    /**
     * Get detailed progress information for a specific download
     */
    fun getDetailedProgress(downloadId: Long): DetailedDownloadProgress? {
        val downloadInfo = activeDownloads[downloadId] ?: return null
        val speedData = downloadSpeedTracking[downloadId] ?: return null
        val currentProgress = _downloadProgress.value?.get(downloadId) ?: return null
        
        return DetailedDownloadProgress(
            downloadInfo = downloadInfo,
            progress = currentProgress,
            speedStats = getDownloadSpeedStats(downloadId),
            speedTrend = determineSpeedTrend(speedData),
            estimatedCompletion = if (currentProgress.estimatedTimeRemaining > 0) {
                System.currentTimeMillis() + currentProgress.estimatedTimeRemaining
            } else null
        )
    }
    
    /**
     * Determine the current speed trend for a download
     */
    private fun determineSpeedTrend(speedData: DownloadSpeedData): String {
        if (speedData.speeds.size < 3) return "Unknown"
        
        val recent = speedData.speeds.takeLast(3)
        val older = speedData.speeds.dropLast(3).takeLast(3)
        
        if (older.isEmpty()) return "Stable"
        
        val recentAvg = recent.average()
        val olderAvg = older.average()
        
        return when {
            recentAvg > olderAvg * 1.1 -> "Increasing"
            recentAvg < olderAvg * 0.9 -> "Decreasing"
            else -> "Stable"
        }
    }
    
    /**
     * Cleanup resources when engine is destroyed
     */
    fun cleanup() {
        scope.cancel()
        downloadSpeedTracking.clear()
        activeDownloads.clear()
        Log.d(TAG, "AdvancedDownloadEngine cleaned up")
    }
}