package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

enum class PlaybackState { IDLE, PLAYING, PAUSED, STOPPED, COMPLETED }

class LyraAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "LyraAudioEngine"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_DURATION_MS = 20L
    }

    private var audioTrack: AudioTrack? = null
    private var lyraDecoder: LyraDecoder? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentFileName: String? = null
    private var fileTotalBytes: Long = 0L
    private var currentBitrate: Int = 3200
    private var currentFrameBytes: Int = 8

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    @Volatile
    private var isPaused = false

    @Volatile
    private var seekRequestedMs: Long? = null

    /**
     * Preparing the chapter file from assets/audio/
     */
    fun prepare(fileName: String, initialPositionMs: Long = 0L) {
        stop()
        currentFileName = fileName
        currentBitrate = LyraDecoder.parseBitrateFromFileName(fileName)
        currentFrameBytes = LyraDecoder.getFrameSizeBytes(currentBitrate)

        Log.d(TAG, "Preparing asset: $fileName | Bitrate: $currentBitrate bps | Frame size: $currentFrameBytes bytes")

        try {
            context.assets.open("audio/$fileName").use { inputStream ->
                fileTotalBytes = inputStream.available().toLong()
                val totalFrames = fileTotalBytes / currentFrameBytes
                _durationMs.value = totalFrames * FRAME_DURATION_MS
                Log.d(TAG, "File: $fileTotalBytes bytes | Frames: $totalFrames | Duration: ${_durationMs.value} ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening asset audio/$fileName", e)
            _playbackState.value = PlaybackState.IDLE
            return
        }

        lyraDecoder?.releaseDecoder()
        
        scope.launch {
            val decoder = withContext(Dispatchers.IO) {
                LyraDecoder.create(sampleRate = SAMPLE_RATE, numChannels = 1, bitrate = currentBitrate)
            }
            lyraDecoder = decoder
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        _playbackState.value = PlaybackState.IDLE
        _currentPositionMs.value = initialPositionMs
        if (initialPositionMs > 0) {
            seekRequestedMs = initialPositionMs
        }
    }

    fun play() {
        if (currentFileName == null) return

        if (_playbackState.value == PlaybackState.PAUSED) {
            isPaused = false
            _playbackState.value = PlaybackState.PLAYING
            audioTrack?.play()
            return
        }

        _playbackState.value = PlaybackState.PLAYING
        audioTrack?.play()

        playbackJob?.cancel()
        playbackJob = scope.launch {
            runPlaybackLoop()
        }
    }

    fun pause() {
        isPaused = true
        _playbackState.value = PlaybackState.PAUSED
        audioTrack?.pause()
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        isPaused = false

        try {
            audioTrack?.stop()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }

        _playbackState.value = PlaybackState.STOPPED
        _currentPositionMs.value = 0L
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value)
        seekRequestedMs = clamped
        _currentPositionMs.value = clamped // Update immediately for UI smoothness
    }

    private suspend fun runPlaybackLoop() = withContext(Dispatchers.IO) {
        val fileName = currentFileName ?: return@withContext
        var inputStream: InputStream? = null

        try {
            inputStream = context.assets.open("audio/$fileName")
            val frameBuffer = ByteArray(currentFrameBytes)
            var currentFrameIndex = 0L
            var decodedCount = 0
            var errorCount = 0

            while (coroutineContext.isActive) {
                while (isPaused && coroutineContext.isActive && seekRequestedMs == null) {
                    delay(20)
                }

                // Seek to the target frame
                seekRequestedMs?.let { seekMs ->
                    val targetFrame = seekMs / FRAME_DURATION_MS
                    val byteOffset = targetFrame * currentFrameBytes
                    inputStream?.close()
                    inputStream = context.assets.open("audio/$fileName")
                    inputStream?.skip(byteOffset)
                    currentFrameIndex = targetFrame
                    _currentPositionMs.value = seekMs

                    lyraDecoder?.reset()
                    audioTrack?.pause()
                    audioTrack?.flush()
                    if (!isPaused) {
                        audioTrack?.play()
                    }
                    seekRequestedMs = null
                }

                if (isPaused) continue // Go back to wait loop if still paused after seek

                val bytesRead = inputStream?.read(frameBuffer, 0, currentFrameBytes) ?: -1
                if (bytesRead < currentFrameBytes) {
                    Log.d(TAG, "Playback finished! Decoded frames: $decodedCount, errors: $errorCount")
                    _playbackState.value = PlaybackState.COMPLETED
                    _currentPositionMs.value = _durationMs.value
                    break
                }

                // Use the new optimized decoding method
                val samplesCount = lyraDecoder?.decodeFrameToBuffer(frameBuffer) ?: 0
                if (samplesCount > 0) {
                    decodedCount++
                    val buffer = lyraDecoder?.pcmBuffer
                    if (buffer != null) {
                        buffer.position(0)
                        buffer.limit(samplesCount * 2) // 2 bytes per short sample
                        // Write ByteBuffer directly to AudioTrack
                        audioTrack?.write(buffer, samplesCount * 2, AudioTrack.WRITE_BLOCKING)
                    }
                } else {
                    errorCount++
                }

                currentFrameIndex++
                _currentPositionMs.value = currentFrameIndex * FRAME_DURATION_MS
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "Playback loop error: ${e.message}")
            }
        } finally {
            inputStream?.close()
        }
    }

    fun release() {
        stop()
        lyraDecoder?.releaseDecoder()
        lyraDecoder = null
        scope.cancel()
    }
}

