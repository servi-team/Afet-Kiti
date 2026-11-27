package com.kylecorry.trail_sense.tools.earthquakes.ui

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.ItemEarthquakeBinding
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.tools.earthquakes.domain.Earthquake
import com.kylecorry.trail_sense.tools.earthquakes.domain.MagnitudeLevel
import java.time.ZoneId
import java.util.Locale

/**
 * Fills an earthquake card. Used by the list and by the card shown when a point on the map is tapped.
 */
object EarthquakeItemBinder {

    fun bind(binding: ItemEarthquakeBinding, earthquake: Earthquake) {
        val context = binding.root.context
        val formatter = FormatService.getInstance(context)
        val level = earthquake.level

        binding.earthquakeMagnitude.text = formatOneDecimal(earthquake.magnitude)
        binding.earthquakeMagnitude.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, getColor(level)))
        binding.earthquakeMagnitude.setTextColor(getTextColor(level))

        binding.earthquakeLocation.text = earthquake.location
        binding.earthquakeTime.text = formatter.formatDateTime(
            earthquake.time.atZone(ZoneId.systemDefault()),
            relative = true
        )
        binding.earthquakeDepth.text = context.getString(
            R.string.earthquakes_depth,
            formatOneDecimal(earthquake.depthKm)
        )
    }

    @ColorRes
    fun getColor(level: MagnitudeLevel): Int {
        return when (level) {
            MagnitudeLevel.Minor -> R.color.earthquake_minor
            MagnitudeLevel.Light -> R.color.earthquake_light
            MagnitudeLevel.Moderate -> R.color.earthquake_moderate
            MagnitudeLevel.Strong -> R.color.earthquake_strong
        }
    }

    /**
     * Text drawn on top of [getColor]: white only on the dark red used for strong earthquakes.
     */
    fun getTextColor(level: MagnitudeLevel): Int {
        return if (level == MagnitudeLevel.Strong) Color.WHITE else Color.BLACK
    }

    private fun formatOneDecimal(value: Float): String {
        return String.format(Locale.getDefault(), "%.1f", value)
    }
}
