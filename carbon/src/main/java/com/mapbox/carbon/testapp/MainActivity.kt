package com.mapbox.carbon.testapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gesture.OnMapClickListener
import com.mapbox.maps.plugin.gesture.OnMapLongClickListener
import com.mapbox.maps.plugin.gesture.getGesturePlugin
import com.mapbox.navigation.ui.base.map.route.model.RouteLineState
import com.mapbox.navigation.ui.map.route.api.MapboxMapRouteLineApi
import com.mapbox.navigation.ui.map.route.api.withNewOptions
import com.mapbox.navigation.ui.map.route.api.withNewRoute
import com.mapbox.navigation.ui.map.route.view.MapRouteLine
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val route: DirectionsRoute by lazy {
        val tokenHere = "YOUR_TOKEN"
        val directionsRouteAsJson = resources
            .openRawResource(R.raw.route)
            .bufferedReader()
            .use { it.readText() }
            .replace("\$tokenHere", tokenHere, true)

        DirectionsRoute.fromJson(directionsRouteAsJson)
    }

    private lateinit var routeLineState: RouteLineState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        routeLineState = MapboxMapRouteLineApi.getState(this)
        val mapboxMap = mapView.getMapboxMap()
        mapboxMap.jumpTo(getInitialCameraPosition())
        mapboxMap.loadStyleUri(
            Style.MAPBOX_STREETS,
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    val mapRouteLine = MapRouteLine(style)
                    mapView.getGesturePlugin().addOnMapClickListener(object : OnMapClickListener {
                        override fun onMapClick(point: Point): Boolean {
                            routeLineState = routeLineState.withNewOptions {
                                primaryRouteVisible(
                                    routeLineState.options.primaryRouteVisible.not()
                                )
                            }
                            mapRouteLine.render(routeLineState)
                            return false
                        }
                    })

                    mapView.getGesturePlugin()
                        .addOnMapLongClickListener(object : OnMapLongClickListener {
                            override fun onMapLongClick(point: Point): Boolean {
                                routeLineState = routeLineState.withNewRoute(route)
                                mapRouteLine.render(routeLineState)
                                return false
                            }
                        })
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    private fun getInitialCameraPosition(): CameraOptions {
        val originCoordinate = route.routeOptions()?.coordinates()?.get(0)
        return CameraOptions.Builder()
            .center(originCoordinate)
            .zoom(15.0)
            .build()
    }
}
