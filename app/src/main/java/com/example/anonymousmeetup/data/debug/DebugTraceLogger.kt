package com.example.anonymousmeetup.data.debug

import android.util.Log
import com.example.anonymousmeetup.BuildConfig
import com.example.anonymousmeetup.data.local.DebugTraceStore
import com.example.anonymousmeetup.data.model.DebugTraceEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugTraceLogger @Inject constructor(
    private val debugTraceStore: DebugTraceStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun traces(): Flow<List<DebugTraceEntry>> = debugTraceStore.observeEntries()

    fun debug(tag: String, message: String) = append("DEBUG", tag, message, null)

    fun error(tag: String, message: String, throwable: Throwable? = null) = append("ERROR", tag, message, throwable)

    fun clear() {
        scope.launch { debugTraceStore.clear() }
    }

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            if (level == "ERROR") {
                Log.e(tag, message, throwable)
            } else {
                Log.d(tag, message)
            }
        }
        scope.launch {
            val suffix = throwable?.message?.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
            debugTraceStore.append(tag = tag, message = message + suffix, level = level)
        }
    }
}
