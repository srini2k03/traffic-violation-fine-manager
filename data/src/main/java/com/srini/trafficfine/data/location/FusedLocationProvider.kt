package com.srini.trafficfine.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.srini.trafficfine.data.di.IoDispatcher
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.repository.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // checked in hasPermission()
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val location: Location = fetchLocation() ?: return null

        return GeoPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            address = reverseGeocode(location),
        )
    }

    @SuppressLint("MissingPermission") // only called after hasPermission()
    private suspend fun fetchLocation(): Location? {
        val cts = CancellationTokenSource()
        return try {
            val fresh: Location? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
            fresh ?: client.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            cts.cancel()
        }
    }

    private fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private suspend fun reverseGeocode(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(io) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }
                        .getOrNull()
                }
            }
            address?.let { a ->
                (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ").ifBlank { null }
            }
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 10_000L
        const val GEOCODE_TIMEOUT_MS = 4_000L
    }
}
