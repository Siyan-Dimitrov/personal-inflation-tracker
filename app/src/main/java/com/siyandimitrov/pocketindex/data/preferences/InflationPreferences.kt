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

    fun setBaseWindowDays(days: Long) {
        require(days in MIN_BASE_WINDOW_DAYS..MAX_BASE_WINDOW_DAYS) {
            "Base window must be between 2 and 26 weeks."
        }
        preferences.edit().putLong(KEY_BASE_WINDOW_DAYS, days).apply()
    }

    private companion object {
        const val FILE_NAME = "inflation_preferences"
        const val KEY_BASE_WINDOW_DAYS = "base_window_days"
        const val MIN_BASE_WINDOW_DAYS = 14L
        const val MAX_BASE_WINDOW_DAYS = 182L
    }
}
