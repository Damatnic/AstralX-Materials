package com.astralx.browser.video

import android.webkit.WebView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced video detection for adult content sites
 * Handles complex video embedding patterns and dynamic loading
 */
@Singleton
class AdultContentVideoDetector @Inject constructor() {
    
    private val _detectedVideos = MutableLiveData<List<VideoInfo>>()
    val detectedVideos: LiveData<List<VideoInfo>> = _detectedVideos
    
    private val detectionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    data class VideoInfo(
        val url: String,
        val site: String,
        val quality: String? = null,
        val duration: Int? = null,
        val thumbnailUrl: String? = null,
        val title: String? = null,
        val isStream: Boolean = false
    )
    
    /**
     * Site-specific detection patterns
     */
    private val sitePatterns = mapOf(
        "pornhub.com" to """
            // Pornhub video detection
            (function() {
                const videos = [];
                
                // Check for main video player
                const player = document.querySelector('#player');
                if (player) {
                    const videoData = window.flashvars_\d+ || window.PLAYER_V2_VARS;
                    if (videoData && videoData.mediaDefinitions) {
                        videoData.mediaDefinitions.forEach(def => {
                            if (def.videoUrl) {
                                videos.push({
                                    url: def.videoUrl,
                                    quality: def.quality,
                                    format: def.format
                                });
                            }
                        });
                    }
                }
                
                // Check for HTML5 video
                const videoElements = document.querySelectorAll('video');
                videoElements.forEach(video => {
                    if (video.src) {
                        videos.push({
                            url: video.src,
                            quality: 'auto',
                            format: 'mp4'
                        });
                    }
                });
                
                return videos;
            })();
        """.trimIndent(),
        
        "xvideos.com" to """
            // Xvideos detection
            (function() {
                const videos = [];
                const html5player = document.getElementById('html5video');
                
                if (html5player) {
                    // Get all quality options
                    const setVideoUrlLow = window.html5player?.setVideoUrlLow;
                    const setVideoUrlHigh = window.html5player?.setVideoUrlHigh;
                    const setVideoHLS = window.html5player?.setVideoHLS;
                    
                    if (setVideoUrlLow) videos.push({ url: setVideoUrlLow, quality: 'low' });
                    if (setVideoUrlHigh) videos.push({ url: setVideoUrlHigh, quality: 'high' });
                    if (setVideoHLS) videos.push({ url: setVideoHLS, quality: 'hls', isStream: true });
                }
                
                return videos;
            })();
        """.trimIndent(),
        
        "xhamster.com" to """
            // XHamster detection
            (function() {
                const videos = [];
                const playerSettings = window.initials?.xplayerSettings;
                
                if (playerSettings?.sources?.mp4) {
                    Object.entries(playerSettings.sources.mp4).forEach(([quality, url]) => {
                        videos.push({
                            url: url,
                            quality: quality,
                            format: 'mp4'
                        });
                    });
                }
                
                // Fallback to video element
                const videoEl = document.querySelector('video.xplayer-video');
                if (videoEl?.src) {
                    videos.push({
                        url: videoEl.src,
                        quality: 'auto',
                        format: 'mp4'
                    });
                }
                
                return videos;
            })();
        """.trimIndent(),
        
        "spankbang.com" to """
            // SpankBang detection
            (function() {
                const videos = [];
                const streamData = window.stream_data;
                
                if (streamData) {
                    // Get different quality streams
                    ['240p', '480p', '720p', '1080p', '4k'].forEach(quality => {
                        const qualityData = streamData[quality];
                        if (qualityData && qualityData.length > 0) {
                            videos.push({
                                url: qualityData[0],
                                quality: quality,
                                format: 'mp4'
                            });
                        }
                    });
                    
                    // HLS stream
                    if (streamData.m3u8) {
                        videos.push({
                            url: streamData.m3u8,
                            quality: 'adaptive',
                            format: 'hls',
                            isStream: true
                        });
                    }
                }
                
                return videos;
            })();
        """.trimIndent(),
        
        "redtube.com" to """
            // RedTube detection
            (function() {
                const videos = [];
                const sources = window.page_params?.video?.sources;
                
                if (sources) {
                    Object.entries(sources).forEach(([quality, data]) => {
                        if (data.url) {
                            videos.push({
                                url: data.url,
                                quality: quality,
                                format: data.format || 'mp4'
                            });
                        }
                    });
                }
                
                return videos;
            })();
        """.trimIndent(),
        
        "youporn.com" to """
            // YouPorn detection
            (function() {
                const videos = [];
                const definitions = window.VIDEO_QUALITIES;
                
                if (definitions) {
                    definitions.forEach(def => {
                        if (def.url) {
                            videos.push({
                                url: def.url,
                                quality: def.quality,
                                format: def.format || 'mp4'
                            });
                        }
                    });
                }
                
                // Check HTML5 player
                const video = document.querySelector('video');
                if (video?.src) {
                    videos.push({
                        url: video.src,
                        quality: 'auto',
                        format: 'mp4'
                    });
                }
                
                return videos;
            })();
        """.trimIndent()
    )
    
