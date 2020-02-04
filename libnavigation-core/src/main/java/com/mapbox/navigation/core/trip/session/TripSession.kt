package com.mapbox.navigation.core.trip.session

import android.location.Location
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.trip.service.TripService

internal interface TripSession {

    val tripService: TripService
    val locationEngine: LocationEngine
    val locationEngineRequest: LocationEngineRequest
    var route: DirectionsRoute?

    fun getRawLocation(): Location?
    fun getEnhancedLocation(): Location?
    fun getRouteProgress(): RouteProgress?

    fun start()
    fun stop()

    fun registerLocationObserver(locationObserver: LocationObserver)
    fun unregisterLocationObserver(locationObserver: LocationObserver)
    fun unregisterLocationObservers()

    fun registerRouteProgressObserver(routeProgressObserver: RouteProgressObserver)
    fun unregisterRouteProgressObserver(routeProgressObserver: RouteProgressObserver)
    fun unregisterRouteProgressObservers()

    fun registerOffRouteObserver(offRouteObserver: OffRouteObserver)
    fun unregisterOffRouteObserver(offRouteObserver: OffRouteObserver)
    fun unregisterOffRouteObservers()

    fun registerStateObserver(stateObserver: TripSessionStateObserver)
    fun unregisterStateObserver(stateObserver: TripSessionStateObserver)
    fun unregisterStateObservers()

    fun registerBannerInstructionsObserver(bannerInstructionsObserver: BannerInstructionsObserver)
    fun unregisterBannerInstructionsObserver(bannerInstructionsObserver: BannerInstructionsObserver)
    fun unregisterBannerInstructionsObservers()

    fun registerVoiceInstructionsObserver(voiceInstructionsObserver: VoiceInstructionsObserver)
    fun unregisterVoiceInstructionsObserver(voiceInstructionsObserver: VoiceInstructionsObserver)
    fun unregisterVoiceInstructionsObservers()
}
