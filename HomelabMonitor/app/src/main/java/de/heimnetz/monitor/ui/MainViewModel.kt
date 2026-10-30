package de.heimnetz.monitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.heimnetz.monitor.data.AppConfig
import de.heimnetz.monitor.data.HomelabRepository
import de.heimnetz.monitor.data.ServiceAction
import de.heimnetz.monitor.data.ServiceStatus
import de.heimnetz.monitor.data.SettingsRepository
import de.heimnetz.monitor.work.UpdateScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val config: AppConfig,
    val services: List<ServiceStatus>,
    val loading: Boolean = false,
    val lastRefresh: Long? = null,
    val actionRunning: Boolean = false,
    val actionTitle: String? = null,
    val actionOutput: String? = null,
) {
    val configured: Boolean get() = config.ubuntuUser.isNotBlank() || config.omvUser.isNotBlank()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val repo = HomelabRepository()

    private val _state = MutableStateFlow(settings.load().let { UiState(it, repo.placeholders(it)) })
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        UpdateScheduler.apply(app, _state.value.config)
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        _state.update { it.copy(loading = true) }
        refreshJob = viewModelScope.launch {
            val list = repo.collectAll(_state.value.config)
            _state.update { it.copy(loading = false, services = list, lastRefresh = System.currentTimeMillis()) }
        }
    }

    fun saveConfig(c: AppConfig) {
        val clean = c.sanitized()
        settings.save(clean)
        _state.update { it.copy(config = clean, services = repo.placeholders(clean), lastRefresh = null) }
        UpdateScheduler.apply(getApplication(), clean)
        refresh()
    }

    fun runAction(serviceId: String, action: ServiceAction) {
        if (_state.value.actionRunning) return
        _state.update { it.copy(actionRunning = true) }
        viewModelScope.launch {
            val out = repo.runAction(_state.value.config, serviceId, action)
            _state.update { it.copy(actionRunning = false, actionTitle = action.label, actionOutput = out) }
            if (action != ServiceAction.REBOOT) refresh()
        }
    }

    fun clearOutput() {
        _state.update { it.copy(actionOutput = null, actionTitle = null) }
    }
}
