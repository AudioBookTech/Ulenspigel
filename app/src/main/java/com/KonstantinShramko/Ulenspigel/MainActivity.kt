package com.KonstantinShramko.Ulenspigel

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.KonstantinShramko.Ulenspigel.ui.theme.UlenspigelTheme
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
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            UlenspigelTheme(darkTheme = darkTheme) {
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
    val currentThemeMode by viewModel.themeMode.collectAsState()

    var behavior by remember { mutableStateOf(currentBehavior) }
    var jumpDuration by remember { mutableFloatStateOf(currentDuration.toFloat()) }
    var themeMode by remember { mutableStateOf(currentThemeMode) }

    val scrollState = rememberScrollState()

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
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Appearance
            Column {
                Text(
                    text = "Appearance",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemeOption("System Default", ThemeMode.SYSTEM, themeMode) { themeMode = it }
                ThemeOption("Light (Paper & Leather)", ThemeMode.LIGHT, themeMode) { themeMode = it }
                ThemeOption("Dark (Midnight Library)", ThemeMode.DARK, themeMode) { themeMode = it }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Section 1: Previous, Next Buttons Behavior
            Column {
                Text(
                    text = "Previous, Next Buttons Behavior",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
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
                    Text("Jump in/over Chapter", modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Section 2: Jump Duration
            Column {
                Text(
                    text = "Jump Duration (${jumpDuration.toInt()} seconds)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
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
                        valueRange = 1f..60f,
                        steps = 60,
                        modifier = Modifier.fillMaxWidth()
                        //modifier = Modifier.weight(1f)
                    )
                    //Spacer(modifier = Modifier.width(16.dp))
                    //Text(
                    //    text = jumpDuration.toInt().toString(),
                    //    fontSize = 18.sp,
                    //    fontWeight = FontWeight.Bold,
                    //    modifier = Modifier.width(48.dp),
                    //    textAlign = TextAlign.End
                    //)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.applySettings(behavior, jumpDuration.toInt())
                    viewModel.setThemeMode(themeMode)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ThemeOption(
    text: String,
    mode: ThemeMode,
    selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { onSelect(mode) }
        )
        Text(text, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

