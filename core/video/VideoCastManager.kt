package com.astralx.browser.video

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Status
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video casting manager for Chromecast and Smart TV support
 */
@Singleton
class VideoCastManager @Inject constructor(
    private val context: Context
) {
    
    private var sessionManager: SessionManager? = null
    private var castSession: CastSession? = null
    private var remoteMediaClient: RemoteMediaClient? = null
    
    private val _castState = MutableLiveData<CastState>()
    val castState: LiveData<CastState> = _castState
    
    private val _castProgress = MutableLiveData<CastProgress>()
    val castProgress: LiveData<CastProgress> = _castProgress
    
    data class CastState(
        val isConnected: Boolean = false,
        val deviceName: String? = null,
        val isPlaying: Boolean = false,
        val error: String? = null
    )
    
    data class CastProgress(
        val position: Long = 0,
        val duration: Long = 0,
        val bufferedPosition: Long = 0
    )
    
    private val sessionManagerListener = object : SessionManagerListener<Session> {
        override fun onSessionStarted(session: Session, sessionId: String) {
            onCastSessionStarted(session as? CastSession)
        }
        
        override fun onSessionEnded(session: Session, error: Int) {
            onCastSessionEnded()
        }
        
        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            onCastSessionStarted(session as? CastSession)
        }
        
        override fun onSessionStarting(session: Session) {}
        override fun onSessionEnding(session: Session) {}
        override fun onSessionResuming(session: Session, sessionId: String) {}
        override fun onSessionSuspended(session: Session, reason: Int) {}
        override fun onSessionStartFailed(session: Session, error: Int) {
            _castState.value = CastState(error = "Failed to start cast session")
        }
        
        override fun onSessionResumeFailed(session: Session, error: Int) {
            _castState.value = CastState(error = "Failed to resume cast session")
        }
    }
    
    /**
     * Initialize Cast framework
     */
    fun initialize() {
        try {
            CastContext.getSharedInstance(context)?.let { castContext ->
                sessionManager = castContext.sessionManager
                sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                
                // Check if already casting
                castSession = sessionManager?.currentCastSession
                if (castSession != null && castSession!!.isConnected) {
                    onCastSessionStarted(castSession)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Cast framework")
        }
    }
    
    /**
     * Cast video to connected device
     */
    fun castVideo(
        videoUrl: String,
        title: String,
        thumbnailUrl: String? = null,
        contentType: String = "video/mp4",
        metadata: Map<String, String> = emptyMap()
    ) {
        if (castSession == null || !castSession!!.isConnected) {
            Timber.w("No cast session available")
            return
        }
        
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            thumbnailUrl?.let {
                addImage(WebImage(android.net.Uri.parse(it)))
            }
            
            // Add custom metadata for adult content
            metadata.forEach { (key, value) ->
                putString(key, value)
            }
        }
        
        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(movieMetadata)
            .build()
        
        val loadOptions = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(0)
            .build()
        
        remoteMediaClient?.load(mediaInfo, loadOptions)?.setResultCallback { result ->
            if (result.status.isSuccess) {
                Timber.d("Video cast started successfully")
                updateCastState(isPlaying = true)
            } else {
                Timber.e("Failed to cast video: ${result.status.statusMessage}")
                _castState.value = castState.value?.copy(error = result.status.statusMessage)
            }
        }
    }
    
    /**
     * Control playback on cast device
     */
    fun play() {
        remoteMediaClient?.play()
    }
    
    fun pause() {
        remoteMediaClient?.pause()
    }
    
    fun seekTo(position: Long) {
        remoteMediaClient?.seek(position)
    }
    
    fun setVolume(volume: Double) {
        remoteMediaClient?.setStreamVolume(volume)
    }
    
    fun stop() {
        remoteMediaClient?.stop()
    }
    
    /**
     * Get available cast devices
     */
    fun getCastDevices(): List<CastDevice> {
        return try {
            val castContext = CastContext.getSharedInstance(context)
            val mediaRouter = MediaRouter.getInstance(context)
            val routes = mediaRouter.routes
            
            routes.mapNotNull { route ->
                CastDevice.getFromBundle(route.extras)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get cast devices")
            emptyList()
        }
    }
    
    private fun onCastSessionStarted(session: CastSession?) {
        castSession = session
        castSession?.let { activeSession ->
            remoteMediaClient = activeSession.remoteMediaClient
            
            // Set up progress updates
            remoteMediaClient?.addProgressListener { progress, duration ->
                _castProgress.value = CastProgress(
                    position = progress,
                    duration = duration,
                    bufferedPosition = remoteMediaClient?.approximateStreamPosition ?: 0
                )
            }
            
            // Update state
            val device = activeSession.castDevice
            updateCastState(
                isConnected = true,
                deviceName = device?.friendlyName
            )
            
            Timber.d("Cast session started with device: ${device?.friendlyName}")
        }
    }
    
    private fun onCastSessionEnded() {
        castSession = null
        remoteMediaClient = null
        updateCastState(isConnected = false, deviceName = null, isPlaying = false)
        _castProgress.value = CastProgress()
        
        Timber.d("Cast session ended")
    }
    
    private fun updateCastState(
        isConnected: Boolean? = null,
        deviceName: String? = null,
        isPlaying: Boolean? = null,
        error: String? = null
    ) {
        val currentState = _castState.value ?: CastState()
        _castState.value = currentState.copy(
            isConnected = isConnected ?: currentState.isConnected,
            deviceName = deviceName ?: currentState.deviceName,
            isPlaying = isPlaying ?: currentState.isPlaying,
            error = error
        )
    }
    
    /**
     * Check if casting is available
     */
    fun isCastAvailable(): Boolean {
        return try {
            val castContext = CastContext.getSharedInstance(context)
            castContext != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        castSession = null
        remoteMediaClient = null
    }
}

/**
 * Cast options provider for manifest configuration
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(LaunchOptions.getSupportedActivities(context)[0].name)
            .build()
            
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(LaunchOptions.getSupportedActivities(context)[0].name)
            .build()
            
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(mediaOptions)
            .build()
    }
    
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}