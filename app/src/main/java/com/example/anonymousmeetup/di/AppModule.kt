package com.example.anonymousmeetup.di

import android.content.Context
import androidx.work.Configuration
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.security.EncryptionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideEncryptionService(): EncryptionService {
        return EncryptionService()
    }

    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        @ApplicationContext context: Context
    ): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
}
