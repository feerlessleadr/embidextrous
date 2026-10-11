package com.feerlessleadr.embidextrous.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.BuildConfig

@Composable
fun SignInScreen(vm: SignInViewModel) {
    val focus = LocalFocusManager.current
    var showPassword by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Embidextrous", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Watch-time limits and smart lists for your Emby server. Sign in with your Emby account: an administrator gets every tool, a regular account gets Watching Eye.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = vm.server,
                onValueChange = { vm.server = it },
                label = { Text("Server address") },
                placeholder = { Text("https://emby.example.com") },
                singleLine = true,
                enabled = !vm.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.userName,
                onValueChange = { vm.userName = it },
                label = { Text("User name") },
                singleLine = true,
                enabled = !vm.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !vm.busy,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focus.clearFocus()
                    vm.signIn()
                }),
                modifier = Modifier.fillMaxWidth(),
            )

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    focus.clearFocus()
                    vm.signIn()
                },
                enabled = !vm.busy && vm.server.isNotBlank() && vm.userName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Sign in")
                }
            }

            Text(
                if (BuildConfig.DEBUG) {
                    "Debug build: plain http:// addresses are allowed for a local test server."
                } else {
                    "This app connects over HTTPS only. Your password is sent to your server once; only an access token is kept on the phone, encrypted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}
