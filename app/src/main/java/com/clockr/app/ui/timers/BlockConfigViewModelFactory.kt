package com.clockr.app.ui.timers

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** [BlockConfigViewModel] needs an extra [blockId] arg — the default factory only knows (Application). */
class BlockConfigViewModelFactory(
    private val application: Application,
    private val blockId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BlockConfigViewModel(application, blockId) as T
    }
}
