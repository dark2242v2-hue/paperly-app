package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CompressionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.paperlyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "paperly_prefs"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val COMPRESSION_LEVEL = stringPreferencesKey("compression_level")
        val AI_UNLOCKED_UNTIL = longPreferencesKey("ai_unlocked_until")
    }

    val isDarkMode: Flow<Boolean> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val isPro: Flow<Boolean> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.IS_PRO] ?: false
    }

    val defaultCompression: Flow<CompressionLevel> = context.paperlyDataStore.data.map { prefs ->
        val raw = prefs[Keys.COMPRESSION_LEVEL]
        CompressionLevel.entries.firstOrNull { it.name == raw } ?: CompressionLevel.BALANCED
    }

    val aiUnlockedUntilMillis: Flow<Long> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.AI_UNLOCKED_UNTIL] ?: 0L
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.paperlyDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setPro(enabled: Boolean) {
        context.paperlyDataStore.edit { it[Keys.IS_PRO] = enabled }
    }

    suspend fun setDefaultCompression(level: CompressionLevel) {
        context.paperlyDataStore.edit { it[Keys.COMPRESSION_LEVEL] = level.name }
    }

    suspend fun grantAiSessionUnlock(durationMillis: Long = 30 * 60 * 1000L) {
        context.paperlyDataStore.edit {
            it[Keys.AI_UNLOCKED_UNTIL] = System.currentTimeMillis() + durationMillis
        }
    }
}
