package com.astralx.browser.performance

import android.webkit.WebView
import android.webkit.WebSettings
import android.view.View
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import kotlinx.coroutines.*

/**
 * Optimizer for media-heavy websites to ensure smooth performance
 */
@Singleton
class MediaOptimizer @Inject constructor(
    private val scope: CoroutineScope
) {
    
    private var optimizationLevel = OptimizationLevel.STANDARD
    
    enum class OptimizationLevel {
        STANDARD,
        ENHANCED,
        MAXIMUM
    }
    
    /**
     * Optimize WebView for media-heavy sites
     */
    fun optimizeForMediaSite(webView: WebView, url: String = "") {
        Timber.d("Optimizing WebView for media site: $url")
        
        // Determine optimization level based on URL patterns
        optimizationLevel = determineOptimizationLevel(url)
        
        // Apply WebView settings optimizations
        applyWebViewOptimizations(webView)
        
        // Inject performance optimizations
        injectPerformanceOptimizations(webView)
        
        // Setup lazy loading
        setupLazyLoading(webView)
        
        // Enable hardware acceleration
        enableHardwareAcceleration(webView)
    }
    
    /**
     * Determine optimization level based on site
     */
    private fun determineOptimizationLevel(url: String): OptimizationLevel {
        return when {
            url.contains("video") || url.contains("stream") -> OptimizationLevel.MAXIMUM
            url.contains("gallery") || url.contains("photos") -> OptimizationLevel.ENHANCED
            else -> OptimizationLevel.STANDARD
        }
    }
    
    /**
     * Apply WebView settings for optimal media performance
     */
    private fun applyWebViewOptimizations(webView: WebView) {
        webView.settings.apply {
            // Cache settings for media
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
            
            // Media playback settings
            mediaPlaybackRequiresUserGesture = false
            
            // Performance settings
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                offscreenPreRaster = true
            }
            
            // Enable mixed content for media sites
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            
            // Database and DOM storage for video players
            databaseEnabled = true
            domStorageEnabled = true
            
            // Large heap for media
            if (optimizationLevel == OptimizationLevel.MAXIMUM) {
                if (android.os.Build.VERSION.SDK_INT >= 19) {
                    loadsImagesAutomatically = true
                }
            }
            
            // Plugin state for legacy video players
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT < 18) {
                pluginState = WebSettings.PluginState.ON
            }
            
            // Viewport settings
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // Zoom settings
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        
        // Set render priority based on optimization level
        @Suppress("DEPRECATION")
        webView.settings.setRenderPriority(
            when (optimizationLevel) {
                OptimizationLevel.MAXIMUM -> WebSettings.RenderPriority.HIGH
                OptimizationLevel.ENHANCED -> WebSettings.RenderPriority.HIGH
                OptimizationLevel.STANDARD -> WebSettings.RenderPriority.NORMAL
            }
        )
    }
    
    /**
     * Enable hardware acceleration
     */
    private fun enableHardwareAcceleration(webView: WebView) {
        // Enable hardware acceleration for smooth video playback
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Force GPU rendering for media elements
        webView.isHardwareAccelerated = true
    }
    
    /**
     * Inject JavaScript optimizations
     */
    private fun injectPerformanceOptimizations(webView: WebView) {
        val js = """
        (function() {
            // Lazy load images not in viewport
            const imageObserver = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const img = entry.target;
                        if (img.dataset.src) {
                            img.src = img.dataset.src;
                            img.removeAttribute('data-src');
                            imageObserver.unobserve(img);
                        }
                    }
                });
            }, {
                rootMargin: '50px'
            });
            
            // Observe all images with data-src
            document.querySelectorAll('img[data-src]').forEach(img => {
                imageObserver.observe(img);
            });
            
            // Preload video metadata for visible thumbnails
            const videoPreloader = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const element = entry.target;
                        const videoUrl = element.dataset.video || element.href;
                        if (videoUrl && videoUrl.includes('video')) {
                            // Preload video metadata
                            const link = document.createElement('link');
                            link.rel = 'prefetch';
                            link.href = videoUrl;
                            document.head.appendChild(link);
                        }
                    }
                });
            }, { 
                rootMargin: '100px' 
            });
            
            // Observe video links and thumbnails
            document.querySelectorAll('a[href*="video"], [data-video]').forEach(element => {
                videoPreloader.observe(element);
            });
            
            // Optimize video elements
            document.querySelectorAll('video').forEach(video => {
                // Preload metadata only
                video.preload = 'metadata';
                
                // Pause videos not in viewport
                const videoObserver = new IntersectionObserver((entries) => {
                    entries.forEach(entry => {
                        if (!entry.isIntersecting && !video.paused) {
                            video.pause();
                        }
                    });
                });
                videoObserver.observe(video);
            });
            
            // Request idle callback for non-critical tasks
            if ('requestIdleCallback' in window) {
                requestIdleCallback(() => {
                    // Preconnect to common CDNs
                    const cdns = ['cdn.jsdelivr.net', 'cdnjs.cloudflare.com'];
                    cdns.forEach(cdn => {
                        const link = document.createElement('link');
                        link.rel = 'preconnect';
                        link.href = 'https://' + cdn;
                        document.head.appendChild(link);
                    });
                });
            }
            
            // Reduce reflows and repaints
            const style = document.createElement('style');
            style.textContent = `
                img, video { 
                    will-change: transform; 
                    transform: translateZ(0);
                }
                .thumbnail, .preview {
                    contain: layout style paint;
                }
            `;
            document.head.appendChild(style);
        })();
        """
        
        webView.evaluateJavascript(js, null)
    }
    
    /**
     * Setup lazy loading for media elements
     */
    private fun setupLazyLoading(webView: WebView) {
        val lazyLoadJs = """
        (function() {
            // Advanced lazy loading with priority
            class LazyLoader {
                constructor() {
                    this.queue = [];
                    this.loading = new Set();
                    this.maxConcurrent = 3;
                    
                    this.observer = new IntersectionObserver(
                        this.handleIntersection.bind(this),
                        {
                            rootMargin: '${if (optimizationLevel == OptimizationLevel.MAXIMUM) "200px" else "100px"}',
                            threshold: [0, 0.25, 0.5, 0.75, 1]
                        }
                    );
                    
                    this.init();
                }
                
                init() {
                    // Find all lazy loadable elements
                    const elements = document.querySelectorAll(
                        'img[data-src], img[data-lazy], video[data-src], iframe[data-src]'
                    );
                    
                    elements.forEach(el => this.observer.observe(el));
                }
                
                handleIntersection(entries) {
                    entries.forEach(entry => {
                        if (entry.isIntersecting) {
                            // Priority based on intersection ratio
                            const priority = entry.intersectionRatio;
                            this.queue.push({
                                element: entry.target,
                                priority: priority
                            });
                            this.observer.unobserve(entry.target);
                        }
                    });
                    
                    // Sort by priority
                    this.queue.sort((a, b) => b.priority - a.priority);
                    this.processQueue();
                }
                
                processQueue() {
                    while (this.loading.size < this.maxConcurrent && this.queue.length > 0) {
                        const item = this.queue.shift();
                        this.loadElement(item.element);
                    }
                }
                
                loadElement(element) {
                    const src = element.dataset.src || element.dataset.lazy;
                    if (!src) return;
                    
                    this.loading.add(element);
                    
                    if (element.tagName === 'IMG') {
                        const img = new Image();
                        img.onload = () => {
                            element.src = src;
                            element.removeAttribute('data-src');
                            element.removeAttribute('data-lazy');
                            this.loading.delete(element);
                            this.processQueue();
                        };
                        img.onerror = () => {
                            this.loading.delete(element);
                            this.processQueue();
                        };
                        img.src = src;
                    } else {
                        element.src = src;
                        element.removeAttribute('data-src');
                        this.loading.delete(element);
                        this.processQueue();
                    }
                }
            }
            
            // Initialize lazy loader
            new LazyLoader();
        })();
        """
        
        webView.evaluateJavascript(lazyLoadJs, null)
    }
    
    /**
     * Monitor and adjust optimization based on performance
     */
    fun monitorPerformance(webView: WebView) {
        scope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                
                webView.evaluateJavascript("""
                    (function() {
                        const fps = performance.now();
                        const memory = performance.memory ? 
                            performance.memory.usedJSHeapSize / 1048576 : 0;
                        return {
                            fps: fps,
                            memory: memory,
                            videoCount: document.querySelectorAll('video').length,
                            imageCount: document.querySelectorAll('img').length
                        };
                    })();
                """) { result ->
                    // Adjust optimization based on performance metrics
                    Timber.d("Performance metrics: $result")
                }
            }
        }
    }
    
    /**
     * Cleanup optimizations
     */
    fun cleanup() {
        scope.coroutineContext.cancelChildren()
    }
}