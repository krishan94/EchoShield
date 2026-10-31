package com.echoshield.voice.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

data class ClientAudioQualityReport(
    val isValid: Boolean,
    val durationSeconds: Float,
    val snrDb: Float,
    val rmsEnergy: Float,
    val silenceRatio: Float,
    val clippingRatio: Float,
    val errorMessage: String? = null
)

/**
 * Validates 16kHz PCM WAV audio on Android device before transmission.
 */
object AudioQualityValidator {

    private const val EXPECTED_SAMPLE_RATE = 16000
    private const val MIN_DURATION_SECONDS = 2.5f
    private const val MAX_DURATION_SECONDS = 15.0f
    private const val MIN_RMS_ENERGY = 0.008f
    private const val MAX_SILENCE_RATIO = 0.65f
    private const val MAX_CLIPPING_RATIO = 0.015f

    fun validateWavFile(file: File): ClientAudioQualityReport {
        if (!file.exists() || file.length() < 44) {
            return ClientAudioQualityReport(
                isValid = false,
                durationSeconds = 0f,
                snrDb = 0f,
                rmsEnergy = 0f,
                silenceRatio = 1f,
                clippingRatio = 0f,
                errorMessage = "Audio sample is missing or empty."
            )
        }

        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(44)
                val readHeader = fis.read(header)
                if (readHeader < 44) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = 0f,
                        snrDb = 0f,
                        rmsEnergy = 0f,
                        silenceRatio = 1f,
                        clippingRatio = 0f,
                        errorMessage = "Invalid WAV audio header."
                    )
                }

                val headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                if (riff != "RIFF" || wave != "WAVE") {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = 0f,
                        snrDb = 0f,
                        rmsEnergy = 0f,
                        silenceRatio = 1f,
                        clippingRatio = 0f,
                        errorMessage = "Audio format must be standard 16kHz PCM WAV."
                    )
                }

                headerBuffer.position(22)
                val channels = headerBuffer.short.toInt()
                val sampleRate = headerBuffer.int
                headerBuffer.position(34)
                val bitsPerSample = headerBuffer.short.toInt()

                if (sampleRate != EXPECTED_SAMPLE_RATE) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = 0f,
                        snrDb = 0f,
                        rmsEnergy = 0f,
                        silenceRatio = 0f,
                        clippingRatio = 0f,
                        errorMessage = "Sample rate ($sampleRate Hz) does not match required 16,000 Hz."
                    )
                }

                // Read audio samples
                val pcmData = fis.readBytes()
                val numSamples = pcmData.size / 2
                if (numSamples == 0) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = 0f,
                        snrDb = 0f,
                        rmsEnergy = 0f,
                        silenceRatio = 1f,
                        clippingRatio = 0f,
                        errorMessage = "Audio data payload is empty."
                    )
                }

                val duration = numSamples.toFloat() / EXPECTED_SAMPLE_RATE
                if (duration < MIN_DURATION_SECONDS) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = duration,
                        snrDb = 0f,
                        rmsEnergy = 0f,
                        silenceRatio = 0f,
                        clippingRatio = 0f,
                        errorMessage = "Sample too short (${"%.1f".format(duration)}s). Minimum 2.5 seconds required."
                    )
                }

                if (duration > MAX_DURATION_SECONDS) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = duration,
                        snrDb = 0f,
                        rmsEnergy = 0f,
                        silenceRatio = 0f,
                        clippingRatio = 0f,
                        errorMessage = "Sample too long (${"%.1f".format(duration)}s). Maximum 15 seconds allowed."
                    )
                }

                // Parse 16-bit PCM samples
                val sampleBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
                var sumSquares = 0.0
                var clippedCount = 0
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val s = sampleBuffer.short
                    samples[i] = s
                    sumSquares += (s * s).toDouble()
                    if (s >= 32500 || s <= -32500) {
                        clippedCount++
                    }
                }

                val rmsNormalized = (sqrt(sumSquares / numSamples) / 32768.0).toFloat()
                if (rmsNormalized < MIN_RMS_ENERGY) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = duration,
                        snrDb = 0f,
                        rmsEnergy = rmsNormalized,
                        silenceRatio = 1f,
                        clippingRatio = 0f,
                        errorMessage = "Audio volume is too low. Please speak closer to the microphone."
                    )
                }

                val clippingRatio = clippedCount.toFloat() / numSamples
                if (clippingRatio > MAX_CLIPPING_RATIO) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = duration,
                        snrDb = 0f,
                        rmsEnergy = rmsNormalized,
                        silenceRatio = 0f,
                        clippingRatio = clippingRatio,
                        errorMessage = "Audio clipping detected. Please reduce microphone proximity."
                    )
                }

                // Windowed silence detection (30ms frames)
                val frameSize = (EXPECTED_SAMPLE_RATE * 0.030).toInt()
                val totalFrames = numSamples / frameSize
                var silentFrames = 0
                val silenceThreshold = rmsNormalized * 0.25f

                for (f in 0 until totalFrames) {
                    var frameSum = 0.0
                    for (i in 0 until frameSize) {
                        val s = samples[f * frameSize + i]
                        frameSum += (s * s).toDouble()
                    }
                    val frameRms = (sqrt(frameSum / frameSize) / 32768.0).toFloat()
                    if (frameRms < silenceThreshold) {
                        silentFrames++
                    }
                }

                val silenceRatio = if (totalFrames > 0) silentFrames.toFloat() / totalFrames else 0f
                if (silenceRatio > MAX_SILENCE_RATIO) {
                    return ClientAudioQualityReport(
                        isValid = false,
                        durationSeconds = duration,
                        snrDb = 0f,
                        rmsEnergy = rmsNormalized,
                        silenceRatio = silenceRatio,
                        clippingRatio = clippingRatio,
                        errorMessage = "Too much background silence (${(silenceRatio * 100).toInt()}%). Please read prompt continuously."
                    )
                }

                val snrDb = (20 * log10(maxOf(1e-4f, rmsNormalized / 0.005f))).coerceIn(12f, 42f)

                return ClientAudioQualityReport(
                    isValid = true,
                    durationSeconds = duration,
                    snrDb = snrDb,
                    rmsEnergy = rmsNormalized,
                    silenceRatio = silenceRatio,
                    clippingRatio = clippingRatio,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            return ClientAudioQualityReport(
                isValid = false,
                durationSeconds = 0f,
                snrDb = 0f,
                rmsEnergy = 0f,
                silenceRatio = 1f,
                clippingRatio = 0f,
                errorMessage = "Audio inspection error: ${e.message}"
            )
        }
    }
}

