package com.astralx.browser.media

import android.webkit.WebView
import android.webkit.JavascriptInterface
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import com.astralx.browser.core.download.AdvancedDownloadEngine
import timber.log.Timber
import org.json.JSONObject

/**
 * Universal video player that enhances video playback on any website
 */
@Singleton
class UniversalVideoPlayer @Inject constructor(
    private val downloadManager: AdvancedDownloadEngine,
    private val scope: CoroutineScope
) {
    
    private var webView: WebView? = null
    
    /**
     * Initialize the universal video player for a WebView
     */
    fun initialize(webView: WebView) {
        this.webView = webView
        injectVideoEnhancer()
        webView.addJavascriptInterface(VideoInterface(), "AndroidVideo")
    }
    
    /**
     * Inject JavaScript to enhance all video players universally
     */
    private fun injectVideoEnhancer() {
        val js = """
        (function() {
            // Universal video detector
            const videoDetector = new MutationObserver((mutations) => {
                // Find ALL video elements (including in iframes)
                const videos = document.querySelectorAll('video, iframe, embed, object');
                videos.forEach(enhanceVideo);
            });
            
            function enhanceVideo(element) {
                if (element.enhanced) return;
                element.enhanced = true;
                
                // Detect player type
                const playerType = detectPlayerType(element);
                const videoData = {
                    url: element.src || element.dataset.src || extractVideoUrl(element),
                    type: playerType,
                    bounds: element.getBoundingClientRect()
                };
                
                AndroidVideo.onVideoDetected(
                    JSON.stringify(videoData)
                );
                
                // Add universal controls
                addUniversalControls(element);
                
                // Add gesture support
                addGestureControls(element);
            }
            
            function detectPlayerType(element) {
                // Check for common player signatures
                if (element.id?.includes('player')) return 'custom';
                if (element.className?.includes('jw-video')) return 'jwplayer';
                if (element.className?.includes('video-js')) return 'videojs';
                if (element.closest('.html5-video-player')) return 'youtube';
                if (element.tagName === 'IFRAME') return 'iframe';
                return 'html5';
            }
            
            function extractVideoUrl(element) {
                // Try multiple extraction methods
                return element.src ||
                       element.dataset.src ||
                       element.querySelector('source')?.src ||
                       // Extract from JavaScript objects
                       window.playerInstance?.getSource() ||
                       // Extract from flashvars
                       element.querySelector('param[name="flashvars"]')?.value?.match(/video_url=([^&]+)/)?.[1];
            }
            
            function addUniversalControls(video) {
                // Create floating control overlay
                const controls = document.createElement('div');
                controls.className = 'astralx-controls';
                controls.style.cssText = `
                    position: absolute;
                    bottom: 10px;
                    right: 10px;
                    background: rgba(0,0,0,0.7);
                    padding: 5px;
                    border-radius: 5px;
                    display: flex;
                    gap: 5px;
                    z-index: 99999;
                `;
                
                controls.innerHTML = `
                    <button class="ax-play" style="background:none;border:none;color:white;cursor:pointer;">⏯</button>
                    <button class="ax-pip" style="background:none;border:none;color:white;cursor:pointer;">📱</button>
                    <button class="ax-speed" style="background:none;border:none;color:white;cursor:pointer;">⚡</button>
                    <button class="ax-quality" style="background:none;border:none;color:white;cursor:pointer;">🎯</button>
                    <button class="ax-download" style="background:none;border:none;color:white;cursor:pointer;">💾</button>
                    <button class="ax-cast" style="background:none;border:none;color:white;cursor:pointer;">📺</button>
                `;
                
                // Position near video
                if (video.parentElement) {
                    video.parentElement.style.position = 'relative';
                    video.parentElement.appendChild(controls);
                }
                
                // Control handlers
                controls.querySelector('.ax-play').onclick = () => {
                    if (video.paused) {
                        video.play();
                    } else {
                        video.pause();
                    }
                };
                
                controls.querySelector('.ax-pip').onclick = () => {
                    if (video.requestPictureInPicture) {
                        video.requestPictureInPicture();
                    }
                };
                
                controls.querySelector('.ax-speed').onclick = () => showSpeedMenu(video);
                controls.querySelector('.ax-download').onclick = () => {
                    const url = video.src || video.currentSrc;
                    if (url) {
                        AndroidVideo.downloadVideo(url);
                    }
                };
            }
            
            function addGestureControls(video) {
                let startX = 0;
                let startY = 0;
                let startTime = 0;
                let startVolume = 0;
                let startBrightness = 0;
                
                video.addEventListener('touchstart', (e) => {
                    startX = e.touches[0].clientX;
                    startY = e.touches[0].clientY;
                    startTime = video.currentTime;
                    startVolume = video.volume;
                });
                
                video.addEventListener('touchmove', (e) => {
                    const deltaX = e.touches[0].clientX - startX;
                    const deltaY = e.touches[0].clientY - startY;
                    
                    // Horizontal swipe for seek
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        const seekDelta = (deltaX / video.clientWidth) * 30; // 30 seconds max
                        video.currentTime = Math.max(0, Math.min(video.duration, startTime + seekDelta));
                    }
                    // Vertical swipe for volume/brightness
                    else {
                        const side = startX < video.clientWidth / 2 ? 'left' : 'right';
                        const delta = -deltaY / video.clientHeight;
                        
                        if (side === 'left') {
                            // Brightness control
                            AndroidVideo.adjustBrightness(delta);
                        } else {
                            // Volume control
                            video.volume = Math.max(0, Math.min(1, startVolume + delta));
                        }
                    }
                });
            }
            
            function showSpeedMenu(video) {
                const speeds = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4];
                const menu = document.createElement('div');
                menu.style.cssText = `
                    position: absolute;
                    bottom: 50px;
                    right: 10px;
                    background: rgba(0,0,0,0.9);
                    padding: 10px;
                    border-radius: 5px;
                    z-index: 100000;
                `;
                
                speeds.forEach(speed => {
                    const btn = document.createElement('button');
                    btn.textContent = speed + 'x';
                    btn.style.cssText = 'display:block;width:100%;padding:5px;margin:2px;background:#333;color:white;border:none;cursor:pointer;';
                    btn.onclick = () => {
                        video.playbackRate = speed;
                        menu.remove();
                    };
                    menu.appendChild(btn);
                });
                
                video.parentElement.appendChild(menu);
                setTimeout(() => menu.remove(), 5000);
            }
            
            // Start observing
            videoDetector.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            // Initial scan
            document.querySelectorAll('video, iframe').forEach(enhanceVideo);
        })();
        """
        
        webView?.evaluateJavascript(js, null)
    }
    
    /**
     * JavaScript interface for video interactions
     */
    inner class VideoInterface {
        @JavascriptInterface
        fun onVideoDetected(videoDataJson: String) {
            try {
                val videoData = JSONObject(videoDataJson)
                val url = videoData.optString("url", "")
                val playerType = videoData.optString("type", "unknown")
                
                Timber.d("Video detected: $playerType - $url")
                
                // Handle video detection
                scope.launch {
                    processVideo(url, playerType)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing video detection")
            }
        }
        
        @JavascriptInterface
        fun downloadVideo(url: String) {
            if (url.isNotEmpty()) {
                scope.launch {
                    val filename = detectFilename(url)
                    downloadManager.downloadFile(url, filename)
                }
            }
        }
        
        @JavascriptInterface
        fun adjustBrightness(delta: Float) {
            // Adjust screen brightness
            scope.launch {
                adjustScreenBrightness(delta)
            }
        }
    }
    
    /**
     * Process detected video
     */
    private suspend fun processVideo(url: String, playerType: String) {
        // Log video detection for analytics
        Timber.d("Processing video: $playerType at $url")
    }
    
    /**
     * Detect filename from URL
     */
    private fun detectFilename(url: String): String {
        return try {
            val urlPath = url.substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
            
            if (urlPath.contains('.')) {
                urlPath
            } else {
                "video_${System.currentTimeMillis()}.mp4"
            }
        } catch (e: Exception) {
            "video_${System.currentTimeMillis()}.mp4"
        }
    }
    
    /**
     * Adjust screen brightness
     */
    private fun adjustScreenBrightness(delta: Float) {
        // This would adjust the actual screen brightness
        // Implementation depends on activity context
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        webView = null
    }
}