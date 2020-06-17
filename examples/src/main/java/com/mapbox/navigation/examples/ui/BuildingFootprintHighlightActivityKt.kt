package com.mapbox.navigation.examples.ui

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory.zoomTo
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.ui.NavigationViewOptions
import com.mapbox.navigation.ui.OnNavigationReadyCallback
import com.mapbox.navigation.ui.internal.building.BuildingFootprintHighlightLayer
import com.mapbox.navigation.ui.listeners.BannerInstructionsListener
import com.mapbox.navigation.ui.listeners.NavigationListener
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.mapbox.navigation.utils.internal.ifNonNull
import kotlinx.android.synthetic.main.activity_final_destination_arrival_building_highlight.*

/**
 * This activity shows how to use the Navigation UI SDK's [BuildingFootprintHighlightLayer]
 * class to highlight a building footprint. The final destination arrival callback is from
 * [ArrivalObserver.onFinalDestinationArrival].
 */
class BuildingFootprintHighlightActivityKt :
    AppCompatActivity(),
    OnNavigationReadyCallback,
    NavigationListener,
    BannerInstructionsListener,
    ArrivalObserver {

    private lateinit var mapboxMap: MapboxMap
    private lateinit var navigationMapboxMap: NavigationMapboxMap
    private lateinit var mapboxNavigation: MapboxNavigation
    private var colorList = listOf(Color.BLUE, Color.MAGENTA, Color.parseColor("#32a88f"))
    private var opacityList = listOf(.5f, .2f, .8f)
    private var adjustFootprintHighlightStyleButtonIndex = 0
    private lateinit var buildingFootprintHighlightLayer: BuildingFootprintHighlightLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_final_destination_arrival_building_highlight)

        navigationView.onCreate(savedInstanceState)
        navigationView.initialize(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        navigationView.onLowMemory()
    }

    override fun onStart() {
        super.onStart()
        navigationView.onStart()
    }

    override fun onResume() {
        super.onResume()
        navigationView.onResume()
    }

    override fun onStop() {
        super.onStop()
        navigationView.onStop()
    }

    override fun onPause() {
        super.onPause()
        navigationView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        navigationView.onDestroy()
    }

    override fun onBackPressed() {
        // If the navigation view didn't need to do anything, call super
        if (!navigationView.onBackPressed()) {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        navigationView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        navigationView.onRestoreInstanceState(savedInstanceState)
    }

    override fun onNavigationReady(isRunning: Boolean) {
        if (!isRunning && !::navigationMapboxMap.isInitialized) {
            ifNonNull(navigationView.retrieveNavigationMapboxMap()) { navMapboxMap ->

                this.navigationMapboxMap = navMapboxMap
                this.navigationMapboxMap.updateLocationLayerRenderMode(RenderMode.NORMAL)
                this.mapboxMap = navMapboxMap.retrieveMap()
                navigationView.retrieveMapboxNavigation()?.let { this.mapboxNavigation = it }

                val directionsRoute = getDirectionsRoute()

                val optionsBuilder = NavigationViewOptions.builder()
                optionsBuilder.navigationListener(this)

                // Pass the ArrivalObserver interface (this activity)
                optionsBuilder.arrivalObserver(this)

                optionsBuilder.directionsRoute(directionsRoute)
                optionsBuilder.shouldSimulateRoute(true)
                optionsBuilder.bannerInstructionsListener(this)
                optionsBuilder.navigationOptions(NavigationOptions.Builder().build())
                navigationView.startNavigation(optionsBuilder.build())

                // Initialize the Nav UI SDK's BuildingFootprintHighlightLayer class.
                buildingFootprintHighlightLayer =
                    BuildingFootprintHighlightLayer(
                        mapboxMap
                    )
                adjust_highlight_color_and_opacity.show()

                adjust_highlight_color_and_opacity.setOnClickListener {
                    if (adjustFootprintHighlightStyleButtonIndex == opacityList.size) {
                        adjustFootprintHighlightStyleButtonIndex = 0
                    }
                    buildingFootprintHighlightLayer.opacity =
                        opacityList[adjustFootprintHighlightStyleButtonIndex]
                    buildingFootprintHighlightLayer.color =
                        colorList[adjustFootprintHighlightStyleButtonIndex]
                    adjustFootprintHighlightStyleButtonIndex++
                }
            }
        }
    }

    override fun willDisplay(instructions: BannerInstructions?): BannerInstructions {
        return instructions!!
    }

    override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
        // Not needed in this example
    }

    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
        mapboxMap.easeCamera(zoomTo(18.0), 1800)

        // Adjust the visibility of the building footprint highlight layer
        buildingFootprintHighlightLayer.updateVisibility(true)

        /**
         * Set the [LatLng] to be used by the [BuildingFootprintHighlightLayer].
         * The LatLng would fall within a building polygon footprint. If not, the
         * [BuildingFootprintHighlightLayer] class won't highlight a footprint.
         * The LatLng passed through below is different than the coordinate used as the
         * final destination coordinate in this example's [DirectionsRoute].
         */
        buildingFootprintHighlightLayer.setBuildingFootprintLocation(
            LatLng(
                37.790932,
                -122.414279
            )
        )
    }

    override fun onNavigationRunning() {
        // Not needed in this example
    }

    override fun onNavigationFinished() {
        finish()
    }

    override fun onCancelNavigation() {
        navigationView.stopNavigation()
        finish()
    }

    private fun getDirectionsRoute(): DirectionsRoute {
        val tokenHere = Utils.getMapboxAccessToken(applicationContext)
        val directionsRouteAsJson = resources
            .openRawResource(R.raw.route_building_footprint_highlight)
            .bufferedReader()
            .use { it.readText() }
            .replace("\$tokenHere", tokenHere, true)
        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }
}
