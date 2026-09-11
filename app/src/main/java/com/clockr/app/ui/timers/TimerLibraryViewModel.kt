package com.clockr.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.LibraryHeaderEntity
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.LibraryHeader
import com.clockr.app.domain.LibraryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TimerLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ClockrDatabase.get(application)
    private val repository = BlockRepository(db)

    val headers: StateFlow<List<LibraryHeader>> = db.libraryHeaderDao().observeAll()
        .map { rows -> rows.map { LibraryHeader(it.id, it.title, it.sortOrder) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<LibraryItem>> = combine(
        db.timerDefDao().observeAll(),
        db.chainDefDao().observeAll(),
    ) { timers, chains -> timers to chains }
        .map { (timers, chains) ->
            val timerItems = timers.map {
                LibraryItem(it.id, it.label, BlockKind.TIMER, repository.totalDurationSeconds(it.label), it.headerId, it.sortOrder)
            }
            val chainItems = chains.map {
                LibraryItem(it.id, it.label, BlockKind.CHAIN, repository.totalDurationSeconds(it.label), it.headerId, it.sortOrder)
            }
            (chainItems + timerItems).sortedWith(compareBy({ it.headerId ?: "" }, { it.sortOrder }, { it.label.lowercase() }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addHeader(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        db.libraryHeaderDao().upsert(LibraryHeaderEntity(id = UUID.randomUUID().toString(), title = title.trim()))
    }

    fun renameHeader(id: String, title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        db.libraryHeaderDao().findById(id)?.let { db.libraryHeaderDao().upsert(it.copy(title = title.trim())) }
    }

    /** The blocks under a deleted header aren't touched beyond losing their grouping — they fall
     *  back to Ungrouped, never deleted. */
    fun deleteHeader(id: String) = viewModelScope.launch {
        db.libraryHeaderDao().findById(id)?.let { header ->
            db.timerDefDao().getAllByHeader(id).forEach { db.timerDefDao().upsert(it.copy(headerId = null)) }
            db.chainDefDao().getAllByHeader(id).forEach { db.chainDefDao().upsert(it.copy(headerId = null)) }
            db.libraryHeaderDao().delete(header)
        }
    }

    fun moveHeader(headerId: String, newSortOrder: Long) = viewModelScope.launch {
        db.libraryHeaderDao().findById(headerId)?.let { db.libraryHeaderDao().upsert(it.copy(sortOrder = newSortOrder)) }
    }

    /** Drag-and-drop lands on both at once: which section a block is in, and where within it. */
    fun moveBlock(blockId: String, kind: BlockKind, newHeaderId: String?, newSortOrder: Long) = viewModelScope.launch {
        when (kind) {
            BlockKind.TIMER -> db.timerDefDao().findById(blockId)?.let {
                db.timerDefDao().upsert(it.copy(headerId = newHeaderId, sortOrder = newSortOrder))
            }
            BlockKind.CHAIN -> db.chainDefDao().findById(blockId)?.let {
                db.chainDefDao().upsert(it.copy(headerId = newHeaderId, sortOrder = newSortOrder))
            }
        }
    }
}
