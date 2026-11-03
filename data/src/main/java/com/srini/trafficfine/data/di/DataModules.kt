package com.srini.trafficfine.data.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.srini.trafficfine.data.local.OfficerLogDao
import com.srini.trafficfine.data.local.TrafficFineDatabase
import com.srini.trafficfine.data.local.ViolationDao
import com.srini.trafficfine.data.location.FusedLocationProvider
import com.srini.trafficfine.data.pdf.AndroidChallanPdfGenerator
import com.srini.trafficfine.data.repository.FirebaseAuthRepository
import com.srini.trafficfine.data.repository.FirebaseOfficerDirectoryRepository
import com.srini.trafficfine.data.repository.OfflineFirstViolationRepository
import com.srini.trafficfine.data.repository.RoomOfficerLogRepository
import com.srini.trafficfine.data.sync.WorkManagerSyncScheduler
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.repository.ChallanDocumentGenerator
import com.srini.trafficfine.domain.repository.LocationProvider
import com.srini.trafficfine.domain.repository.OfficerDirectoryRepository
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.repository.ViolationRepository
import com.srini.trafficfine.domain.util.ChallanNumberGenerator
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Offline persistence must be enabled before the first database reference is created,
     * which a @Singleton provider guarantees. Pending writes then survive process death
     * and are replayed by the SDK when connectivity returns.
     */
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance().apply {
        setPersistenceEnabled(true)
        setPersistenceCacheSizeBytes(PERSISTENCE_CACHE_BYTES)
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance().apply {
        maxUploadRetryTimeMillis = 60_000
    }

    private const val PERSISTENCE_CACHE_BYTES = 20L * 1024 * 1024
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrafficFineDatabase =
        Room.databaseBuilder(context, TrafficFineDatabase::class.java, TrafficFineDatabase.NAME).build()

    @Provides
    fun provideViolationDao(db: TrafficFineDatabase): ViolationDao = db.violationDao()

    @Provides
    fun provideOfficerLogDao(db: TrafficFineDatabase): OfficerLogDao = db.officerLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider(Clock.systemDefaultZone())

    @Provides
    fun provideIdGenerator(): IdGenerator = IdGenerator.UUID_BASED

    @Provides
    @Singleton
    fun provideChallanNumberGenerator(): ChallanNumberGenerator = ChallanNumberGenerator()

    @Provides
    fun provideRepeatOffenderPolicy(): RepeatOffenderPolicy = RepeatOffenderPolicy.DEFAULT

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindViolationRepository(impl: OfflineFirstViolationRepository): ViolationRepository

    @Binds
    abstract fun bindOfficerLogRepository(impl: RoomOfficerLogRepository): OfficerLogRepository

    @Binds
    abstract fun bindOfficerDirectoryRepository(impl: FirebaseOfficerDirectoryRepository): OfficerDirectoryRepository

    @Binds
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindChallanDocumentGenerator(impl: AndroidChallanPdfGenerator): ChallanDocumentGenerator
}
