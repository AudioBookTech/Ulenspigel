package com.KonstantinShramko.Ulenspigel

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM
import androidx.media3.common.Player.Commands
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var audioEngine: LyraAudioEngine
        private set

    private lateinit var lyraPlayerAdapter: LyraPlayerAdapter
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        audioEngine = LyraAudioEngine(this)
        lyraPlayerAdapter = LyraPlayerAdapter(Looper.getMainLooper(), audioEngine)
        mediaSession = MediaSession.Builder(this, lyraPlayerAdapter).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        audioEngine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Adapter linking custom LyraAudioEngine with Jetpack Media3 framework
     */
    private inner class LyraPlayerAdapter(
        looper: Looper,
        private val engine: LyraAudioEngine
    ) : SimpleBasePlayer(looper) {

        private var currentMediaItem: MediaItem? = null

        init {
            serviceScope.launch {
                engine.playbackState.collectLatest {
                    invalidateState()
                }
            }
            serviceScope.launch {
                engine.currentPositionMs.collectLatest {
                    invalidateState()
                }
            }
        }

        override fun getState(): State {
            val playerState = when (engine.playbackState.value) {
                PlaybackState.PLAYING, PlaybackState.PAUSED -> STATE_READY
                PlaybackState.COMPLETED -> STATE_ENDED
                PlaybackState.IDLE, PlaybackState.STOPPED -> STATE_IDLE
            }

            val playWhenReady = engine.playbackState.value == PlaybackState.PLAYING

            val availableCommands = Commands.Builder()
                .addAll(
                    COMMAND_PLAY_PAUSE,
                    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SET_MEDIA_ITEM
                )
                .build()

            val mediaItemData = MediaItemData.Builder(currentMediaItem ?: MediaItem.EMPTY)
                .setDurationUs(engine.durationMs.value * 1000L)
                .build()

            return State.Builder()
                .setAvailableCommands(availableCommands)
                .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(playerState)
                .setPlaylist(listOf(mediaItemData))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(engine.currentPositionMs.value)
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) {
                engine.play()
            } else {
                engine.pause()
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int
        ): ListenableFuture<*> {
            engine.seekTo(positionMs)
            return Futures.immediateVoidFuture()
        }

        fun playChapter(fileName: String, displayName: String) {
            currentMediaItem = MediaItem.Builder()
                .setMediaId(fileName)
                .setUri(fileName)
                .build()
            engine.prepare(fileName)
            engine.play()
            invalidateState()
        }
    }
}

