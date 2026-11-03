package com.srini.trafficfine.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.srini.trafficfine.data.remote.FirebasePaths
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.OfficerDirectoryRepository
import dagger.Lazy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live view of `/officers` for administrators. Reads are permitted only for admins by the
 * security rules; RTDB offline persistence keeps the last snapshot available offline.
 */
@Singleton
class FirebaseOfficerDirectoryRepository @Inject constructor(
    private val database: Lazy<FirebaseDatabase>,
) : OfficerDirectoryRepository {

    private val officers get() = database.get().getReference(FirebasePaths.OFFICERS)

    override fun observeOfficers(): Flow<List<Officer>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toOfficer() }.sortedBy { it.name.lowercase() })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        officers.addValueEventListener(listener)
        awaitClose { officers.removeEventListener(listener) }
    }

    override suspend fun setActive(uid: String, active: Boolean) {
        officers.child(uid).child("active").setValue(active).await()
    }

    private fun DataSnapshot.toOfficer(): Officer? {
        val uid = key ?: return null
        return Officer(
            uid = uid,
            name = (child("name").value as? String) ?: return null,
            email = (child("email").value as? String).orEmpty(),
            badgeNumber = (child("badgeNumber").value as? String).orEmpty(),
            station = (child("station").value as? String).orEmpty(),
            role = Role.fromClaim((child("role").value as? String)) ?: Role.OFFICER,
            active = child("active").value as? Boolean ?: true,
        )
    }
}
