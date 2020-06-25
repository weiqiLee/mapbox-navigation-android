package com.mapbox.navigation.core.internal.trip.session

import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.VoiceInstructions
import com.mapbox.base.common.logger.Logger
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.internal.trip.service.TripService
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.core.trip.session.TripSessionStateObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.navigator.internal.MapboxNativeNavigator
import com.mapbox.navigation.navigator.internal.TripStatus
import com.mapbox.navigation.testing.MainCoroutineRule
import com.mapbox.navigation.utils.internal.JobControl
import com.mapbox.navigation.utils.internal.ThreadController
import com.mapbox.navigator.NavigationStatus
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MapboxTripSessionTest {

    @get:Rule
    var coroutineRule = MainCoroutineRule()

    private lateinit var tripSession: MapboxTripSession

    private val tripService: TripService = mockk(relaxUnitFun = true)
    private val locationEngine: LocationEngine = mockk(relaxUnitFun = true)
    private val locationEngineRequest: LocationEngineRequest = mockk()
    private val route: DirectionsRoute = mockk()

    private val locationCallbackSlot = slot<LocationEngineCallback<LocationEngineResult>>()
    private val locationEngineResult: LocationEngineResult = mockk(relaxUnitFun = true)
    private val location: Location = mockk(relaxUnitFun = true)
    private val enhancedLocation: Location = mockk(relaxUnitFun = true)
    private val keyPoints: List<Location> = listOf(mockk(relaxUnitFun = true))

    private val navigator: MapboxNativeNavigator = mockk(relaxUnitFun = true)
    private val navigationStatus: NavigationStatus = mockk(relaxUnitFun = true)
    private val tripStatus: TripStatus = mockk(relaxUnitFun = true)
    private val logger: Logger = mockk(relaxUnitFun = true)

    private val routeProgress: RouteProgress = mockk()
    private val navigatorPredictionMillis = 1500L

    private val parentJob = SupervisorJob()
    private val testScope = CoroutineScope(parentJob + coroutineRule.testDispatcher)

    private val stateObserver: TripSessionStateObserver = mockk(relaxUnitFun = true)

    @Before
    fun setUp() {
        mockkObject(ThreadController)
        every { ThreadController.IODispatcher } returns coroutineRule.testDispatcher
        every { ThreadController.getIOScopeAndRootJob() } returns JobControl(parentJob, testScope)
        every { ThreadController.getMainScopeAndRootJob() } returns JobControl(parentJob, testScope)

        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            logger = logger
        )

        coEvery { navigator.getStatus(any()) } returns tripStatus
        coEvery { navigator.updateLocation(any(), any()) } returns false
        coEvery { navigator.setRoute(any()) } returns navigationStatus
        every { tripStatus.enhancedLocation } returns enhancedLocation
        every { tripStatus.keyPoints } returns keyPoints
        every { tripStatus.offRoute } returns false
        every { routeProgress.bannerInstructions } returns null
        every { routeProgress.voiceInstructions } returns null

        every {
            locationEngine.requestLocationUpdates(
                any(),
                capture(locationCallbackSlot),
                any()
            )
        } answers {}
        every { locationEngineResult.locations } returns listOf(location)

        every { tripStatus.routeProgress } returns routeProgress
    }

    @Test
    fun startSession() {
        tripSession.startActiveGuidance()

        verify { tripService.startService() }
        verify {
            locationEngine.requestLocationUpdates(
                locationEngineRequest,
                any(),
                Looper.getMainLooper()
            )
        }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun stopSessionCallsTripServiceStopService() {
        tripSession.startActiveGuidance()

        tripSession.stopActiveGuidance()

        verify { locationEngine.removeLocationUpdates(locationCallbackSlot.captured) }
    }

    @Test
    fun stopSessionCallsLocationEngineRemoveLocationUpdates() {
        tripSession.startActiveGuidance()
        locationCallbackSlot.captured.onSuccess(locationEngineResult)

        tripSession.stopActiveGuidance()

        verify { locationEngine.removeLocationUpdates(locationCallbackSlot.captured) }
    }

    @Test
    fun stopSessionDoesNotClearUpRoute() {
        tripSession.route = route
        tripSession.startActiveGuidance()

        tripSession.stopActiveGuidance()

        assertEquals(route, tripSession.route)
    }

    @Test
    fun locationObserverSuccess() = coroutineRule.runBlockingTest {
        tripSession.startActiveGuidance()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        tripSession.registerLocationObserver(observer)

        updateLocationAndJoin()

        verify { observer.onRawLocationChanged(location) }
        assertEquals(location, tripSession.getRawLocation())

        tripSession.stopActiveGuidance()
    }

    @Test
    fun locationObserverOnFailure() {
        tripSession.startActiveGuidance()

        locationCallbackSlot.captured.onFailure(Exception("location failure"))

        verify(exactly = 0) { locationEngine.removeLocationUpdates(locationCallbackSlot.captured) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun locationObserverImmediate() = coroutineRule.runBlockingTest {
        tripSession.startActiveGuidance()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        updateLocationAndJoin()

        tripSession.registerLocationObserver(observer)

        verify { observer.onRawLocationChanged(location) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun unregisterLocationObserver() = coroutineRule.runBlockingTest {
        tripSession.startActiveGuidance()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        tripSession.registerLocationObserver(observer)
        tripSession.unregisterLocationObserver(observer)
        updateLocationAndJoin()
        verify(exactly = 0) { observer.onRawLocationChanged(any()) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun locationPush() = coroutineRule.runBlockingTest {
        tripSession.startActiveGuidance()
        updateLocationAndJoin()
        coVerify { navigator.updateLocation(location, any()) }
        tripSession.stopActiveGuidance()
    }

    @Test
    fun getStatusImmediatelyAfterUpdateLocation() = coroutineRule.runBlockingTest {
        tripSession.startActiveGuidance()

        updateLocationAndJoin()

        coVerify { navigator.getStatus(any()) }
        tripSession.stopActiveGuidance()
    }

    @Test
    fun noLocationUpdateLongerThanASecondUnconditionallyGetStatus() = coroutineRule.runBlockingTest {
        tripSession.startActiveGuidance()

        locationCallbackSlot.captured.onSuccess(locationEngineResult)
        SystemClock.setCurrentTimeMillis(1100)
        advanceTimeBy(1100)
        updateLocationAndJoin()

        coVerify(exactly = 3) { navigator.getStatus(any()) }
        tripSession.stopActiveGuidance()
    }

    @Test
    fun routeProgressObserverSuccess() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val observer: RouteProgressObserver = mockk(relaxUnitFun = true)
        tripSession.registerRouteProgressObserver(observer)
        updateLocationAndJoin()

        verify { observer.onRouteProgressChanged(routeProgress) }
        assertEquals(routeProgress, tripSession.getRouteProgress())
        tripSession.stopActiveGuidance()
    }

    @Test
    fun routeProgressObserverNotCalledWhenInFreeDrive() = coroutineRule.runBlockingTest {
        every { tripStatus.routeProgress } returns null
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val observer: RouteProgressObserver = mockk(relaxUnitFun = true)
        tripSession.registerRouteProgressObserver(observer)
        updateLocationAndJoin()

        verify(exactly = 0) { observer.onRouteProgressChanged(routeProgress) }
        assertNull(tripSession.getRouteProgress())
        tripSession.stopActiveGuidance()
    }

    @Test
    fun routeProgressObserverImmediate() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        updateLocationAndJoin()
        val observer: RouteProgressObserver = mockk(relaxUnitFun = true)
        tripSession.registerRouteProgressObserver(observer)

        verify(exactly = 1) { observer.onRouteProgressChanged(routeProgress) }
        assertEquals(routeProgress, tripSession.getRouteProgress())
        tripSession.stopActiveGuidance()
    }

    @Test
    fun routeProgressObserverUnregister() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val observer: RouteProgressObserver = mockk(relaxUnitFun = true)
        tripSession.registerRouteProgressObserver(observer)
        tripSession.unregisterRouteProgressObserver(observer)
        updateLocationAndJoin()

        verify(exactly = 0) { observer.onRouteProgressChanged(routeProgress) }
        tripSession.stopActiveGuidance()
    }

    @Test
    fun routeProgressObserverDoubleRegister() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val observer: RouteProgressObserver = mockk(relaxUnitFun = true)
        tripSession.registerRouteProgressObserver(observer)
        updateLocationAndJoin()
        tripSession.unregisterRouteProgressObserver(observer)
        tripSession.registerRouteProgressObserver(observer)

        verify(exactly = 2) { observer.onRouteProgressChanged(routeProgress) }
        tripSession.stopActiveGuidance()
    }

    @Test
    fun enhancedLocationObserverSuccess() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        tripSession.registerLocationObserver(observer)
        updateLocationAndJoin()

        verify { observer.onEnhancedLocationChanged(enhancedLocation, keyPoints) }
        assertEquals(enhancedLocation, tripSession.getEnhancedLocation())
        tripSession.stopActiveGuidance()
    }

    @Test
    fun enhancedLocationObserverImmediate() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        updateLocationAndJoin()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        tripSession.registerLocationObserver(observer)

        verify(exactly = 1) { observer.onEnhancedLocationChanged(enhancedLocation, emptyList()) }
        assertEquals(enhancedLocation, tripSession.getEnhancedLocation())
        tripSession.stopActiveGuidance()
    }

    @Test
    fun enhancedLocationObserverUnregister() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        tripSession.registerLocationObserver(observer)
        tripSession.unregisterLocationObserver(observer)
        updateLocationAndJoin()
        verify(exactly = 0) { observer.onEnhancedLocationChanged(enhancedLocation, keyPoints) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun getTripService() {
        assertEquals(tripService, tripSession.tripService)
    }

    @Test
    fun getRoute() {
        tripSession.route = route
        assertEquals(route, tripSession.route)
    }

    @Test
    fun setRoute() {
        tripSession.route = route

        coVerify { navigator.setRoute(route) }
    }

    @Test
    fun setRoute_nullable() {
        tripSession.route = null

        coVerify { navigator.setRoute(null) }
    }

    @Test
    fun stateObserverImmediateStop() {
        tripSession.registerStateObserver(stateObserver)
        verify(exactly = 1) { stateObserver.onSessionStateChanged(TripSessionState.STOPPED) }
    }

    @Test
    fun stateObserverImmediateStart() {
        tripSession.startActiveGuidance()
        tripSession.registerStateObserver(stateObserver)
        verify(exactly = 1) { stateObserver.onSessionStateChanged(TripSessionState.STARTED) }
    }

    @Test
    fun stateObserverStart() {
        tripSession.registerStateObserver(stateObserver)
        tripSession.startActiveGuidance()
        verify(exactly = 1) { stateObserver.onSessionStateChanged(TripSessionState.STARTED) }
    }

    @Test
    fun stateObserverStop() {
        tripSession.startActiveGuidance()
        tripSession.registerStateObserver(stateObserver)
        tripSession.stopActiveGuidance()
        verify(exactly = 1) { stateObserver.onSessionStateChanged(TripSessionState.STOPPED) }
    }

    @Test
    fun stateObserverDoubleStart() {
        tripSession.registerStateObserver(stateObserver)
        tripSession.startActiveGuidance()
        tripSession.startActiveGuidance()
        verify(exactly = 1) { stateObserver.onSessionStateChanged(TripSessionState.STARTED) }
    }

    @Test
    fun stateObserverDoubleStop() {
        tripSession.startActiveGuidance()
        tripSession.registerStateObserver(stateObserver)
        tripSession.stopActiveGuidance()
        tripSession.stopActiveGuidance()
        verify(exactly = 1) { stateObserver.onSessionStateChanged(TripSessionState.STOPPED) }
    }

    @Test
    fun stateObserverUnregister() {
        tripSession.registerStateObserver(stateObserver)
        clearMocks(stateObserver)
        tripSession.unregisterStateObserver(stateObserver)
        tripSession.startActiveGuidance()
        tripSession.stopActiveGuidance()
        verify(exactly = 0) { stateObserver.onSessionStateChanged(any()) }
    }

    @Test
    fun unregisterAllLocationObservers() = coroutineRule.runBlockingTest {
        every { routeProgress.bannerInstructions } returns null
        every { routeProgress.voiceInstructions } returns null

        tripSession.startActiveGuidance()
        val observer: LocationObserver = mockk(relaxUnitFun = true)
        tripSession.registerLocationObserver(observer)
        tripSession.unregisterAllLocationObservers()

        updateLocationAndJoin()

        verify(exactly = 0) { observer.onRawLocationChanged(location) }
        assertEquals(location, tripSession.getRawLocation())

        tripSession.stopActiveGuidance()
    }

    @Test
    fun unregisterAllRouteProgressObservers() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val routeProgressObserver: RouteProgressObserver = mockk(relaxUnitFun = true)
        tripSession.registerRouteProgressObserver(routeProgressObserver)
        tripSession.unregisterAllRouteProgressObservers()
        updateLocationAndJoin()

        verify(exactly = 0) { routeProgressObserver.onRouteProgressChanged(any()) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun unregisterAllOffRouteObservers() = coroutineRule.runBlockingTest {
        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        val offRouteObserver: OffRouteObserver = mockk(relaxUnitFun = true)
        tripSession.registerOffRouteObserver(offRouteObserver)
        tripSession.unregisterAllOffRouteObservers()

        every { tripStatus.offRoute } returns true
        updateLocationAndJoin()

        // registerOffRouteObserver will call onOffRouteStateChanged() on
        // the offRouteObserver so that accounts for the verify 1 time
        // below. However there shouldn't be any additional calls when
        // the locationCallback.onSuccess() is called because the collection
        // of offRouteObservers should be empty.
        verify(exactly = 1) { offRouteObserver.onOffRouteStateChanged(false) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun unregisterAllStateObservers() = coroutineRule.runBlockingTest {
        tripSession.registerStateObserver(stateObserver)
        clearMocks(stateObserver)
        tripSession.unregisterAllStateObservers()

        tripSession.stopActiveGuidance()

        verify(exactly = 0) { stateObserver.onSessionStateChanged(any()) }
    }

    @Test
    fun unregisterAllBannerInstructionsObservers() = coroutineRule.runBlockingTest {
        val bannerInstructionsObserver: BannerInstructionsObserver = mockk(relaxUnitFun = true)
        val bannerInstructions: BannerInstructions = mockk()

        every { routeProgress.bannerInstructions } returns bannerInstructions
        every { routeProgress.voiceInstructions } returns null
        every { tripStatus.offRoute } returns true

        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        tripSession.registerBannerInstructionsObserver(bannerInstructionsObserver)

        updateLocationAndJoin()

        tripSession.stopActiveGuidance()

        tripSession.startActiveGuidance()
        tripSession.unregisterAllBannerInstructionsObservers()

        updateLocationAndJoin()

        verify(exactly = 1) { bannerInstructionsObserver.onNewBannerInstructions(any()) }

        tripSession.stopActiveGuidance()
    }

    @Test
    fun unregisterAllVoiceInstructionsObservers() = coroutineRule.runBlockingTest {
        val voiceInstructionsObserver: VoiceInstructionsObserver = mockk(relaxUnitFun = true)
        val voiceInstructions: VoiceInstructions = mockk()
        every { routeProgress.bannerInstructions } returns null
        every { routeProgress.voiceInstructions } returns voiceInstructions
        every { tripStatus.offRoute } returns true

        tripSession = MapboxTripSession(
            tripService,
            locationEngine,
            navigatorPredictionMillis,
            navigator,
            ThreadController,
            logger = logger
        )
        tripSession.startActiveGuidance()
        tripSession.registerVoiceInstructionsObserver(voiceInstructionsObserver)

        updateLocationAndJoin()

        tripSession.stopActiveGuidance()

        tripSession.startActiveGuidance()
        tripSession.unregisterAllVoiceInstructionsObservers()

        updateLocationAndJoin()

        verify(exactly = 1) { voiceInstructionsObserver.onNewVoiceInstructions(any()) }

        tripSession.stopActiveGuidance()
    }

    @After
    fun cleanUp() {
        unmockkObject(ThreadController)
    }

    private suspend fun updateLocationAndJoin() {
        locationCallbackSlot.captured.onSuccess(locationEngineResult)
        parentJob.cancelAndJoin()
    }
}
