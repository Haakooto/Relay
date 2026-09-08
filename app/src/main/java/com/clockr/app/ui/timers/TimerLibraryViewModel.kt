package com.clockr.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.LibraryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TimerLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ClockrDatabase.get(application)
    private val repository = BlockRepository(db)

    val items: StateFlow<List<LibraryItem>> = combine(
        db.timerDefDao().observeAll(),
        db.chainDefDao().observeAll(),
    ) { timers, chains -> timers to chains }
        .map { (timers, chains) ->
            val timerItems = timers.map {
                LibraryItem(it.id, it.label, BlockKind.TIMER, repository.totalDurationSeconds(it.label))
            }
            val chainItems = chains.map {
                LibraryItem(it.id, it.label, BlockKind.CHAIN, repository.totalDurationSeconds(it.label))
            }
            (chainItems + timerItems).sortedBy { it.label.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
