package com.clockr.app.ui.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.data.ClockrDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

const val NEW_CLOCK_ROUTE_ID = "new_clock"

data class ClockConfigUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val id: String = "",
    val style: ClockStyleState = ClockStyleState(),
)

class ClockConfigViewModel(application: Application, private val clockId: String) : AndroidViewModel(application) {
    private val dao = ClockrDatabase.get(application).clockConfigDao()

    private val _uiState = MutableStateFlow(ClockConfigUiState())
    val uiState: StateFlow<ClockConfigUiState> = _uiState

    init {
        viewModelScope.launch {
            if (clockId == NEW_CLOCK_ROUTE_ID) {
                _uiState.value = ClockConfigUiState(loading = false, isNew = true, id = UUID.randomUUID().toString())
                return@launch
            }
            val entity = dao.findById(clockId)
            _uiState.value = if (entity == null) {
                ClockConfigUiState(loading = false, notFound = true, id = clockId)
            } else {
                ClockConfigUiState(loading = false, isNew = false, id = entity.id, style = entity.toStyleState())
            }
        }
    }

    fun updateStyle(style: ClockStyleState) {
        _uiState.value = _uiState.value.copy(style = style)
    }

    fun save(onSuccess: () -> Unit) = viewModelScope.launch {
        val s = _uiState.value
        val existing = dao.findById(s.id) ?: ClockConfigEntity.default(id = s.id)
        dao.upsert(existing.withStyle(s.style))
        onSuccess()
    }

    fun delete(onSuccess: () -> Unit) = viewModelScope.launch {
        dao.findById(_uiState.value.id)?.let { dao.delete(it) }
        onSuccess()
    }
}

class ClockConfigViewModelFactory(
    private val application: Application,
    private val clockId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ClockConfigViewModel(application, clockId) as T
    }
}
