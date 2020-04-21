package com.mapbox.navigation.core.trip.session

import android.hardware.SensorEvent
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.VoiceInstructions
import com.mapbox.navigation.base.trip.RouteProgressObserver
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.sensors.SensorMapper
import com.mapbox.navigation.core.trip.service.TripService
import com.mapbox.navigation.navigator.MapboxNativeNavigator
import com.mapbox.navigation.navigator.MapboxNativeNavigatorImpl
import com.mapbox.navigation.navigator.TripStatus
import com.mapbox.navigation.utils.extensions.ifNonNull
import com.mapbox.navigation.utils.thread.JobControl
import com.mapbox.navigation.utils.thread.ThreadController
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// todo make internal
/**
 * Default implementation of [TripSession]
 *
 * @param tripService TripService
 * @param locationEngine LocationEngine
 * @param locationEngineRequest LocationEngineRequest
 * @param navigatorPredictionMillis millis for navigation status predictions
 * For more information see [MapboxNativeNavigator.getStatus]. Unit is milliseconds
 * @param navigator Native navigator
 *
 * @property route should be set to start routing
 */
class MapboxTripSession(
    override val tripService: TripService,
    override val locationEngine: LocationEngine,
    override val locationEngineRequest: LocationEngineRequest,
    private val navigatorPredictionMillis: Long,
    private val navigator: MapboxNativeNavigator = MapboxNativeNavigatorImpl,
    threadController: ThreadController = ThreadController
) : TripSession {

    companion object {
        private const val STATUS_POLLING_INTERVAL = 1000L
    }

    override var route: DirectionsRoute? = null
        set(value) {
            field = value
            ioJobController.scope.launch {
                navigator.setRoute(value)
            }
        }
    private val ioJobController: JobControl = threadController.getIOScopeAndRootJob()
    private val mainJobController: JobControl = threadController.getMainScopeAndRootJob()

    private val locationObservers = CopyOnWriteArrayList<LocationObserver>()
    private val routeProgressObservers = CopyOnWriteArrayList<RouteProgressObserver>()
    private val offRouteObservers = CopyOnWriteArrayList<OffRouteObserver>()
    private val stateObservers = CopyOnWriteArrayList<TripSessionStateObserver>()
    private val bannerInstructionsObservers = CopyOnWriteArrayList<BannerInstructionsObserver>()
    private val voiceInstructionsObservers = CopyOnWriteArrayList<VoiceInstructionsObserver>()

    private val bannerInstructionEvent = BannerInstructionEvent()
    private val voiceInstructionEvent = VoiceInstructionEvent()

    private val minTimeBetweenGetStatusCallsMillis = TimeUnit.SECONDS.toMillis(1)
    private var lastStatusUpdateTimeMillis: Long = 0

    private var state: TripSessionState = TripSessionState.STOPPED
        set(value) {
            if (field == value) {
                return
            }
            field = value
            stateObservers.forEach { it.onSessionStateChanged(value) }
        }

    private var isOffRoute: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            offRouteObservers.forEach { it.onOffRouteStateChanged(value) }
        }

    private var rawLocation: Location? = null
    private var enhancedLocation: Location? = null
    private var routeProgress: RouteProgress? = null

    /**
     * Return raw location
     */
    override fun getRawLocation() = rawLocation

    /**
     * Return enhanced location
     */
    override fun getEnhancedLocation() = enhancedLocation

    /**
     * Provide route progress
     */
    override fun getRouteProgress() = routeProgress

    /**
     * Current [MapboxTripSession] state
     */
    override fun getState(): TripSessionState = state

    /**
     * Start MapboxTripSession
     */
    override fun start() {
        if (state == TripSessionState.STARTED) {
            return
        }
        tripService.startService()
        startLocationUpdates()
        state = TripSessionState.STARTED
    }

    private fun startLocationUpdates() {
        locationEngine.requestLocationUpdates(
            locationEngineRequest,
            locationEngineCallback,
            Looper.getMainLooper()
        )
        locationEngine.getLastLocation(locationEngineCallback)
    }

    /**
     * Stop MapboxTripSession
     */
    override fun stop() {
        if (state == TripSessionState.STOPPED) {
            return
        }
        tripService.stopService()
        stopLocationUpdates()
        ioJobController.job.cancelChildren()
        mainJobController.job.cancelChildren()
        reset()
        state = TripSessionState.STOPPED
    }

    private fun stopLocationUpdates() {
        locationEngine.removeLocationUpdates(locationEngineCallback)
    }

    private fun reset() {
        rawLocation = null
        enhancedLocation = null
        routeProgress = null
        isOffRoute = false
    }

    /**
     * Register [LocationObserver] to receive location updates
     */
    override fun registerLocationObserver(locationObserver: LocationObserver) {
        locationObservers.add(locationObserver)
        rawLocation?.let { locationObserver.onRawLocationChanged(it) }
        enhancedLocation?.let { locationObserver.onEnhancedLocationChanged(it, emptyList()) }
    }

    /**
     * Unregister [LocationObserver]
     */
    override fun unregisterLocationObserver(locationObserver: LocationObserver) {
        locationObservers.remove(locationObserver)
    }

    /**
     * Unregister all [LocationObserver]
     *
     * @see [registerLocationObserver]
     */
    override fun unregisterAllLocationObservers() {
        locationObservers.clear()
    }

    /**
     * Register [RouteProgressObserver] to receive information about routing's state
     * like [BannerInstructions], [RouteLegProgress], etc.
     *
     * @see [RouteProgress]
     */
    override fun registerRouteProgressObserver(routeProgressObserver: RouteProgressObserver) {
        routeProgressObservers.add(routeProgressObserver)
        routeProgress?.let { routeProgressObserver.onRouteProgressChanged(it) }
    }

    /**
     * Unregister [RouteProgressObserver]
     */
    override fun unregisterRouteProgressObserver(routeProgressObserver: RouteProgressObserver) {
        routeProgressObservers.remove(routeProgressObserver)
    }

    /**
     * Unregister all [RouteProgressObserver]
     *
     * @see [registerRouteProgressObserver]
     */
    override fun unregisterAllRouteProgressObservers() {
        routeProgressObservers.clear()
    }

    /**
     * Register [OffRouteObserver] to receive notification about off-route events
     */
    override fun registerOffRouteObserver(offRouteObserver: OffRouteObserver) {
        offRouteObservers.add(offRouteObserver)
        offRouteObserver.onOffRouteStateChanged(isOffRoute)
    }

    /**
     * Unregister [OffRouteObserver]
     */
    override fun unregisterOffRouteObserver(offRouteObserver: OffRouteObserver) {
        offRouteObservers.remove(offRouteObserver)
    }

    /**
     * Unregister all [OffRouteObserver]
     *
     * @see [registerOffRouteObserver]
     */
    override fun unregisterAllOffRouteObservers() {
        offRouteObservers.clear()
    }

    /**
     * Register [TripSessionStateObserver] to receive current TripSession's state
     *
     * @see [TripSessionState]
     */
    override fun registerStateObserver(stateObserver: TripSessionStateObserver) {
        stateObservers.add(stateObserver)
        stateObserver.onSessionStateChanged(state)
    }

    /**
     * Unregister [TripSessionStateObserver]
     */
    override fun unregisterStateObserver(stateObserver: TripSessionStateObserver) {
        stateObservers.remove(stateObserver)
    }

    /**
     * Unregister all [TripSessionStateObserver]
     *
     * @see [registerStateObserver]
     */
    override fun unregisterAllStateObservers() {
        stateObservers.clear()
    }

    /**
     * Register [BannerInstructionsObserver]
     */
    override fun registerBannerInstructionsObserver(bannerInstructionsObserver: BannerInstructionsObserver) {
        bannerInstructionsObservers.add(bannerInstructionsObserver)
        routeProgress?.let {
            checkBannerInstructionEvent(it) { bannerInstruction ->
                bannerInstructionsObserver.onNewBannerInstructions(bannerInstruction)
            }
        }
    }

    /**
     * Unregister all [BannerInstructionsObserver]
     *
     * @see [registerBannerInstructionsObserver]
     */
    override fun unregisterAllBannerInstructionsObservers() {
        bannerInstructionsObservers.clear()
    }

    /**
     * Unregister [BannerInstructionsObserver]
     */
    override fun unregisterBannerInstructionsObserver(bannerInstructionsObserver: BannerInstructionsObserver) {
        bannerInstructionsObservers.remove(bannerInstructionsObserver)
    }

    /**
     * Register [VoiceInstructionsObserver]
     */
    override fun registerVoiceInstructionsObserver(voiceInstructionsObserver: VoiceInstructionsObserver) {
        voiceInstructionsObservers.add(voiceInstructionsObserver)
        routeProgress?.let {
            checkVoiceInstructionEvent(it) { voiceInstruction ->
                voiceInstructionsObserver.onNewVoiceInstructions(voiceInstruction)
            }
        }
    }

    /**
     * Unregister [VoiceInstructionsObserver]
     */
    override fun unregisterVoiceInstructionsObserver(voiceInstructionsObserver: VoiceInstructionsObserver) {
        voiceInstructionsObservers.remove(voiceInstructionsObserver)
    }

    /**
     * Unregister all [VoiceInstructionsObserver]
     *
     * @see [registerVoiceInstructionsObserver]
     */
    override fun unregisterAllVoiceInstructionsObservers() {
        voiceInstructionsObservers.clear()
    }

    /**
     * Sensor event consumed by native
     */
    override fun updateSensorEvent(sensorEvent: SensorEvent) {
        SensorMapper.toSensorData(sensorEvent)?.let { sensorData ->
            navigator.updateSensorData(sensorData)
        }
    }

    /**
     * Updates the configuration to enable or disable the extended kalman filter (EKF).
     *
     * @param useEKF the new value for EKF
     */
    override fun useExtendedKalmanFilter(useEKF: Boolean) {
        val config = navigator.getConfig()
        if (config.useEKF != useEKF) {
            config.useEKF = useEKF
            navigator.setConfig(config)
        }
    }

    private var locationEngineCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult?) {
            result?.locations?.firstOrNull()?.let {
                updateRawLocation(it)
            }
        }

        override fun onFailure(exception: Exception) {
            Log.d("DEBUG", "location on failure", exception)
            stopLocationUpdates()
        }
    }

    private fun updateRawLocation(rawLocation: Location) {
        locationObservers.forEach { it.onRawLocationChanged(rawLocation) }
        ioJobController.scope.launch {
            val currentDate = Date()
            lastStatusUpdateTimeMillis = SystemClock.elapsedRealtime()
            navigator.updateLocation(rawLocation, currentDate)
            updateDataFromNavigatorStatus(currentDate)
        }

        if (this.rawLocation == null) {
            ioJobController.scope.launch {
                while (isActive) {
                    delay(STATUS_POLLING_INTERVAL)
                    launch {
                        emitUnconditionallyGetStatus()
                    }
                }
            }
        }

        this.rawLocation = rawLocation
    }

    private fun emitUnconditionallyGetStatus() {
        val currentTimeMillis = SystemClock.elapsedRealtime()
        val deltaTime = currentTimeMillis - lastStatusUpdateTimeMillis
        if (deltaTime < minTimeBetweenGetStatusCallsMillis) return

        lastStatusUpdateTimeMillis = currentTimeMillis
        updateDataFromNavigatorStatus(Date())
    }

    private fun updateDataFromNavigatorStatus(date: Date) {
        mainJobController.scope.launch {
            val status = getNavigatorStatus(date)
            launch {
                updateEnhancedLocation(status.enhancedLocation, status.keyPoints)
                updateRouteProgress(status.routeProgress)
                isOffRoute = status.offRoute
            }
        }
    }

    private suspend fun getNavigatorStatus(date: Date): TripStatus =
        withContext(ioJobController.scope.coroutineContext) {
            date.time = date.time + navigatorPredictionMillis
            navigator.getStatus(date)
        }

    private fun updateEnhancedLocation(location: Location, keyPoints: List<Location>) {
        enhancedLocation = location
        locationObservers.forEach { it.onEnhancedLocationChanged(location, keyPoints) }
    }

    private fun updateRouteProgress(progress: RouteProgress) {
        routeProgress = progress
        tripService.updateNotification(progress)
        routeProgressObservers.forEach { it.onRouteProgressChanged(progress) }
        checkBannerInstructionEvent(progress) { bannerInstruction ->
            bannerInstructionsObservers.forEach {
                it.onNewBannerInstructions(bannerInstruction)
            }
        }
        checkVoiceInstructionEvent(progress) { voiceInstruction ->
            voiceInstructionsObservers.forEach {
                it.onNewVoiceInstructions(voiceInstruction)
            }
        }
    }

    private fun checkBannerInstructionEvent(
        progress: RouteProgress,
        action: (BannerInstructions) -> Unit
    ) {
        if (bannerInstructionEvent.isOccurring(progress)) {
            ifNonNull(bannerInstructionEvent.bannerInstructions) {
                action(it)
            }
        }
    }

    private fun checkVoiceInstructionEvent(
        progress: RouteProgress,
        action: (VoiceInstructions) -> Unit
    ) {
        if (voiceInstructionEvent.isOccurring(progress)) {
            action(voiceInstructionEvent.voiceInstructions)
        }
    }
}
