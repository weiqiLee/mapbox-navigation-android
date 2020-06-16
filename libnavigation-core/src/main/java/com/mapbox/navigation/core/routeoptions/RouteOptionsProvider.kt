package com.mapbox.navigation.core.routeoptions

import android.location.Location
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.fasterroute.FasterRouteController
import com.mapbox.navigation.core.trip.session.OffRouteObserver

/**
 * Provider is used for *Reroute* and *Faster Route* flow.
 *
 * It's used every time when turn-by-turn navigation goes out of the route (see [OffRouteObserver])
 * and when need to find faster route (see [FasterRouteController]).
 */
internal interface RouteOptionsProvider {

    /**
     * Provide a new instance *RouteOptions* based on *RouteOptions*, *RouteProgress*, and
     * *Location*
     *
     * Return *null* in case if cannot combine a new [RouteOptions] instance base on input. When *null*
     * is returned new route is not fetched
     */
    fun newRouteOptions(
        routeOptions: RouteOptions?,
        routeProgress: RouteProgress?,
        location: Location?
    ): RouteOptions?
}
