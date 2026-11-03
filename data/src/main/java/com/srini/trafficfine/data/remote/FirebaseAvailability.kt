package com.srini.trafficfine.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase is initialised automatically only when the build was made with
 * `app/google-services.json`. Without it every Firebase entry point throws, so callers check
 * this before touching (or even constructing) a Firebase client.
 */
@Singleton
class FirebaseAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean by lazy { FirebaseApp.getApps(context).isNotEmpty() }
}
