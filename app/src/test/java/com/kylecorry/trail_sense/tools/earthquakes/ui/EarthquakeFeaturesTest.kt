package com.kylecorry.trail_sense.tools.earthquakes.ui

import com.kylecorry.trail_sense.tools.earthquakes.domain.Earthquake
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.time.Instant

internal class EarthquakeFeaturesTest {

    private val earthquake = Earthquake(
        id = "735337",
        time = Instant.parse("2026-11-26T05:10:42Z"),
        magnitude = 4.6f,
        depthKm = 7.3f,
        latitude = 37.13217,
        longitude = 27.864,
        location = "Milas (Muğla)"
    )

    @Test
    fun pointUsesLongitudeThenLatitude() {
        val point = EarthquakeFeatures.toFeature(earthquake).geometry() as Point

        assertEquals(27.864, point.longitude())
        assertEquals(37.13217, point.latitude())
    }

    @Test
    fun storesIdAndMagnitude() {
        val feature = EarthquakeFeatures.toFeature(earthquake)

        assertEquals("735337", EarthquakeFeatures.getId(feature))
        assertEquals(
            4.6,
            feature.getNumberProperty(EarthquakeFeatures.PROPERTY_MAGNITUDE).toDouble(),
            0.001
        )
    }

    @Test
    fun convertsEveryEarthquake() {
        val collection = EarthquakeFeatures.toFeatureCollection(
            listOf(earthquake, earthquake.copy(id = "2"), earthquake.copy(id = "3"))
        )

        assertEquals(
            listOf("735337", "2", "3"),
            collection.features()!!.map { EarthquakeFeatures.getId(it) }
        )
    }

    @Test
    fun clusterFeaturesHaveNoId() {
        // Clusters made by the map do not carry earthquake properties
        val cluster = Feature.fromGeometry(Point.fromLngLat(35.0, 39.0))

        assertNull(EarthquakeFeatures.getId(cluster))
    }
}
