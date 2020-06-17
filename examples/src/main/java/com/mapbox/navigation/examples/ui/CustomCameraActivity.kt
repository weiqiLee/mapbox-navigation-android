package com.mapbox.navigation.examples.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.ui.NavigationViewOptions
import com.mapbox.navigation.ui.OnNavigationReadyCallback
import com.mapbox.navigation.ui.camera.Camera
import com.mapbox.navigation.ui.camera.RouteInformation
import com.mapbox.navigation.ui.camera.SimpleCamera
import com.mapbox.navigation.ui.listeners.BannerInstructionsListener
import com.mapbox.navigation.ui.listeners.NavigationListener
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import kotlinx.android.synthetic.main.activity_navigation_view.navigationView

/**
 * This activity shows how to create a custom class that extends the
 * [Camera] class. The custom class' object is then given to
 * the [NavigationViewOptions.Builder] via
 * [NavigationViewOptions.Builder.camera].
 */
class CustomCameraActivity :
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
            navigationView.retrieveNavigationMapboxMap()?.let { navMapboxMap ->
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

                // Add the custom camera
                optionsBuilder.camera(CustomCamera())

                optionsBuilder.navigationOptions(NavigationOptions.Builder().build())
                navigationView.startNavigation(optionsBuilder.build())
            }
        }
    }

    /**
     * A custom class made just for this example. This class is used in the
     * NavigationViewOptions.builder()'s `.camera()` method.
     */
    private class CustomCamera : SimpleCamera() {

        // These values are different than the defaults in the Navigation UI SDK's
        // SimpleCamera.kt class.
        companion object {
            const val CUSTOM_TILT = 60
            const val CUSTOM_ZOOM = 17.0
        }

        override fun tilt(routeInformation: RouteInformation): Double {
            return CUSTOM_TILT.toDouble()
        }

        override fun zoom(routeInformation: RouteInformation): Double {
            return CUSTOM_ZOOM
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
            .openRawResource(R.raw.route_custom_camera_activity)
            .bufferedReader()
            .use { it.readText() }
            .replace("\$tokenHere", tokenHere, true)
        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }
}
