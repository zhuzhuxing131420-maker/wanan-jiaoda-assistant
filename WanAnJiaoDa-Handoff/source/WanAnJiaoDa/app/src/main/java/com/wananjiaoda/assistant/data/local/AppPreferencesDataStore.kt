package com.wananjiaoda.assistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wan_an_jiao_da_preferences",
)
class AppPreferencesDataStore(context: Context) {
    val store: DataStore<Preferences> = context.applicationContext.appPreferencesDataStore
}
