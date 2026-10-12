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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.BuildConfig

@Composable
fun SignInScreen(vm: SignInViewModel) {
    val focus = LocalFocusManager.current
    var showPassword by remember { mutableStateOf(false) }

    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            LogoMark(92.dp)
            Spacer(Modifier.height(18.dp))
            Text("Embidextrous", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Brand.OnDark)
            Text("Both hands on your Emby server", style = MaterialTheme.typography.titleMedium, color = accentOnDark(), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Watch-time limits for the kids and smart lists, in one app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.OnDarkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            val darkScreen = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (darkScreen) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Use your Emby account. An administrator gets every tool, a regular account gets Hall Monitor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = vm.server,
                        onValueChange = { vm.server = it },
                        label = { Text("Server address") },
                        placeholder = { Text("https://emby.example.com") },
                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                        singleLine = true,
                        enabled = !vm.busy,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = vm.userName,
                        onValueChange = { vm.userName = it },
                        label = { Text("User name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        enabled = !vm.busy,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = vm.password,
                        onValueChange = { vm.password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        enabled = !vm.busy,
                        shape = RoundedCornerShape(16.dp),
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
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        if (vm.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Sign in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (BuildConfig.DEBUG) {
                    "Debug build: plain http:// addresses are allowed for a local test server."
                } else {
                    "This app connects over HTTPS only. Your password is sent to your server once; only an access token is kept on the phone, encrypted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Brand.OnDarkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
