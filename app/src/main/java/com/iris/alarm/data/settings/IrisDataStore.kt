package com.iris.alarm.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * One DataStore for the whole app. `preferencesDataStore` allows exactly one
 * instance per file, so both the settings and wake-check repositories read this
 * single delegate rather than declaring their own.
 */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iris_settings")
