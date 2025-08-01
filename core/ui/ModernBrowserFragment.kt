package com.astralx.browser.presentation.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.astralx.browser.R
import com.astralx.browser.databinding.FragmentBrowserModernBinding
import com.astralx.browser.video.AdultContentVideoDetector
import com.astralx.browser.video.VideoThumbnailPreviewEngine
import com.astralx.browser.video.AdultContentVideoCodecs
import com.astralx.browser.video.ModernVideoControlsOverlay
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Modern browser fragment with enhanced UI and video capabilities
 */
@AndroidEntryPoint
class ModernBrowserFragment : Fragment() {
    
    private var _binding: FragmentBrowserModernBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BrowserViewModel by viewModels()
    
    @Inject
    lateinit var adultVideoDetector: AdultContentVideoDetector
    
    @Inject
    lateinit var videoThumbnailEngine: VideoThumbnailPreviewEngine
    
    @Inject
    lateinit var videoCodecs: AdultContentVideoCodecs
    
    private var videoControlsOverlay: ModernVideoControlsOverlay? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserModernBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupEdgeToEdge()
        setupWebView()
        setupVideoHandling()
        setupToolbar()
        setupBottomNavigation()
        setupFAB()
        observeViewModel()
        
        // Animate entrance
        animateEntrance()
    }
    
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            
            // Adjust bottom navigation padding
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)
            
            insets
        }
    }
    
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                
                // Video playback settings
                mediaPlaybackRequiresUserGesture = false
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            webViewClient = WebViewClient()
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                    binding.progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                }
                
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    handleFullscreenVideo(view)
                }
                
                override fun onHideCustomView() {
                    super.onHideCustomView()
                    exitFullscreenVideo()
                }
            }
            
            // Load default page
            loadUrl("https://www.google.com")
        }
    }
    
    private fun setupVideoHandling() {
        // Observe detected videos
        adultVideoDetector.detectedVideos.observe(viewLifecycleOwner) { videos ->
            if (videos.isNotEmpty()) {
                Timber.d("Detected ${videos.size} videos")
                showVideoDetectionIndicator()
            }
        }
        
        // Observe hover previews
        videoThumbnailEngine.hoverPreviews.observe(viewLifecycleOwner) { previews ->
            // Handle preview display
            Timber.d("${previews.size} video previews available")
        }
        
        // Log codec capabilities
        lifecycleScope.launch {
            val capabilities = videoCodecs.getCodecCapabilities()
            Timber.d("Codec capabilities: $capabilities")
        }
        
        // Inject video detection scripts on page load
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    // Inject video detection
                    adultVideoDetector.injectDetectionScript(binding.webView, it)
                    
                    // Inject preview script
                    videoThumbnailEngine.injectPreviewScript(binding.webView)
                }
            }
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                }
            }
            
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        binding.webView.reload()
                        true
                    }
                    R.id.action_privacy -> {
                        viewModel.togglePrivacyMode()
                        true
                    }
                    else -> false
                }
            }
        }
        
        // Setup omnibox
        binding.omnibox.setOnEditorActionListener { _, _, _ ->
            val url = binding.omnibox.text.toString()
            if (url.isNotEmpty()) {
                binding.webView.loadUrl(
                    if (url.startsWith("http")) url else "https://$url"
                )
                binding.omnibox.clearFocus()
            }
            true
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.webView.loadUrl("https://www.google.com")
                    true
                }
                R.id.nav_tabs -> {
                    // Show tab switcher
                    true
                }
                R.id.nav_bookmarks -> {
                    // Show bookmarks
                    true
                }
                R.id.nav_downloads -> {
                    // Show downloads
                    true
                }
                R.id.nav_settings -> {
                    // Show settings
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupFAB() {
        binding.fab.setOnClickListener {
            // Create new tab
            viewModel.createNewTab()
        }
    }
    
    private fun observeViewModel() {
        viewModel.currentUrl.observe(viewLifecycleOwner) { url ->
            binding.omnibox.setText(url)
        }
        
        viewModel.isPrivacyMode.observe(viewLifecycleOwner) { isPrivate ->
            updatePrivacyIndicator(isPrivate)
        }
    }
    
    private fun animateEntrance() {
        binding.toolbar.alpha = 0f
        binding.bottomNavigation.translationY = 200f
        binding.fab.scaleX = 0f
        binding.fab.scaleY = 0f
        
        binding.toolbar.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        binding.bottomNavigation.animate()
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(100)
            .start()
        
        binding.fab.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setStartDelay(200)
            .start()
    }
    
    private fun updatePrivacyIndicator(isPrivate: Boolean) {
        binding.privacyIndicator.visibility = if (isPrivate) View.VISIBLE else View.GONE
        if (isPrivate) {
            binding.privacyIndicator.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }
    }
    
    private fun showVideoDetectionIndicator() {
        // Show a subtle indicator that videos were detected
        // This could be a badge on the toolbar or a toast
    }
    
    private fun handleFullscreenVideo(view: View?) {
        view?.let {
            // Create fullscreen video container
            val fullscreenContainer = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Add video view to fullscreen container
            (activity?.window?.decorView as? ViewGroup)?.addView(view, fullscreenContainer)
            
            // Create and show video controls overlay
            videoControlsOverlay = ModernVideoControlsOverlay(requireContext()).apply {
                show(view)
            }
            
            // Hide system UI
            activity?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
    
    private fun exitFullscreenVideo() {
        // Remove video controls overlay
        videoControlsOverlay?.hide()
        videoControlsOverlay = null
        
        // Show system UI
        activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        videoControlsOverlay = null
    }
    
    companion object {
        fun newInstance() = ModernBrowserFragment()
    }
}