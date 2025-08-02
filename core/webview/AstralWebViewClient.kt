package com.astralx.browser.core.webview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import android.util.Log
import com.astralx.browser.downloads.AdvancedDownloadEngine
import com.astralx.browser.downloads.DownloadService

class AstralWebViewClient(
    private val context: Context,
    private val downloadEngine: AdvancedDownloadEngine
) : WebViewClient() {
    
    companion object {
        private const val TAG = "AstralWebViewClient"
    }
    
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        
        // Handle special URLs for adult content
        when {
            isVideoUrl(url) -> {
                // Launch video player instead of loading in WebView
                handleVideoUrl(view, url)
                return true
            }
            isDownloadUrl(url) -> {
                // Trigger download
                handleDownloadUrl(view, url)
                return true
            }
            else -> {
                // Load normally
                return false
            }
        }
    }
    
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        
        // Inject adult content optimizations
        if (isAdultContentSite(url)) {
            injectAdultContentOptimizations(view)
        }
    }
    
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        
        // Inject video detection script
        view?.let { webView ->
            injectVideoDetectionScript(webView)
        }
    }
    
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
        
        // Use enhanced privacy manager for request interception
        val context = view?.context
        if (context != null) {
            val privacyManager = com.astralx.browser.core.privacy.PrivacyManager(context)
            val blockedResponse = privacyManager.interceptRequest(request)
            if (blockedResponse != null) {
                return blockedResponse
            }
        }
        
        return super.shouldInterceptRequest(view, request)
    }
    
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // For adult content sites, we might need to proceed despite SSL errors
        // But this should be configurable for security
        handler?.proceed() // Proceed with SSL errors (configurable)
    }
    
    private fun isVideoUrl(url: String): Boolean {
        val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov")
        return videoExtensions.any { url.contains(it, ignoreCase = true) }
    }
    
    private fun isDownloadUrl(url: String): Boolean {
        // Check for download indicators
        return url.contains("download", ignoreCase = true) ||
               url.contains("dl=", ignoreCase = true)
    }
    
    private fun isAdultContentSite(url: String?): Boolean {
        if (url == null) return false
        
        val adultDomains = listOf(
            "pornhub.com", "xvideos.com", "redtube.com", "youporn.com",
            "xnxx.com", "xhamster.com", "tube8.com", "spankbang.com"
        )
        
        return adultDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }
    
    private fun handleVideoUrl(view: WebView?, url: String) {
        // Launch video player with AI subtitles
        view?.context?.let { context ->
            val intent = android.content.Intent(context, 
                com.astralx.browser.video.player.AstralVideoPlayerActivity::class.java).apply {
                putExtra("video_url", url)
                putExtra("adult_content", isAdultContentSite(url))
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
    
    private fun handleDownloadUrl(view: WebView?, url: String) {
        // Use the advanced download engine
        val currentUrl = view?.url ?: ""
        val isAdultContent = isAdultContentSite(currentUrl)
        val title = view?.title ?: "Video"
        
        DownloadService.startDownload(
            context = context,
            url = url,
            title = title,
            isAdultContent = isAdultContent
        )
        
        Log.d(TAG, "Started download for: $url")
    }
    
    private fun injectAdultContentOptimizations(view: WebView?) {
        view?.evaluateJavascript("""
            (function() {
                // Remove age verification overlays
                const ageOverlays = document.querySelectorAll('[class*="age"], [id*="age"], [class*="verify"]');
                ageOverlays.forEach(overlay => overlay.remove());
                
                // Auto-accept cookies for better experience
                const cookieButtons = document.querySelectorAll('[class*="cookie"], [id*="cookie"]');
                cookieButtons.forEach(button => {
                    if (button.textContent.includes('Accept') || button.textContent.includes('OK')) {
                        button.click();
                    }
                });
                
                // Optimize video elements
                const videos = document.querySelectorAll('video');
                videos.forEach(video => {
                    video.preload = 'metadata';
                    video.setAttribute('playsinline', '');
                });
            })();
        """.trimIndent(), null)
    }
    
    private fun injectVideoDetectionScript(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                // Enhanced video detection for adult content sites
                function detectAllVideos() {
                    const videos = [];
                    
                    // HTML5 videos
                    document.querySelectorAll('video').forEach(video => {
                        const src = video.src || video.currentSrc;
                        if (src && !src.startsWith('blob:')) {
                            videos.push({
                                type: 'html5',
                                url: src,
                                title: video.title || document.title
                            });
                        }
                    });
                    
                    // Source elements in videos
                    document.querySelectorAll('video source').forEach(source => {
                        if (source.src) {
                            videos.push({
                                type: 'html5_source',
                                url: source.src,
                                title: document.title
                            });
                        }
                    });
                    
                    // HLS/DASH streams
                    const scripts = document.querySelectorAll('script');
                    scripts.forEach(script => {
                        const content = script.textContent || script.innerText;
                        const m3u8Match = content.match(/["']([^"']*\.m3u8[^"']*)["']/g);
                        const mpegDashMatch = content.match(/["']([^"']*\.mpd[^"']*)["']/g);
                        
                        if (m3u8Match) {
                            m3u8Match.forEach(match => {
                                const url = match.replace(/['"]/g, '');
                                videos.push({
                                    type: 'hls',
                                    url: url,
                                    title: document.title
                                });
                            });
                        }
                        
                        if (mpegDashMatch) {
                            mpegDashMatch.forEach(match => {
                                const url = match.replace(/['"]/g, '');
                                videos.push({
                                    type: 'dash',
                                    url: url,
                                    title: document.title
                                });
                            });
                        }
                    });
                    
                    return videos;
                }
                
                // Add download buttons to videos
                function addDownloadButtons() {
                    document.querySelectorAll('video').forEach((video, index) => {
                        if (video.dataset.astralDownloadAdded) return;
                        
                        const src = video.src || video.currentSrc;
                        if (!src || src.startsWith('blob:')) return;
                        
                        const button = document.createElement('button');
                        button.innerHTML = '⬇️ Download';
                        button.style.cssText = `
                            position: absolute;
                            top: 10px;
                            right: 10px;
                            z-index: 10000;
                            background: rgba(0,0,0,0.7);
                            color: white;
                            border: none;
                            padding: 8px 12px;
                            border-radius: 4px;
                            cursor: pointer;
                            font-size: 12px;
                        `;
                        
                        button.onclick = () => {
                            if (window.AstralVideoDetector) {
                                AstralVideoDetector.downloadVideo(src, document.title);
                            }
                        };
                        
                        const container = video.parentElement;
                        if (container) {
                            container.style.position = 'relative';
                            container.appendChild(button);
                        }
                        
                        video.dataset.astralDownloadAdded = 'true';
                    });
                }
                
                // Detect videos immediately
                const detectedVideos = detectAllVideos();
                if (detectedVideos.length > 0) {
                    console.log('Videos detected:', detectedVideos);
                    if (window.AstralVideoDetector) {
                        AstralVideoDetector.onVideoDetected(JSON.stringify(detectedVideos));
                    }
                    
                    // Add download buttons
                    setTimeout(addDownloadButtons, 1000);
                }
                
                // Monitor for dynamic video loading
                const observer = new MutationObserver(() => {
                    const newVideos = detectAllVideos();
                    if (newVideos.length > 0) {
                        if (window.AstralVideoDetector) {
                            AstralVideoDetector.onVideoDetected(JSON.stringify(newVideos));
                        }
                    }
                });
                
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
            })();
        """.trimIndent(), null)
    }
    
    private fun shouldBlockRequest(url: String): Boolean {
        val blockedDomains = listOf(
            "google-analytics.com",
            "googletagmanager.com",
            "doubleclick.net",
            "googlesyndication.com",
            "facebook.com/tr"
        )
        
        return blockedDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }
}