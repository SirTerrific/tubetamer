package com.sirterrific.tubetamer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.api.Profile

internal val ScreenPadding = PaddingValues(horizontal = 58.dp, vertical = 32.dp)

@Composable
fun AppRoot(vm: AppViewModel, screen: Screen) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Home pads itself, so the player can use the whole screen.
            .then(if (screen is Screen.Home) Modifier else Modifier.padding(ScreenPadding)),
        contentAlignment = Alignment.Center,
    ) {
        when (screen) {
            Screen.Loading -> Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onBackground)
            is Screen.ServerSetup -> ServerSetupScreen(screen, vm::connect)
            is Screen.Offline -> OfflineScreen(screen.error, onRetry = vm::start, onChangeServer = vm::editServer)
            is Screen.Profiles -> ProfilesScreen(screen.profiles, vm::pickProfile, vm::editServer)
            is Screen.Pin -> {
                BackHandler(enabled = !screen.busy) { vm.backToProfiles() }
                PinScreen(screen, onSubmit = { vm.submitPin(screen.profile, it) })
            }
            is Screen.Home -> HomeScreen(screen.profile, onSwitchProfile = vm::signOut, onExpired = vm::sessionExpired)
        }
    }
}

@Composable
private fun errorText(e: UiError): String = stringResource(
    when (e) {
        UiError.BAD_ADDRESS -> R.string.err_bad_address
        UiError.UNREACHABLE -> R.string.err_unreachable
        UiError.NOT_TUBETAMER -> R.string.err_not_tubetamer
        UiError.TOO_OLD -> R.string.err_too_old
        UiError.WRONG_PIN -> R.string.err_wrong_pin
        UiError.TOO_MANY_TRIES -> R.string.err_too_many_tries
        UiError.SERVER -> R.string.err_server
    },
)

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
internal fun ErrorLine(e: UiError?) {
    if (e != null) {
        Spacer(Modifier.height(12.dp))
        Text(errorText(e), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ServerSetupScreen(state: Screen.ServerSetup, onConnect: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(state.current) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(560.dp)) {
        Title(stringResource(R.string.setup_title))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            enabled = !state.busy,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onConnect(text) }),
            modifier = Modifier
                .width(560.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(stringResource(R.string.setup_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            },
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onConnect(text) }, enabled = !state.busy) {
            Text(stringResource(if (state.busy) R.string.setup_checking else R.string.setup_connect))
        }
    }
}

@Composable
private fun OfflineScreen(error: UiError, onRetry: () -> Unit, onChangeServer: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Title(stringResource(R.string.offline_title))
        ErrorLine(error)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = onChangeServer) { Text(stringResource(R.string.change_server)) }
        }
    }
}

@Composable
private fun ProfilesScreen(profiles: List<Profile>, onPick: (Profile) -> Unit, onChangeServer: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(profiles) { if (profiles.isNotEmpty()) focus.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Title(stringResource(R.string.profiles_title))
        Spacer(Modifier.height(32.dp))
        if (profiles.isEmpty()) {
            Text(stringResource(R.string.profiles_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Padding leaves room for the focused card's scale-up, which LazyRow would clip.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    val mod = if (p == profiles.first()) Modifier.focusRequester(focus) else Modifier
                    Card(onClick = { onPick(p) }, modifier = mod.width(160.dp)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Avatar(p, 88)
                            Spacer(Modifier.height(12.dp))
                            Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        OutlinedButton(onClick = onChangeServer) { Text(stringResource(R.string.change_server)) }
    }
}

@Composable
internal fun Avatar(p: Profile, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(parseColor(p.avatarColor), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            p.avatarIcon.ifEmpty { p.displayName.take(1).uppercase() },
            fontSize = (sizeDp / 2).sp,
            color = Color(0xFF212121),
        )
    }
}

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF8EC5E8))

private const val PIN_MAX = 8

@Composable
private fun PinScreen(state: Screen.Pin, onSubmit: (String) -> Unit) {
    var pin by remember(state.profile.id, state.error) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.error, state.busy) { if (!state.busy) focus.requestFocus() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(state.profile, 72)
        Spacer(Modifier.height(12.dp))
        Title(stringResource(R.string.pin_title, state.profile.displayName))
        Spacer(Modifier.height(16.dp))
        Text(
            if (pin.isEmpty()) " " else "● ".repeat(pin.length).trim(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(20.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(DEL, "0", OK))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        val mod = if (key == "5") Modifier.focusRequester(focus) else Modifier
                        Button(
                            enabled = !state.busy,
                            onClick = {
                                when (key) {
                                    DEL -> pin = pin.dropLast(1)
                                    OK -> if (pin.isNotEmpty()) onSubmit(pin)
                                    else -> if (pin.length < PIN_MAX) pin += key
                                }
                            },
                            modifier = mod.size(width = 88.dp, height = 56.dp),
                        ) {
                            Text(
                                when (key) {
                                    DEL -> "⌫"
                                    OK -> stringResource(R.string.ok)
                                    else -> key
                                },
                                modifier = Modifier.fillMaxSize(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val DEL = "del"
private const val OK = "ok"

