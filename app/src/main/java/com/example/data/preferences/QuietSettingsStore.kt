package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.QuietZonePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quietCareDataStore by preferencesDataStore(name = "quiet_care_settings")

data class QuietPreferences(
    val thresholdDb: Int = QuietZonePreset.GENERAL_WARD.thresholdDb,
    val zoneName: String = QuietZonePreset.GENERAL_WARD.labelMm,
    val autoGuardEnabled: Boolean = true,
    val autoLogViolations: Boolean = true,
    val defaultFacilityName: String = ""
)

class QuietSettingsStore(private val context: Context) {

    private object Keys {
        val THRESHOLD_DB = intPreferencesKey("threshold_db")
        val ZONE_NAME = stringPreferencesKey("zone_name")
        val AUTO_GUARD = booleanPreferencesKey("auto_guard_enabled")
        val AUTO_LOG = booleanPreferencesKey("auto_log_violations")
        val FACILITY_NAME = stringPreferencesKey("default_facility_name")
    }

    val preferences: Flow<QuietPreferences> = context.quietCareDataStore.data.map { prefs ->
        QuietPreferences(
            thresholdDb = prefs[Keys.THRESHOLD_DB] ?: QuietZonePreset.GENERAL_WARD.thresholdDb,
            zoneName = prefs[Keys.ZONE_NAME] ?: QuietZonePreset.GENERAL_WARD.labelMm,
            autoGuardEnabled = prefs[Keys.AUTO_GUARD] ?: true,
            autoLogViolations = prefs[Keys.AUTO_LOG] ?: true,
            defaultFacilityName = prefs[Keys.FACILITY_NAME] ?: ""
        )
    }

    suspend fun setZonePreset(preset: QuietZonePreset) {
        context.quietCareDataStore.edit { prefs ->
            prefs[Keys.THRESHOLD_DB] = preset.thresholdDb
            prefs[Keys.ZONE_NAME] = preset.labelMm
        }
    }

    suspend fun setCustomThreshold(thresholdDb: Int) {
        context.quietCareDataStore.edit { prefs ->
            prefs[Keys.THRESHOLD_DB] = thresholdDb.coerceIn(30, 85)
        }
    }

    suspend fun setAutoGuardEnabled(enabled: Boolean) {
        context.quietCareDataStore.edit { prefs ->
            prefs[Keys.AUTO_GUARD] = enabled
        }
    }

    suspend fun setAutoLogViolations(enabled: Boolean) {
        context.quietCareDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOG] = enabled
        }
    }

    suspend fun setDefaultFacilityName(name: String) {
        context.quietCareDataStore.edit { prefs ->
            prefs[Keys.FACILITY_NAME] = name.trim().take(120)
        }
    }
}
