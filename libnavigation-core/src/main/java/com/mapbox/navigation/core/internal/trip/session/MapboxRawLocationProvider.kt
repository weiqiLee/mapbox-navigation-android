package com.mapbox.navigation.core.internal.trip.session

import android.location.Location
import android.os.Looper
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.base.common.logger.Logger
import com.mapbox.base.common.logger.model.Message

internal class MapboxRawLocationProvider(
    private val locationEngine: LocationEngine,
    logger: Logger
) {

    private var mapMatching: ((Location) -> Unit)? = null
    private var locationUpdates: ((Location) -> Unit)? = null
    private var isObservingLocations = false

    var locationEngineRequest: LocationEngineRequest = defaultLocationEngineRequest
        set(value) {
            field = value
            refreshLocationEngine(true)
        }

    fun requestForMapMatching(mapMatching: (Location) -> Unit) {
        this.mapMatching = mapMatching
        refreshLocationEngine(false)
    }

    fun stopMapMatching() {
        this.mapMatching = null
        refreshLocationEngine(false)
    }

    fun requestForLocationUpdates(locationUpdates: (Location) -> Unit) {
        this.locationUpdates = locationUpdates
        refreshLocationEngine(false)
    }

    fun stopLocationUpdates() {
        this.locationUpdates = null
        refreshLocationEngine(false)
    }

    private fun refreshLocationEngine(forceRefresh: Boolean) {
        val hasObserver = (mapMatching ?: locationUpdates) != null
        if (forceRefresh || !hasObserver) {
            locationEngine.removeLocationUpdates(locationEngineCallback)
            isObservingLocations = false
        }
        if (hasObserver) {
            if (!isObservingLocations) {
                isObservingLocations = true
                locationEngine.requestLocationUpdates(locationEngineRequest, locationEngineCallback, mainLooper)
            }
            locationEngine.getLastLocation(locationEngineCallback)
        }
    }

    private val locationEngineCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult?) {
            result?.locations?.firstOrNull()?.let {
                val observer = (mapMatching ?: locationUpdates)
                observer?.invoke(it)
            }
        }

        override fun onFailure(exception: Exception) {
            logger.d(msg = Message("Location request failure"), tr = exception)
        }
    }

    companion object {
        private val defaultLocationEngineRequest = LocationEngineRequest.Builder(1000L)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        private val mainLooper = Looper.getMainLooper()
    }
}
