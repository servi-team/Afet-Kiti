package com.kylecorry.trail_sense.tools.earthquakes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentRecentEarthquakesBinding
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.tools.earthquakes.domain.Earthquake
import com.kylecorry.trail_sense.tools.earthquakes.domain.EarthquakeSort
import com.kylecorry.trail_sense.tools.earthquakes.domain.EarthquakeTimeRange
import com.kylecorry.trail_sense.tools.earthquakes.infrastructure.AfadEarthquakeSource
import com.kylecorry.trail_sense.tools.earthquakes.infrastructure.EarthquakePreferences
import com.kylecorry.trail_sense.tools.earthquakes.infrastructure.EarthquakeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.util.Locale

class RecentEarthquakesFragment : BoundFragment<FragmentRecentEarthquakesBinding>() {

    private val source: EarthquakeSource = AfadEarthquakeSource()
    private val adapter = EarthquakeAdapter { openInMaps(it) }

    private lateinit var prefs: EarthquakePreferences
    private var timeRange = EarthquakeTimeRange.Day
    private var minMagnitude = EarthquakePreferences.DEFAULT_MIN_MAGNITUDE
    private var sort = EarthquakeSort.Time

    private var showMap = false
    private var hasContent = false
    private var mapController: EarthquakeMapController? = null

    private var earthquakes = emptyList<Earthquake>()
    private var loadJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Filters are kept in preferences: the app recreates tool screens from scratch
        // after configuration changes, so instance state would be lost
        prefs = EarthquakePreferences(requireContext())
        timeRange = prefs.timeRange
        minMagnitude = prefs.minMagnitude.takeIf { it in MIN_MAGNITUDES } ?: MIN_MAGNITUDES.first()
        sort = prefs.sort
        showMap = prefs.showMap

