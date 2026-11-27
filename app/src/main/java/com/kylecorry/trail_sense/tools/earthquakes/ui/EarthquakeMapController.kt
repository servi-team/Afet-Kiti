package com.kylecorry.trail_sense.tools.earthquakes.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kylecorry.trail_sense.tools.earthquakes.domain.Earthquake
import com.kylecorry.trail_sense.tools.earthquakes.domain.MagnitudeLevel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.accumulated
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.util.Locale

/**
 * Shows earthquakes on a MapLibre map with OpenFreeMap tiles (free, no account or API key).
 * Nearby earthquakes are clustered; a cluster is colored by the strongest earthquake inside it.
 *
 * Add it to the view lifecycle with `lifecycle.addObserver(controller)` so the map is started,
 * paused and destroyed with the fragment's view.
 */
class EarthquakeMapController(
    context: Context,
    private val onEarthquakeSelected: (Earthquake?) -> Unit
) : DefaultLifecycleObserver {

    val view: MapView

    private val appContext = context.applicationContext
    private val isNightMode =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    private val touchTolerance = TOUCH_TOLERANCE_DP * context.resources.displayMetrics.density
    private val boundsPadding =
        (BOUNDS_PADDING_DP * context.resources.displayMetrics.density).toInt()

    private var map: MapLibreMap? = null
    private var source: GeoJsonSource? = null
    private var earthquakes = emptyList<Earthquake>()
    private var earthquakesById = emptyMap<String, Earthquake>()

    init {
        MapLibre.getInstance(appContext)
        // The app does not have the network state permission, so MapLibre must not check it.
        // Tiles that can't be downloaded are simply not drawn.
        MapLibre.setConnected(true)

        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(LatLng(TURKEY_CENTER_LATITUDE, TURKEY_CENTER_LONGITUDE))
                    .zoom(INITIAL_ZOOM)
                    .build()
            )
            .tiltGesturesEnabled(false)
            .attributionEnabled(true)
        view = MapView(context, options)
    }

    fun setEarthquakes(earthquakes: List<Earthquake>) {
        this.earthquakes = earthquakes
        earthquakesById = earthquakes.associateBy { it.id }
        source?.setGeoJson(EarthquakeFeatures.toFeatureCollection(earthquakes))
    }

    override fun onCreate(owner: LifecycleOwner) {
        view.onCreate(null)
        view.getMapAsync { map ->
            this.map = map
            map.uiSettings.isRotateGesturesEnabled = false
            map.setStyle(if (isNightMode) STYLE_DARK else STYLE_LIGHT) { style ->
                localizeLabels(style)
                addLayers(style)
                // The view has its final size once the style is loaded
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(TURKEY_BOUNDS, boundsPadding))
            }
            map.addOnMapClickListener { onMapClick(it) }
        }
    }

    override fun onStart(owner: LifecycleOwner) = view.onStart()

    override fun onResume(owner: LifecycleOwner) = view.onResume()

    override fun onPause(owner: LifecycleOwner) = view.onPause()

    override fun onStop(owner: LifecycleOwner) = view.onStop()

    override fun onDestroy(owner: LifecycleOwner) {
        map = null
        source = null
        view.onDestroy()
    }

    private fun addLayers(style: Style) {
        val options = GeoJsonOptions()
            .withCluster(true)
            .withClusterRadius(CLUSTER_RADIUS)
            .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
            .withClusterProperty(
                PROPERTY_MAX_MAGNITUDE,
                Expression.max(accumulated(), get(PROPERTY_MAX_MAGNITUDE)),
                get(EarthquakeFeatures.PROPERTY_MAGNITUDE)
            )
        val newSource = GeoJsonSource(
            SOURCE_ID,
            EarthquakeFeatures.toFeatureCollection(earthquakes),
            options
        )
        style.addSource(newSource)
        source = newSource

        val strokeColor = if (isNightMode) Color.BLACK else Color.WHITE

        style.addLayer(
            CircleLayer(LAYER_CLUSTERS, SOURCE_ID)
                .withFilter(has(PROPERTY_POINT_COUNT))
                .withProperties(
                    circleColor(magnitudeColor(get(PROPERTY_MAX_MAGNITUDE))),
                    circleRadius(
                        step(
                            get(PROPERTY_POINT_COUNT), literal(14f),
                            stop(10, 18f),
                            stop(50, 23f),
                            stop(200, 28f)
                        )
                    ),
                    circleOpacity(1f),
                    circleStrokeColor(strokeColor),
                    circleStrokeWidth(2f)
                )
        )

        style.addLayer(
            SymbolLayer(LAYER_CLUSTER_COUNT, SOURCE_ID)
                .withFilter(has(PROPERTY_POINT_COUNT))
                .withProperties(
                    textField(Expression.toString(get(PROPERTY_POINT_COUNT))),
                    textFont(arrayOf(FONT_BOLD)),
                    textSize(12f),
                    textColor(
                        step(
                            get(PROPERTY_MAX_MAGNITUDE),
                            Expression.color(Color.BLACK),
                            stop(
                                MagnitudeLevel.Strong.minMagnitude,
                                Expression.color(Color.WHITE)
                            )
                        )
                    ),
                    textAllowOverlap(true),
                    textIgnorePlacement(true)
                )
        )

        style.addLayer(
            CircleLayer(LAYER_POINTS, SOURCE_ID)
                .withFilter(not(has(PROPERTY_POINT_COUNT)))
                .withProperties(
                    circleColor(magnitudeColor(get(EarthquakeFeatures.PROPERTY_MAGNITUDE))),
                    circleRadius(
                        step(
                            get(EarthquakeFeatures.PROPERTY_MAGNITUDE), literal(6f),
                            stop(MagnitudeLevel.Light.minMagnitude, 8f),
                            stop(MagnitudeLevel.Moderate.minMagnitude, 11f),
                            stop(MagnitudeLevel.Strong.minMagnitude, 15f)
                        )
                    ),
                    circleStrokeColor(strokeColor),
                    circleStrokeWidth(1.5f)
                )
        )
    }

    /**
     * OpenFreeMap labels places in Latin script (e.g. "TURKEY"). Prefer the name in the
     * device language when the map data has one (e.g. "TÜRKİYE"), otherwise keep the Latin name.
     */
    private fun localizeLabels(style: Style) {
        val language = Locale.getDefault().language
        val localizedName = Expression.coalesce(
            get("name:$language"),
            get("name:latin"),
            get("name")
        )
        style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
            val textField = layer.textField
            if (textField.isExpression && textField.expression.toString().contains("name:latin")) {
                layer.setProperties(textField(localizedName))
            }
        }
    }

    /**
     * Same colors and thresholds as the list (see [EarthquakeItemBinder.getColor]).
     */
    private fun magnitudeColor(magnitude: Expression): Expression {
        fun colorOf(level: MagnitudeLevel): Expression {
            return Expression.color(
                ContextCompat.getColor(appContext, EarthquakeItemBinder.getColor(level))
            )
        }
        return step(
            magnitude,
            colorOf(MagnitudeLevel.Minor),
            stop(MagnitudeLevel.Light.minMagnitude, colorOf(MagnitudeLevel.Light)),
            stop(MagnitudeLevel.Moderate.minMagnitude, colorOf(MagnitudeLevel.Moderate)),
            stop(MagnitudeLevel.Strong.minMagnitude, colorOf(MagnitudeLevel.Strong))
        )
    }

    private fun onMapClick(latLng: LatLng): Boolean {
        val map = map ?: return false
        val point = map.projection.toScreenLocation(latLng)
        val area = touchArea(point)

        val cluster = map.queryRenderedFeatures(area, LAYER_CLUSTERS).firstOrNull()
        if (cluster != null) {
            val center = cluster.geometry() as? Point ?: return false
            // The expansion zoom is where the cluster first splits, which can be a tiny step
            val minimumZoom = map.cameraPosition.zoom + MIN_CLUSTER_ZOOM_STEP
            val expansionZoom = source?.getClusterExpansionZoom(cluster)?.toDouble() ?: minimumZoom
            val zoom = maxOf(expansionZoom, minimumZoom)
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(center.latitude(), center.longitude()),
                    zoom
                )
            )
            onEarthquakeSelected(null)
            return true
        }

        val earthquake = map.queryRenderedFeatures(area, LAYER_POINTS)
            .firstNotNullOfOrNull { feature ->
                EarthquakeFeatures.getId(feature)?.let { earthquakesById[it] }
            }
        onEarthquakeSelected(earthquake)
        return earthquake != null
    }

    private fun touchArea(point: PointF): RectF {
        return RectF(
            point.x - touchTolerance,
            point.y - touchTolerance,
            point.x + touchTolerance,
            point.y + touchTolerance
        )
    }

    companion object {
        // OpenFreeMap: free vector tiles, no account or API key. Attribution is added by MapLibre.
        private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
        private const val FONT_BOLD = "Noto Sans Bold"

        private const val SOURCE_ID = "earthquakes"
        private const val LAYER_CLUSTERS = "earthquake-clusters"
        private const val LAYER_CLUSTER_COUNT = "earthquake-cluster-count"
        private const val LAYER_POINTS = "earthquake-points"

        private const val PROPERTY_POINT_COUNT = "point_count"
        private const val PROPERTY_MAX_MAGNITUDE = "max_magnitude"

        private const val CLUSTER_RADIUS = 50
        private const val CLUSTER_MAX_ZOOM = 12
        private const val TOUCH_TOLERANCE_DP = 12f
        private const val MIN_CLUSTER_ZOOM_STEP = 1.5

        private const val TURKEY_CENTER_LATITUDE = 39.0
        private const val TURKEY_CENTER_LONGITUDE = 35.0
        private const val INITIAL_ZOOM = 4.5
        private const val BOUNDS_PADDING_DP = 16f

        // Türkiye with a small margin, so the Aegean and the eastern border are visible
        private val TURKEY_BOUNDS = LatLngBounds.Builder()
            .include(LatLng(35.6, 25.5))
            .include(LatLng(42.3, 44.9))
            .build()
    }
}
