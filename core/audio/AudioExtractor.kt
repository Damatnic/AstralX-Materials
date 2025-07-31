package com.astralx.browser.core.audio

import android.net.Uri
import java.io.File

/**
 * Interface for audio extraction from video files
 * Provides optimized audio extraction with fallback mechanisms
 */
interface AudioExtractor {
    
    /**
     * Extract audio from video with optimized parameters
     * @param videoUri Source video URI
     * @param outputFile Output audio file
     * @param maxDurationSeconds Maximum duration to extract (default: 300 seconds)
     * @param sampleRate Audio sample rate (default: 16000 Hz for speech recognition)
     * @param channels Number of audio channels (default: 1 for mono)
     * @return Result containing extraction details or failure
     */
    suspend fun extractAudioOptimized(
        videoUri: Uri,
        outputFile: File,
        maxDurationSeconds: Int = 300,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): Result<AudioExtractionResult>
    
    /**
     * Verify that the extracted audio file is valid
     * @param file Audio file to verify
     * @return true if file is valid, false otherwise
     */
    suspend fun verifyAudioFile(file: File): Boolean
    
    /**
     * Create a fallback WAV file when extraction fails
     * @param outputFile Output file for fallback WAV
     * @param maxDurationSeconds Maximum duration for fallback file
     * @param sampleRate Sample rate for the WAV file
     * @param channels Number of channels for the WAV file
     * @return Result containing extraction details or failure
     */
    suspend fun createFallbackWavFile(
        outputFile: File, 
        maxDurationSeconds: Int = 300,
        sampleRate: Int = 16000, 
        channels: Int = 1
    ): Result<AudioExtractionResult>
}