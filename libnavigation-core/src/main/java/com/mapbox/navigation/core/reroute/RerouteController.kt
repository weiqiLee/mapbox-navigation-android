package com.mapbox.navigation.core.reroute

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.OffRouteObserver

/**
 * Reroute controller allow change reroute logic externally. Use [MapboxNavigation.setRerouteController]
 * to replace that logic.
 */
interface RerouteController {

    /**
     * State of Reroute process
     */
    val rerouteState: RerouteState

    /**
     * Invoked whenever re-route is needed. As instance when a driver is off route. Called just after
     * an off route event.
     *
     * @see [OffRouteObserver]
     */
    fun reroute(routesCallback: RoutesCallback)

    /**
     * Invoked when re-route is not needed anymore (as instance when driver returned to previous route).
     * Might be ignored depends on [RerouteState] (when route has been fetched no sense to interrupt re-route)
     */
    fun interruptReroute()

    /**
     * Route Callback is useful to set new route(s) on reroute event. Doing the same as
     * [MapboxNavigation.setRoutes].
     */
    interface RoutesCallback {
        /**
         * Called whenever new route(s) has been found.
         * @see [MapboxNavigation.setRoutes]
         */
        fun onNewRoutes(routes: List<DirectionsRoute>)
    }
}

/**
 * Reroute process state
 */
enum class RerouteState {
    /**
     * Stared and finished state of Reroute process. Mean that [RerouteController] is idling.
     */
    IDLE,

    /**
     * Reroute process has been interrupted.
     *
     * Might be invoked by:
     * - [RerouteController.interruptReroute];
     * - the NavSdk internally if another route request has been requested(only for the default
     * implementation, see [MapboxRerouteController]).
     */
    INTERRUPTED,

    /**
     * A request of new route has been failed.
     */
    FAILED,

    /**
     * Route fetching is in progress.
     */
    FETCHING_ROUTE,

    /**
     * Route has been fetched.
     */
    ROUTE_HAS_BEEN_FETCHED
}
