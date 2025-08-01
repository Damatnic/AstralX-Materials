package com.astralx.browser.presentation.browser

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.astralx.browser.domain.privacy.TrackingProtection
import com.astralx.browser.domain.privacy.CookieManager
import com.astralx.browser.domain.privacy.HttpsOnlyMode
import com.astralx.browser.domain.video.VideoDetector
import com.astralx.browser.privacy.PrivacyShield
import com.astralx.browser.performance.MediaOptimizer
import timber.log.Timber

/**
 * Enhanced WebViewClient with privacy and media optimization features
 */
class EnhancedWebViewClient(
    private val context: Context,
    private val privacyShield: PrivacyShield,
    private val mediaOptimizer: MediaOptimizer,
    private val trackingProtection: TrackingProtection = TrackingProtection(),
    private val cookieManager: CookieManager = CookieManager(context),
    private val httpsOnlyMode: HttpsOnlyMode = HttpsOnlyMode(),
    private val videoDetector: VideoDetector = VideoDetector()
) : WebViewClient() {

    private var pageStartTime = 0L
    
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        pageStartTime = System.currentTimeMillis()
        
        url?.let {
            Timber.d("Page started: $url")
            
            // Check if privacy should be activated
            privacyShield.checkAndActivatePrivacy(url)
            
            // Optimize for media sites
            if (isMediaSite(url)) {
                view?.let { webView ->
                    mediaOptimizer.optimizeForMediaSite(webView, url)
                }
            }
        }
    }
    
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        
        val loadTime = System.currentTimeMillis() - pageStartTime
        Timber.d("Page loaded in ${loadTime}ms: $url")
        
        // Inject any additional optimizations after page load
        view?.let { webView ->
            if (url != null && isMediaSite(url)) {
                injectMediaEnhancements(webView)
            }
        }
    }
    
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        val webView = view ?: return null

        try {
            // Apply tracking protection
            if (trackingProtection.shouldBlockRequest(url, webView.url ?: "")) {
                Timber.d("Blocked tracker: $url")
                return WebResourceResponse("text/plain", "utf-8", null)
            }

            // Apply cookie management based on privacy mode
            if (privacyShield.isPrivacyActive() && cookieManager.shouldBlockCookies(url)) {
                Timber.d("Blocked cookies for: $url")
                // Return empty response to block cookies
                return WebResourceResponse("text/plain", "utf-8", null)
            }

            // Block ads and trackers in privacy mode
            if (privacyShield.isPrivacyActive() && isAdOrTracker(url)) {
                Timber.d("Blocked ad/tracker: $url")
                return WebResourceResponse("text/plain", "utf-8", null)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error intercepting request: $url")
        }

        return null
    }
    
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        
        // Apply HTTPS-only mode
        if (httpsOnlyMode.shouldUpgradeToHttps(url)) {
            val httpsUrl = url.replace("http://", "https://")
            view?.loadUrl(httpsUrl)
            return true
        }
        
        // Check for privacy activation on navigation
        privacyShield.checkAndActivatePrivacy(url)
        
        return false
    }
    
    /**
     * Check if URL is likely a media site
     */
    private fun isMediaSite(url: String): Boolean {
        val mediaKeywords = listOf(
            "video", "stream", "watch", "play",
            "media", "tv", "movie", "clip",
            "youtube", "vimeo", "dailymotion"
        )
        
        val lowercaseUrl = url.lowercase()
        return mediaKeywords.any { lowercaseUrl.contains(it) }
    }
    
    /**
     * Check if URL is likely an ad or tracker
     */
    private fun isAdOrTracker(url: String): Boolean {
        val adPatterns = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "facebook.com/tr",
            "amazon-adsystem.com", "adsystem", "adserver", "adsrvr",
            "tracking", "analytics", "metrics", "telemetry"
        )
        
        val lowercaseUrl = url.lowercase()
        return adPatterns.any { lowercaseUrl.contains(it) }
    }
    
    /**
     * Inject additional media enhancements after page load
     */
    private fun injectMediaEnhancements(webView: WebView) {
        val js = """
        (function() {
            // Auto-play videos when visible
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    const video = entry.target;
                    if (entry.isIntersecting && video.paused) {
                        video.play().catch(() => {});
                    } else if (!entry.isIntersecting && !video.paused) {
                        video.pause();
                    }
                });
            });
            
            // Observe all videos
            document.querySelectorAll('video').forEach(video => {
                observer.observe(video);
            });
            
            // Enhance image loading
            document.querySelectorAll('img').forEach(img => {
                if (!img.loading) {
                    img.loading = 'lazy';
                }
            });
        })();
        """
        
        webView.evaluateJavascript(js, null)
    }
}