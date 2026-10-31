package com.gojapan.core.maps

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripMapProviderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(): TripMapProvider = preferences.getString(KEY_PROVIDER, null)
        ?.let { value -> TripMapProvider.entries.firstOrNull { it.name == value } }
        ?: TripMapProvider.GOOGLE

    fun set(provider: TripMapProvider) {
        preferences.edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "map_preferences"
        const val KEY_PROVIDER = "trip_map_provider"
    }
}
