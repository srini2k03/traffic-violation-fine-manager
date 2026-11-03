package com.srini.trafficfine.di

import com.srini.trafficfine.BuildConfig
import com.srini.trafficfine.domain.demo.DemoModeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** `DEMO_MODE` is set in app/build.gradle.kts (override with `-PdemoMode=true|false`). */
    @Provides
    fun provideDemoModeConfig(): DemoModeConfig = DemoModeConfig(enabled = BuildConfig.DEMO_MODE)
}
