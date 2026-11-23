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
        val PLAY_PRO_ACTIVE = booleanPreferencesKey("is_pro")
        val DEBUG_PRO_OVERRIDE = booleanPreferencesKey("debug_pro_override")
        val ACTIVE_PLAN_PRODUCT_ID = stringPreferencesKey("active_plan_product_id")
        val COMPRESSION_LEVEL = stringPreferencesKey("compression_level")
        val AI_UNLOCKED_UNTIL = longPreferencesKey("ai_unlocked_until")
    }

    val isDarkMode: Flow<Boolean> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val playProActive: Flow<Boolean> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.PLAY_PRO_ACTIVE] ?: false
    }

    val debugProOverride: Flow<Boolean> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.DEBUG_PRO_OVERRIDE] ?: false
    }

    val activePlanProductId: Flow<String?> = context.paperlyDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PLAN_PRODUCT_ID]
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

    suspend fun setPlayProActive(enabled: Boolean, productId: String? = null) {
        context.paperlyDataStore.edit {
            it[Keys.PLAY_PRO_ACTIVE] = enabled
            if (enabled && productId != null) {
                it[Keys.ACTIVE_PLAN_PRODUCT_ID] = productId
            } else if (!enabled) {
                it.remove(Keys.ACTIVE_PLAN_PRODUCT_ID)
            }
        }
    }

    suspend fun setDebugProOverride(enabled: Boolean) {
        context.paperlyDataStore.edit { it[Keys.DEBUG_PRO_OVERRIDE] = enabled }
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
