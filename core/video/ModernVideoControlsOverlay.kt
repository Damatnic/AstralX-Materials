package com.astralx.browser.video

import android.content.Context
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.astralx.browser.R
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.abs

/**
 * Modern video controls overlay with gesture support
 * Similar to YouTube's video player controls
 */
class ModernVideoControlsOverlay(
    private val context: Context
) : FrameLayout(context) {
    
    private var controlsView: View? = null
    private var playPauseButton: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var currentTimeText: TextView? = null
    private var durationText: TextView? = null
    private var qualityButton: ImageButton? = null
    private var fullscreenButton: ImageButton? = null
    private var speedButton: ImageButton? = null
    
    private var videoView: View? = null
    private var isPlaying = true
    private var hideControlsJob: Job? = null
    
    private val hideDelay = 3000L // 3 seconds
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Gesture detection
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    init {
        setupControls()
        setupGestures()
    }
    
    private fun setupControls() {
        // Inflate controls layout
        controlsView = LayoutInflater.from(context).inflate(
            R.layout.video_controls_overlay,
            this,
            false
        )
        
        // Find views
        controlsView?.let { controls ->
            playPauseButton = controls.findViewById(R.id.play_pause_button)
            seekBar = controls.findViewById(R.id.video_seek_bar)
            currentTimeText = controls.findViewById(R.id.current_time)
            durationText = controls.findViewById(R.id.duration)
            qualityButton = controls.findViewById(R.id.quality_button)
            fullscreenButton = controls.findViewById(R.id.fullscreen_button)
            speedButton = controls.findViewById(R.id.speed_button)
            
            // Set click listeners
            playPauseButton?.setOnClickListener { togglePlayPause() }
            qualityButton?.setOnClickListener { showQualityOptions() }
            speedButton?.setOnClickListener { showSpeedOptions() }
            fullscreenButton?.setOnClickListener { toggleFullscreen() }
            
            // Seek bar listener
            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        seekToPosition(progress)
                    }
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    cancelHideControls()
                }
                
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    scheduleHideControls()
                }
            })
        }
        
        addView(controlsView)
        
        // Initially hide controls
        controlsView?.alpha = 0f
    }
    
    private fun setupGestures() {
        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }
    
    /**
     * Show controls for a video view
     */
    fun show(video: View) {
        videoView = video
        showControls()
        scheduleHideControls()
    }
    
    /**
     * Hide controls
     */
    fun hide() {
        hideControls()
        coroutineScope.cancel()
    }
    
    private fun showControls() {
        controlsView?.animate()
            ?.alpha(1f)
            ?.setDuration(200)
            ?.start()
    }
    
    private fun hideControls() {
        controlsView?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.start()
    }
    
    private fun toggleControls() {
        if (controlsView?.alpha == 0f) {
            showControls()
            scheduleHideControls()
        } else {
            hideControls()
        }
    }
    
    private fun scheduleHideControls() {
        cancelHideControls()
        hideControlsJob = coroutineScope.launch {
            delay(hideDelay)
            hideControls()
        }
    }
    
    private fun cancelHideControls() {
        hideControlsJob?.cancel()
    }
    
    private fun togglePlayPause() {
        isPlaying = !isPlaying
        updatePlayPauseButton()
        // Notify video player about play/pause
        onPlayPauseChanged?.invoke(isPlaying)
    }
    
    private fun updatePlayPauseButton() {
        playPauseButton?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    private fun seekToPosition(position: Int) {
        // Convert progress to video position
        onSeekTo?.invoke(position)
    }
    
    private fun showQualityOptions() {
        // Show quality selection dialog
        onQualityClick?.invoke()
    }
    
    private fun showSpeedOptions() {
        // Show playback speed options
        onSpeedClick?.invoke()
    }
    
    private fun toggleFullscreen() {
        // Toggle fullscreen mode
        onFullscreenClick?.invoke()
    }
    
    /**
     * Update video progress
     */
    fun updateProgress(current: Long, duration: Long) {
        currentTimeText?.text = formatTime(current)
        durationText?.text = formatTime(duration)
        
        if (duration > 0) {
            val progress = ((current * 100) / duration).toInt()
            seekBar?.progress = progress
        }
    }
    
    /**
     * Format time in mm:ss or hh:mm:ss
     */
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%02d:%02d", minutes, seconds % 60)
        }
    }
    
    /**
     * Gesture listener for video controls
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        
        private var startBrightness = 0f
        private var startVolume = 0
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            toggleControls()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val viewWidth = width
            val tapX = e.x
            
            // Double tap left/right to seek
            when {
                tapX < viewWidth / 3 -> {
                    // Seek backward 10 seconds
                    onSeekRelative?.invoke(-10000)
                    showSeekIndicator(-10)
                }
                tapX > viewWidth * 2 / 3 -> {
                    // Seek forward 10 seconds
                    onSeekRelative?.invoke(10000)
                    showSeekIndicator(10)
                }
                else -> {
                    // Center double tap - play/pause
                    togglePlayPause()
                }
            }
            return true
        }
        
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            
            // Determine gesture type based on initial touch position
            when {
                // Left side - brightness
                e1.x < viewWidth / 3 && abs(distanceY) > abs(distanceX) -> {
                    adjustBrightness(-distanceY / viewHeight)
                    return true
                }
                // Right side - volume
                e1.x > viewWidth * 2 / 3 && abs(distanceY) > abs(distanceX) -> {
                    adjustVolume(-distanceY / viewHeight)
                    return true
                }
                // Horizontal scroll - seek
                abs(distanceX) > abs(distanceY) -> {
                    val seekDelta = (distanceX / viewWidth) * 60000 // 60 seconds max
                    onSeekRelative?.invoke(-seekDelta.toLong())
                    return true
                }
            }
            
            return false
        }
    }
    
    private fun adjustBrightness(delta: Float) {
        onBrightnessChange?.invoke(delta)
        showBrightnessIndicator(delta)
    }
    
    private fun adjustVolume(delta: Float) {
        onVolumeChange?.invoke(delta)
        showVolumeIndicator(delta)
    }
    
    private fun showSeekIndicator(seconds: Int) {
        // Show seek indicator UI
        Timber.d("Seek ${if (seconds > 0) "forward" else "backward"} $seconds seconds")
    }
    
    private fun showBrightnessIndicator(delta: Float) {
        // Show brightness indicator UI
        Timber.d("Brightness adjustment: $delta")
    }
    
    private fun showVolumeIndicator(delta: Float) {
        // Show volume indicator UI
        Timber.d("Volume adjustment: $delta")
    }
    
    // Callbacks
    var onPlayPauseChanged: ((Boolean) -> Unit)? = null
    var onSeekTo: ((Int) -> Unit)? = null
    var onSeekRelative: ((Long) -> Unit)? = null
    var onQualityClick: (() -> Unit)? = null
    var onSpeedClick: (() -> Unit)? = null
    var onFullscreenClick: (() -> Unit)? = null
    var onBrightnessChange: ((Float) -> Unit)? = null
    var onVolumeChange: ((Float) -> Unit)? = null
}