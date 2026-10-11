package com.feerlessleadr.embidextrous.watching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.MAX_MINUTES
import com.feerlessleadr.embidextrous.data.UserStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** A message for the screen, shown once; with an Undo button when [undo] is set. */
data class WatchEvent(
    val message: String,
    val isError: Boolean = false,
    val undoLabel: String? = null,
    val undo: (suspend () -> Unit)? = null,
)

class WatchingEyeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.watchingEye

    val state: StateFlow<WatchState> = repository.state

    private val _events = MutableSharedFlow<WatchEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WatchEvent> = _events.asSharedFlow()

    /** Call when the screen becomes visible. */
    fun acquire() = repository.acquire()

    /** Call when the screen is no longer visible. */
    fun release() = repository.release()

    fun refresh() = repository.refreshNow()

    fun savePluginKey(key: String) = container.setPluginKey(key)

    /** The user's profile picture, or null when they have none (or it cannot be loaded). */
    suspend fun avatar(userId: String): android.graphics.Bitmap? = container.api()?.let { container.avatars.load(it, userId) }

    // ---- actions: each one tells the person how it went ------------------------------------------------------

    fun extend(user: UserStatus, minutes: Int) = run("Add time") {
        if (!validMinutes(minutes)) return@run
        repository.act { extend(user.userId, minutes) }
        _events.emit(
            WatchEvent("Added $minutes min for ${user.username}", undoLabel = "UNDO") {
                try {
                    repository.act { reduce(user.userId, minutes) }
                    _events.emit(WatchEvent("Undone for ${user.username}"))
                } catch (e: ApiException) {
                    _events.emit(WatchEvent(e.message.orEmpty(), isError = true))
                }
            },
        )
    }

    fun timeOut(user: UserStatus, minutes: Int) = run("Time-out") {
        if (!validMinutes(minutes)) return@run
        repository.act { timeOut(user.userId, minutes) }
        _events.emit(
            WatchEvent("${user.username}: timed out for $minutes min", undoLabel = "UNDO") {
                try {
                    repository.act { clearTimeOut(user.userId) }
                    _events.emit(WatchEvent("Time-out ended for ${user.username}"))
                } catch (e: ApiException) {
                    _events.emit(WatchEvent(e.message.orEmpty(), isError = true))
                }
            },
        )
    }

    fun clearTimeOut(user: UserStatus) = run("End time-out") {
        repository.act { clearTimeOut(user.userId) }
        _events.emit(WatchEvent("Time-out ended for ${user.username}"))
    }

    /** Ends a time-out and adds time in one step. */
    fun resume(user: UserStatus, minutes: Int) = run("Resume") {
        if (!validMinutes(minutes)) return@run
        repository.act {
            clearTimeOut(user.userId)
            extend(user.userId, minutes)
        }
        _events.emit(WatchEvent("Time-out ended, added $minutes min for ${user.username}"))
    }

    fun resetTime(user: UserStatus) = run("Reset") {
        repository.act { resetTime(user.userId) }
        _events.emit(WatchEvent("Time reset for ${user.username}"))
    }

    fun stopStream(user: UserStatus) = run("Stop stream") {
        repository.act { stopStream(user.userId) }
        _events.emit(WatchEvent("Stream stopped for ${user.username}"))
    }

    private suspend fun validMinutes(minutes: Int): Boolean {
        if (minutes in 1..MAX_MINUTES) return true
        _events.emit(WatchEvent("Minutes must be between 1 and $MAX_MINUTES.", isError = true))
        return false
    }

    private fun run(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                _events.emit(WatchEvent(e.message ?: "$what failed.", isError = true))
            } catch (e: Exception) {
                _events.emit(WatchEvent("$what failed. ${e.message.orEmpty()}".trim(), isError = true))
            }
        }
    }
}
