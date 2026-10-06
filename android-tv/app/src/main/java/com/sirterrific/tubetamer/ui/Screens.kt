package com.sirterrific.tubetamer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.api.Profile

@Composable
fun AppRoot(vm: AppViewModel, screen: Screen) {
    val l = LocalTvLayout.current
    TtBackground {
        // Home pads itself, so the player can use the whole screen.
        val pad = if (screen is Screen.Home) Modifier else Modifier.padding(l.screenPadding)
        Box(Modifier.fillMaxSize().then(pad), contentAlignment = Alignment.Center) {
            when (screen) {
                Screen.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogo(size = 96.dp, showName = false)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.loading), color = TtColors.Muted)
                }
                is Screen.ServerSetup -> ServerSetupScreen(screen, vm::connect)
                is Screen.Offline -> OfflineScreen(screen.error, onRetry = vm::start, onChangeServer = vm::editServer)
                is Screen.Profiles -> ProfilesScreen(screen.profiles, vm::pickProfile, vm::editServer)
                is Screen.Pin -> {
                    BackHandler(enabled = !screen.busy) { vm.backToProfiles() }
                    PinScreen(screen, onSubmit = { vm.submitPin(screen.profile, it) })
                }
                is Screen.Home -> HomeScreen(screen.profile, onSwitchProfile = vm::signOut, onExpired = vm::sessionExpired)
            }
            // Logo in the corner of the sign-in screens, like the web header.
            // Short screens (720p at TV density) have no room above the centered content.
            if (screen !is Screen.Home && screen !is Screen.Loading && l.height >= 480.dp) {
                Box(Modifier.align(Alignment.TopStart)) { AppLogo(size = 36.dp) }
            }
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
internal fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = TtColors.Text, textAlign = TextAlign.Center)
}

@Composable
internal fun ErrorLine(e: UiError?) {
    if (e != null) {
        Spacer(Modifier.height(12.dp))
        Text(errorText(e), color = TtColors.Error, textAlign = TextAlign.Center)
    }
}

/** Round icon badge heading the setup and error screens. */
@Composable
internal fun IconDisc(icon: ImageVector, tint: Color = TtColors.Accent) {
    Box(
        Modifier.size(76.dp).background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp)) }
}

@Composable
internal fun ServerSetupScreen(state: Screen.ServerSetup, onConnect: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(state.current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Top-aligned: the TV keyboard covers the lower half of the screen while typing.
    val l = LocalTvLayout.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 620.dp).fillMaxHeight().padding(top = l.height * 0.06f),
    ) {
        IconDisc(TtIcons.Server)
        Spacer(Modifier.height(20.dp))
        Title(stringResource(R.string.setup_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_hint), color = TtColors.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        TtTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(R.string.setup_placeholder),
            icon = TtIcons.Wifi,
            enabled = !state.busy,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onAction = { onConnect(text) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(24.dp))
        TtButton(
            stringResource(if (state.busy) R.string.setup_checking else R.string.setup_connect),
            onClick = { onConnect(text) },
            icon = TtIcons.Check,
            primary = true,
            enabled = !state.busy,
        )
    }
}

@Composable
private fun OfflineScreen(error: UiError, onRetry: () -> Unit, onChangeServer: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
        IconDisc(TtIcons.Wifi, tint = TtColors.Error)
        Spacer(Modifier.height(20.dp))
        Title(stringResource(R.string.offline_title))
        ErrorLine(error)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TtButton(stringResource(R.string.retry), onRetry, Modifier.focusRequester(focus), icon = TtIcons.Retry, primary = true)
            TtButton(stringResource(R.string.change_server), onChangeServer, icon = TtIcons.Server)
        }
    }
}

