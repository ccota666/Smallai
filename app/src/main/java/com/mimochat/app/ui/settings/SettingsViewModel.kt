package com.mimochat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimochat.app.data.AppSettings
import com.mimochat.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setApiKey(v: String) = viewModelScope.launch { repo.setApiKey(v) }
    fun setBaseUrl(v: String) = viewModelScope.launch { repo.setBaseUrl(v) }
    fun setModels(v: String) = viewModelScope.launch { repo.setModels(v) }
    fun setModel(v: String) = viewModelScope.launch { repo.setModel(v) }
    fun setThemeMode(v: Int) = viewModelScope.launch { repo.setThemeMode(v) }
    fun setFontSize(v: Float) = viewModelScope.launch { repo.setFontSize(v) }
    fun setContextLimit(v: Int) = viewModelScope.launch { repo.setContextLimit(v) }
    fun setSystemPrompt(v: String) = viewModelScope.launch { repo.setSystemPrompt(v) }
    fun setBoldColor(v: Int) = viewModelScope.launch { repo.setBoldColor(v) }
}
