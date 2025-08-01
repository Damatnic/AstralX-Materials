package com.astralx.browser.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.webkit.WebView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video thumbnail preview engine for hover effects
 * Similar to YouTube's video preview feature
 */
@Singleton
class VideoThumbnailPreviewEngine @Inject constructor(
    private val context: Context
) {
    
    private val _hoverPreviews = MutableLiveData<Map<String, PreviewData>>()
    val hoverPreviews: LiveData<Map<String, PreviewData>> = _hoverPreviews
    
    private val previewScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val previewCache = ConcurrentHashMap<String, PreviewData>()
    private val cacheDir = File(context.cacheDir, "video_previews")
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    data class PreviewData(
        val videoUrl: String,
        val frames: List<FrameData>,
        val duration: Long,
        val isReady: Boolean = false
    )
    
    data class FrameData(
        val timestamp: Long,
        val thumbnailPath: String,
        val frameIndex: Int
    )
    
    /**
     * Generate preview frames for a video
     */
    fun generatePreview(videoUrl: String, frameCount: Int = 10) {
        if (previewCache.containsKey(videoUrl)) {
            Timber.d("Preview already exists for: $videoUrl")
            return
        }
        
        previewScope.launch {
            try {
                val frames = extractVideoFrames(videoUrl, frameCount)
                val previewData = PreviewData(
                    videoUrl = videoUrl,
                    frames = frames,
                    duration = getVideoDuration(videoUrl),
                    isReady = true
                )
                
                previewCache[videoUrl] = previewData
                updatePreviews()
                
                Timber.d("Generated preview with ${frames.size} frames for: $videoUrl")
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate preview for: $videoUrl")
            }
        }
    }
    
    /**
     * Extract frames from video at regular intervals
     */
    private suspend fun extractVideoFrames(
        videoUrl: String, 
        frameCount: Int
    ): List<FrameData> = withContext(Dispatchers.IO) {
        
        val frames = mutableListOf<FrameData>()
        val retriever = MediaMetadataRetriever()
        
        try {
            // Set data source with headers for adult content sites
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to videoUrl
            )
            retriever.setDataSource(videoUrl, headers)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            
            if (duration > 0) {
                val interval = duration / frameCount
                
                for (i in 0 until frameCount) {
                    val timestamp = i * interval * 1000 // Convert to microseconds
                    val bitmap = retriever.getFrameAtTime(timestamp)
                    
                    bitmap?.let {
                        val framePath = saveFrameToCache(it, videoUrl, i)
                        frames.add(
                            FrameData(
                                timestamp = timestamp / 1000, // Back to milliseconds
                                thumbnailPath = framePath,
                                frameIndex = i
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting frames from video")
        } finally {
            retriever.release()
        }
        
        return frames
    }
    
    /**
     * Save frame bitmap to cache
     */
    private fun saveFrameToCache(bitmap: Bitmap, videoUrl: String, frameIndex: Int): String {
        val videoHash = videoUrl.hashCode().toString()
        val frameFile = File(cacheDir, "${videoHash}_frame_$frameIndex.jpg")
        
        frameFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        
        return frameFile.absolutePath
    }
    
    /**
     * Get video duration
     */
    private fun getVideoDuration(videoUrl: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoUrl)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Inject preview script into WebView
     */
    fun injectPreviewScript(webView: WebView) {
        val previewScript = """
            (function() {
                let currentPreview = null;
                let previewContainer = null;
                let previewTimer = null;
                
                // Create preview container
                function createPreviewContainer() {
                    if (!previewContainer) {
                        previewContainer = document.createElement('div');
                        previewContainer.id = 'astralx-video-preview';
                        previewContainer.style.cssText = `
                            position: absolute;
                            width: 320px;
                            height: 180px;
                            background: #000;
                            border: 2px solid #6C5CE7;
                            border-radius: 8px;
                            display: none;
                            z-index: 10000;
                            overflow: hidden;
                            box-shadow: 0 4px 20px rgba(0,0,0,0.5);
                        `;
                        document.body.appendChild(previewContainer);
                    }
                    return previewContainer;
                }
                
                // Show preview on hover
                function showPreview(videoElement, previewData) {
                    const container = createPreviewContainer();
                    const rect = videoElement.getBoundingClientRect();
                    
                    // Position preview above video
                    container.style.left = rect.left + 'px';
                    container.style.top = (rect.top - 190) + 'px';
                    container.style.display = 'block';
                    
                    // Cycle through frames
                    let frameIndex = 0;
                    const frames = previewData.frames;
                    
                    function updateFrame() {
                        if (frames && frames[frameIndex]) {
                            container.style.backgroundImage = `url(${frames[frameIndex].thumbnailPath})`;
                            container.style.backgroundSize = 'cover';
                            container.style.backgroundPosition = 'center';
                            
                            frameIndex = (frameIndex + 1) % frames.length;
                        }
                    }
                    
                    updateFrame();
                    previewTimer = setInterval(updateFrame, 500); // Change frame every 500ms
                }
                
                // Hide preview
                function hidePreview() {
                    if (previewContainer) {
                        previewContainer.style.display = 'none';
                    }
                    if (previewTimer) {
                        clearInterval(previewTimer);
                        previewTimer = null;
                    }
                }
                
                // Attach hover listeners to video elements
                document.addEventListener('mouseover', function(e) {
                    const video = e.target.closest('video, .video-thumbnail, .thumb');
                    if (video) {
                        const videoUrl = video.src || video.dataset.videoUrl;
                        if (videoUrl) {
                            // Request preview data from Android
                            window.AstralXBridge.requestVideoPreview(videoUrl);
                            
                            // Show preview after delay
                            setTimeout(() => {
                                const previewData = window.AstralXVideoPreview?.[videoUrl];
                                if (previewData) {
                                    showPreview(video, previewData);
                                }
                            }, 300);
                        }
                    }
                });
                
                document.addEventListener('mouseout', function(e) {
                    const video = e.target.closest('video, .video-thumbnail, .thumb');
                    if (video) {
                        hidePreview();
                    }
                });
                
                // Expose preview data receiver
                window.setVideoPreviewData = function(videoUrl, previewData) {
                    if (!window.AstralXVideoPreview) {
                        window.AstralXVideoPreview = {};
                    }
                    window.AstralXVideoPreview[videoUrl] = previewData;
                };
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(previewScript) { result ->
            Timber.d("Preview script injected")
        }
    }
    
    /**
     * Provide preview data to WebView
     */
    fun providePreviewData(webView: WebView, videoUrl: String) {
        val previewData = previewCache[videoUrl]
        if (previewData != null && previewData.isReady) {
            val framesJson = previewData.frames.joinToString(",") { frame ->
                """{ "thumbnailPath": "${frame.thumbnailPath}", "timestamp": ${frame.timestamp} }"""
            }
            
            webView.evaluateJavascript("""
                window.setVideoPreviewData('$videoUrl', {
                    frames: [$framesJson],
                    duration: ${previewData.duration}
                });
            """.trimIndent()) { result ->
                Timber.d("Preview data provided for: $videoUrl")
            }
        }
    }
    
    /**
     * Clear preview cache
     */
    fun clearCache() {
        previewScope.launch {
            previewCache.clear()
            cacheDir.listFiles()?.forEach { it.delete() }
            updatePreviews()
        }
    }
    
    /**
     * Update live data
     */
    private fun updatePreviews() {
        _hoverPreviews.postValue(previewCache.toMap())
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        previewScope.cancel()
        clearCache()
    }
}