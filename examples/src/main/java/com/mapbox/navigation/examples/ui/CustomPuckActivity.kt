package com.mapbox.navigation.examples.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.trip.model.RouteProgressState
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.ui.NavigationViewOptions
import com.mapbox.navigation.ui.OnNavigationReadyCallback
import com.mapbox.navigation.ui.listeners.BannerInstructionsListener
import com.mapbox.navigation.ui.listeners.NavigationListener
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.mapbox.navigation.ui.puck.PuckDrawableSupplier
import com.mapbox.navigation.utils.internal.ifNonNull
import kotlinx.android.synthetic.main.activity_navigation_view.*

/**
 * This activity shows how to use a
 * [com.mapbox.navigation.ui.NavigationView] and customize the
 * device location puck's image based on the [RouteProgressState]
 * status.
 */
class CustomPuckActivity :
    AppCompatActivity(),
    OnNavigationReadyCallback,
    NavigationListener,
    BannerInstructionsListener {

    private lateinit var mapboxMap: MapboxMap
    private lateinit var navigationMapboxMap: NavigationMapboxMap
    private lateinit var mapboxNavigation: MapboxNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_view)

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
                optionsBuilder.directionsRoute(directionsRoute)
                optionsBuilder.shouldSimulateRoute(true)
                optionsBuilder.bannerInstructionsListener(this)
                optionsBuilder.navigationOptions(NavigationOptions.Builder().build())
                optionsBuilder.puckDrawableSupplier(CustomPuckDrawableSupplier())
                navigationView.startNavigation(optionsBuilder.build())
            }
        }
    }

    override fun willDisplay(instructions: BannerInstructions?): BannerInstructions {
        return instructions!!
    }

    override fun onNavigationRunning() {
        // todo
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
            .openRawResource(R.raw.route_custom_puck_activity)
            .bufferedReader()
            .use { it.readText() }
            .replace("\$tokenHere", tokenHere, true)
        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }

    class CustomPuckDrawableSupplier : PuckDrawableSupplier {
        override fun getPuckDrawable(
            routeProgressState: RouteProgressState
        ): Int = when (routeProgressState) {
            RouteProgressState.ROUTE_INVALID -> R.drawable.custom_puck_icon_uncertain_location
            RouteProgressState.ROUTE_INITIALIZED -> R.drawable.custom_user_puck_icon
            RouteProgressState.LOCATION_TRACKING -> R.drawable.custom_user_puck_icon
            RouteProgressState.ROUTE_COMPLETE -> R.drawable.custom_puck_icon_uncertain_location
            RouteProgressState.LOCATION_STALE -> R.drawable.custom_user_puck_icon
            else -> R.drawable.custom_puck_icon_uncertain_location
        }
    }
}
