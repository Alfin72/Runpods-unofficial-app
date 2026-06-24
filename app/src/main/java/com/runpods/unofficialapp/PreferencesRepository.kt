package com.runpods.unofficialapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "runpods_preferences")

class PreferencesRepository(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val API_TOKEN_KEY = preferencesKey<String>("api_token")
        private val ENDPOINT_KEY = preferencesKey<String>("endpoint_url")
    }

    val tokenFlow: Flow<String> = dataStore.data.map { it[API_TOKEN_KEY] ?: "" }
    val endpointFlow: Flow<String> = dataStore.data.map { it[ENDPOINT_KEY] ?: DefaultEndpoints.list.first().url }

    suspend fun updateToken(token: String) {
        dataStore.edit { preferences ->
            preferences[API_TOKEN_KEY] = token
        }
    }

    suspend fun updateEndpoint(endpoint: String) {
        dataStore.edit { preferences ->
            preferences[ENDPOINT_KEY] = endpoint
        }
    }
}
