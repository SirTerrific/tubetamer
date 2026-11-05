package com.sirterrific.tubetamer.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.ui.AppViewModel
import com.sirterrific.tubetamer.ui.Screen
import com.sirterrific.tubetamer.ui.UiError
import com.sirterrific.tubetamer.core.R as CoreR

private const val PIN_MAX = 8

@Composable
fun MobileRoot(vm: AppViewModel, screen: Screen) {
    if (screen is Screen.Home) {
        // Home is a Scaffold that handles the insets itself.
        HomeScreen(screen.profile, onSwitchProfile = vm::signOut, onExpired = vm::sessionExpired)
        return
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        when (screen) {
            Screen.Loading -> CenteredColumn {
                AppLogo(size = 96.dp, showName = false)
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(CoreR.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is Screen.ServerSetup -> ServerSetupScreen(screen, vm::connect)
            is Screen.Offline -> OfflineScreen(screen.error, onRetry = vm::start, onChangeServer = vm::editServer)
            is Screen.Profiles -> ProfilesScreen(screen.profiles, vm::pickProfile, vm::editServer)
            is Screen.Pin -> {
                BackHandler(enabled = !screen.busy) { vm.backToProfiles() }
                PinScreen(screen, onSubmit = { vm.submitPin(screen.profile, it) })
            }
            is Screen.Home -> Unit
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
}

@Composable
internal fun ServerSetupScreen(state: Screen.ServerSetup, onConnect: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(state.current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        AppLogo(size = 72.dp, showName = false)
        Spacer(Modifier.height(20.dp))
        Title(stringResource(CoreR.string.setup_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(CoreR.string.setup_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(CoreR.string.setup_placeholder)) },
            singleLine = true,
            enabled = !state.busy,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConnect(text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onConnect(text) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.busy) CoreR.string.setup_checking else CoreR.string.setup_connect))
        }
    }
}

@Composable
private fun OfflineScreen(error: UiError, onRetry: () -> Unit, onChangeServer: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 480.dp).padding(24.dp)) {
        Title(stringResource(CoreR.string.offline_title))
        ErrorLine(error)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(CoreR.string.retry)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CoreR.string.change_server))
        }
    }
}

@Composable
private fun ProfilesScreen(profiles: List<Profile>, onPick: (Profile) -> Unit, onChangeServer: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Title(stringResource(CoreR.string.profiles_title))
        Spacer(Modifier.height(24.dp))
        if (profiles.isEmpty()) {
            Text(stringResource(CoreR.string.profiles_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(profiles, key = { it.id }) { p ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onPick(p) }
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                    ) {
                        Avatar(p, 80.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onChangeServer) { Text(stringResource(CoreR.string.change_server)) }
    }
}

@Composable
internal fun PinScreen(state: Screen.Pin, onSubmit: (String) -> Unit) {
    var pin by remember(state.profile.id, state.error) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.error, state.busy) { if (!state.busy) focus.requestFocus() }
    val submit = { if (pin.isNotEmpty() && !state.busy) onSubmit(pin) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Avatar(state.profile, 88.dp)
        Spacer(Modifier.height(16.dp))
        Title(stringResource(CoreR.string.pin_title, state.profile.displayName))
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter(Char::isDigit).take(PIN_MAX) },
            singleLine = true,
            enabled = !state.busy,
            isError = state.error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { submit() }, enabled = !state.busy && pin.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CoreR.string.ok))
        }
    }
}
