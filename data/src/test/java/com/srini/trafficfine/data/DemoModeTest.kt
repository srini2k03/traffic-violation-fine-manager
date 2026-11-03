package com.srini.trafficfine.data

import android.content.Context
import android.content.SharedPreferences
import com.srini.trafficfine.data.demo.DemoSessionStore
import com.srini.trafficfine.data.demo.RoomDemoModeRepository
import com.srini.trafficfine.data.local.OfficerLogDao
import com.srini.trafficfine.data.local.ViolationDao
import com.srini.trafficfine.data.remote.FirebaseAvailability
import com.srini.trafficfine.data.repository.DemoAwareAuthRepository
import com.srini.trafficfine.data.repository.DemoAwareOfficerDirectoryRepository
import com.srini.trafficfine.data.repository.FirebaseAuthRepository
import com.srini.trafficfine.data.repository.FirebaseOfficerDirectoryRepository
import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.demo.DemoModeConfig
import com.srini.trafficfine.domain.demo.DemoSeed
import com.srini.trafficfine.domain.model.DomainError
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DemoModeTest {

    private val prefs = mockk<SharedPreferences> {
        every { getString(any(), any()) } returns null
        every { edit() } returns mockk(relaxed = true)
    }
    private val context = mockk<Context> {
        every { getSharedPreferences(any<String>(), any()) } returns prefs
    }
    private val session = DemoSessionStore(context)
    private val availability = mockk<FirebaseAvailability>()
    private val firebaseAuth = mockk<FirebaseAuthRepository>(relaxUnitFun = true)
    private var firebaseResolved = false
    private val lazyFirebase = Lazy<FirebaseAuthRepository> { firebaseResolved = true; firebaseAuth }
    private val violationDao = mockk<ViolationDao>(relaxed = true)
    private val logDao = mockk<OfficerLogDao>(relaxed = true)
    private val demoRepo = RoomDemoModeRepository(DemoModeConfig(true), availability, violationDao, logDao, session)
    private val auth = DemoAwareAuthRepository(lazyFirebase, availability, session, Lazy<RoomDemoModeRepository> { demoRepo })

    @Test
    fun `without a Firebase config the app is signed out and never touches Firebase`() = runTest {
        every { availability.isConfigured } returns false

        assertNull(auth.currentOfficer.first())
        assertEquals(DomainError.BackendNotConfigured, auth.signIn("a@b.in", "password123").exceptionOrNull())
        assertEquals(DomainError.BackendNotConfigured, auth.sendPasswordReset("a@b.in").exceptionOrNull())
        auth.signOut()
        assertFalse(firebaseResolved)
    }

    @Test
    fun `starting a demo seeds Room and signs in locally, ending it cleans up`() = runTest {
        every { availability.isConfigured } returns false

        demoRepo.start(DemoData.ADMIN, DemoSeed(emptyList(), emptyList()))
        assertEquals(DemoData.ADMIN, auth.currentOfficer.first())

        auth.signOut()
        assertNull(auth.currentOfficer.first())
        coVerifyOrder {
            violationDao.deleteByOfficerPrefix(DemoData.UID_PREFIX)
            violationDao.upsertAll(any())
            logDao.insertAll(any())
            violationDao.deleteByOfficerPrefix(DemoData.UID_PREFIX)
        }
        assertFalse(firebaseResolved)
    }

    @Test
    fun `demo session takes precedence over Firebase and real sessions delegate`() = runTest {
        every { availability.isConfigured } returns true
        every { firebaseAuth.currentOfficer } returns flowOf(null)
        coEvery { firebaseAuth.signIn("a@b.in", "password123") } returns Result.failure(DomainError.InvalidCredentials)

        assertNull(auth.currentOfficer.first())
        assertEquals(DomainError.InvalidCredentials, auth.signIn("a@b.in", "password123").exceptionOrNull())

        session.activate(DemoData.OFFICER)
        assertEquals(DemoData.OFFICER, auth.currentOfficer.first())
        auth.signOut()
        coVerify(exactly = 0) { firebaseAuth.signOut() }
        assertFalse(session.isActive)

        auth.signOut()
        coVerify(exactly = 1) { firebaseAuth.signOut() }
    }

    @Test(expected = IllegalStateException::class)
    fun `demo cannot start when the build disables it`() = runTest {
        RoomDemoModeRepository(DemoModeConfig(false), availability, violationDao, logDao, session)
            .start(DemoData.ADMIN, DemoSeed(emptyList(), emptyList()))
    }

    @Test
    fun `demo roster is served locally and toggling stays local`() = runTest {
        val firebaseDirectory = mockk<FirebaseOfficerDirectoryRepository>()
        val directory = DemoAwareOfficerDirectoryRepository(Lazy<FirebaseOfficerDirectoryRepository> { firebaseDirectory }, session)
        session.activate(DemoData.ADMIN)

        assertEquals(DemoData.officers, directory.observeOfficers().first())
        directory.setActive(DemoData.OFFICER_3.uid, true)
        assertEquals(true, directory.observeOfficers().first().single { it.uid == DemoData.OFFICER_3.uid }.active)
        verify(exactly = 0) { firebaseDirectory.observeOfficers() }
    }
}