@Composable
private fun ProfilesScreen(profiles: List<Profile>, onPick: (Profile) -> Unit, onChangeServer: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(profiles) { if (profiles.isNotEmpty()) focus.requestFocus() }
    val l = LocalTvLayout.current
    val cardWidth = (l.cardWidth * 0.85f).coerceIn(140.dp, 200.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Title(stringResource(R.string.profiles_title))
        Spacer(Modifier.height(28.dp))
        if (profiles.isEmpty()) {
            Text(stringResource(R.string.profiles_empty), color = TtColors.Muted)
        } else {
            // Padding leaves room for the focused card's scale-up, which LazyRow would clip.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(l.gap + 4.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    val mod = if (p == profiles.first()) Modifier.focusRequester(focus) else Modifier
                    Card(
                        onClick = { onPick(p) },
                        modifier = mod.width(cardWidth),
                        shape = TtCard.shape,
                        colors = TtCard.colors(),
                        border = TtCard.border(),
                        glow = TtCard.glow(),
                        scale = CardDefaults.scale(focusedScale = 1.08f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 12.dp),
                        ) {
                            Avatar(p, (cardWidth.value * 0.52f).toInt())
                            Spacer(Modifier.height(14.dp))
                            Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        TtButton(stringResource(R.string.change_server), onChangeServer, icon = TtIcons.Server)
    }
}

/** Child avatar: their chosen color and emoji (set in the web app), else a gold initial. */
@Composable
internal fun Avatar(p: Profile, sizeDp: Int) {
    val color = parseColor(p.avatarColor)
    Box(
        Modifier.size(sizeDp.dp).background(color ?: TtColors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            p.avatarIcon.ifEmpty { p.displayName.take(1).uppercase() },
            fontSize = (sizeDp * 0.46f).sp,
            color = if (color == null) TtColors.OnAccent else Color(0xFF1B2233),
        )
    }
}

private fun parseColor(hex: String): Color? =
    if (hex.isBlank()) null else runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

private const val PIN_MAX = 8

@Composable
internal fun PinScreen(state: Screen.Pin, onSubmit: (String) -> Unit) {
    var pin by remember(state.profile.id, state.error) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.error, state.busy) { if (!state.busy) focus.requestFocus() }
    // Short screens: smaller keys so the pad always fits.
    val compact = LocalTvLayout.current.height < 520.dp
    val keyW = if (compact) 84.dp else 100.dp
    val keyH = if (compact) 46.dp else 54.dp

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 320.dp)) {
            Avatar(state.profile, if (compact) 80 else 104)
            Spacer(Modifier.height(16.dp))
            Title(stringResource(R.string.pin_title, state.profile.displayName))
            Spacer(Modifier.height(20.dp))
            PinDots(pin.length)
            ErrorLine(state.error)
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(DEL, "0", OK))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        val mod = if (key == "5") Modifier.focusRequester(focus) else Modifier
                        TtButton(
                            when (key) {
                                DEL -> stringResource(R.string.pin_delete)
                                OK -> stringResource(R.string.ok)
                                else -> key
                            },
                            onClick = {
                                when (key) {
                                    DEL -> pin = pin.dropLast(1)
                                    OK -> if (pin.isNotEmpty()) onSubmit(pin)
                                    else -> if (pin.length < PIN_MAX) pin += key
                                }
                            },
                            modifier = mod.size(width = keyW, height = keyH),
                            icon = when (key) { DEL -> TtIcons.Delete; OK -> TtIcons.Check; else -> null },
                            iconOnly = key == DEL || key == OK,
                            primary = key == OK,
                            enabled = !state.busy,
                            centered = true,
                        )
                    }
                }
            }
        }
    }
}

/** Typed digits as gold dots, with empty slots for the usual 4-digit PIN. */
@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(18.dp)) {
        repeat(maxOf(4, count)) { i ->
            Box(Modifier.size(16.dp).background(if (i < count) TtColors.Accent else TtColors.Card, RoundedCornerShape(8.dp)))
        }
    }
}

private const val DEL = "del"
private const val OK = "ok"
