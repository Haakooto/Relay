package com.clockr.app.ui.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.data.ClockrDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Backs the in-app Clocks list (the Clock pane, now a small library rather than one fixed
 *  face). Seeds one default clock the first time it's empty, so upgrading from a version with
 *  no Clocks feature doesn't land on a surprising empty list — today's exact plain-digital look
 *  becomes clock #1 instead. */
class ClocksViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ClockrDatabase.get(application).clockConfigDao()

    val clocks: StateFlow<List<ClockConfigEntity>> = dao.observeInApp()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (dao.observeInApp().first().isEmpty()) {
                dao.upsert(ClockConfigEntity.default(id = UUID.randomUUID().toString()))
            }
        }
    }

    fun moveTo(id: String, newSortOrder: Long) = viewModelScope.launch {
        dao.findById(id)?.let { dao.upsert(it.copy(sortOrder = newSortOrder)) }
    }

    fun delete(id: String) = viewModelScope.launch {
        dao.findById(id)?.let { dao.delete(it) }
    }
}

class ClocksViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ClocksViewModel(application) as T
    }
}
