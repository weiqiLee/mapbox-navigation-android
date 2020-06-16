package com.mapbox.navigation.core.reroute

import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.base.common.logger.Logger
import com.mapbox.navigation.core.directions.session.DirectionsSession
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.routeoptions.RouteOptionsProvider
import com.mapbox.navigation.core.trip.session.TripSession
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapboxRerouteControllerTest {

    private lateinit var rerouteController: MapboxRerouteController

    @MockK
    private lateinit var directionsSession: DirectionsSession

    @MockK
    private lateinit var tripSession: TripSession

    @MockK
    private lateinit var routeOptionsProvider: RouteOptionsProvider

    @MockK
    private lateinit var logger: Logger

    @MockK
    private lateinit var routeOptions: RouteOptions

    @MockK
    private lateinit var routeCallback: RerouteController.RoutesCallback

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true, relaxed = true)
        rerouteController = spyk(
            MapboxRerouteController(
                directionsSession,
                tripSession,
                routeOptionsProvider,
                logger
            )
        )
    }

    @After
    fun cleanUp() {
        assertEquals(RerouteState.IDLE, rerouteController.rerouteState)
        // routeCallback mustn't called in current implementation. DirectionSession update routes internally
        verify(exactly = 0) { routeCallback.onNewRoutes(any()) }
    }

    @Test
    fun initial_state() {
        assertEquals(rerouteController.rerouteState, RerouteState.IDLE)
        verify(exactly = 0) { rerouteController.rerouteState = any() }
        verify(exactly = 0) { rerouteController.reroute(any()) }
        verify(exactly = 0) { rerouteController.interruptReroute() }
    }

    @Test
    fun reroute_success() {
        mockNewRouteOptions()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(
                routeOptions,
                capture(routeRequestCallback)
            )
        } returns mockk()

        rerouteController.reroute(routeCallback)
        routeRequestCallback.captured.onRoutesReady(mockk())

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.ROUTE_HAS_BEEN_FETCHED
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.IDLE
        }
        verify(ordering = Ordering.ORDERED) {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
            rerouteController.rerouteState = RerouteState.ROUTE_HAS_BEEN_FETCHED
            rerouteController.rerouteState = RerouteState.IDLE
        }
    }

    @Test
    fun reroute_unsuccess() {
        mockNewRouteOptions()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(routeOptions, capture(routeRequestCallback))
        } returns mockk()

        rerouteController.reroute(routeCallback)
        routeRequestCallback.captured.onRoutesRequestFailure(mockk(), mockk())

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.FAILED
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.IDLE
        }
        verify(ordering = Ordering.ORDERED) {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
            rerouteController.rerouteState = RerouteState.FAILED
            rerouteController.rerouteState = RerouteState.IDLE
        }
    }

    @Test
    fun reroute_request_canceled_external() {
        mockNewRouteOptions()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(routeOptions, capture(routeRequestCallback))
        } returns mockk()

        rerouteController.reroute(routeCallback)
        routeRequestCallback.captured.onRoutesRequestCanceled(mockk())

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.INTERRUPTED
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.IDLE
        }
        verifyOrder {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
            rerouteController.rerouteState = RerouteState.INTERRUPTED
            rerouteController.rerouteState = RerouteState.IDLE
        }
    }

    @Test
    fun interrupt_route_request() {
        mockNewRouteOptions()
        val routeRequestCallback = slot<RoutesRequestCallback>()
        every {
            directionsSession.requestRoutes(routeOptions, capture(routeRequestCallback))
        } returns mockk()
        every {
            directionsSession.cancel()
        } answers {
            routeRequestCallback.captured.onRoutesRequestCanceled(mockk())
        }

        rerouteController.reroute(routeCallback)
        rerouteController.interruptReroute()

        assertTrue(routeRequestCallback.isCaptured)
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.INTERRUPTED
        }
        verify(exactly = 1) {
            rerouteController.rerouteState = RerouteState.IDLE
        }
        verifyOrder {
            rerouteController.rerouteState = RerouteState.FETCHING_ROUTE
            rerouteController.rerouteState = RerouteState.INTERRUPTED
            rerouteController.rerouteState = RerouteState.IDLE
        }
    }

    @Test
    fun invalid_route_option() {
        mockNewRouteOptions(null)

        rerouteController.reroute(routeCallback)

        verify(exactly = 0) { rerouteController.rerouteState = any() }
        verify(exactly = 0) { directionsSession.requestRoutes(any(), any()) }
    }

    private fun mockNewRouteOptions(_routeOptions: RouteOptions? = routeOptions) {
        every {
            routeOptionsProvider.newRouteOptions(
                any(),
                any(),
                any()
            )
        } returns _routeOptions
    }
}
