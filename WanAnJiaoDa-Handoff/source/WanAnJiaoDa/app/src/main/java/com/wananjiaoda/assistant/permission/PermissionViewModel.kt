package com.wananjiaoda.assistant.permission

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wananjiaoda.assistant.data.local.AppPreferencesDataStore
import com.wananjiaoda.assistant.service.AccessibilityEventLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PermissionViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = AppPreferencesDataStore(application).store
    private val checker = PermissionChecker(application)
    private val guideSeenKey = booleanPreferencesKey("permission_guide_seen")

    val guideSeen: StateFlow<Boolean?> = dataStore.data.map { values ->
        val seen: Boolean? = values[guideSeenKey] ?: false
        seen
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissionState = MutableStateFlow<PermissionState?>(null)
    val permissionState: StateFlow<PermissionState?> = _permissionState.asStateFlow()

    init {
        viewModelScope.launch {
            AccessibilityEventLogger.connected.collect { connected ->
                _permissionState.update { it?.withAccessibilityConnection(connected) }
            }
        }
    }

    fun refresh() {
        _permissionState.value = checker.check()
    }

    suspend fun markGuideSeen() {
        dataStore.edit { it[guideSeenKey] = true }
    }
}
