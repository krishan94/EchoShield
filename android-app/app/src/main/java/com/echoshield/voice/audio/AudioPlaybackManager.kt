package com.echoshield.voice.audio

import android.media.MediaPlayer
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

/**
 * Manages audio sample playback on Android using MediaPlayer.
 */
class AudioPlaybackManager {

    companion object {
        private const val TAG = "EchoShield.Playback"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0.0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    fun playAudio(file: File, coroutineScope: CoroutineScope) {
        stop()

        if (!file.exists()) {
            Log.e(TAG, "Audio file does not exist: ${file.absolutePath}")
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackProgress.value = 1.0f
                    progressJob?.cancel()
                }
                start()
            }
            _isPlaying.value = true
            _playbackProgress.value = 0.0f

            progressJob = coroutineScope.launch(Dispatchers.Main) {
                while (isActive && _isPlaying.value && mediaPlayer != null) {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val total = mediaPlayer?.duration ?: 1
                    if (total > 0) {
                        _playbackProgress.value = (current.toFloat() / total).coerceIn(0.0f, 1.0f)
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio file: ${e.message}", e)
            _isPlaying.value = false
            stop()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing MediaPlayer: ${e.message}")
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && !_isPlaying.value) {
                mediaPlayer?.start()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming MediaPlayer: ${e.message}")
        }
    }

    fun stop() {
        _isPlaying.value = false
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }
}

