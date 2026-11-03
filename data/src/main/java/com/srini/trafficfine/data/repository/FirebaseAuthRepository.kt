package com.srini.trafficfine.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.srini.trafficfine.data.remote.FirebasePaths
import com.srini.trafficfine.data.session.SessionStore
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val session: SessionStore,
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentOfficer: Flow<Officer?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
        .distinctUntilChanged { old, new -> old?.uid == new?.uid }
        .mapLatest { user -> user?.let { resolveOfficer(it, forceRefresh = false).getOrNull() } }
        .distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Result<Officer> = try {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: throw DomainError.InvalidCredentials
        // Force a token refresh so a role granted after the last sign-in is picked up.
        resolveOfficer(user, forceRefresh = true).onFailure { auth.signOut() }
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Result.failure(DomainError.InvalidCredentials)
    } catch (e: FirebaseAuthInvalidUserException) {
        Result.failure(DomainError.InvalidCredentials)
    } catch (e: FirebaseNetworkException) {
        Result.failure(DomainError.Network(e.message))
    } catch (e: DomainError) {
        Result.failure(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun signOut() {
        session.clear()
        auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    /**
     * Role comes from the `role` custom claim (set with the Admin SDK, see scripts/set-role.mjs);
     * profile details from `/officers/{uid}`. When offline, falls back to the cached profile.
     */
    private suspend fun resolveOfficer(user: FirebaseUser, forceRefresh: Boolean): Result<Officer> {
        val token = runCatching { user.getIdToken(forceRefresh).await() }.getOrNull()
            ?: return session.cachedOfficer(user.uid)?.let { Result.success(it) }
                ?: Result.failure(DomainError.Network("Unable to verify session"))

        val role = Role.fromClaim(token.claims[FirebasePaths.ROLE_CLAIM])
            ?: return Result.failure(DomainError.NotAuthorized)

        val profile = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
            runCatching {
                database.getReference(FirebasePaths.OFFICERS).child(user.uid).get().await()
            }.getOrNull()
        }
        val cached = session.cachedOfficer(user.uid)
        val officer = Officer(
            uid = user.uid,
            name = profile?.child("name")?.getValue(String::class.java)
                ?: cached?.name ?: user.displayName ?: user.email.orEmpty(),
            email = user.email.orEmpty(),
            badgeNumber = profile?.child("badgeNumber")?.getValue(String::class.java)
                ?: cached?.badgeNumber ?: user.uid.takeLast(4).uppercase(),
            station = profile?.child("station")?.getValue(String::class.java) ?: cached?.station.orEmpty(),
            role = role,
        )
        session.cacheOfficer(officer)
        return Result.success(officer)
    }

    private companion object {
        const val PROFILE_TIMEOUT_MS = 5_000L
    }
}