        binding.earthquakesList.adapter = adapter
        binding.earthquakesTitle.subtitle.text = ""
        binding.earthquakesTitle.rightButton.setOnClickListener { load() }
        binding.earthquakesRetryBtn.setOnClickListener { load() }
        binding.earthquakesTimeRange.setOnClickListener { pickTimeRange() }
        binding.earthquakesMinMagnitude.setOnClickListener { pickMinMagnitude() }
        binding.earthquakesSort.setOnClickListener { pickSort() }
        binding.earthquakesViewToggle.check(
            if (showMap) R.id.earthquakes_view_map else R.id.earthquakes_view_list
        )
        binding.earthquakesViewToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                setShowMap(checkedId == R.id.earthquakes_view_map)
            }
        }
        if (showMap) {
            createMapIfNeeded()
        }
        updateFilterLabels()
        updateContentVisibility()
        load()
    }

    override fun onDestroyView() {
        // The map view itself is destroyed by its view lifecycle observer
        mapController = null
        super.onDestroyView()
    }

    private fun load() {
        loadJob?.cancel()
        showLoading()
        val end = Instant.now()
        val start = end.minus(timeRange.duration)
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                earthquakes = source.getEarthquakes(start, end, minMagnitude)
                showEarthquakes(end)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                showMessage(R.string.earthquakes_error_connection, showRetry = true)
            } catch (_: Exception) {
                showMessage(R.string.earthquakes_error_server, showRetry = true)
            }
        }
    }

    private fun showLoading() {
        binding.earthquakesLoading.isVisible = true
        binding.earthquakesMessageView.isVisible = false
        binding.earthquakesTitle.rightButton.isEnabled = false
        hasContent = false
        updateContentVisibility()
    }

    private fun showEarthquakes(updatedAt: Instant) {
        binding.earthquakesLoading.isVisible = false
        binding.earthquakesTitle.rightButton.isEnabled = true
        val formatter = FormatService.getInstance(requireContext())
        binding.earthquakesTitle.subtitle.text = getString(
            R.string.earthquakes_updated,
            formatter.formatTime(updatedAt, includeSeconds = false)
        )
        if (earthquakes.isEmpty()) {
            showMessage(R.string.earthquakes_empty, showRetry = false)
            return
        }
        binding.earthquakesMessageView.isVisible = false
        hasContent = true
        updateContentVisibility()
        updateList(scrollToTop = true)
        mapController?.setEarthquakes(earthquakes)
        showSelectedEarthquake(null)
    }

    private fun showMessage(@StringRes message: Int, showRetry: Boolean) {
        binding.earthquakesLoading.isVisible = false
        binding.earthquakesTitle.rightButton.isEnabled = true
        hasContent = false
        updateContentVisibility()
        binding.earthquakesMessageView.isVisible = true
        binding.earthquakesMessage.setText(message)
        binding.earthquakesRetryBtn.isVisible = showRetry
    }

    private fun updateList(scrollToTop: Boolean) {
        adapter.submitList(sort.sort(earthquakes)) {
            if (scrollToTop && isBound) {
                binding.earthquakesList.scrollToPosition(0)
            }
        }
    }

    private fun setShowMap(show: Boolean) {
        if (show == showMap) {
            return
        }
        showMap = show
        prefs.showMap = show
        if (show) {
            createMapIfNeeded()
        }
        showSelectedEarthquake(null)
        updateContentVisibility()
    }

    /**
     * MapLibre is only started once the map is opened, so the list costs nothing extra.
     */
    private fun createMapIfNeeded() {
        if (mapController != null) {
            return
        }
        val controller = EarthquakeMapController(requireContext()) { showSelectedEarthquake(it) }
        binding.earthquakesMapHolder.addView(controller.view)
        viewLifecycleOwner.lifecycle.addObserver(controller)
        controller.setEarthquakes(earthquakes)
        mapController = controller
    }

    private fun updateContentVisibility() {
        binding.earthquakesList.isVisible = hasContent && !showMap
        binding.earthquakesMapContainer.isVisible = hasContent && showMap
        // Sorting only changes the order of the list
        binding.earthquakesSort.isVisible = !showMap
    }

    private fun showSelectedEarthquake(earthquake: Earthquake?) {
        val card = binding.earthquakesMapCard
        if (earthquake == null) {
            card.root.isVisible = false
            return
        }
        EarthquakeItemBinder.bind(card, earthquake)
        card.root.setOnClickListener { openInMaps(earthquake) }
        card.root.isVisible = true
    }

    private fun updateFilterLabels() {
        binding.earthquakesTimeRange.text = getString(getTimeRangeName(timeRange))
        binding.earthquakesMinMagnitude.text = formatMinMagnitude(minMagnitude)
        binding.earthquakesSort.text = getString(getSortName(sort))
    }

    private fun pickTimeRange() {
        val options = EarthquakeTimeRange.entries
        Pickers.item(
            requireContext(),
            getString(R.string.earthquakes_time_range),
            options.map { getString(getTimeRangeName(it)) },
            options.indexOf(timeRange)
        ) { index ->
            if (index != null && options[index] != timeRange) {
                timeRange = options[index]
                prefs.timeRange = timeRange
                updateFilterLabels()
                load()
            }
        }
    }

    private fun pickMinMagnitude() {
        Pickers.item(
            requireContext(),
            getString(R.string.earthquakes_min_magnitude),
            MIN_MAGNITUDES.map { formatMinMagnitude(it) },
            MIN_MAGNITUDES.indexOf(minMagnitude)
        ) { index ->
            if (index != null && MIN_MAGNITUDES[index] != minMagnitude) {
                minMagnitude = MIN_MAGNITUDES[index]
                prefs.minMagnitude = minMagnitude
                updateFilterLabels()
                load()
            }
        }
    }

    private fun pickSort() {
        val options = EarthquakeSort.entries
        Pickers.item(
            requireContext(),
            getString(R.string.sort),
            options.map { getString(getSortName(it)) },
            options.indexOf(sort)
        ) { index ->
            if (index != null && options[index] != sort) {
                sort = options[index]
                prefs.sort = sort
                updateFilterLabels()
                // Sorting is done locally, no need to download again
                updateList(scrollToTop = true)
            }
        }
    }

    private fun openInMaps(earthquake: Earthquake) {
        val lat = earthquake.latitude
        val lon = earthquake.longitude
        val label = Uri.encode(earthquake.location)
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.earthquakes_no_map_app, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun formatMinMagnitude(magnitude: Float): String {
        return getString(
            R.string.earthquakes_magnitude_at_least,
            String.format(Locale.getDefault(), "%.1f", magnitude)
        )
    }

    @StringRes
    private fun getTimeRangeName(range: EarthquakeTimeRange): Int {
        return when (range) {
            EarthquakeTimeRange.Day -> R.string.earthquakes_last_day
            EarthquakeTimeRange.Week -> R.string.earthquakes_last_week
            EarthquakeTimeRange.Month -> R.string.earthquakes_last_month
        }
    }

    @StringRes
    private fun getSortName(sort: EarthquakeSort): Int {
        return when (sort) {
            EarthquakeSort.Time -> R.string.earthquakes_sort_time
            EarthquakeSort.Magnitude -> R.string.earthquakes_sort_magnitude
            EarthquakeSort.Depth -> R.string.earthquakes_sort_depth
            EarthquakeSort.Location -> R.string.earthquakes_sort_location
        }
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentRecentEarthquakesBinding {
        return FragmentRecentEarthquakesBinding.inflate(layoutInflater, container, false)
    }

    companion object {
        private val MIN_MAGNITUDES = listOf(2f, 3f, 4f)
    }
}
