package com.relay.app.ui.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.clock.City
import com.relay.app.clock.ClockStyle
import com.relay.app.data.clock.LocationEntity
import com.relay.app.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val clocks = container.clocks

    val settings: StateFlow<AppSettings?> = container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val locations: StateFlow<List<LocationEntity>> = clocks.observeLocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(city: City) = viewModelScope.launch { clocks.addLocation(city) }
    fun delete(id: String) = viewModelScope.launch { clocks.deleteLocation(id) }
    fun move(id: String, sortOrder: Long) = viewModelScope.launch { clocks.moveLocation(id, sortOrder) }
    fun saveStyle(style: ClockStyle) = viewModelScope.launch { clocks.setMainStyle(style) }
}
