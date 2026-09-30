package com.relay.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.library.LibraryHeaderEntity
import com.relay.app.data.settings.AppSettings
import com.relay.app.library.BlockKind
import com.relay.app.library.LibraryItem
import com.relay.app.library.LibraryState
import com.relay.app.run.RunView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimersViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val runs = container.runs
    private val library = container.library

    val activeRuns: StateFlow<List<RunView>> = runs.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val libraryState: StateFlow<LibraryState?> = library.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings?> = container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A start that failed (broken reference, too big, ...): the message to show. */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    fun start(item: LibraryItem) = launchCatching {
        when (item.kind) {
            BlockKind.TIMER -> library.startTimer(item.id)
            BlockKind.RELAY -> library.startRelay(item.id)
        }
    }

    fun quickStart(seconds: Long) = launchCatching { library.quickStart(seconds) }

    fun pause(id: String) = launchCatching { runs.pause(id) }
    fun resume(id: String) = launchCatching { runs.resume(id) }
    fun reset(id: String) = launchCatching { runs.reset(id) }
    fun addMinute(id: String) = launchCatching { runs.addMinute(id) }
    fun stop(id: String) = launchCatching { runs.stop(id) }
    fun continueRun(id: String) = launchCatching { runs.continueRun(id) }
    fun restart(id: String) = launchCatching { runs.restart(id) }

    fun addHeader(title: String) = launchCatching { library.addHeader(title) }
    fun renameHeader(header: LibraryHeaderEntity, title: String) = launchCatching { library.renameHeader(header, title) }
    fun deleteHeader(id: String) = launchCatching { library.deleteHeader(id) }
    fun moveHeader(header: LibraryHeaderEntity, sortOrder: Long) = launchCatching { library.moveHeader(header, sortOrder) }
    fun moveBlock(item: LibraryItem, headerId: String?, sortOrder: Long) = launchCatching { library.moveBlock(item, headerId, sortOrder) }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            _errors.tryEmit(e.message ?: e.toString())
        }
    }
}
