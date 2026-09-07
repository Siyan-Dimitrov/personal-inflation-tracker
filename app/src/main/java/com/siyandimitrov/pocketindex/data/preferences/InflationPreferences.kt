package com.siyandimitrov.pocketindex.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.siyandimitrov.pocketindex.domain.BaseWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Which service reads receipt photos. Claude is first choice; Ollama the alternative. */
enum class VisionProvider { CLAUDE, OLLAMA }

/**
 * Where receipt photos are sent for AI reading. Claude (Anthropic's API) is the default and
 * needs an API key. Ollama's hosted service also needs a key; a PC on the home network
 * (`http://192.168.0.9:11434`) needs none. Claude without a key, or Ollama with an empty
 * [serverUrl], keeps scanning fully on-device.
 */
data class VisionSettings(
    val provider: VisionProvider = VisionProvider.CLAUDE,
    val claudeApiKey: String = "",
    val serverUrl: String = DEFAULT_SERVER_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    val isCloud: Boolean
        get() = serverUrl.contains("ollama.com", ignoreCase = true)

    /** The hosted services reject unauthenticated calls, so without a key nothing is sent. */
    val isEnabled: Boolean
        get() = when (provider) {
            VisionProvider.CLAUDE -> claudeApiKey.isNotBlank()
            VisionProvider.OLLAMA -> serverUrl.isNotBlank() && (!isCloud || apiKey.isNotBlank())
        }

    /** The model the active provider will be asked for. */
    val activeModel: String
        get() = if (provider == VisionProvider.CLAUDE) CLAUDE_MODEL else model

    companion object {
        const val CLAUDE_MODEL = "claude-sonnet-5"
        const val DEFAULT_SERVER_URL = "https://ollama.com"
        const val DEFAULT_MODEL = "gemma4:31b"
    }
}

@Singleton
class InflationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val baseWindowDays: Flow<Long> = callbackFlow {
        fun emitValue() {
            trySend(preferences.getLong(KEY_BASE_WINDOW_DAYS, BaseWindow.DEFAULT_DURATION_DAYS))
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BASE_WINDOW_DAYS) emitValue()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        emitValue()
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    val chartRangeMonths: Flow<Int> = callbackFlow {
        fun emitValue() {
            trySend(preferences.getInt(KEY_CHART_RANGE_MONTHS, DEFAULT_CHART_RANGE_MONTHS))
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CHART_RANGE_MONTHS) emitValue()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        emitValue()
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Reactive copy for the settings screen; extraction reads [visionSettings] directly. */
    val visionSettingsFlow: Flow<VisionSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in VISION_KEYS) trySend(visionSettings)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(visionSettings)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    val visionSettings: VisionSettings
        get() = VisionSettings(
            provider = preferences.getString(KEY_VISION_PROVIDER, null)
                ?.let { name -> VisionProvider.entries.firstOrNull { it.name == name } }
                ?: VisionProvider.CLAUDE,
            claudeApiKey = preferences.getString(KEY_VISION_CLAUDE_API_KEY, null).orEmpty(),
            // Only an address the user never touched falls back to the default: a cleared
            // field is stored as "" and means off.
            serverUrl = preferences.getString(KEY_VISION_SERVER_URL, VisionSettings.DEFAULT_SERVER_URL)
                .orEmpty(),
            apiKey = preferences.getString(KEY_VISION_API_KEY, null).orEmpty(),
            model = preferences.getString(KEY_VISION_MODEL, null)
                ?.takeIf(String::isNotBlank)
                ?: VisionSettings.DEFAULT_MODEL,
        )

    fun setVisionSettings(settings: VisionSettings) {
        preferences.edit()
            .putString(KEY_VISION_PROVIDER, settings.provider.name)
            .putString(KEY_VISION_CLAUDE_API_KEY, settings.claudeApiKey.trim())
            .putString(KEY_VISION_SERVER_URL, settings.serverUrl.trim().trimEnd('/'))
            .putString(KEY_VISION_API_KEY, settings.apiKey.trim())
            .putString(KEY_VISION_MODEL, settings.model.trim())
            .apply()
    }

    fun setBaseWindowDays(days: Long) {
        require(days in MIN_BASE_WINDOW_DAYS..MAX_BASE_WINDOW_DAYS) {
            "Base window must be between 2 and 26 weeks."
        }
        preferences.edit().putLong(KEY_BASE_WINDOW_DAYS, days).apply()
    }

    fun setChartRangeMonths(months: Int) {
        require(months in CHART_RANGE_OPTIONS) { "Unsupported chart range: $months months." }
        preferences.edit().putInt(KEY_CHART_RANGE_MONTHS, months).apply()
    }

    /** True once the user has wiped their data, so debug builds never re-seed demo history. */
    val isDemoSeedBlocked: Boolean
        get() = preferences.getBoolean(KEY_DEMO_SEED_BLOCKED, false)

    /** Written synchronously: a reset interrupted by process death must not restore demo data. */
    fun blockDemoSeed() {
        preferences.edit().putBoolean(KEY_DEMO_SEED_BLOCKED, true).commit()
    }

    /** Clears the user-configurable settings only; the demo seed block must survive a reset. */
    fun resetToDefaults() {
        preferences.edit()
            .remove(KEY_BASE_WINDOW_DAYS)
            .remove(KEY_CHART_RANGE_MONTHS)
            .remove(KEY_VISION_PROVIDER)
            .remove(KEY_VISION_CLAUDE_API_KEY)
            .remove(KEY_VISION_SERVER_URL)
            .remove(KEY_VISION_API_KEY)
            .remove(KEY_VISION_MODEL)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "inflation_preferences"
        const val KEY_BASE_WINDOW_DAYS = "base_window_days"
        const val KEY_CHART_RANGE_MONTHS = "chart_range_months"
        const val KEY_DEMO_SEED_BLOCKED = "demo_seed_blocked"
        const val KEY_VISION_PROVIDER = "vision_provider"
        const val KEY_VISION_CLAUDE_API_KEY = "vision_claude_api_key"
        const val KEY_VISION_SERVER_URL = "vision_server_url"
        const val KEY_VISION_API_KEY = "vision_api_key"
        const val KEY_VISION_MODEL = "vision_model"
        val VISION_KEYS = setOf(
            KEY_VISION_PROVIDER,
            KEY_VISION_CLAUDE_API_KEY,
            KEY_VISION_SERVER_URL,
            KEY_VISION_API_KEY,
            KEY_VISION_MODEL,
        )
        const val DEFAULT_CHART_RANGE_MONTHS = 6
        const val MIN_BASE_WINDOW_DAYS = 14L
        const val MAX_BASE_WINDOW_DAYS = 182L
        val CHART_RANGE_OPTIONS = setOf(0, 1, 6, 12)
    }
}
