package com.astralx.browser.domain.model

/**
 * Video format types supported by the browser
 */
enum class VideoFormat {
    MP4,
    WEBM,
    HLS,      // HTTP Live Streaming (.m3u8)
    DASH,     // Dynamic Adaptive Streaming over HTTP (.mpd)
    FLV,      // Flash Video
    AVI,
    MKV,
    MOV,
    WMV,
    UNKNOWN
}