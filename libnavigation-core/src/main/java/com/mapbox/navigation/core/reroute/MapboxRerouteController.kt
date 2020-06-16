package com.mapbox.navigation.core.reroute

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.base.common.logger.Logger
import com.mapbox.base.common.logger.model.Message
import com.mapbox.base.common.logger.model.Tag
import com.mapbox.navigation.core.directions.session.DirectionsSession
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.routeoptions.RouteOptionsProvider
import com.mapbox.navigation.core.trip.session.TripSession

/**
 * Default implementation of [RerouteController]
 */
internal class MapboxRerouteController(
    private val directionsSession: DirectionsSession,
    private val tripSession: TripSession,
    private val routeOptionsProvider: RouteOptionsProvider,
    private val logger: Logger
) : RerouteController {

    internal companion object {
        const val TAG = "MapboxRerouteController"
    }

    override var rerouteState: RerouteState = RerouteState.IDLE
        set(value) {
            if (field == value) {
                return
            }
            field = value
        }

    // current implementation ignore `onNewRoutes` callback because `DirectionsSession` update routes internally
    override fun reroute(routesCallback: RerouteController.RoutesCallback) {
        logger.d(
            Tag(TAG),
            Message("Reroute has been started")
        )
        routeOptionsProvider.newRouteOptions(
            directionsSession.getRouteOptions(),
            tripSession.getRouteProgress(),
            tripSession.getEnhancedLocation()
        )
            ?.let { routeOptions ->
                tryReroute(routeOptions)
            }
    }

    private fun tryReroute(routeOptions: RouteOptions) {
        rerouteState = RerouteState.FETCHING_ROUTE
        directionsSession.requestRoutes(routeOptions, object : RoutesRequestCallback {
            // ignore result, DirectionsSession sets routes internally
            override fun onRoutesReady(routes: List<DirectionsRoute>) {
                logger.d(
                    Tag(TAG),
                    Message("Route request has been finished success.")
                )
                rerouteState = RerouteState.ROUTE_HAS_BEEN_FETCHED
                rerouteState = RerouteState.IDLE
            }

            override fun onRoutesRequestFailure(
                throwable: Throwable,
                routeOptions: RouteOptions
            ) {
                logger.e(
                    Tag(TAG),
                    Message("Route request has failed"),
                    throwable
                )

                rerouteState = RerouteState.FAILED
                rerouteState = RerouteState.IDLE
            }

            override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
                logger.d(
                    Tag(TAG),
                    Message("Route request has been canceled")
                )
                rerouteState = RerouteState.INTERRUPTED
                rerouteState = RerouteState.IDLE
            }
        })
    }

    override fun interruptReroute() {
        if (rerouteState == RerouteState.FETCHING_ROUTE) {
            directionsSession.cancel() // do not change state here because it's changed into onRoutesRequestCanceled callback
            logger.d(
                Tag(TAG),
                Message("Route fetching has been interrupted")
            )
        }
    }
}
