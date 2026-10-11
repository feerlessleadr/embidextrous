package com.feerlessleadr.embidextrous.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import kotlinx.coroutines.launch

class SignInViewModel(private val container: AppContainer) : ViewModel() {
    var server by mutableStateOf(container.sessions.lastServerUrl())
    var userName by mutableStateOf("")
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun signIn() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            try {
                container.signIn(server, userName, password)
                password = ""
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }
}