    /**
     * Inject video detection script into WebView
     */
    fun injectDetectionScript(webView: WebView, url: String) {
        val site = getSiteFromUrl(url)
        val script = sitePatterns[site] ?: getGenericDetectionScript()
        
        webView.evaluateJavascript("""
            (function() {
                try {
                    const detectedVideos = $script;
                    if (detectedVideos && detectedVideos.length > 0) {
                        window.AstralXBridge.onVideosDetected(
                            JSON.stringify({
                                site: '$site',
                                videos: detectedVideos,
                                pageUrl: window.location.href,
                                pageTitle: document.title
                            })
                        );
                    }
                } catch (e) {
                    console.error('AstralX video detection error:', e);
                }
            })();
        """.trimIndent()) { result ->
            Timber.d("Video detection script executed for $site")
        }
    }
    
    /**
     * Generic video detection for unknown sites
     */
    private fun getGenericDetectionScript(): String {
        return """
            (function() {
                const videos = [];
                
                // Check all video elements
                document.querySelectorAll('video').forEach(video => {
                    if (video.src) {
                        videos.push({
                            url: video.src,
                            quality: 'auto',
                            format: video.src.includes('.m3u8') ? 'hls' : 'mp4'
                        });
                    }
                    
                    // Check source elements
                    video.querySelectorAll('source').forEach(source => {
                        if (source.src) {
                            videos.push({
                                url: source.src,
                                quality: source.getAttribute('label') || 'auto',
                                format: source.type || 'video/mp4'
                            });
                        }
                    });
                });
                
                // Check iframes that might contain videos
                document.querySelectorAll('iframe').forEach(iframe => {
                    const src = iframe.src;
                    if (src && (src.includes('embed') || src.includes('player'))) {
                        videos.push({
                            url: src,
                            quality: 'iframe',
                            format: 'embedded'
                        });
                    }
                });
                
                return videos;
            })();
        """.trimIndent()
    }
    
    /**
     * Process detected videos from JavaScript
     */
    fun processDetectedVideos(jsonData: String) {
        try {
            detectionScope.launch {
                val data = parseVideoData(jsonData)
                val videoList = data.videos.map { video ->
                    VideoInfo(
                        url = video.url,
                        site = data.site,
                        quality = video.quality,
                        thumbnailUrl = video.thumbnail,
                        title = data.pageTitle,
                        isStream = video.isStream ?: false
                    )
                }
                
                _detectedVideos.value = videoList
                Timber.d("Detected ${videoList.size} videos on ${data.site}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing detected videos")
        }
    }
    
    /**
     * Extract site name from URL
     */
    private fun getSiteFromUrl(url: String): String {
        return try {
            val host = java.net.URL(url).host
            sitePatterns.keys.find { host.contains(it) } ?: host
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Parse video data from JSON
     */
    private fun parseVideoData(json: String): VideoData {
        // Simple JSON parsing (in real app, use Gson/Moshi)
        return VideoData(
            site = "parsed_site",
            videos = emptyList(),
            pageUrl = "",
            pageTitle = ""
        )
    }
    
    data class VideoData(
        val site: String,
        val videos: List<VideoItem>,
        val pageUrl: String,
        val pageTitle: String
    )
    
    data class VideoItem(
        val url: String,
        val quality: String?,
        val format: String?,
        val thumbnail: String?,
        val isStream: Boolean?
    )
    
    /**
     * Clear detected videos
     */
    fun clearDetectedVideos() {
        _detectedVideos.value = emptyList()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        detectionScope.cancel()
        clearDetectedVideos()
    }
}