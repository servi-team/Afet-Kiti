package com.kylecorry.trail_sense.tools.earthquakes.infrastructure

import android.content.Context
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.tools.earthquakes.domain.EarthquakeSort
import com.kylecorry.trail_sense.tools.earthquakes.domain.EarthquakeTimeRange

/**
 * Remembers the filters chosen on the Recent Earthquakes screen.
 * Enum names are stored (not ordinals) so reordering an enum does not change saved choices.
 */
class EarthquakePreferences(context: Context) {

    private val preferences = PreferencesSubsystem.getInstance(context).preferences

    var timeRange: EarthquakeTimeRange
        get() = enumOrDefault(preferences.getString(KEY_TIME_RANGE), EarthquakeTimeRange.Day)
        set(value) = preferences.putString(KEY_TIME_RANGE, value.name)

    var sort: EarthquakeSort
        get() = enumOrDefault(preferences.getString(KEY_SORT), EarthquakeSort.Time)
        set(value) = preferences.putString(KEY_SORT, value.name)

    var minMagnitude: Float
        get() = preferences.getFloat(KEY_MIN_MAGNITUDE) ?: DEFAULT_MIN_MAGNITUDE
        set(value) = preferences.putFloat(KEY_MIN_MAGNITUDE, value)

    /**
     * True if the map was the last view shown, false for the list.
     */
    var showMap: Boolean
        get() = preferences.getBoolean(KEY_SHOW_MAP) ?: false
        set(value) = preferences.putBoolean(KEY_SHOW_MAP, value)

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    companion object {
        const val DEFAULT_MIN_MAGNITUDE = 2f
        private const val KEY_TIME_RANGE = "pref_earthquakes_time_range"
        private const val KEY_SORT = "pref_earthquakes_sort"
        private const val KEY_MIN_MAGNITUDE = "pref_earthquakes_min_magnitude"
        private const val KEY_SHOW_MAP = "pref_earthquakes_show_map"
    }
}
