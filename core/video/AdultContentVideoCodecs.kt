package com.astralx.browser.video

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized codec support for adult content streaming
 * Optimizes for various video formats and qualities
 */
@Singleton
class AdultContentVideoCodecs @Inject constructor(
    private val context: Context
) {
    
    /**
     * Supported video codecs with priority
     */
    enum class VideoCodec(val mimeType: String, val priority: Int) {
        H264("video/avc", 100),      // Most compatible
        H265("video/hevc", 90),      // Better compression
        VP9("video/x-vnd.on2.vp9", 80),  // YouTube/WebM
        VP8("video/x-vnd.on2.vp8", 70),  // Legacy WebM
        AV1("video/av01", 60),       // Future-proof
        MPEG4("video/mp4v-es", 50),  // Legacy support
        H263("video/3gpp", 40)       // Very old devices
    }
    
    /**
     * Get available hardware codecs
     */
    fun getHardwareCodecs(): List<CodecInfo> {
        val codecs = mutableListOf<CodecInfo>()
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder && isHardwareAccelerated(codecInfo)) {
                val supportedTypes = codecInfo.supportedTypes
                for (type in supportedTypes) {
                    if (type.startsWith("video/")) {
                        codecs.add(
                            CodecInfo(
                                name = codecInfo.name,
                                mimeType = type,
                                isHardware = true,
                                capabilities = getCodecCapabilities(codecInfo, type)
                            )
                        )
                    }
                }
            }
        }
        
        return codecs.sortedByDescending { getCodecPriority(it.mimeType) }
    }
    
    /**
     * Check if codec is hardware accelerated
     */
    private fun isHardwareAccelerated(codecInfo: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codecInfo.isHardwareAccelerated
        } else {
            // Heuristic for older devices
            val name = codecInfo.name.lowercase()
            !name.contains("google") && 
            !name.contains("sw") && 
            !name.contains("software") &&
            (name.contains("omx") || name.contains("c2"))
        }
    }
    
    /**
     * Get codec capabilities
     */
    private fun getCodecCapabilities(
        codecInfo: MediaCodecInfo, 
        mimeType: String
    ): CodecCapabilities {
        return try {
            val capabilities = codecInfo.getCapabilitiesForType(mimeType)
            val videoCapabilities = capabilities.videoCapabilities
            
            CodecCapabilities(
                maxWidth = videoCapabilities?.supportedWidths?.upper ?: 1920,
                maxHeight = videoCapabilities?.supportedHeights?.upper ?: 1080,
                maxFrameRate = videoCapabilities?.supportedFrameRates?.upper?.toInt() ?: 30,
                maxBitrate = videoCapabilities?.bitrateRange?.upper ?: 10_000_000,
                profiles = getProfileSupport(capabilities),
                levels = getLevelSupport(capabilities)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting codec capabilities for $mimeType")
            CodecCapabilities()
        }
    }
    
    /**
     * Get supported profiles
     */
    private fun getProfileSupport(capabilities: MediaCodecInfo.CodecCapabilities): List<Int> {
        val profiles = mutableListOf<Int>()
        capabilities.profileLevels.forEach { profileLevel ->
            profiles.add(profileLevel.profile)
        }
        return profiles.distinct()
    }
    
    /**
     * Get supported levels
     */
    private fun getLevelSupport(capabilities: MediaCodecInfo.CodecCapabilities): List<Int> {
        val levels = mutableListOf<Int>()
        capabilities.profileLevels.forEach { profileLevel ->
            levels.add(profileLevel.level)
        }
        return levels.distinct()
    }
    
    /**
     * Get codec priority based on mime type
     */
    private fun getCodecPriority(mimeType: String): Int {
        return VideoCodec.values().find { it.mimeType == mimeType }?.priority ?: 0
    }
    
    /**
     * Select best codec for video URL
     */
    fun selectBestCodec(
        videoUrl: String,
        availableCodecs: List<String>
    ): String? {
        // Prioritize based on URL patterns
        val preferredCodec = when {
            videoUrl.contains(".mp4") -> VideoCodec.H264.mimeType
            videoUrl.contains(".webm") -> VideoCodec.VP9.mimeType
            videoUrl.contains(".m3u8") -> VideoCodec.H264.mimeType // HLS usually H264
            videoUrl.contains(".mpd") -> VideoCodec.H264.mimeType  // DASH can vary
            else -> VideoCodec.H264.mimeType // Default to most compatible
        }
        
        // Check if preferred codec is available
        if (availableCodecs.contains(preferredCodec)) {
            return preferredCodec
        }
        
        // Fall back to priority order
        VideoCodec.values().sortedByDescending { it.priority }.forEach { codec ->
            if (availableCodecs.contains(codec.mimeType)) {
                return codec.mimeType
            }
        }
        
        return availableCodecs.firstOrNull()
    }
    
    /**
     * Configure ExoPlayer for adult content
     */
    fun getOptimalPlayerConfig(): PlayerConfig {
        val hardwareCodecs = getHardwareCodecs()
        val hasH265 = hardwareCodecs.any { it.mimeType == VideoCodec.H265.mimeType }
        val hasVP9 = hardwareCodecs.any { it.mimeType == VideoCodec.VP9.mimeType }
        
        return PlayerConfig(
            preferHardwareDecoding = true,
            enableAdaptiveBitrate = true,
            bufferDurationMs = 30_000, // 30 seconds
            rebufferDurationMs = 5_000, // 5 seconds
            maxVideoWidth = if (hasH265) 3840 else 1920, // 4K if HEVC available
            maxVideoHeight = if (hasH265) 2160 else 1080,
            preferredVideoCodecs = buildList {
                add(VideoCodec.H264.mimeType) // Always include H264
                if (hasH265) add(VideoCodec.H265.mimeType)
                if (hasVP9) add(VideoCodec.VP9.mimeType)
            }
        )
    }
    
    /**
     * Get codec capabilities summary
     */
    fun getCodecCapabilities(): Map<String, Any> {
        val hardwareCodecs = getHardwareCodecs()
        
        return mapOf(
            "hardwareCodecs" to hardwareCodecs.size,
            "supports4K" to hardwareCodecs.any { it.capabilities.maxWidth >= 3840 },
            "supportsHEVC" to hardwareCodecs.any { it.mimeType == VideoCodec.H265.mimeType },
            "supportsVP9" to hardwareCodecs.any { it.mimeType == VideoCodec.VP9.mimeType },
            "supportsAV1" to hardwareCodecs.any { it.mimeType == VideoCodec.AV1.mimeType },
            "maxSupportedWidth" to hardwareCodecs.maxOfOrNull { it.capabilities.maxWidth } ?: 1920,
            "maxSupportedHeight" to hardwareCodecs.maxOfOrNull { it.capabilities.maxHeight } ?: 1080,
            "maxSupportedFrameRate" to hardwareCodecs.maxOfOrNull { it.capabilities.maxFrameRate } ?: 30
        )
    }
    
    /**
     * Codec information
     */
    data class CodecInfo(
        val name: String,
        val mimeType: String,
        val isHardware: Boolean,
        val capabilities: CodecCapabilities
    )
    
    /**
     * Codec capabilities
     */
    data class CodecCapabilities(
        val maxWidth: Int = 1920,
        val maxHeight: Int = 1080,
        val maxFrameRate: Int = 30,
        val maxBitrate: Long = 10_000_000,
        val profiles: List<Int> = emptyList(),
        val levels: List<Int> = emptyList()
    )
    
    /**
     * Player configuration
     */
    data class PlayerConfig(
        val preferHardwareDecoding: Boolean,
        val enableAdaptiveBitrate: Boolean,
        val bufferDurationMs: Int,
        val rebufferDurationMs: Int,
        val maxVideoWidth: Int,
        val maxVideoHeight: Int,
        val preferredVideoCodecs: List<String>
    )
}