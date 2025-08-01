package com.astralx.browser.media

import android.webkit.WebView
import android.webkit.JavascriptInterface
import android.util.LruCache
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import org.json.JSONObject

/**
 * Data class for preview information
 */
data class PreviewData(
    val sprite: String? = null,
    val gif: String? = null,
    val videoUrl: String? = null,
    val frames: List<String> = emptyList(),
    val duration: Int = 0
)

/**
 * Engine for detecting and displaying thumbnail previews on any website
 */
@Singleton
class ThumbnailPreviewEngine @Inject constructor(
    private val scope: CoroutineScope
) {
    
    private val previewCache = LruCache<String, PreviewData>(100)
    private var hoverJob: Job? = null
    private var webView: WebView? = null
    
    /**
     * Initialize the thumbnail preview engine
     */
    fun initialize(webView: WebView) {
        this.webView = webView
        injectThumbnailEnhancer()
        webView.addJavascriptInterface(ThumbnailInterface(), "AndroidThumbnail")
    }
    
    /**
     * Inject JavaScript to detect and enhance thumbnails
     */
    private fun injectThumbnailEnhancer() {
        val js = """
        (function() {
            // Universal thumbnail detector
            const thumbDetector = new MutationObserver((mutations) => {
                findAllThumbnails();
            });
            
            let thumbIdCounter = 0;
            
            function generateId(element) {
                if (!element.id) {
                    element.id = 'astralx-thumb-' + (++thumbIdCounter);
                }
                return element.id;
            }
            
            function findAllThumbnails() {
                // Multiple detection strategies
                const thumbnails = new Set();
                
                // Strategy 1: Common thumbnail classes
                document.querySelectorAll('[class*="thumb"], [class*="preview"], [class*="poster"]').forEach(t => thumbnails.add(t));
                
                // Strategy 2: Images near video links
                document.querySelectorAll('a[href*="video"], a[href*="watch"]').forEach(link => {
                    link.querySelectorAll('img').forEach(img => thumbnails.add(img));
                });
                
                // Strategy 3: Data attributes
                document.querySelectorAll('[data-preview], [data-trailer], [data-sprite]').forEach(t => thumbnails.add(t));
                
                // Strategy 4: Specific site patterns
                document.querySelectorAll('.videoPreviewBg, .previewContainer, .gif-preview').forEach(t => thumbnails.add(t));
                
                // Strategy 5: Images with specific dimensions (likely thumbnails)
                document.querySelectorAll('img').forEach(img => {
                    const ratio = img.naturalWidth / img.naturalHeight;
                    if (ratio > 1.3 && ratio < 1.8) { // 16:9-ish ratio
                        thumbnails.add(img);
                    }
                });
                
                thumbnails.forEach(enhanceThumbnail);
            }
            
            function enhanceThumbnail(thumb) {
                if (thumb.enhanced) return;
                thumb.enhanced = true;
                
                // Extract preview data
                const previewData = extractPreviewData(thumb);
                if (!previewData || Object.keys(previewData).length === 0) return;
                
                const thumbId = generateId(thumb);
                
                // Desktop: hover
                thumb.addEventListener('mouseenter', () => {
                    AndroidThumbnail.onHoverStart(thumbId, JSON.stringify(previewData));
                    showPreview(thumb, previewData);
                });
                
                thumb.addEventListener('mouseleave', () => {
                    AndroidThumbnail.onHoverEnd(thumbId);
                    hidePreview(thumb);
                });
                
                // Mobile: long press
                let pressTimer;
                thumb.addEventListener('touchstart', (e) => {
                    pressTimer = setTimeout(() => {
                        AndroidThumbnail.onLongPress(thumbId, JSON.stringify(previewData));
                        showPreview(thumb, previewData);
                        e.preventDefault();
                    }, 500);
                });
                
                thumb.addEventListener('touchend', () => {
                    clearTimeout(pressTimer);
                    hidePreview(thumb);
                });
                
                thumb.addEventListener('touchmove', () => {
                    clearTimeout(pressTimer);
                });
            }
            
            function extractPreviewData(thumb) {
                // Try multiple extraction methods
                const data = {};
                
                // Method 1: Data attributes
                data.sprite = thumb.dataset.sprite || thumb.dataset.preview || thumb.dataset.mediabook;
                data.gif = thumb.dataset.gif || thumb.dataset.animatedPreview;
                
                // Method 2: Parse from style/background
                const bgImage = window.getComputedStyle(thumb).backgroundImage;
                if (bgImage && bgImage !== 'none') {
                    const match = bgImage.match(/url\(["']?(.+?)["']?\)/);
                    if (match && match[1].includes('sprite')) {
                        data.sprite = match[1];
                    }
                }
                
                // Method 3: Find related video URL
                const link = thumb.closest('a');
                if (link) {
                    data.videoUrl = link.href;
                    // Try to construct preview URL from video URL
                    data.computed = computePreviewUrl(link.href);
                }
                
                // Method 4: Extract from nearby script tags
                const container = thumb.closest('.video-item, .video-thumb, .thumb-container');
                if (container) {
                    const scriptData = container.querySelector('script')?.textContent;
                    if (scriptData) {
                        data.fromScript = extractFromScript(scriptData);
                    }
                }
                
                // Method 5: Check for hover preview data
                const hoverData = thumb.getAttribute('onmouseover');
                if (hoverData) {
                    data.hoverPreview = extractFromHoverEvent(hoverData);
                }
                
                return data;
            }
            
            function computePreviewUrl(videoUrl) {
                // Common patterns for preview URLs
                if (videoUrl.includes('/video/')) {
                    const videoId = videoUrl.match(/\/video\/([^\/]+)/)?.[1];
                    if (videoId) {
                        return {
                            sprite: videoUrl.replace('/video/', '/previews/') + '/sprite.jpg',
                            gif: videoUrl.replace('/video/', '/previews/') + '/preview.gif'
                        };
                    }
                }
                return null;
            }
            
            function extractFromScript(scriptContent) {
                // Look for preview URLs in script content
                const spriteMatch = scriptContent.match(/sprite['"]:['"]([^'"]+)/);
                const gifMatch = scriptContent.match(/preview['"]:['"]([^'"]+)/);
                
                return {
                    sprite: spriteMatch?.[1],
                    gif: gifMatch?.[1]
                };
            }
            
            function extractFromHoverEvent(hoverData) {
                // Extract preview info from hover events
                const urlMatch = hoverData.match(/['"]([^'"]+\.(gif|webp|mp4))/);
                return urlMatch?.[1];
            }
            
            function showPreview(thumb, previewData) {
                const preview = document.createElement('div');
                preview.className = 'astralx-preview';
                preview.style.cssText = `
                    position: absolute;
                    z-index: 99999;
                    border: 2px solid #333;
                    border-radius: 4px;
                    overflow: hidden;
                    background: #000;
                    box-shadow: 0 4px 8px rgba(0,0,0,0.3);
                `;
                
                const rect = thumb.getBoundingClientRect();
                preview.style.left = rect.left + 'px';
                preview.style.top = rect.top + 'px';
                preview.style.width = rect.width + 'px';
                preview.style.height = rect.height + 'px';
                
                if (previewData.sprite) {
                    // Animate sprite sheet
                    animateSpriteSheet(preview, previewData.sprite);
                } else if (previewData.gif) {
                    // Show GIF
                    preview.innerHTML = `<img src="${previewData.gif}" style="width:100%;height:100%;object-fit:cover;" />`;
                } else if (previewData.videoUrl) {
                    // Create mini video player
                    preview.innerHTML = `<video src="${previewData.videoUrl}" autoplay muted loop style="width:100%;height:100%;object-fit:cover;" />`;
                } else if (previewData.computed) {
                    // Try computed URLs
                    if (previewData.computed.gif) {
                        preview.innerHTML = `<img src="${previewData.computed.gif}" style="width:100%;height:100%;object-fit:cover;" />`;
                    }
                }
                
                document.body.appendChild(preview);
                thumb.astralxPreview = preview;
            }
            
            function hidePreview(thumb) {
                if (thumb.astralxPreview) {
                    thumb.astralxPreview.remove();
                    thumb.astralxPreview = null;
                }
            }
            
            function animateSpriteSheet(preview, spriteUrl) {
                const img = new Image();
                img.onload = function() {
                    const frameHeight = preview.clientHeight;
                    const frameCount = Math.floor(img.height / frameHeight);
                    let currentFrame = 0;
                    
                    preview.style.backgroundImage = `url(${spriteUrl})`;
                    preview.style.backgroundSize = '100% auto';
                    
                    const animate = () => {
                        preview.style.backgroundPosition = `0 -${currentFrame * frameHeight}px`;
                        currentFrame = (currentFrame + 1) % frameCount;
                    };
                    
                    const interval = setInterval(animate, 100);
                    preview.dataset.interval = interval;
                };
                img.src = spriteUrl;
            }
            
            // Start observing
            thumbDetector.observe(document.body, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['data-preview', 'data-sprite', 'data-gif']
            });
            
            // Initial scan
            findAllThumbnails();
            
            // Rescan periodically for dynamic content
            setInterval(findAllThumbnails, 2000);
        })();
        """
        
        webView?.evaluateJavascript(js, null)
    }
    
    /**
     * JavaScript interface for thumbnail interactions
     */
    inner class ThumbnailInterface {
        @JavascriptInterface
        fun onHoverStart(thumbId: String, previewDataJson: String) {
            hoverJob?.cancel()
            hoverJob = scope.launch {
                try {
                    val previewData = parsePreviewData(previewDataJson)
                    preloadPreview(thumbId, previewData)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling hover start")
                }
            }
        }
        
        @JavascriptInterface
        fun onHoverEnd(thumbId: String) {
            hoverJob?.cancel()
        }
        
        @JavascriptInterface
        fun onLongPress(thumbId: String, previewDataJson: String) {
            scope.launch {
                try {
                    val previewData = parsePreviewData(previewDataJson)
                    preloadPreview(thumbId, previewData)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling long press")
                }
            }
        }
    }
    
    /**
     * Parse preview data from JSON
     */
    private fun parsePreviewData(json: String): PreviewData {
        return try {
            val obj = JSONObject(json)
            PreviewData(
                sprite = obj.optString("sprite").takeIf { it.isNotEmpty() },
                gif = obj.optString("gif").takeIf { it.isNotEmpty() },
                videoUrl = obj.optString("videoUrl").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            PreviewData()
        }
    }
    
    /**
     * Preload preview resources
     */
    private suspend fun preloadPreview(thumbId: String, previewData: PreviewData) {
        // Check cache first
        val cached = previewCache.get(thumbId)
        if (cached != null) {
            return
        }
        
        // Preload resources
        withContext(Dispatchers.IO) {
            try {
                // Preload sprite or GIF
                when {
                    previewData.sprite != null -> preloadImage(previewData.sprite)
                    previewData.gif != null -> preloadImage(previewData.gif)
                    previewData.videoUrl != null -> preloadVideo(previewData.videoUrl)
                }
                
                // Cache the data
                previewCache.put(thumbId, previewData)
            } catch (e: Exception) {
                Timber.e(e, "Error preloading preview")
            }
        }
    }
    
    /**
     * Preload image resource
     */
    private fun preloadImage(url: String) {
        // This would use image loading library to preload
        Timber.d("Preloading image: $url")
    }
    
    /**
     * Preload video metadata
     */
    private fun preloadVideo(url: String) {
        // This would preload video metadata
        Timber.d("Preloading video: $url")
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        hoverJob?.cancel()
        webView = null
        previewCache.evictAll()
    }
}