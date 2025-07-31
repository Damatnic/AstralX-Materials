package com.astralx.browser.core.audio

import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import timber.log.Timber

class MediaCodecAudioExtractor {
    
    suspend fun extract(
        context: Context,
        videoUri: Uri,
        outputFile: File,
        maxDurationSeconds: Int,
        targetSampleRate: Int,
        targetChannels: Int
    ): AudioExtractionResult = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)
            
            val audioTrackIndex = selectAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                throw Exception("No audio track found")
            }
            
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            
            extractor.selectTrack(audioTrackIndex)
            
            // Extract and decode audio
            val pcmData = extractPcmData(extractor, decoder, maxDurationSeconds)
            
            // Resample if necessary
            val resampledData = if (needsResampling(format, targetSampleRate, targetChannels)) {
                resampleAudio(pcmData, format, targetSampleRate, targetChannels)
            } else {
                pcmData
            }
            
            // Write WAV file
            writeWavFile(outputFile, resampledData, targetSampleRate, targetChannels)
            
            val duration = pcmData.size.toFloat() / (targetSampleRate * targetChannels * 2)
            val extractionTime = System.currentTimeMillis() - startTime
            
            AudioExtractionResult(
                file = outputFile,
                duration = duration,
                extractionMethod = "mediacodec",
                extractionTimeMs = extractionTime
            )
            
        } finally {
            decoder?.release()
            extractor?.release()
        }
    }
    
    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }
    
    private fun extractPcmData(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        maxDurationSeconds: Int
    ): ByteArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val pcmData = mutableListOf<Byte>()
        val timeoutUs = 10000L
        val maxDurationUs = maxDurationSeconds * 1_000_000L
        
        while (true) {
            // Feed input to decoder
            val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    val presentationTime = extractor.sampleTime
                    decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTime, 0)
                    
                    if (presentationTime > maxDurationUs) {
                        break
                    }
                    
                    extractor.advance()
                }
            }
            
            // Get output from decoder
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputIndex)!!
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                pcmData.addAll(chunk.toList())
                
                decoder.releaseOutputBuffer(outputIndex, false)
                
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
        
        return pcmData.toByteArray()
    }
    
    private fun needsResampling(
        format: MediaFormat,
        targetSampleRate: Int,
        targetChannels: Int
    ): Boolean {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        return sampleRate != targetSampleRate || channels != targetChannels
    }
    
    private fun resampleAudio(
        pcmData: ByteArray,
        sourceFormat: MediaFormat,
        targetSampleRate: Int,
        targetChannels: Int
    ): ByteArray {
        // Simple resampling - for production, use a proper resampling library
        // This is a placeholder that returns the original data
        return pcmData
    }
    
    private fun writeWavFile(
        outputFile: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int
    ) {
        FileOutputStream(outputFile).use { output ->
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            
            // Write WAV header
            output.write("RIFF".toByteArray())
            output.write(intToByteArray(36 + pcmData.size))
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            output.write(intToByteArray(16)) // Subchunk1Size
            output.write(shortToByteArray(1)) // AudioFormat (PCM)
            output.write(shortToByteArray(channels.toShort()))
            output.write(intToByteArray(sampleRate))
            output.write(intToByteArray(byteRate))
            output.write(shortToByteArray((channels * (bitsPerSample / 8)).toShort()))
            output.write(shortToByteArray(bitsPerSample.toShort()))
            output.write("data".toByteArray())
            output.write(intToByteArray(pcmData.size))
            output.write(pcmData)
        }
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}