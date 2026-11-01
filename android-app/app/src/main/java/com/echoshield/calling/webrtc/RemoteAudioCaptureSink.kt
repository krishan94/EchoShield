package com.echoshield.calling.webrtc

import android.util.Log
import org.webrtc.AudioTrackSink
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Thread-safe WebRTC AudioTrackSink capturing decoded remote caller PCM audio frames.
 * Buffers a rolling window of remote audio to feed Phase 7 Combined Security Engine
 * asynchronously without disturbing the live audio playback stream.
 */
class RemoteAudioCaptureSink(
    private val maxBufferDurationSeconds: Int = 4
) : AudioTrackSink {

    private val tag = "RemoteAudioSink"
    private val lock = Any()

    // Rolling byte buffer
    private val buffer = ByteArrayOutputStream()
    private var currentSampleRate = 16000
    private var currentChannels = 1
    private var currentBitsPerSample = 16

    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long
    ) {
        synchronized(lock) {
            currentSampleRate = sampleRate
            currentChannels = numberOfChannels
            currentBitsPerSample = bitsPerSample

            val bytesPerSec = sampleRate * numberOfChannels * (bitsPerSample / 8)
            val maxBytes = bytesPerSec * maxBufferDurationSeconds

            val remaining = audioData.remaining()
            if (remaining <= 0) return

            // Safely copy bytes using duplicate to preserve original buffer position for WebRTC playback
            val duplicate = audioData.duplicate()
            val tempBytes = ByteArray(remaining)
            duplicate.get(tempBytes)

            // Append new PCM bytes
            buffer.write(tempBytes)

            // Trim oldest bytes if exceeding max capacity
            val allBytes = buffer.toByteArray()
            if (allBytes.size > maxBytes) {
                val excess = allBytes.size - maxBytes
                buffer.reset()
                buffer.write(allBytes, excess, maxBytes)
            }
        }
    }

    /**
     * Extracts the captured remote audio window into a valid 16-bit PCM WAV file.
     * Returns true if a valid sample with sufficient audio length (>1.5s) was written.
     */
    fun exportToWav(outputFile: File, minDurationSeconds: Float = 1.5f): Boolean {
        val pcmData: ByteArray
        val sampleRate: Int
        val channels: Int
        val bitsPerSample: Int

        synchronized(lock) {
            pcmData = buffer.toByteArray()
            sampleRate = currentSampleRate
            channels = currentChannels
            bitsPerSample = currentBitsPerSample
        }

        val bytesPerSec = sampleRate * channels * (bitsPerSample / 8)
        val minRequiredBytes = (bytesPerSec * minDurationSeconds).toInt()

        if (pcmData.size < minRequiredBytes) {
            Log.d(tag, "Insufficient remote audio frames collected: ${pcmData.size} / $minRequiredBytes bytes")
            return false
        }

        try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                writeWavHeader(
                    fos = fos,
                    pcmDataSize = pcmData.size,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample
                )
                fos.write(pcmData)
                fos.flush()
            }
            Log.i(tag, "Exported remote audio sample to ${outputFile.name} (${pcmData.size} bytes, $sampleRate Hz)")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to write remote audio WAV file: ${e.message}", e)
            return false
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.reset()
        }
    }

    private fun writeWavHeader(
        fos: FileOutputStream,
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = pcmDataSize + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE format
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (blockAlign.toInt() and 0xff).toByte()
        header[33] = ((blockAlign.toInt() shr 8) and 0xff).toByte()
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = ((pcmDataSize shr 8) and 0xff).toByte()
        header[42] = ((pcmDataSize shr 16) and 0xff).toByte()
        header[43] = ((pcmDataSize shr 24) and 0xff).toByte()

        fos.write(header)
    }
}

