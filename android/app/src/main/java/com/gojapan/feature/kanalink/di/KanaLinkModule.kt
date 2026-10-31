package com.gojapan.feature.kanalink.di

import android.content.Context
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.gojapan.feature.kanalink.data.course.AssetKanaCourseRepository
import com.gojapan.feature.kanalink.data.course.KanaCourseRepository
import com.gojapan.feature.kanalink.data.local.KanaLinkDatabase
import com.gojapan.feature.kanalink.data.local.DatabaseRecovery
import com.gojapan.feature.kanalink.data.local.MIGRATION_1_2
import com.gojapan.feature.kanalink.data.repository.LearningRepository
import com.gojapan.feature.kanalink.data.repository.RoomLearningRepository
import com.gojapan.feature.kanalink.data.preferences.KanaPreferences
import com.gojapan.feature.kanalink.notification.ReviewNotificationScheduler
import com.gojapan.feature.kanalink.domain.scheduler.MemoryScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object KanaLinkModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KanaLinkDatabase =
        Room.databaseBuilder(context, KanaLinkDatabase::class.java, DatabaseRecovery.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides @Singleton fun provideScheduler(): MemoryScheduler = MemoryScheduler()
    @Provides @Singleton fun provideClock(): Clock = Clock.systemUTC()

    @Provides @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("kana-link-settings") }

    @Provides @Singleton
    fun provideKanaPreferences(dataStore: DataStore<Preferences>): KanaPreferences = KanaPreferences(dataStore)

    @Provides @Singleton
    fun provideReviewNotificationScheduler(@ApplicationContext context: Context) = ReviewNotificationScheduler(context)

    @Provides @Singleton
    fun provideCourse(@ApplicationContext context: Context): KanaCourseRepository =
        AssetKanaCourseRepository(context.assets)

    @Provides @Singleton
    fun provideLearningRepository(database: KanaLinkDatabase, scheduler: MemoryScheduler): LearningRepository =
        RoomLearningRepository(database, scheduler)
}
