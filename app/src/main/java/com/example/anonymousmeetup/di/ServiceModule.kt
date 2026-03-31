package com.example.anonymousmeetup.di

import android.content.Context
import com.example.anonymousmeetup.services.MapService
import com.example.anonymousmeetup.services.StaticMapService
import com.example.anonymousmeetup.data.location.LocationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideMapService(
        @ApplicationContext context: Context
    ): MapService {
        return MapService(context)
    }

    @Provides
    @Singleton
    fun provideStaticMapService(
        @ApplicationContext context: Context
    ): StaticMapService {
        return StaticMapService(context)
    }

    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context): LocationService {
        return LocationService(context)
    }
} 

