package com.mapbox.navigation.ui.camera

import com.mapbox.navigation.ui.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleCameraTest : BaseTest() {

    @Test
    fun overview() {
        val route = buildTestDirectionsRoute()

        val result = SimpleCamera().overview(RouteInformation(route, null, null))

        assertEquals(19, result.size)
    }

    @Test
    fun tilt() {
        val result = SimpleCamera().tilt(RouteInformation(null, null, null))

        assertEquals(50.0, result, 0.0)
    }

    @Test
    fun zoom() {
        val result = SimpleCamera().zoom(RouteInformation(null, null, null))

        assertEquals(15.0, result, 0.0)
    }
}
