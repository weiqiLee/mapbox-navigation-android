package com.mapbox.navigation.examples.core

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.base.internal.extensions.coordinates
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.base.trip.model.RouteProgressState
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.core.trip.session.TripSessionStateObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.examples.utils.Utils.PRIMARY_ROUTE_BUNDLE_KEY
import com.mapbox.navigation.examples.utils.Utils.getRouteFromBundle
import com.mapbox.navigation.examples.utils.extensions.toPoint
import com.mapbox.navigation.ui.camera.NavigationCamera
import com.mapbox.navigation.ui.internal.route.RouteConstants
import com.mapbox.navigation.ui.internal.route.RouteConstants.ROUTE_LINE_VANISH_ANIMATION_DURATION
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.mapbox.navigation.ui.map.NavigationMapboxMapInstanceState
import com.mapbox.navigation.ui.puck.PuckDrawableSupplier
import com.mapbox.navigation.utils.internal.ThreadController
import java.lang.ref.WeakReference
import kotlinx.android.synthetic.main.activity_basic_navigation_layout.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal


/**
 * This activity shows how to set up a basic turn-by-turn
 * navigation experience with the Navigation SDK and
 * Navigation UI SDK.
 */
open class BasicNavigationActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val MAP_INSTANCE_STATE_KEY = "navgation_mapbox_map_instance_state"
        const val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
        const val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5
    }

    private var mapboxNavigation: MapboxNavigation? = null
    private var navigationMapboxMap: NavigationMapboxMap? = null
    private var mapInstanceState: NavigationMapboxMapInstanceState? = null
    private val mapboxReplayer = MapboxReplayer()
    private var directionRoute: DirectionsRoute? = null
    lateinit var style: Style

    private val mapStyles = listOf(
        Style.MAPBOX_STREETS,
        Style.OUTDOORS,
        Style.LIGHT,
        Style.DARK,
        Style.SATELLITE_STREETS
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic_navigation_layout)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val mapboxNavigationOptions = MapboxNavigation.defaultNavigationOptions(
            this,
            Utils.getMapboxAccessToken(this)
        )

        mapboxNavigation = MapboxNavigation(
            applicationContext,
            mapboxNavigationOptions,
            locationEngine = getLocationEngine()
        ).apply {
            registerTripSessionStateObserver(tripSessionStateObserver)
            registerRouteProgressObserver(routeProgressObserver)
        }

        initListeners()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle(Style.MAPBOX_STREETS) {
            this.style = it
            mapboxMap.moveCamera(CameraUpdateFactory.zoomTo(15.0))
            navigationMapboxMap = NavigationMapboxMap(mapView, mapboxMap, this, true).also {
                it.setPuckDrawableSupplier(CustomPuckDrawableSupplier())
            }
            mapInstanceState?.let { state ->
                navigationMapboxMap?.restoreFrom(state)
            }

            when (directionRoute) {
                null -> {
                    if (shouldSimulateRoute()) {
                        mapboxNavigation?.registerRouteProgressObserver(ReplayProgressObserver(mapboxReplayer))
                        mapboxReplayer.pushRealLocation(this, 0.0)
                        mapboxReplayer.play()
                    }
                    mapboxNavigation?.locationEngine?.getLastLocation(locationListenerCallback)
                    Snackbar.make(container, R.string.msg_long_press_map_to_place_waypoint, LENGTH_SHORT)
                        .show()
                }
                else -> restoreNavigation()
            }
        }
        mapboxMap.addOnMapLongClickListener { latLng ->
            mapboxMap.locationComponent.lastKnownLocation?.let { originLocation ->
                mapboxNavigation?.requestRoutes(
                    RouteOptions.builder().applyDefaultParams()
                        .accessToken(Utils.getMapboxAccessToken(applicationContext))
                        .coordinates(originLocation.toPoint(), null, latLng.toPoint())
                        .alternatives(true)
                        .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
                        .build(),
                    routesReqCallback
                )
            }
            true
        }
    }

    private val routeProgressObserver = object : RouteProgressObserver {
        override fun onRouteProgressChanged(routeProgress: RouteProgress) {
            // do something with the route progress
            Timber.i("route progress: ${routeProgress.currentState}")
        }
    }

    fun startLocationUpdates() {
        if (!shouldSimulateRoute()) {
            val requestLocationUpdateRequest =
                LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                    .setPriority(LocationEngineRequest.PRIORITY_NO_POWER)
                    .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME)
                    .build()

            mapboxNavigation?.locationEngine?.requestLocationUpdates(
                requestLocationUpdateRequest,
                locationListenerCallback,
                mainLooper
            )
        }
    }

    private val routesReqCallback = object : RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            if (routes.isNotEmpty()) {
                directionRoute = routes[0]
                navigationMapboxMap?.drawRoute(routes[0])
                startNavigation.visibility = View.VISIBLE
            } else {
                startNavigation.visibility = View.GONE
            }
        }

        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
            Timber.e("route request failure %s", throwable.toString())
        }

        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
            Timber.d("route request canceled")
        }
    }

    @SuppressLint("MissingPermission")
    fun initListeners() {
        startNavigation.setOnClickListener {
            updateCameraOnNavigationStateChange(true)
            navigationMapboxMap?.addProgressChangeListener(mapboxNavigation!!)
//mapboxNavigation?.registerRouteProgressObserver(progChangeListener)
            if (mapboxNavigation?.getRoutes()?.isNotEmpty() == true) {
                navigationMapboxMap?.startCamera(mapboxNavigation?.getRoutes()!![0])
            }
            mapboxNavigation?.startTripSession()
            startNavigation.visibility = View.GONE
            stopLocationUpdates()
        }

        fabToggleStyle.setOnClickListener {
            //navigationMapboxMap?.retrieveMap()?.setStyle(mapStyles.shuffled().first())
            foobar()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxReplayer.finish()
        mapboxNavigation?.unregisterTripSessionStateObserver(tripSessionStateObserver)
        mapboxNavigation?.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation?.stopTripSession()
        mapboxNavigation?.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigationMapboxMap?.saveStateWith(MAP_INSTANCE_STATE_KEY, outState)
        mapView.onSaveInstanceState(outState)

        // This is not the most efficient way to preserve the route on a device rotation.
        // This is here to demonstrate that this event needs to be handled in order to
        // redraw the route line after a rotation.
        directionRoute?.let {
            outState.putString(PRIMARY_ROUTE_BUNDLE_KEY, it.toJson())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mapInstanceState = savedInstanceState?.getParcelable(MAP_INSTANCE_STATE_KEY)
        directionRoute = getRouteFromBundle(savedInstanceState)
    }

    private val locationListenerCallback = MyLocationEngineCallback(this)

    private fun stopLocationUpdates() {
        if (!shouldSimulateRoute()) {
            mapboxNavigation?.locationEngine?.removeLocationUpdates(locationListenerCallback)
        }
    }

    private val tripSessionStateObserver = object : TripSessionStateObserver {
        override fun onSessionStateChanged(tripSessionState: TripSessionState) {
            when (tripSessionState) {
                TripSessionState.STARTED -> {
                    stopLocationUpdates()
                }
                TripSessionState.STOPPED -> {
                    startLocationUpdates()
                    navigationMapboxMap?.removeRoute()
                    updateCameraOnNavigationStateChange(false)
                }
            }
        }
    }

    // Used to determine if the ReplayRouteLocationEngine should be used to simulate the routing.
    // This is used for testing purposes.
    private fun shouldSimulateRoute(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
            .getBoolean(this.getString(R.string.simulate_route_key), false)
    }

    // If shouldSimulateRoute is true a ReplayRouteLocationEngine will be used which is intended
    // for testing else a real location engine is used.
    private fun getLocationEngine(): LocationEngine {
        return if (shouldSimulateRoute()) {
            ReplayLocationEngine(mapboxReplayer)
        } else {
            LocationEngineProvider.getBestLocationEngine(this)
        }
    }

    private fun updateCameraOnNavigationStateChange(
        navigationStarted: Boolean
    ) {
        navigationMapboxMap?.apply {
            if (navigationStarted) {
                updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS)
                updateLocationLayerRenderMode(RenderMode.GPS)
            } else {
                updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_NONE)
                updateLocationLayerRenderMode(RenderMode.COMPASS)
            }
        }
    }

    private class MyLocationEngineCallback(activity: BasicNavigationActivity) :
        LocationEngineCallback<LocationEngineResult> {

        private val activityRef = WeakReference(activity)

        override fun onSuccess(result: LocationEngineResult) {
            activityRef.get()?.navigationMapboxMap?.updateLocation(result.lastLocation)
        }

        override fun onFailure(exception: java.lang.Exception) {
            Timber.i(exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun restoreNavigation() {
        directionRoute?.let {
            mapboxNavigation?.setRoutes(listOf(it))
            navigationMapboxMap?.addProgressChangeListener(mapboxNavigation!!)
            navigationMapboxMap?.startCamera(mapboxNavigation?.getRoutes()!![0])
            updateCameraOnNavigationStateChange(true)
            mapboxNavigation?.startTripSession()
        }
    }

    class CustomPuckDrawableSupplier : PuckDrawableSupplier {
        override fun getPuckDrawable(routeProgressState: RouteProgressState): Int = when (routeProgressState) {
            RouteProgressState.ROUTE_INVALID -> R.drawable.transparent_puck
            RouteProgressState.ROUTE_INITIALIZED -> R.drawable.transparent_puck
            RouteProgressState.LOCATION_TRACKING -> R.drawable.transparent_puck
            RouteProgressState.ROUTE_COMPLETE -> R.drawable.transparent_puck
            RouteProgressState.LOCATION_STALE -> R.drawable.transparent_puck
            else -> R.drawable.transparent_puck
        }
    }

    //0.0033f
    val percentDistanceDelta = 0.0005f
    var step = 0.0001f
    fun foobar() {
        val expression = navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.getExpressionAtOffset(step)
        navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.hideShieldLineAtOffset(step)
        navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.hideRouteLineAtOffset(step)
        navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.decorateRouteLine(expression)
        Timber.e("*** $expression")
        step += percentDistanceDelta
    }

    var loopCounter = 0
    fun foobar1() {
        ThreadController.getMainScopeAndRootJob().scope.launch {
            while (loopCounter <= 5000) {
                step += percentDistanceDelta
                val expression =
                    navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.getExpressionAtOffset(step)
                navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.hideShieldLineAtOffset(step)
                navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.hideRouteLineAtOffset(step)
                navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.decorateRouteLine(expression)
                Timber.e("*** $expression")
                delay(10)
                loopCounter += 100
            }
        }
    }

    fun foobar2() {
        val values = listOf<Float>(
            0.00000430546f,
            0.00000940370f,
            0.00001646094f,
            0.0000254688f,
            0.00003750753f,
            0.0000505566f,
            0.00006551547f,
            0.00008236626f,
            0.00010108911f,
            0.00012362252f,
            0.00014618455f,
            0.00017054232f,
            0.0001966668f,
            0.00022452633f,
            0.00025685912f,
            0.00028823732f,
            0.00032124203f,
            0.00035583344f,
            0.00039533028f,
            0.00043310353f,
            0.00047232962f,
            0.00051296223f,
            0.00055495277f,
            0.0006022497f,
            0.00064691517f,
            0.00069277803f,
            0.0007397844f,
            0.0007878776f,
            0.0008415146f,
            0.00089169346f,
            0.0009427772f,
            0.000994706f,
            0.0010522455f,
            0.0011057386f,
            0.0011598817f,
            0.0012146092f,
            0.0012698574f,
            0.0013306432f,
            0.0013867646f,
            0.0014432003f,
            0.0014998824f,
            0.0015619204f,
            0.0016188998f,
            0.001675917f,
            0.0017329033f,
            0.00178979f,
            0.0018516561f,
            0.0019081166f,
            0.0019642694f,
            0.0020200468f,
            0.002075382f,
            0.0021351667f,
            0.002189365f,
            0.0022429198f,
            0.0022957667f,
            0.0023525357f,
            0.0024036993f,
            0.0024539623f,
            0.0025032659f,
            0.0025515507f,
            0.0026029947f,
            0.0026489645f,
            0.0026937404f,
            0.0027372707f,
            0.002779503f,
            0.0028240338f,
            0.00286339f,
            0.002901297f,
            0.0029377104f,
            0.002975678f,
            0.0030088292f,
            0.0030403566f,
            0.003070224f,
            0.0030983964f,
            0.0031271551f,
            0.003151675f,
            0.0031744011f,
            0.0031953072f,
            0.0032160087f,
            0.0032330304f,
            0.0032481623f,
            0.0032613855f,
            0.0032726845f,
            0.0032828005f,
            0.0032900353f,
            0.0032953124f,
            0.0032986242f,
            0.0032999674f)
        ThreadController.getMainScopeAndRootJob().scope.launch {
            for (x in values) {
                val stepToUse = x.toBigDecimal().setScale(4, BigDecimal.ROUND_DOWN).toFloat()

                if (stepToUse  == 0f) {
                    Timber.e("*** skipping ${x.toBigDecimal()}")
                    continue
                }

                val expression =
                    navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.getExpressionAtOffset(stepToUse)
                navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.hideShieldLineAtOffset(stepToUse)
                navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.hideRouteLineAtOffset(stepToUse)
                navigationMapboxMap?.retrieveMapRoute()?.routeLine!!.decorateRouteLine(expression)
                Timber.e("*** $expression")
                delay(100)
            }
            Timber.e("*** finished")
        }
    }
}
