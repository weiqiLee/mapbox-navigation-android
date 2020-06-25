package com.mapbox.navigation.core.trip.session

import android.hardware.SensorEvent
import android.location.Location
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.internal.trip.service.TripService
import com.mapbox.navigator.NavigationStatus

internal interface TripSession {

    val tripService: TripService
    val locationEngine: LocationEngine
    val locationEngineRequest: LocationEngineRequest
    var route: DirectionsRoute?

    fun getRawLocation(): Location?
    fun getEnhancedLocation(): Location?
    fun getRouteProgress(): RouteProgress?
    fun getState(): TripSessionState

    fun startTripSession()
    fun stopTripSession()
    fun startLocationUpdates()
    fun stopLocationUpdates()
    fun refresh(locationEngineRequest: LocationEngineRequest)

    fun registerLocationObserver(locationObserver: LocationObserver)
    fun unregisterLocationObserver(locationObserver: LocationObserver)
    fun unregisterAllLocationObservers()

    fun registerRouteProgressObserver(routeProgressObserver: RouteProgressObserver)
    fun unregisterRouteProgressObserver(routeProgressObserver: RouteProgressObserver)
    fun unregisterAllRouteProgressObservers()

    fun registerOffRouteObserver(offRouteObserver: OffRouteObserver)
    fun unregisterOffRouteObserver(offRouteObserver: OffRouteObserver)
    fun unregisterAllOffRouteObservers()

    fun registerStateObserver(stateObserver: TripSessionStateObserver)
    fun unregisterStateObserver(stateObserver: TripSessionStateObserver)
    fun unregisterAllStateObservers()

    fun registerBannerInstructionsObserver(bannerInstructionsObserver: BannerInstructionsObserver)
    fun unregisterBannerInstructionsObserver(bannerInstructionsObserver: BannerInstructionsObserver)
    fun unregisterAllBannerInstructionsObservers()

    fun registerVoiceInstructionsObserver(voiceInstructionsObserver: VoiceInstructionsObserver)
    fun unregisterVoiceInstructionsObserver(voiceInstructionsObserver: VoiceInstructionsObserver)
    fun unregisterAllVoiceInstructionsObservers()
    fun updateSensorEvent(sensorEvent: SensorEvent)

    fun useExtendedKalmanFilter(useEKF: Boolean)
    fun updateLegIndex(legIndex: Int): NavigationStatus
}
