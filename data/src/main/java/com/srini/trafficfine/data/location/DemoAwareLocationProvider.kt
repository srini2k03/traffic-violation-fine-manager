package com.srini.trafficfine.data.location

import com.srini.trafficfine.data.demo.DemoSessionStore
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.repository.LocationProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Uses the device location, except in a demo where a fixed spot in Adyar, Chennai is reported. */
@Singleton
class DemoAwareLocationProvider @Inject constructor(
    private val fused: FusedLocationProvider,
    private val demoSession: DemoSessionStore,
) : LocationProvider {

    override suspend fun currentLocation(): GeoPoint? =
        if (demoSession.isActive) DEMO_LOCATION else fused.currentLocation()

    companion object {
        val DEMO_LOCATION = GeoPoint(13.0067, 80.2570, 6f, "LB Road, Adyar, Chennai 600020")
    }
}
