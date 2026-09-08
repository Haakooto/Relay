package com.clockr.app.ui.alarms

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AlarmConfigViewModelFactory(
    private val application: Application,
    private val alarmId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AlarmConfigViewModel(application, alarmId) as T
    }
}
