package com.kylecorry.trail_sense.tools.earthquakes.ui

import com.kylecorry.trail_sense.tools.earthquakes.domain.Earthquake
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Converts earthquakes to GeoJSON features for the map.
 * Only the values the map styles and taps need are stored; the rest is looked up by id.
 */
object EarthquakeFeatures {

    const val PROPERTY_ID = "id"
    const val PROPERTY_MAGNITUDE = "magnitude"

    fun toFeatureCollection(earthquakes: List<Earthquake>): FeatureCollection {
        return FeatureCollection.fromFeatures(earthquakes.map { toFeature(it) })
    }

    fun toFeature(earthquake: Earthquake): Feature {
        // GeoJSON order is longitude, latitude
        val point = Point.fromLngLat(earthquake.longitude, earthquake.latitude)
        val feature = Feature.fromGeometry(point, null, earthquake.id)
        feature.addStringProperty(PROPERTY_ID, earthquake.id)
        feature.addNumberProperty(PROPERTY_MAGNITUDE, earthquake.magnitude)
        return feature
    }

    fun getId(feature: Feature): String? {
        return if (feature.hasNonNullValueForProperty(PROPERTY_ID)) {
            feature.getStringProperty(PROPERTY_ID)
        } else {
            null
        }
    }
}
