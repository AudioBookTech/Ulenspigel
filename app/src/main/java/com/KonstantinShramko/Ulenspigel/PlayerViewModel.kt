package com.KonstantinShramko.Ulenspigel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val audioEngine = LyraAudioEngine(context)
    private val dataStore = PlaylistDataStore(context)

    private val _playlist = MutableStateFlow<List<FileInfo>>(emptyList())
    val playlist: StateFlow<List<FileInfo>> = _playlist.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.LOOP_ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _controlBehavior = MutableStateFlow(ControlBehavior.SKIP_CHAPTER)
    val controlBehavior: StateFlow<ControlBehavior> = _controlBehavior.asStateFlow()

    private val _jumpDurationSeconds = MutableStateFlow(15)
    val jumpDurationSeconds: StateFlow<Int> = _jumpDurationSeconds.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        loadChaptersFromAssets()

        // Restore saved state (chapter, position ms, repeat mode, control settings)
        viewModelScope.launch {
            try {
                // Settings
                launch {
                    dataStore.controlBehavior.collect { _controlBehavior.value = it }
                }
                launch {
                    dataStore.jumpDurationSeconds.collect { _jumpDurationSeconds.value = it }
                }
                launch {
                    dataStore.themeMode.collect { _themeMode.value = it }
                }

                val savedMode = dataStore.repeatMode.first()
                _repeatMode.value = savedMode

                val savedIndex = dataStore.lastTrackIndex.first()
                val savedPos = dataStore.lastTrackPositionMs.first()

                val currentList = _playlist.value
                if (currentList.isNotEmpty()) {
                    val validIndex = savedIndex.coerceIn(0, currentList.lastIndex)
                    _currentTrackIndex.value = validIndex
                    val track = currentList[validIndex]

                    // Prepare the player and set the saved position
                    audioEngine.prepare(track.fileName, savedPos)
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error restoring state from DataStore", e)
            }
        }

        // Auto-transition to the next chapter when the current one finishes
        viewModelScope.launch {
            audioEngine.playbackState.collect { state ->
                if (state == PlaybackState.COMPLETED) {
                    onTrackCompleted()
                }
            }
        }

        // Auto-save position every 2 seconds during playback
        viewModelScope.launch {
            while (true) {
                delay(2000)
                if (audioEngine.playbackState.value == PlaybackState.PLAYING) {
                    saveCurrentState()
                }
            }
        }
    }

    private fun loadChaptersFromAssets() {
        // Load playlist directly from TableOfContents
        _playlist.value = TableOfContents.chapters
    }

    private fun onTrackCompleted() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        when (_repeatMode.value) {
            RepeatMode.LOOP_ONE -> {
                changeTrack(_currentTrackIndex.value, 0L, forcePlay = true)
            }
            RepeatMode.LOOP_ALL -> {
                changeTrack(getNextIndex(), 0L, forcePlay = true)
            }
            RepeatMode.NO_LOOP -> {
                if (_currentTrackIndex.value < tracks.lastIndex) {
                    changeTrack(_currentTrackIndex.value + 1, 0L, forcePlay = true)
                } else {
                    audioEngine.stop()
                }
            }
        }
    }

    fun startService() {
        val intent = Intent(context, PlaybackService::class.java)
        // Use startService instead of startForegroundService to avoid the crash.
        // MediaSessionService will handle the foreground transition when playback starts.
        context.startService(intent)
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.NO_LOOP -> RepeatMode.LOOP_ALL
            RepeatMode.LOOP_ALL -> RepeatMode.LOOP_ONE
            RepeatMode.LOOP_ONE -> RepeatMode.NO_LOOP
        }
        _repeatMode.value = nextMode
        saveCurrentState()
    }

    fun playTrackAtIndex(index: Int) {
        changeTrack(index, 0L, forcePlay = true)
    }

    fun togglePlayPause() {
        if (audioEngine.playbackState.value == PlaybackState.PLAYING) {
            audioEngine.pause()
            saveCurrentState()
        } else if (audioEngine.playbackState.value == PlaybackState.PAUSED) {
            audioEngine.play()
        } else if (_playlist.value.isNotEmpty()) {
            changeTrack(_currentTrackIndex.value, audioEngine.currentPositionMs.value, forcePlay = true)
        }
    }

    private fun getNextIndex(): Int {
        val current = _currentTrackIndex.value
        val tracks = _playlist.value
        if (tracks.isEmpty()) return 0
        return (current + 1) % tracks.size
    }

    private fun getPrevIndex(): Int {
        val current = _currentTrackIndex.value
        val tracks = _playlist.value
        if (tracks.isEmpty()) return 0
        return if (current > 0) current - 1 else tracks.lastIndex
    }

    private fun changeTrack(index: Int, initialPositionMs: Long = 0L, forcePlay: Boolean = false) {
        val tracks = _playlist.value
        if (index !in tracks.indices) return

        val wasPlaying = audioEngine.playbackState.value == PlaybackState.PLAYING
        _currentTrackIndex.value = index
        val track = tracks[index]

        // Prepare the engine. If initialPositionMs is negative, we seek to (Duration + initialPositionMs)
        audioEngine.prepare(track.fileName, if (initialPositionMs < 0) 0L else initialPositionMs)

        if (initialPositionMs < 0) {
            val duration = audioEngine.durationMs.value
            val target = (duration + initialPositionMs).coerceAtLeast(0L)
            audioEngine.seekTo(target)
        }

        if (wasPlaying || forcePlay) {
            audioEngine.play()
        }
        saveCurrentState()
    }

    fun nextTrack() {
        changeTrack(getNextIndex(), 0L)
    }

    fun previousTrack() {
        changeTrack(getPrevIndex(), 0L)
    }

    fun onNextClicked() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        if (_controlBehavior.value == ControlBehavior.JUMP_TIME) {
            val current = audioEngine.currentPositionMs.value
            val duration = audioEngine.durationMs.value
            val jump = _jumpDurationSeconds.value * 1000L
            val target = current + jump

            if (target >= duration) {
                changeTrack(getNextIndex(), 0L)
            } else {
                seekTo(target)
            }
        } else {
            nextTrack()
        }
    }

    fun onPrevClicked() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        if (_controlBehavior.value == ControlBehavior.JUMP_TIME) {
            val current = audioEngine.currentPositionMs.value
            val jump = _jumpDurationSeconds.value * 1000L
            val target = current - jump

            if (target < 0) {
                changeTrack(getPrevIndex(), -20000L) // -20 seconds
            } else {
                seekTo(target)
            }
        } else {
            previousTrack()
        }
    }

    fun applySettings(behavior: ControlBehavior, jumpDuration: Int) {
        _controlBehavior.value = behavior
        _jumpDurationSeconds.value = jumpDuration
        viewModelScope.launch {
            dataStore.saveSettings(behavior, jumpDuration)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            dataStore.saveThemeMode(mode)
        }
    }

    fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
        saveCurrentState()
    }

    fun saveCurrentState() {
        viewModelScope.launch {
            dataStore.saveState(
                index = _currentTrackIndex.value,
                positionMs = audioEngine.currentPositionMs.value,
                repeatMode = _repeatMode.value
            )
        }
    }

    override fun onCleared() {
        saveCurrentState()
        super.onCleared()
        audioEngine.release()
    }
}

