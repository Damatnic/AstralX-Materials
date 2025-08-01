package com.astralx.browser.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.astralx.browser.core.download.AdvancedDownloadEngine
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced video download manager with adult content site support
 */
@Singleton
class VideoDownloadManager @Inject constructor(
    private val context: Context,
    private val downloadEngine: AdvancedDownloadEngine
) {
    
    private val _activeDownloads = MutableLiveData<Map<String, VideoDownloadInfo>>()
    val activeDownloads: LiveData<Map<String, VideoDownloadInfo>> = _activeDownloads
    
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloads = mutableMapOf<String, VideoDownloadInfo>()
    
    data class VideoDownloadInfo(
        val id: String,
        val url: String,
        val title: String,
        val quality: String,
        val site: String,
        val progress: Float = 0f,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val speed: Long = 0,
        val status: DownloadStatus = DownloadStatus.PENDING,
        val filePath: String? = null,
        val error: String? = null
    )
    
    enum class DownloadStatus {
        PENDING, DOWNLOADING, PAUSED, COMPLETED, ERROR, CANCELLED
    }
    
    /**
     * Download video from adult content site with quality selection
     */
    fun downloadVideo(
        url: String,
        title: String,
        quality: String,
        site: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        val downloadId = generateDownloadId(url, quality)
        
        if (downloads.containsKey(downloadId)) {
            Timber.w("Download already exists: $downloadId")
            return downloadId
        }
        
        val downloadInfo = VideoDownloadInfo(
            id = downloadId,
            url = url,
            title = sanitizeFileName(title),
            quality = quality,
            site = site
        )
        
        downloads[downloadId] = downloadInfo
        updateActiveDownloads()
        
        downloadScope.launch {
            try {
                performDownload(downloadId, url, title, quality, headers)
            } catch (e: Exception) {
                Timber.e(e, "Error downloading video: $title")
                updateDownloadStatus(downloadId, DownloadStatus.ERROR, error = e.message)
            }
        }
        
        return downloadId
    }
    
    private suspend fun performDownload(
        downloadId: String,
        url: String,
        title: String,
        quality: String,
        headers: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        
        updateDownloadStatus(downloadId, DownloadStatus.DOWNLOADING)
        
        // Create download directory
        val downloadsDir = File(context.getExternalFilesDir(null), "AstralX_Videos")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        // Generate filename with quality
        val extension = getFileExtension(url)
        val fileName = "${sanitizeFileName(title)}_${quality}.$extension"
        val outputFile = File(downloadsDir, fileName)
        
        // Use the advanced download engine
        val downloadTask = downloadEngine.downloadFile(
            url = url,
            fileName = fileName,
            headers = headers,
            onProgress = { progress, downloadedBytes, totalBytes, speed ->
                updateDownloadProgress(
                    downloadId = downloadId,
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speed = speed
                )
            }
        )
        
        // Wait for completion
        downloadTask.join()
        
        if (outputFile.exists() && outputFile.length() > 0) {
            updateDownloadStatus(
                downloadId = downloadId,
                status = DownloadStatus.COMPLETED,
                filePath = outputFile.absolutePath
            )
            Timber.d("Video downloaded successfully: ${outputFile.absolutePath}")
        } else {
            throw Exception("Download failed - file not created or empty")
        }
    }
    
    private fun updateDownloadProgress(
        downloadId: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long
    ) {
        downloads[downloadId]?.let { info ->
            downloads[downloadId] = info.copy(
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speed = speed
            )
            updateActiveDownloads()
        }
    }
    
    private fun updateDownloadStatus(
        downloadId: String,
        status: DownloadStatus,
        filePath: String? = null,
        error: String? = null
    ) {
        downloads[downloadId]?.let { info ->
            downloads[downloadId] = info.copy(
                status = status,
                filePath = filePath ?: info.filePath,
                error = error
            )
            updateActiveDownloads()
        }
    }
    
    private fun updateActiveDownloads() {
        _activeDownloads.postValue(downloads.toMap())
    }
    
    /**
     * Pause a download
     */
    fun pauseDownload(downloadId: String) {
        updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
        // Implement pause logic in download engine
    }
    
    /**
     * Resume a paused download
     */
    fun resumeDownload(downloadId: String) {
        downloads[downloadId]?.let { info ->
            if (info.status == DownloadStatus.PAUSED) {
                downloadVideo(info.url, info.title, info.quality, info.site)
            }
        }
    }
    
    /**
     * Cancel a download
     */
    fun cancelDownload(downloadId: String) {
        updateDownloadStatus(downloadId, DownloadStatus.CANCELLED)
        // Clean up partial file if exists
        downloads[downloadId]?.filePath?.let { path ->
            File(path).delete()
        }
        downloads.remove(downloadId)
        updateActiveDownloads()
    }
    
    /**
     * Get download info by ID
     */
    fun getDownloadInfo(downloadId: String): VideoDownloadInfo? {
        return downloads[downloadId]
    }
    
    /**
     * Clear completed downloads from memory
     */
    fun clearCompleted() {
        val completed = downloads.filter { it.value.status == DownloadStatus.COMPLETED }
        completed.forEach { downloads.remove(it.key) }
        updateActiveDownloads()
    }
    
    /**
     * Get all downloaded videos
     */
    fun getDownloadedVideos(): List<File> {
        val downloadsDir = File(context.getExternalFilesDir(null), "AstralX_Videos")
        return if (downloadsDir.exists()) {
            downloadsDir.listFiles()?.filter { 
                it.isFile && isVideoFile(it.name) 
            } ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    private fun generateDownloadId(url: String, quality: String): String {
        return "${url.hashCode()}_$quality"
    }
    
    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .take(100) // Limit length
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4") -> "mp4"
            url.contains(".webm") -> "webm"
            url.contains(".m3u8") -> "mp4" // HLS will be converted
            url.contains(".flv") -> "flv"
            else -> "mp4"
        }
    }
    
    private fun isVideoFile(fileName: String): Boolean {
        val videoExtensions = listOf(".mp4", ".webm", ".avi", ".mkv", ".flv", ".mov")
        return videoExtensions.any { fileName.lowercase().endsWith(it) }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        downloadScope.cancel()
        downloads.clear()
        _activeDownloads.value = emptyMap()
    }
}