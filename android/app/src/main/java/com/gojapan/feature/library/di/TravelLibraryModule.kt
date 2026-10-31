package com.gojapan.feature.library.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.gojapan.feature.library.data.TravelLibraryPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import com.gojapan.feature.review.domain.TravelReviewScheduler
import com.gojapan.feature.onboarding.data.UserProfilePreferences
import com.gojapan.core.analytics.AnalyticsTracker

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TravelLibraryStore

@Module
@InstallIn(SingletonComponent::class)
object TravelLibraryModule {
    @Provides
    @Singleton
    @TravelLibraryStore
    fun provideStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("travel-library") }

    @Provides
    @Singleton
    fun provideTravelLibraryPreferences(
        @TravelLibraryStore store: DataStore<Preferences>,
        clock: Clock,
    ): TravelLibraryPreferences = TravelLibraryPreferences(store, clock, TravelReviewScheduler())

    @Provides
    @Singleton
    fun provideUserProfilePreferences(@TravelLibraryStore store: DataStore<Preferences>): UserProfilePreferences =
        UserProfilePreferences(store)

    @Provides
    @Singleton
    fun provideAnalyticsTracker(
        @TravelLibraryStore store: DataStore<Preferences>,
        clock: Clock,
    ): AnalyticsTracker = AnalyticsTracker(store, clock)
}
