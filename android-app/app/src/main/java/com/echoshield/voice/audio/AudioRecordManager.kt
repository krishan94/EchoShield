package com.echoshield.voice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Native Android Audio Capture Engine using AudioRecord.
 * Records standard 16,000Hz 16-bit Mono PCM audio for SpeechBrain ECAPA-TDNN compatibility.
 * Extracts live normalized amplitude samples to power Compose waveform visualizations.
 */
class AudioRecordManager(private val context: Context) {

    companion object {
        private const val TAG = "EchoShield.AudioRecord"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE: Short = 16
        const val CHANNELS: Short = 1
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0.0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val _waveformBars = MutableStateFlow(List(32) { 0.05f })
    val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Starts recording 16kHz PCM audio to a designated file.
     * Continuously samples buffer amplitudes to stream real-time waveform dynamics.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, coroutineScope: CoroutineScope) {
        if (_isRecording.value) {
            Log.w(TAG, "Recording already active.")
            return
        }

        currentOutputFile = outputFile
        _errorMessage.value = null

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, 2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _errorMessage.value = "Microphone hardware could not be initialized. Please check device permissions."
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            _elapsedSeconds.value = 0

            // Start capture loop on IO thread
            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val tempRawFile = File(context.cacheDir, "temp_raw_audio_${System.currentTimeMillis()}.pcm")
                val buffer = ShortArray(bufferSize / 2)
                var totalBytesWritten = 0

                val startTime = System.currentTimeMillis()

                try {
                    FileOutputStream(tempRawFile).use { fos ->
                        while (isActive && _isRecording.value) {
                            val readShorts = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                            if (readShorts > 0) {
                                // Calculate normalized RMS amplitude from buffer
                                var sumSquares = 0.0
                                val byteBuffer = ByteBuffer.allocate(readShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
                                for (i in 0 until readShorts) {
                                    val sample = buffer[i]
                                    byteBuffer.putShort(sample)
                                    sumSquares += (sample * sample).toDouble()
                                }
                                val rms = sqrt(sumSquares / readShorts)
                                val normalizedAmplitude = (rms / 32768.0).toFloat().coerceIn(0.0f, 1.0f)
                                _currentAmplitude.value = normalizedAmplitude

                                // Shift waveform bars with newest amplitude
                                val currentList = _waveformBars.value.toMutableList()
                                if (currentList.isNotEmpty()) {
                                    currentList.removeAt(0)
                                    currentList.add(maxOf(0.08f, normalizedAmplitude * 1.5f).coerceAtMost(1.0f))
                                    _waveformBars.value = currentList
                                }

                                fos.write(byteBuffer.array())
                                totalBytesWritten += readShorts * 2

                                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                                _elapsedSeconds.value = elapsedSec
                            } else {
                                delay(10)
                            }
                        }
                    }

                    // Convert raw PCM to standard 44-byte RIFF WAV
                    if (totalBytesWritten > 0 && currentOutputFile != null) {
                        writeWavHeaderAndCopy(tempRawFile, currentOutputFile!!, totalBytesWritten)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Audio capture error: ${e.message}", e)
                    _errorMessage.value = "Recording error: ${e.message}"
                } finally {
                    tempRawFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}", e)
            _errorMessage.value = "Microphone unavailable: ${e.message}"
            _isRecording.value = false
        }
    }

    /**
     * Stops audio recording and finalizes the WAV file.
     */
    fun stopRecording(): File? {
        _isRecording.value = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null
        }
        _currentAmplitude.value = 0.0f
        return currentOutputFile
    }

    /**
     * Cancels recording and deletes partial files.
     */
    fun cancelRecording() {
        _isRecording.value = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null
        }
        _currentAmplitude.value = 0.0f
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    /**
     * Wraps raw 16kHz PCM data with standard 44-byte RIFF WAV header.
     */
    private fun writeWavHeaderAndCopy(rawFile: File, wavFile: File, totalAudioLen: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalDataLen)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat 1 = PCM
        buffer.putShort(CHANNELS)
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort((CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()) // BlockAlign
        buffer.putShort(BITS_PER_SAMPLE)

        // data chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalAudioLen)

        FileOutputStream(wavFile).use { outStream ->
            outStream.write(header)
            rawFile.inputStream().use { inStream ->
                inStream.copyTo(outStream)
            }
        }
    }
}

