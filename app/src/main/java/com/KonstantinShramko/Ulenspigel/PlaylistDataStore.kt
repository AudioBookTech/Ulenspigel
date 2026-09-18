package com.KonstantinShramko.Ulenspigel

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

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class PlaylistDataStore(private val context: Context) {

    companion object {
        private val LAST_TRACK_INDEX = intPreferencesKey("last_track_index")
        private val LAST_TRACK_POSITION_MS = longPreferencesKey("last_track_position_ms")
        private val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        private val CONTROL_BEHAVIOR = stringPreferencesKey("control_behavior")
        private val JUMP_DURATION = intPreferencesKey("jump_duration")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
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

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val savedName = prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(savedName)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
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

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = themeMode.name
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

