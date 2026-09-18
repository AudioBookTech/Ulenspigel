# Project Source Code: Ulenspigel

## app/src/main/java/com/KonstantinShramko/Audiobook/MainActivity.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.startService()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showCover by rememberSaveable { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        delay(7000)
                        showCover = false
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        UlenspigelScreen(viewModel)

                        AnimatedVisibility(
                            visible = showCover,
                            exit = fadeOut(animationSpec = tween(durationMillis = 800))
                        ) {
                            CoverScreen(onDismiss = { showCover = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.cover),
            contentDescription = "Audiobook Cover",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UlenspigelScreen(viewModel: PlayerViewModel) {
    val playlist by viewModel.playlist.collectAsState()
    val currentIndex by viewModel.currentTrackIndex.collectAsState()
    val playbackState by viewModel.audioEngine.playbackState.collectAsState()
    val currentPositionMs by viewModel.audioEngine.currentPositionMs.collectAsState()
    val durationMs by viewModel.audioEngine.durationMs.collectAsState()

    var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayPositionMs = scrubbingPositionMs ?: currentPositionMs

    val currentTrack = playlist.getOrNull(currentIndex)
    val isPlaying = playbackState == PlaybackState.PLAYING

    // Navigation state
    var showPlaylistOnly by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { showSettings = false }
        )
    } else if (showPlaylistOnly) {
        // --- MODE 1: FULL SCREEN CHAPTER LIST ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Chapter List (${playlist.size})", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { showPlaylistOnly = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Player"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(playlist) { index, track ->
                    val isSelected = index == currentIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.playTrackAtIndex(index)
                                showPlaylistOnly = false // Return to player after selecting
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.fileName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- MODE 2: CLEAN PLAYER SCREEN ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ulenspigel", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Current Chapter Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isLandscape) 12.dp else 20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimeMs(displayPositionMs),
                                fontSize = if (isLandscape) 20.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(if (isLandscape) 36.dp else 56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatTimeMs((durationMs - displayPositionMs).coerceAtLeast(0L)),
                                fontSize = if (isLandscape) 20.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full title displayed without truncation
                        Text(
                            text = currentTrack?.displayName ?: "Loading chapters...",
                            fontSize = if (isLandscape) 17.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // SeekBar Section
                SeekBarSection(
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    enabled = currentTrack != null && durationMs > 0,
                    onSeek = { viewModel.seekTo(it) },
                    onScrubbing = { scrubbingPositionMs = it }
                )

                // Playback Control Buttons (List, Prev, Play/Pause, Next, Loop)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button to open Chapter List
                    IconButton(
                        onClick = { showPlaylistOnly = true },
                        enabled = playlist.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = "Chapter List",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Previous chapter / Jump
                    IconButton(
                        onClick = { viewModel.onPrevClicked() },
                        enabled = playlist.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause button
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        enabled = playlist.isNotEmpty(),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Next chapter / Jump
                    IconButton(
                        onClick = { viewModel.onNextClicked() },
                        enabled = playlist.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Settings button
                    IconButton(
                        onClick = { showSettings = true },
                        enabled = playlist.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeekBarSection(
    currentPositionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onScrubbing: (Long?) -> Unit
) {
    var isUserScrubbing by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (isUserScrubbing) sliderPosition else currentProgress,
            onValueChange = { newValue ->
                isUserScrubbing = true
                sliderPosition = newValue
                onScrubbing((sliderPosition * durationMs).toLong())
            },
            onValueChangeFinished = {
                val targetMs = (sliderPosition * durationMs).toLong()
                onSeek(targetMs)
                // We keep isUserScrubbing = true until onScrubbing(null) is called
                // but we call it here to clear the labels
                onScrubbing(null)
                isUserScrubbing = false
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val currentBehavior by viewModel.controlBehavior.collectAsState()
    val currentDuration by viewModel.jumpDurationSeconds.collectAsState()

    var behavior by remember { mutableStateOf(currentBehavior) }
    var jumpDuration by remember { mutableFloatStateOf(currentDuration.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Button Behavior
            Column {
                Text(
                    text = "Button Behavior",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { behavior = ControlBehavior.SKIP_CHAPTER }
                ) {
                    RadioButton(
                        selected = behavior == ControlBehavior.SKIP_CHAPTER,
                        onClick = { behavior = ControlBehavior.SKIP_CHAPTER }
                    )
                    Text("Switch Chapter", modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { behavior = ControlBehavior.JUMP_TIME }
                ) {
                    RadioButton(
                        selected = behavior == ControlBehavior.JUMP_TIME,
                        onClick = { behavior = ControlBehavior.JUMP_TIME }
                    )
                    Text("Jump in File", modifier = Modifier.padding(start = 8.dp))
                }
            }

            // Section 2: Jump Duration
            Column {
                Text(
                    text = "Jump Duration (seconds)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Slider(
                        value = jumpDuration,
                        onValueChange = { jumpDuration = it },
                        valueRange = 1f..1200f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = jumpDuration.toInt().toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.applySettings(behavior, jumpDuration.toInt())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Apply", fontSize = 16.sp)
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
```

## app/src/main/java/com/KonstantinShramko/Audiobook/FileInfo.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

/**
 * Data model for an audiobook chapter
 * @param fileName System file name in assets/audio/ (e.g., "Q1_001.lyra")
 * @param displayName Display name for the user (e.g., "Chapter 1")
 */
data class FileInfo(
    val fileName: String,
    val displayName: String
)
```

## app/src/main/java/com/KonstantinShramko/Audiobook/LyraAudioEngine.kt
```kotlin
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

                val decodedPcm = lyraDecoder?.decodeFrame(frameBuffer)
                if (decodedPcm != null && decodedPcm.isNotEmpty()) {
                    decodedCount++
                    audioTrack?.write(decodedPcm, 0, decodedPcm.size)
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
```

## app/src/main/java/com/KonstantinShramko/Audiobook/LyraDecoder.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

import android.util.Log

class LyraDecoder private constructor(
    val sampleRate: Int = 16000,
    val numChannels: Int = 1,
    val bitrate: Int = 3200
) {
    private var nativeHandle: Long = 0L

    companion object {
        private const val TAG = "LyraDecoder"

        init {
            // Load the native library.
            // We don't catch the error here so that it fails early with a clear message
            // if the library or its dependencies are missing or incompatible.
            System.loadLibrary("lyra_decoder")
        }

        fun create(sampleRate: Int = 16000, numChannels: Int = 1, bitrate: Int = 3200): LyraDecoder? {
            Log.d(TAG, "Creating LyraDecoder: rate=$sampleRate, channels=$numChannels, bitrate=$bitrate")
            val decoder = LyraDecoder(sampleRate, numChannels, bitrate)
            if (decoder.initNative()) {
                return decoder
            }
            return null
        }

        fun parseBitrateFromFileName(fileName: String): Int {
            return when {
                fileName.contains("Q1", ignoreCase = true) -> 3200
                fileName.contains("Q2", ignoreCase = true) -> 6000
                fileName.contains("Q3", ignoreCase = true) -> 9200
                else -> 3200
            }
        }

        /**
         * Frame size in bytes at 50 frames/sec (20 ms):
         * Q1 (3200 bps) -> 8 bytes
         * Q2 (6000 bps) -> 15 bytes
         * Q3 (9200 bps) -> 23 bytes
         */
        fun getFrameSizeBytes(bitrate: Int): Int {
            return when (bitrate) {
                3200 -> 8  // Q1
                6000 -> 15 // Q2
                9200 -> 23 // Q3
                else -> 8
            }
        }
    }

    // JNI signatures exactly match the C++ file:
    // Java_com_KonstantinShramko_Audiobook_LyraDecoder_init
    // Java_com_KonstantinShramko_Audiobook_LyraDecoder_decode
    // Java_com_KonstantinShramko_Audiobook_LyraDecoder_release
    private external fun init(sampleRateHz: Int, numChannels: Int, bitrate: Int): Long
    private external fun decode(decoderPtr: Long, encodedData: ByteArray): ShortArray?
    private external fun release(decoderPtr: Long)

    private fun initNative(): Boolean {
        nativeHandle = init(sampleRate, numChannels, bitrate)
        if (nativeHandle == 0L) {
            Log.e(TAG, "C++ init() returned 0")
            return false
        }
        Log.d(TAG, "C++ LyraDecoder created! Handle = $nativeHandle")
        return true
    }

    fun decodeFrame(encodedFrame: ByteArray): ShortArray? {
        if (nativeHandle == 0L) return null
        return decode(nativeHandle, encodedFrame)
    }

    fun reset() {
        if (nativeHandle != 0L) {
            release(nativeHandle)
            nativeHandle = 0L
        }
        initNative()
    }

    fun releaseDecoder() {
        if (nativeHandle != 0L) {
            release(nativeHandle)
            nativeHandle = 0L
            Log.d(TAG, "C++ LyraDecoder memory released")
        }
    }
}
```

## app/src/main/java/com/KonstantinShramko/Audiobook/PlaybackService.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

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
```

## app/src/main/java/com/KonstantinShramko/Audiobook/PlayerViewModel.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

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
```

## app/src/main/java/com/KonstantinShramko/Audiobook/PlaylistDataStore.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ulenspigel_prefs")

enum class RepeatMode {
    NO_LOOP,
    LOOP_ALL,
    LOOP_ONE
}

enum class ControlBehavior {
    SKIP_CHAPTER,
    JUMP_TIME
}

class PlaylistDataStore(private val context: Context) {

    companion object {
        private val LAST_TRACK_INDEX = intPreferencesKey("last_track_index")
        private val LAST_TRACK_POSITION_MS = longPreferencesKey("last_track_position_ms")
        private val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        private val CONTROL_BEHAVIOR = stringPreferencesKey("control_behavior")
        private val JUMP_DURATION = intPreferencesKey("jump_duration")
    }

    val lastTrackIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_TRACK_INDEX] ?: 0
    }

    val lastTrackPositionMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_TRACK_POSITION_MS] ?: 0L
    }

    val repeatMode: Flow<RepeatMode> = context.dataStore.data.map { prefs ->
        val savedName = prefs[REPEAT_MODE] ?: RepeatMode.LOOP_ALL.name
        try {
            RepeatMode.valueOf(savedName)
        } catch (e: Exception) {
            RepeatMode.LOOP_ALL
        }
    }

    val controlBehavior: Flow<ControlBehavior> = context.dataStore.data.map { prefs ->
        val savedName = prefs[CONTROL_BEHAVIOR] ?: ControlBehavior.SKIP_CHAPTER.name
        try {
            ControlBehavior.valueOf(savedName)
        } catch (e: Exception) {
            ControlBehavior.SKIP_CHAPTER
        }
    }

    val jumpDurationSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[JUMP_DURATION] ?: 15
    }

    suspend fun saveState(index: Int, positionMs: Long, repeatMode: RepeatMode) {
        context.dataStore.edit { prefs ->
            prefs[LAST_TRACK_INDEX] = index
            prefs[LAST_TRACK_POSITION_MS] = positionMs
            prefs[REPEAT_MODE] = repeatMode.name
        }
    }

    suspend fun saveSettings(behavior: ControlBehavior, jumpDuration: Int) {
        context.dataStore.edit { prefs ->
            prefs[CONTROL_BEHAVIOR] = behavior.name
            prefs[JUMP_DURATION] = jumpDuration
        }
    }

    suspend fun clearState() {
        context.dataStore.edit { prefs ->
            prefs.remove(LAST_TRACK_INDEX)
            prefs.remove(LAST_TRACK_POSITION_MS)
            prefs.remove(REPEAT_MODE)
        }
    }
}
```

## app/src/main/java/com/KonstantinShramko/Audiobook/TableOfContents.kt
```kotlin
package com.KonstantinShramko.Ulenspigel

import com.KonstantinShramko.Audiobook.LyraDecoder

/**
 * Table of contents containing the list of chapters (FileInfo).
 * You can easily modify this list to add, remove, or edit chapter files and display names.
 */
object TableOfContents {
    val chapters: List<FileInfo> = listOf(
        FileInfo(fileName = "Q1_0101.lyra", displayName = "Книга 1 події 1-10"),
        FileInfo(fileName = "Q1_0102.lyra", displayName = "події 10-13"),
        FileInfo(fileName = "Q1_0103.lyra", displayName = "події 13-21"),
        FileInfo(fileName = "Q1_0104.lyra", displayName = "події 22-29"),
        FileInfo(fileName = "Q1_0105.lyra", displayName = "події 30-35"),
        FileInfo(fileName = "Q1_0106.lyra", displayName = "події 35-42"),
        FileInfo(fileName = "Q1_0107.lyra", displayName = "події 43-51"),
        FileInfo(fileName = "Q1_0108.lyra", displayName = "події 51-57"),
        FileInfo(fileName = "Q1_0109.lyra", displayName = "події 57-67"),
        FileInfo(fileName = "Q1_0110.lyra", displayName = "події 68-78"),
        FileInfo(fileName = "Q1_0111.lyra", displayName = "події 79-80"),
        FileInfo(fileName = "Q1_0112.lyra", displayName = "події 81-85"),
        FileInfo(fileName = "Q1_0201.lyra", displayName = "Книга 2 події 1-5"),
        FileInfo(fileName = "Q1_0202.lyra", displayName = "події 5-11"),
        FileInfo(fileName = "Q1_0203.lyra", displayName = "події 11-15"),
        FileInfo(fileName = "Q1_0204.lyra", displayName = "події 15-20"),
        FileInfo(fileName = "Q1_0301.lyra", displayName = "Книга 3 події 1-10"),
        FileInfo(fileName = "Q1_0302.lyra", displayName = "події 11-23"),
        FileInfo(fileName = "Q1_0303.lyra", displayName = "події 23-28"),
        FileInfo(fileName = "Q1_0304.lyra", displayName = "події 28-34"),
        FileInfo(fileName = "Q1_0305.lyra", displayName = "події 34-40"),
        FileInfo(fileName = "Q1_0306.lyra", displayName = "події 40-44"),
        FileInfo(fileName = "Q1_0401.lyra", displayName = "Книга 4 події 1-5"),
        FileInfo(fileName = "Q1_0402.lyra", displayName = "події 5-8"),
        FileInfo(fileName = "Q1_0403.lyra", displayName = "події 9-15"),
        FileInfo(fileName = "Q1_0404.lyra", displayName = "події 16-22"),
        FileInfo(fileName = "Q1_0501.lyra", displayName = "Книга 5 події 1-4"),
        FileInfo(fileName = "Q1_0502.lyra", displayName = "події 5-7"),
        FileInfo(fileName = "Q1_0503.lyra", displayName = "події 7-10")
    )
}
```

## app/src/main/java/com/KonstantinShramko/Audiobook/ui/theme/Color.kt
```kotlin
package com.KonstantinShramko.Ulenspigel.ui.theme

import com.KonstantinShramko.Audiobook.LyraDecoder

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

## app/src/main/java/com/KonstantinShramko/Audiobook/ui/theme/Theme.kt
```kotlin
package com.KonstantinShramko.Ulenspigel.ui.theme

import com.KonstantinShramko.Audiobook.LyraDecoder

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun UlenspigelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

## app/src/main/java/com/KonstantinShramko/Audiobook/ui/theme/Type.kt
```kotlin
package com.KonstantinShramko.Ulenspigel.ui.theme

import com.KonstantinShramko.Audiobook.LyraDecoder

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
```

## app/src/main/AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions for Android 13+ (API 33+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Ulenspigel"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Ulenspigel"
        tools:targetApi="33">

        <activity
            android:name=".MainActivity"
            android:theme="@style/Theme.App.Starting"
            android:screenOrientation="fullSensor"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Background Media3 service declaration -->
        <service
            android:name=".PlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

## app/build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "com.KonstantinShramko.Ulenspigel"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.KonstantinShramko.Ulenspigel"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.3"

        // Sets a pretty name for the generated file (Audiobook-debug.apk)
        base.archivesName.set("Ulenspigel")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        sourceSets {
            getByName("main") {
                jniLibs.srcDirs("src/main/jniLibs")
            }
        }
    }

    // Set legacy packaging to false since the library is now 16 KB page-aligned.
    // This allows the library to be loaded directly from the APK (page-aligned).
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Compose icons (Play, Pause, Skip, Music)
    implementation("androidx.compose.material:material-icons-extended")

    // Media3
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
```

## gradle/libs.versions.toml
```toml
[versions]
agp = "9.3.1"
coreKtx = "1.19.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.11.0"
activityCompose = "1.13.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
media3 = "1.10.1"
datastore = "1.2.1"
coroutines = "1.11.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
# Media3
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
# ViewModel Compose
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version = "1.2.0" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinCompose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

## build.gradle.kts
```kotlin
// Top-level build file
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinCompose) apply false
}
```

## settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ulenspigel"
include(":app")
```

## app/proguard-rules.pro
```pro
# --- R8 / ProGuard Optimization Rules ---

# 1. JNI Protection
# Prevent R8 from obfuscating or removing any native methods.
# This ensures that the C++ side can still find the Java methods.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Explicitly protect the LyraDecoder class and its native methods.
# We also keep the 'nativeHandle' field as it's modified by native code.
-keep class com.KonstantinShramko.Ulenspigel.LyraDecoder {
    private long nativeHandle;
    private <methods>;
    public <methods>;
}

# 2. Media3 / AudioEngine Protection
# Protect the PlaybackState enum which might be used in logging or reflection.
-keep enum com.KonstantinShramko.Ulenspigel.PlaybackState { *; }

# 3. Serialization / DataStore
# If you use Gson or other reflection-based serializers, add rules here.
# For DataStore Preferences, usually no special rules are needed.
```

