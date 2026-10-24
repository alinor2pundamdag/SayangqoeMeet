@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sayangqoemeet.app

import android.content.Intent
import android.Manifest
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.content.ContentValues
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.window.Dialog
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ShellBg = Color(0xFF070B18)
private val ShellPanel = Color(0xFF10182B)
private val ShellCyan = Color(0xFF55F4FF)
private val ShellBlue = Color(0xFF5D8CFF)
private val ShellText = Color(0xFFF0F5FF)
private val ShellMuted = Color(0xFF9BAAC5)
private val ShellLine = Color(0xFF2A3856)

private val RegistrationDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
private fun formatRegistrationDate(value: String): String = runCatching {
    RegistrationDateFormatter.withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrElse { value.replace("T", " ").substringBefore(".").removeSuffix("Z") }

private val ReligionOptions = listOf(
    "Prefer not to say", "Christianity", "Islam", "Buddhism", "Hinduism", "Judaism", "Other", "None"
)

internal enum class AppDestination { HOME, PROFILE, SETTINGS, CHATS, CHAT, CALL, ADMIN, USERS }

@Composable
internal fun SignedInShell(openChatsOnStart: Boolean = false, initialConversationId: String? = null, initialCallId: String? = null, initialCallRecipientId: String? = null, initialCallType: String = "voice", onIncomingCallHandled: (String) -> Unit = {}, isDarkTheme: Boolean = true, onThemeChanged: (Boolean) -> Unit = {}, onSignOut: () -> Unit) {
    // Heartbeats run only while this screen's Activity is STARTED. When the
    // app goes to the background, stop refreshing and expire this user's row.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (currentCoroutineContext().isActive) {
                    runCatching { SupabaseDataRepository.heartbeatPresence() }
                    kotlinx.coroutines.delay(30_000)
                }
            } finally {
                // repeatOnLifecycle cancels this block at ON_STOP. Ensure the
                // presence row is marked offline even when cancellation occurs.
                withContext(NonCancellable) {
                    runCatching { SupabaseDataRepository.clearPresence() }
                }
            }
        }
    }

    var destination by remember { mutableStateOf(if (!initialConversationId.isNullOrBlank()) AppDestination.CHAT else if (!initialCallId.isNullOrBlank() || !initialCallRecipientId.isNullOrBlank()) AppDestination.CALL else if (openChatsOnStart) AppDestination.CHATS else AppDestination.HOME) }
    var selectedConversation by remember { mutableStateOf<String?>(initialConversationId) }
    var selectedCallRecipient by remember { mutableStateOf<String?>(initialCallRecipientId) }
    var selectedCallType by remember { mutableStateOf(if (initialCallType == "video") "video" else "voice") }
    var showAbout by remember { mutableStateOf(false) }
    LaunchedEffect(initialConversationId) {
        if (!initialConversationId.isNullOrBlank()) {
            selectedConversation = initialConversationId
            destination = AppDestination.CHAT
        }
    }

    LaunchedEffect(openChatsOnStart) {
        if (openChatsOnStart) destination = AppDestination.CHATS
    }

    LaunchedEffect(initialCallId) {
        if (!initialCallId.isNullOrBlank()) destination = AppDestination.CALL
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Brush.verticalGradient(listOf(Color(0xFF0C1530), ShellBg)) else Brush.verticalGradient(listOf(Color(0xFFF5F8FF), Color(0xFFE5EDFA))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDarkTheme) Color(0xE60B1225) else Color(0xFFF8FAFF))
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("SayangqoeMeet", color = ShellCyan, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("PRIVATE CONNECTIONS", color = ShellMuted, fontSize = 9.sp, letterSpacing = 1.8.sp)
            }
            var showOverflow by remember { mutableStateOf(false) }
            Box {
                Text(
                    "⋮",
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showOverflow = true },
                                onLongPress = { destination = AppDestination.ADMIN }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = ShellCyan,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false },
                    modifier = Modifier.background(ShellPanel)
                ) {
                    DropdownMenuItem(text = { Text("Settings", color = ShellText) }, onClick = { showOverflow = false; destination = AppDestination.SETTINGS })
                    DropdownMenuItem(
                        text = { Text("About", color = ShellText) },
                        onClick = { showOverflow = false; showAbout = true }
                    )
                    DropdownMenuItem(text = { Text("Enable floating chat bubble", color = ShellText) }, onClick = {
                        showOverflow = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else context.startService(Intent(context, FloatingChatBubbleService::class.java))
                    })
                    DropdownMenuItem(text = { Text("Disable floating chat bubble", color = ShellText) }, onClick = {
                        showOverflow = false
                        context.stopService(Intent(context, FloatingChatBubbleService::class.java))
                    })
                    DropdownMenuItem(
                        text = { Text("Logout", color = ShellMuted) },
                        onClick = { showOverflow = false; onSignOut() }
                    )
                }
            }
        }

        if (showAbout) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("About SayangqoeMeet") },
                text = {
                    Text("SayangqoeMeet is a private communication app. Current areas include account authentication, profiles, private chats, and voice/video call UI. Email delivery, online presence, and real audio/video calling must be configured and verified before they can be described as fully operational.")
                },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) { Text("CLOSE") }
                }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    onProfile = { destination = AppDestination.PROFILE },
                    onChats = { destination = AppDestination.CHATS },
                    onCall = { destination = AppDestination.CALL },
                    onUsers = { destination = AppDestination.USERS }
                )
                AppDestination.PROFILE -> ProfileScreen(onBack = { destination = AppDestination.HOME })
                AppDestination.SETTINGS -> SettingsScreen(isDarkTheme = isDarkTheme, onThemeChanged = onThemeChanged, onBack = { destination = AppDestination.HOME })
                AppDestination.CHATS -> ChatListScreen(
                    onBack = { destination = AppDestination.HOME },
                    onOpenConversation = { id -> selectedConversation = id; destination = AppDestination.CHAT }
                )
                AppDestination.CHAT -> ConversationScreen(
                    conversationId = selectedConversation,
                    onBack = { destination = AppDestination.CHATS },
                    onCall = { id, type -> selectedCallRecipient = id; selectedCallType = type; destination = AppDestination.CALL }
                )
                AppDestination.CALL -> CallCenterScreen(initialTargetUserId = selectedCallRecipient, initialCallType = selectedCallType, initialCallId = initialCallId, onInitialCallHandled = onIncomingCallHandled, onBack = { destination = AppDestination.HOME })
                AppDestination.ADMIN -> AdminConsoleScreen(onBack = { destination = AppDestination.HOME })
                AppDestination.USERS -> RegisteredUsersScreen(
                    onBack = { destination = AppDestination.HOME },
                    onOpenConversation = { id -> selectedConversation = id; destination = AppDestination.CHAT },
                    onCall = { id, type -> selectedCallRecipient = id; selectedCallType = type; destination = AppDestination.CALL }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(if (isDarkTheme) Color(0xF20A1020) else Color(0xFFF8FAFF)).padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavAction("HOME", destination == AppDestination.HOME) { destination = AppDestination.HOME }
            NavAction("PROFILE", destination == AppDestination.PROFILE) { destination = AppDestination.PROFILE }
            NavAction("CHATS", destination == AppDestination.CHATS) { destination = AppDestination.CHATS }
            NavAction("SETTINGS", destination == AppDestination.SETTINGS) { destination = AppDestination.SETTINGS }
        }
    }
}

@Composable
private fun NavAction(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = if (selected) ShellCyan else ShellMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun HomeScreen(onProfile: () -> Unit, onChats: () -> Unit, onCall: () -> Unit, onUsers: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("YOUR SPACE. YOUR PEOPLE.", color = ShellMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("Stay close.\nStay connected.", color = ShellText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
        Text("Your private communication space.", color = ShellMuted, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        ActionCard("REGISTERED USERS", "Browse SayangqoeMeet profiles and discover people", "BROWSE USERS", onUsers)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, Brush.horizontalGradient(listOf(Color(0x7755F4FF), ShellLine)), RoundedCornerShape(20.dp))
            .background(ShellPanel, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = ShellCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(subtitle, color = ShellText, fontSize = 14.sp)
        Text(action, color = ShellBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(isDarkTheme: Boolean, onThemeChanged: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("sayangqoemeet_local_settings", android.content.Context.MODE_PRIVATE) }
    var showRegistrationDates by remember { mutableStateOf(preferences.getBoolean("show_registration_dates", true)) }
    var historyCount by remember { mutableStateOf(context.getSharedPreferences("people_search_history", android.content.Context.MODE_PRIVATE).getStringSet("queries", emptySet()).orEmpty().size) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("SETTINGS", onBack)
        Text("App preferences", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("These preferences are saved only on this device.", color = ShellMuted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().background(ShellPanel, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show registration dates", color = ShellText, fontWeight = FontWeight.SemiBold)
                Text("Display registration dates in the People directory.", color = ShellMuted, fontSize = 11.sp)
            }
            androidx.compose.material3.Switch(checked = showRegistrationDates, onCheckedChange = {
                showRegistrationDates = it
                preferences.edit().putBoolean("show_registration_dates", it).apply()
            })
        }
        Row(Modifier.fillMaxWidth().background(ShellPanel, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark theme", color = ShellText, fontWeight = FontWeight.SemiBold)
                Text("Use the dark appearance for app surfaces.", color = ShellMuted, fontSize = 11.sp)
            }
            androidx.compose.material3.Switch(checked = isDarkTheme, onCheckedChange = {
                preferences.edit().putBoolean("dark_theme", it).apply()
                onThemeChanged(it)
            })
        }
        Text("Privacy", color = ShellCyan, fontWeight = FontWeight.Bold)
        Text("People search history is stored locally on this device and is never uploaded to Supabase.", color = ShellMuted, fontSize = 12.sp)
        Text("Saved search terms: $historyCount", color = ShellMuted, fontSize = 12.sp)
        Button(onClick = {
            context.getSharedPreferences("people_search_history", android.content.Context.MODE_PRIVATE).edit().remove("queries").apply()
            historyCount = 0
        }, enabled = historyCount > 0, colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)) {
            Text("CLEAR LOCAL SEARCH HISTORY")
        }
        Text("Push notifications, online presence, and calls still depend on Android permissions and backend configuration.", color = ShellMuted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var religion by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Loading your profile…") }
    var loading by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showReligionMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            loading = true
            status = "Uploading profile photo…"
            scope.launch {
                try {
                    avatarUrl = SupabaseDataRepository.uploadProfileAvatar(context, uri)
                    status = "Photo uploaded. Save your profile to publish the change."
                } catch (error: Exception) {
                    status = error.message ?: "Could not upload profile photo."
                } finally { loading = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val profile = SupabaseDataRepository.loadProfile()
            if (profile != null) {
                displayName = profile.displayName
                username = profile.username.orEmpty()
                avatarUrl = profile.avatarUrl.orEmpty()
                bio = profile.bio.orEmpty()
                birthday = profile.birthday.orEmpty()
                religion = profile.religion.orEmpty()
                address = profile.address.orEmpty()
                phoneNumber = profile.phoneNumber.orEmpty()
                status = "Profile loaded."
            } else {
                status = "No profile yet. Create one below."
            }
        } catch (error: Exception) {
            status = error.message ?: "Could not load profile."
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        birthday = Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("CANCEL") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackHeader("YOUR PROFILE", onBack)
        Text("Make it yours.", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Your profile is linked to your signed-in account.", color = ShellMuted, fontSize = 13.sp)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(96.dp).border(1.dp, ShellCyan, CircleShape).background(ShellPanel, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = "Profile photo", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Text(displayName.trim().firstOrNull()?.uppercase() ?: username.trim().firstOrNull()?.uppercase() ?: "?", color = ShellCyan, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = { avatarPicker.launch("image/*") }, enabled = !loading) {
                Text(if (avatarUrl.isBlank()) "CHOOSE PROFILE PHOTO" else "CHANGE PROFILE PHOTO", color = ShellCyan)
            }
        }

        Field("DISPLAY NAME", displayName, { displayName = it }, "How should people see you?")
        Field("USERNAME", username, { username = it }, "3–24 letters, numbers, _, ., -")
        Field("BIO", bio, { bio = it }, "A short line about you")
        Field("ADDRESS", address, { address = it }, "City / area")
        Field("PHONE NUMBER", phoneNumber, { phoneNumber = it }, "e.g. 09xx xxx xxxx")

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BIRTHDAY", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Box(
                Modifier.fillMaxWidth()
                    .border(1.dp, ShellLine, RoundedCornerShape(14.dp))
                    .background(ShellPanel, RoundedCornerShape(14.dp))
                    .clickable { showDatePicker = true }
                    .padding(16.dp)
            ) {
                Text(
                    birthday.ifBlank { "Tap to select your birthday" },
                    color = if (birthday.isBlank()) ShellMuted else ShellText, fontSize = 14.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RELIGION", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Box {
                Box(
                    Modifier.fillMaxWidth()
                        .border(1.dp, ShellLine, RoundedCornerShape(14.dp))
                        .background(ShellPanel, RoundedCornerShape(14.dp))
                        .clickable { showReligionMenu = true }
                        .padding(16.dp)
                ) {
                    Text(
                        religion.ifBlank { "Tap to select" },
                        color = if (religion.isBlank()) ShellMuted else ShellText, fontSize = 14.sp
                    )
                }
                DropdownMenu(
                    expanded = showReligionMenu,
                    onDismissRequest = { showReligionMenu = false },
                    modifier = Modifier.background(ShellPanel)
                ) {
                    ReligionOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = ShellText) },
                            onClick = { religion = option; showReligionMenu = false }
                        )
                    }
                }
            }
        }

        Text(status, color = ShellMuted, fontSize = 12.sp)
        Button(
            onClick = {
                val cleanName = displayName.trim()
                val cleanUsername = username.trim()
                if (cleanName.isBlank()) {
                    status = "Display name is required."
                } else if (!Regex("^[A-Za-z0-9_.-]{3,24}$").matches(cleanUsername)) {
                    status = "Username must be 3–24 characters: letters, numbers, _, . or -."
                } else {
                    loading = true
                    scope.launch {
                        try {
                            val saved = SupabaseDataRepository.saveProfile(
                                displayName = cleanName,
                                username = cleanUsername,
                                avatarUrl = avatarUrl,
                                bio = bio,
                                birthday = birthday,
                                religion = religion,
                                address = address,
                                phoneNumber = phoneNumber
                            )
                            displayName = saved.displayName
                            username = saved.username.orEmpty()
                            avatarUrl = saved.avatarUrl.orEmpty()
                            bio = saved.bio.orEmpty()
                            birthday = saved.birthday.orEmpty()
                            religion = saved.religion.orEmpty()
                            address = saved.address.orEmpty()
                            phoneNumber = saved.phoneNumber.orEmpty()
                            status = "Profile saved."
                        } catch (error: Exception) {
                            status = error.message ?: "Could not save profile."
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
        ) { Text(if (loading) "SAVING…" else "SAVE PROFILE", fontWeight = FontWeight.Bold) }

        Text(
            "SayangqoeMeet v${BuildConfig.VERSION_NAME}",
            color = ShellMuted,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        )
    }
}

@Composable
private fun ChatListScreen(onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    var conversations by remember { mutableStateOf<List<ConversationRecord>>(emptyList()) }
    var status by remember { mutableStateOf("Loading conversations…") }

    suspend fun refresh() {
        try {
            conversations = SupabaseDataRepository.loadConversations()
            status = if (conversations.isEmpty()) "Your accepted chats will appear here." else ""
        } catch (error: Exception) {
            status = error.message ?: "Could not load conversations."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("CHATS", onBack)
        Text("Your chats", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Only conversations you belong to are shown here.", color = ShellMuted, fontSize = 13.sp)
        if (status.isNotBlank()) Text(status, color = ShellMuted, fontSize = 12.sp)
        if (conversations.isEmpty()) {
            EmptyState("NO CHATS YET", "When you have an accepted conversation, it will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    Column(
                        Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(16.dp))
                            .background(ShellPanel, RoundedCornerShape(16.dp))
                            .clickable { onOpenConversation(conversation.id) }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = conversation.displayLabel.ifBlank {
                                if (conversation.isGroup) "Group conversation" else "SayangqoeMeet user"
                            },
                            color = ShellText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!conversation.isGroup && !conversation.otherUsername.isNullOrBlank()) {
                            Text("@${conversation.otherUsername}", color = ShellMuted, fontSize = 11.sp)
                        }
                        Text("Tap to open messages", color = ShellMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisteredUsersScreen(onBack: () -> Unit, onOpenConversation: (String) -> Unit, onCall: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<RegisteredUserRecord>>(emptyList()) }
    var status by remember { mutableStateOf("Loading registered users…") }
    var loadingUserId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchHistory by remember { mutableStateOf(false) }
    var activeOnly by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val searchPrefs = remember { context.getSharedPreferences("people_search_history", android.content.Context.MODE_PRIVATE) }
    var searchHistory by remember { mutableStateOf(searchPrefs.getStringSet("queries", emptySet()).orEmpty().toList().take(8)) }
    val filteredUsers = remember(users, searchQuery, activeOnly) {
        val query = searchQuery.trim().removePrefix("@").lowercase()
        users.filter { user ->
            (!activeOnly || user.isOnline == true) &&
                (query.isBlank() || user.displayName.lowercase().contains(query) ||
                    user.username.orEmpty().lowercase().contains(query))
        }
    }

    LaunchedEffect(refreshKey) {
        status = "Loading registered users…"
        try {
            users = SupabaseDataRepository.loadRegisteredUsers()
            status = if (users.isEmpty()) "No other registered profiles are available yet." else ""
        } catch (error: Exception) {
            status = error.message ?: "Could not load registered users."
        }
    }

    LaunchedEffect(Unit) {
        val subscription = SupabaseRealtime.subscribe(table = "user_presence", filter = "", onRecord = { record ->
            val id = record.optString("user_id")
            if (id.isNotBlank()) {
                val online = runCatching { java.time.Instant.parse(record.optString("last_seen_at")).isAfter(java.time.Instant.now().minusSeconds(90)) }.getOrDefault(false)
                users = users.map { if (it.id == id) it.copy(isOnline = online) else it }
            }
        }, onFailure = { /* Keep the polling fallback for transient Realtime errors. */ })
        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(30_000)
                if (loadingUserId == null) {
                    val refreshed = runCatching { SupabaseDataRepository.loadRegisteredUsers() }.getOrNull()
                    if (refreshed != null && refreshed != users) users = refreshed
                }
            }
        } finally { subscription?.close() }
    }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader("REGISTERED USERS", onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Discover people", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("${users.size} registered profile(s) shown · newest first", color = ShellMuted, fontSize = 12.sp)
            }
            TextButton(onClick = { refreshKey++ }, enabled = loadingUserId == null) {
                Text("REFRESH", color = ShellCyan, fontSize = 11.sp)
            }
        }
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it; showSearchHistory = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search name or @username", color = ShellMuted) }, trailingIcon = { TextButton(onClick = { showSearchHistory = !showSearchHistory }) { Text("RECENT", color = ShellCyan, fontSize = 9.sp) } }, colors = fieldColorsForShell())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { activeOnly = true }) {
                Text("ACTIVE", color = if (activeOnly) ShellCyan else ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { activeOnly = false }) {
                Text("ALL PEOPLE", color = if (!activeOnly) ShellCyan else ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val query = searchQuery.trim()
                if (query.isNotBlank()) {
                    searchHistory = (listOf(query) + searchHistory.filterNot { it.equals(query, ignoreCase = true) }).take(8)
                    searchPrefs.edit().putStringSet("queries", searchHistory.toSet()).apply()
                    showSearchHistory = false
                }
            }, enabled = searchQuery.isNotBlank()) { Text("SAVE SEARCH LOCALLY", color = ShellCyan, fontSize = 9.sp) }
            TextButton(onClick = { searchHistory = emptyList(); searchPrefs.edit().remove("queries").apply() }) { Text("CLEAR HISTORY", color = ShellMuted, fontSize = 9.sp) }
        }
        if (showSearchHistory && searchHistory.isNotEmpty()) Column(Modifier.fillMaxWidth().background(ShellPanel, RoundedCornerShape(12.dp)).padding(8.dp)) {
            searchHistory.forEach { query ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { searchQuery = query; showSearchHistory = false }, modifier = Modifier.weight(1f)) {
                        Text(query, color = ShellText)
                    }
                    TextButton(onClick = {
                        searchHistory = searchHistory.filterNot { it == query }
                        searchPrefs.edit().putStringSet("queries", searchHistory.toSet()).apply()
                    }) { Text("X", color = ShellMuted, fontSize = 10.sp) }
                }
            }
        }
        Text("Green = active within the last 90 seconds; amber = presence could not be verified. Search history stays on this device.", color = ShellMuted, fontSize = 11.sp)
        if (status.isNotBlank()) Text(status, color = ShellMuted, fontSize = 12.sp)
        if (filteredUsers.isEmpty() && status.isBlank()) Text("No people match this search.", color = ShellMuted, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredUsers, key = { it.id }) { user ->
                Row(
                    Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(16.dp))
                        .background(ShellPanel, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).background(when (user.isOnline) { true -> Color(0xFF43E08A); false -> ShellMuted; null -> Color(0xFFFFCE79) }, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(ShellPanel), contentAlignment = Alignment.Center) {
                        if (!user.avatarUrl.isNullOrBlank()) {
                            AsyncImage(model = user.avatarUrl, contentDescription = "Profile photo for ${user.displayName}", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else Text(user.displayName.trim().firstOrNull()?.uppercase() ?: "?", color = ShellCyan, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName.ifBlank { user.username ?: "SayangqoeMeet user" }, color = ShellText, fontWeight = FontWeight.SemiBold)
                        Text(user.username?.let { "@$it" } ?: "Username not set", color = ShellMuted, fontSize = 11.sp)
                        val showDates = context.getSharedPreferences("sayangqoemeet_local_settings", android.content.Context.MODE_PRIVATE).getBoolean("show_registration_dates", true)
                        if (showDates) Text("Registered: ${formatRegistrationDate(user.createdAt)}", color = ShellMuted, fontSize = 10.sp)
                        Text(when (user.isOnline) { true -> "Active now"; false -> "Offline"; null -> "Status unavailable" }, color = when (user.isOnline) { true -> Color(0xFF43E08A); false -> ShellMuted; null -> Color(0xFFFFCE79) }, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { onCall(user.id, "voice") }) { Text("VOICE", color = ShellCyan, fontSize = 10.sp) }
                        TextButton(onClick = { onCall(user.id, "video") }) { Text("VIDEO", color = ShellCyan, fontSize = 10.sp) }
                    }
                    TextButton(
                        enabled = loadingUserId == null && !user.username.isNullOrBlank(),
                        onClick = {
                            loadingUserId = user.id
                            status = "Opening private chat…"
                            scope.launch {
                                try {
                                    val conversationId = SupabaseDataRepository.openPrivateConversation(user.id)
                                    status = ""
                                    onOpenConversation(conversationId)
                                } catch (error: Exception) {
                                    status = error.message ?: "Could not open private chat."
                                } finally {
                                    loadingUserId = null
                                }
                            }
                        }
                    ) {
                        Text(if (loadingUserId == user.id) "OPENING…" else "MESSAGE", color = ShellCyan, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ConversationScreen(conversationId: String?, onBack: () -> Unit, onCall: (String?, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var pendingSendRequestId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<MessageRecord>>(emptyList()) }
    var status by remember { mutableStateOf("Loading message history…") }
    var loading by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var hasOlderMessages by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<MessageRecord?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var otherUserId by remember { mutableStateOf<String?>(null) }
    var otherDisplayName by remember { mutableStateOf<String?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var showChatInfo by remember { mutableStateOf(false) }
    var showSharedMedia by remember { mutableStateOf(false) }
    var capturePreviewUri by remember { mutableStateOf<Uri?>(null) }
    var capturePreviewMime by remember { mutableStateOf<String?>(null) }
    var capturePreviewName by remember { mutableStateOf<String?>(null) }
    val messageListState = rememberLazyListState()
    val photoCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (!saved) { capturePreviewUri = null; capturePreviewMime = null; capturePreviewName = null }
    }
    val videoCapture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            capturePreviewUri = uri
            capturePreviewMime = context.contentResolver.getType(uri) ?: "video/mp4"
            capturePreviewName = context.contentResolver.getDisplayName(uri) ?: "captured-video.mp4"
        } else { capturePreviewUri = null; capturePreviewMime = null; capturePreviewName = null }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !conversationId.isNullOrBlank()) {
            scope.launch {
                loading = true
                try {
                    val name = context.contentResolver.getDisplayName(uri) ?: "video"
                    val sent = SupabaseDataRepository.uploadChatAttachment(context, conversationId, uri, context.contentResolver.getType(uri) ?: "video/mp4", name)
                    messages = (messages + sent).distinctBy { it.id }.sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
                    status = ""
                } catch (error: Exception) { status = error.message ?: "Video upload failed." }
                finally { loading = false }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !conversationId.isNullOrBlank()) {
            scope.launch {
                loading = true
                try {
                    val name = context.contentResolver.getDisplayName(uri) ?: "attachment"
                    val sent = SupabaseDataRepository.uploadChatAttachment(context, conversationId, uri, context.contentResolver.getType(uri) ?: "application/octet-stream", name)
                    messages = (messages + sent).distinctBy { it.id }.sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
                    status = ""
                } catch (error: Exception) { status = error.message ?: "Attachment upload failed." }
                finally { loading = false }
            }
        }
    }

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) messageListState.animateScrollToItem(messages.lastIndex)
    }

    DisposableEffect(conversationId) {
        if (conversationId.isNullOrBlank()) return@DisposableEffect onDispose { }
        val subscription = SupabaseDataRepository.subscribeToMessages(conversationId, onChange = { change ->
            scope.launch {
                val affected = if (change.eventType == "DELETE") change.oldRecord else change.record
                val affectedId = affected.optString("id").takeIf { it.isNotBlank() && it != "null" }
                val deletedAt = affected.optString("deleted_at")
                val isDeletion = isMessageDeletionEvent(change.eventType, affectedId, deletedAt)
                if (isDeletion) {
                    messages = reconcileRealtimeMessages(
                        existing = messages,
                        fresh = emptyList(),
                        eventType = change.eventType,
                        affectedId = affectedId,
                        deletedAt = deletedAt
                    )
                } else {
                    runCatching { SupabaseDataRepository.loadMessages(conversationId) }.onSuccess { fresh ->
                        messages = reconcileRealtimeMessages(
                            existing = messages,
                            fresh = fresh,
                            eventType = change.eventType,
                            affectedId = affectedId,
                            deletedAt = deletedAt
                        )
                        if (messages.isNotEmpty()) status = ""
                    }.onFailure { error ->
                        status = error.message ?: "Could not refresh live messages."
                    }
                }
            }
        }, onFailure = { reason ->
            scope.launch { status = reason }
        })
        onDispose { subscription?.close() }
    }

    LaunchedEffect(conversationId) {
        draft = ""
        pendingSendRequestId = null
        currentUserId = SupabaseAuthRepository.currentUserId()
        otherUserId = null
        otherDisplayName = null
        if (conversationId.isNullOrBlank()) {
            status = "Select a real conversation first."
        } else {
            try {
                val conversation = SupabaseDataRepository.loadConversations().firstOrNull { it.id == conversationId }
                otherUserId = conversation?.otherUserId
                otherDisplayName = conversation?.displayLabel
                messages = SupabaseDataRepository.loadMessages(conversationId)
                hasOlderMessages = messages.size >= 100
                status = if (messages.isEmpty()) "No messages yet. Say hello." else ""
            } catch (error: Exception) { status = error.message ?: "Could not load messages." }
        }
    }

    fun beginPhotoCapture() {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "sayangqoemeet-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create a photo destination.")
            capturePreviewUri = uri; capturePreviewMime = "image/jpeg"
            capturePreviewName = context.contentResolver.getDisplayName(uri) ?: "photo.jpg"
            photoCapture.launch(uri)
        }.onFailure { status = it.message ?: "Could not open the camera." }
    }
    fun beginVideoCapture() {
        runCatching { videoCapture.launch(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)) }
            .onFailure { status = it.message ?: "Could not open video capture." }
    }
    fun sendCapturedMedia() {
        val uri = capturePreviewUri ?: return
        val mime = capturePreviewMime ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = capturePreviewName ?: context.contentResolver.getDisplayName(uri) ?: "capture"
        if (conversationId.isNullOrBlank()) return
        scope.launch {
            loading = true
            try {
                val sent = SupabaseDataRepository.uploadChatAttachment(context, conversationId, uri, mime, name)
                messages = (messages + sent).distinctBy { it.id }.sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
                status = ""; capturePreviewUri = null; capturePreviewMime = null; capturePreviewName = null
            } catch (error: Exception) { status = error.message ?: "Media upload failed." }
            finally { loading = false }
        }
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ BACK", color = ShellCyan, modifier = Modifier.clickable(onClick = onBack).padding(8.dp), fontSize = 11.sp)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(otherDisplayName ?: "PRIVATE CHAT", color = ShellText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (conversationId.isNullOrBlank()) "No conversation selected" else "Private conversation", color = ShellMuted, fontSize = 10.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showChatInfo = true }, enabled = !conversationId.isNullOrBlank()) { Text("INFO", color = ShellCyan, fontSize = 9.sp) }
                TextButton(onClick = { showSharedMedia = true }, enabled = messages.any { it.attachmentPath != null }) { Text("MEDIA", color = ShellCyan, fontSize = 9.sp) }
                IconButton(onClick = { onCall(otherUserId, "voice") }, enabled = !otherUserId.isNullOrBlank()) {
                    Icon(Icons.Default.Call, contentDescription = "Start voice call", tint = ShellCyan)
                }
                IconButton(onClick = { onCall(otherUserId, "video") }, enabled = !otherUserId.isNullOrBlank()) {
                    Icon(Icons.Default.Videocam, contentDescription = "Start video call", tint = ShellCyan)
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = ShellMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (hasOlderMessages && messages.isNotEmpty()) {
            TextButton(
                enabled = !loadingOlder && !loading,
                onClick = {
                    val first = messages.firstOrNull() ?: return@TextButton
                    loadingOlder = true
                    scope.launch {
                        try {
                            val older = SupabaseDataRepository.loadOlderMessages(conversationId.orEmpty(), first)
                            messages = (older + messages).distinctBy { it.id }
                                .sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
                            hasOlderMessages = older.size >= 50
                            status = if (older.isEmpty()) "You have reached the beginning of this conversation." else ""
                        } catch (error: Exception) {
                            status = error.message ?: "Could not load older messages."
                        } finally { loadingOlder = false }
                    }
                }
            ) { Text(if (loadingOlder) "LOADING OLDER MESSAGES…" else "LOAD OLDER MESSAGES", color = ShellCyan, fontSize = 10.sp) }
        }
        LazyColumn(
            state = messageListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == currentUserId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp)
                            .background(if (mine) Color(0xFF173B70) else ShellPanel, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (message.attachmentPath == null) {
                            Text(message.body, color = ShellText, fontSize = 14.sp)
                        } else {
                            Text("ATTACHMENT", color = ShellCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(message.attachmentName ?: message.body, color = ShellText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (isAttachmentPurged(message.attachmentPurgedAt)) {
                                Text(
                                    "MEDIA EXPIRED",
                                    color = ShellMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                Text(
                                    "This attachment was cleaned up by the retention policy. The message record remains.",
                                    color = ShellMuted,
                                    fontSize = 11.sp
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val cachedUri = SupabaseDataRepository.downloadAttachmentToCache(
                                                    context = context,
                                                    messageId = message.id,
                                                    senderId = message.senderId,
                                                    objectPath = message.attachmentPath,
                                                    mimeType = message.attachmentMimeType,
                                                    displayName = message.attachmentName ?: message.body
                                                )
                                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(cachedUri, message.attachmentMimeType ?: "*/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    clipData = android.content.ClipData.newUri(context.contentResolver, "SayangqoeMeet attachment", cachedUri)
                                                }
                                                context.startActivity(Intent.createChooser(openIntent, "Open attachment"))
                                            } catch (error: Exception) { status = error.message ?: "Could not open attachment." }
                                        }
                                    }
                                ) { Text("OPEN FILE", color = ShellCyan, fontSize = 10.sp) }
                            }
                        }
                        Text(if (mine) "YOU" else (otherDisplayName ?: "CONTACT"), color = ShellMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (mine) {
                            TextButton(onClick = { messageToDelete = message }, enabled = !loading) {
                                Text("DELETE", color = Color(0xFFFF9DAF), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { showAttachmentSheet = true },
                enabled = !loading && !conversationId.isNullOrBlank()
            ) { Icon(Icons.Default.Add, contentDescription = "Add attachment", tint = ShellCyan) }
            OutlinedTextField(
                value = draft,
                onValueChange = { value ->
                    if (value != draft) pendingSendRequestId = null
                    draft = value
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message…", color = ShellMuted) },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColorsForShell(),
                enabled = !loading && !conversationId.isNullOrBlank()
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val content = draft.trim()
                    if (content.isNotEmpty() && !conversationId.isNullOrBlank()) {
                        loading = true
                        scope.launch {
                            try {
                                val requestId = pendingSendRequestId ?: java.util.UUID.randomUUID().toString().also { pendingSendRequestId = it }
                                val sent = SupabaseDataRepository.sendMessage(conversationId, content, requestId)
                                messages = (messages + sent).distinctBy { it.id }.sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
                                draft = ""
                                pendingSendRequestId = null
                                status = ""
                            } catch (error: Exception) { status = error.message ?: "Message could not be sent." }
                            finally { loading = false }
                        }
                    }
                },
                enabled = !loading && draft.isNotBlank() && !conversationId.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
            ) { Text(if (loading) "…" else "SEND") }
        }
    }

    if (messageToDelete != null) {
        Dialog(onDismissRequest = { messageToDelete = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = ShellPanel) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DELETE MESSAGE?", color = Color(0xFFFF9DAF), fontWeight = FontWeight.Bold)
                    Text("This will soft-delete your message for the conversation. It cannot be restored from the app.", color = ShellMuted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { messageToDelete = null }) { Text("CANCEL", color = ShellMuted) }
                        TextButton(onClick = {
                            val target = messageToDelete ?: return@TextButton
                            messageToDelete = null
                            scope.launch {
                                loading = true
                                try {
                                    SupabaseDataRepository.softDeleteMessage(target)
                                    messages = messages.filterNot { it.id == target.id }
                                    status = ""
                                } catch (error: Exception) {
                                    status = error.message ?: "Could not delete this message."
                                } finally { loading = false }
                            }
                        }, enabled = !loading) { Text("DELETE", color = Color(0xFFFF9DAF), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ShellPanel
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SHARE WITH THIS CONVERSATION", color = ShellCyan, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAttachmentSheet = false; beginPhotoCapture() }) { Text("📷  Take photo", color = ShellText) }
                TextButton(onClick = { showAttachmentSheet = false; beginVideoCapture() }) { Text("🎥  Record video", color = ShellText) }
                TextButton(onClick = { showAttachmentSheet = false; showVoiceRecorder = true }) { Text("🎙  Voice recording", color = ShellText) }
                TextButton(onClick = { showAttachmentSheet = false; videoPicker.launch("video/*") }) { Text("▶  Select video", color = ShellText) }
                TextButton(onClick = { showAttachmentSheet = false; filePicker.launch("*/*") }) { Text("▤  Select file", color = ShellText) }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
    if (capturePreviewUri != null && capturePreviewMime != null) {
        Dialog(onDismissRequest = { capturePreviewUri = null; capturePreviewMime = null; capturePreviewName = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = ShellPanel) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (capturePreviewMime.orEmpty().startsWith("image/")) "PHOTO PREVIEW" else "VIDEO PREVIEW", color = ShellCyan, fontWeight = FontWeight.Bold)
                    if (capturePreviewMime.orEmpty().startsWith("image/")) {
                        AsyncImage(model = capturePreviewUri, contentDescription = "Captured photo preview", modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                    } else {
                        Text(capturePreviewName ?: "Captured video", color = ShellText)
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(capturePreviewUri, capturePreviewMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { status = it.message ?: "Could not preview video." } }) { Text("PLAY VIDEO", color = ShellCyan) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { capturePreviewUri = null; capturePreviewMime = null; capturePreviewName = null }, modifier = Modifier.weight(1f)) { Text("DISCARD", color = ShellMuted) }
                        Button(onClick = ::sendCapturedMedia, enabled = !loading, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)) { Text(if (loading) "SENDING…" else "SEND") }
                    }
                }
            }
        }
    }
    if (showVoiceRecorder) {
        VoiceAttachmentRecorder(
            onDismiss = { showVoiceRecorder = false },
            onRecorded = { uri ->
                showVoiceRecorder = false
                if (!conversationId.isNullOrBlank()) scope.launch {
                    loading = true
                    try {
                        val sent = SupabaseDataRepository.uploadChatAttachment(context, conversationId, uri, "audio/mp4", "voice-note.m4a")
                        messages = (messages + sent).distinctBy { it.id }.sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
                        status = ""
                    } catch (error: Exception) { status = error.message ?: "Voice note upload failed." }
                    finally { loading = false }
                }
            }
        )
    }

    if (showChatInfo) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showChatInfo = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = ShellPanel) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CHAT INFO", color = ShellCyan, fontWeight = FontWeight.Bold)
                    Text(otherDisplayName ?: "Private conversation", color = ShellText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(if (otherUserId.isNullOrBlank()) "Participant details are unavailable." else "Direct participant: ${otherDisplayName ?: "Contact"}", color = ShellMuted, fontSize = 12.sp)
                    Text("Conversation ID", color = ShellCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(conversationId.orEmpty(), color = ShellMuted, fontSize = 11.sp)
                    Text("Messages loaded: ${messages.size}", color = ShellMuted, fontSize = 11.sp)
                    Text("Attachments are opened using authenticated conversation membership and short-lived signed storage URLs.", color = ShellMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showChatInfo = false; showSharedMedia = true }) { Text("SHARED MEDIA", color = ShellCyan) }
                        TextButton(onClick = { showChatInfo = false }) { Text("CLOSE", color = ShellMuted) }
                    }
                }
            }
        }
    }
    if (showSharedMedia) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSharedMedia = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = ShellPanel) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SHARED MEDIA & FILES", color = ShellCyan, fontWeight = FontWeight.Bold)
                    val attachments = messages.filter { it.attachmentPath != null }.asReversed()
                    if (attachments.isEmpty()) Text("No shared attachments in the loaded conversation history.", color = ShellMuted, fontSize = 12.sp)
                    else LazyColumn(Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachments, key = { it.id }) { attachment ->
                            Column(Modifier.fillMaxWidth().background(Color(0xFF18243A), RoundedCornerShape(12.dp)).padding(10.dp)) {
                                Text(attachment.attachmentName ?: attachment.body, color = ShellText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(attachment.createdAt, color = ShellMuted, fontSize = 9.sp)
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            val signedUrl = SupabaseDataRepository.createSignedAttachmentUrl(attachment.attachmentPath!!)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signedUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        } catch (error: Exception) { status = error.message ?: "Could not open shared media." }
                                    }
                                }) { Text("OPEN SECURELY", color = ShellCyan, fontSize = 10.sp) }
                            }
                        }
                    }
                    TextButton(onClick = { showSharedMedia = false }, modifier = Modifier.align(Alignment.End)) { Text("CLOSE", color = ShellMuted) }
                }
            }
        }
    }
}

@Composable
private fun VoiceAttachmentRecorder(onDismiss: () -> Unit, onRecorded: (Uri) -> Unit) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<java.io.File?>(null) }
    var recordedUri by remember { mutableStateOf<Uri?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var playbackDuration by remember { mutableStateOf(0) }
    var playbackPosition by remember { mutableStateOf(0) }
    var dragDistance by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var amplitudeSamples by remember { mutableStateOf(listOf(0.08f)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecordingAfterPermission(context, onStarted = { startedRecorder, file ->
            recorder = startedRecorder
            recordingFile = file
            elapsedSeconds = 0
            amplitudeSamples = listOf(0.08f)
            recording = true
            error = null
        }, onFailure = { error = it }) else {
            error = "Microphone permission is required to record a voice note."
        }
    }

    fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = false
        playbackPosition = 0
    }

    fun finishRecording(keepFile: Boolean): Uri? {
        val file = recordingFile
        val success = runCatching { recorder?.stop() }.isSuccess
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        return if (keepFile && success && file?.exists() == true && file.length() > 0L) {
            Uri.fromFile(file)
        } else {
            runCatching { file?.delete() }
            recordingFile = null
            null
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startRecordingAfterPermission(context, onStarted = { startedRecorder, file ->
            recorder = startedRecorder
            recordingFile = file
            elapsedSeconds = 0
            amplitudeSamples = listOf(0.08f)
            recording = true
            error = null
        }, onFailure = { error = it })
    }

    fun preparePlayback(uri: Uri) {
        releasePlayer()
        runCatching {
            val created = checkNotNull(MediaPlayer.create(context, uri)) { "Could not prepare voice-note playback." }
            player = created
            playbackDuration = created.duration.coerceAtLeast(0)
            playbackPosition = 0
            created.setOnCompletionListener {
                playing = false
                playbackPosition = 0
                runCatching { it.seekTo(0) }
            }
            created.start()
            playing = true
        }.onFailure {
            releasePlayer()
            error = it.message ?: "Could not play the recorded voice note."
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            kotlinx.coroutines.delay(1_000)
            elapsedSeconds += 1
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            kotlinx.coroutines.delay(120)
            val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val normalized = (amplitude / 32767f).coerceIn(0.02f, 1f)
            amplitudeSamples = (amplitudeSamples + normalized).takeLast(32)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            kotlinx.coroutines.delay(100)
            playbackPosition = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            if (player?.isPlaying != true) playing = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            recorder = null
            releasePlayer()
            if (recording) runCatching { recordingFile?.delete() }
        }
    }

    Dialog(onDismissRequest = {
        if (recording) finishRecording(false)
        releasePlayer()
        recordedUri?.let { runCatching { java.io.File(it.path.orEmpty()).delete() } }
        onDismiss()
    }) {
        Surface(shape = RoundedCornerShape(22.dp), color = ShellPanel) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("VOICE NOTE", color = ShellCyan, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        recording -> "Recording in progress — waveform follows microphone input"
                        recordedUri != null -> "Preview your recording before sending"
                        else -> "Record a private voice note."
                    },
                    color = ShellText,
                    fontSize = 13.sp
                )
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    amplitudeSamples.forEach { sample ->
                        Box(
                            Modifier.weight(1f)
                                .height((5f + sample * 46f).dp)
                                .background(if (recording) ShellCyan else ShellMuted, RoundedCornerShape(4.dp))
                        )
                    }
                }
                Text(
                    String.format(java.util.Locale.ROOT, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                    color = if (recording) ShellCyan else ShellMuted,
                    fontWeight = FontWeight.Bold
                )
                if (recordedUri != null && !recording) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val activePlayer = player
                            if (activePlayer == null) {
                                preparePlayback(recordedUri!!)
                            } else if (playing) {
                                runCatching { activePlayer.pause() }
                                playing = false
                            } else {
                                runCatching {
                                    if (playbackPosition >= playbackDuration && playbackDuration > 0) activePlayer.seekTo(0)
                                    activePlayer.start()
                                    playing = true
                                }.onFailure { error = it.message ?: "Playback could not resume." }
                            }
                        }) { Text(if (playing) "PAUSE" else "PLAY", color = ShellCyan) }
                        Text(
                            String.format(
                                java.util.Locale.ROOT, "%02d:%02d",
                                (playbackPosition / 1000) / 60, (playbackPosition / 1000) % 60
                            ),
                            color = ShellMuted,
                            fontSize = 11.sp
                        )
                    }
                    Slider(
                        value = if (playbackDuration > 0) playbackPosition.toFloat() / playbackDuration else 0f,
                        onValueChange = { fraction ->
                            val position = (fraction * playbackDuration).toInt().coerceIn(0, playbackDuration)
                            playbackPosition = position
                            runCatching { player?.seekTo(position) }
                        },
                        enabled = playbackDuration > 0
                    )
                    Text("Drag the progress control to seek through the recording.", color = ShellMuted, fontSize = 10.sp)
                }
                if (recording) {
                    Text(
                        when {
                            dragDistance < -70f -> "Release to cancel"
                            dragDistance > 70f -> "Release to send"
                            else -> "Slide left to cancel • slide right to send"
                        },
                        color = if (kotlin.math.abs(dragDistance) > 70f) ShellCyan else ShellMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().pointerInput(recording) {
                            detectDragGestures(
                                onDragEnd = {
                                    when {
                                        dragDistance < -70f -> {
                                            finishRecording(false)
                                            dragDistance = 0f
                                            error = "Recording discarded."
                                        }
                                        dragDistance > 70f -> {
                                            val uri = finishRecording(true)
                                            dragDistance = 0f
                                            if (uri != null) {
                                                releasePlayer()
                                                recordingFile = null
                                                onRecorded(uri)
                                            } else error = "Recording was too short. Try again."
                                        }
                                        else -> dragDistance = 0f
                                    }
                                },
                                onDragCancel = { dragDistance = 0f },
                                onDrag = { change, amount -> change.consume(); dragDistance += amount.x }
                            )
                        }
                    )
                }
                error?.let { Text(it, color = Color(0xFFFF8297), fontSize = 11.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            if (recording) finishRecording(false)
                            releasePlayer()
                            recordedUri?.let { runCatching { java.io.File(it.path.orEmpty()).delete() } }
                            recordedUri = null
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("CANCEL", color = ShellMuted) }
                    Button(
                        onClick = {
                            when {
                                recording -> {
                                    val uri = finishRecording(true)
                                    if (uri != null) {
                                        recordedUri = uri
                                        preparePlayback(uri)
                                    } else error = "Recording was too short. Try again."
                                }
                                recordedUri != null -> {
                                    val uri = recordedUri!!
                                    releasePlayer()
                                    recordedUri = null
                                    recordingFile = null
                                    onRecorded(uri)
                                }
                                else -> startRecording()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFFB9415B) else ShellBlue)
                    ) {
                        Text(when {
                            recording -> "STOP & PREVIEW"
                            recordedUri != null -> "SEND VOICE NOTE"
                            else -> "RECORD"
                        })
                    }
                }
            }
        }
    }
}

private fun startRecordingAfterPermission(
    context: android.content.Context,
    onStarted: (MediaRecorder, java.io.File) -> Unit,
    onFailure: (String) -> Unit
) {
    runCatching {
        val file = java.io.File(context.cacheDir, "voice-${java.util.UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        onStarted(mediaRecorder, file)
    }.onFailure {
        android.util.Log.w("VoiceAttachmentRecorder", "Could not start voice recording", it)
        onFailure(it.message ?: "Could not start recording.")
    }
}

private fun android.content.ContentResolver.getDisplayName(uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getString(0) else null
    } catch (_: Exception) { null } finally { cursor?.close() }
}

@Composable
private fun AdminConsoleScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<AdminDashboardOverview?>(null) }
    var auditEvents by remember { mutableStateOf<List<AdminAuditEvent>>(emptyList()) }
    var status by remember { mutableStateOf("Checking server-side admin authorization…") }
    var loading by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        try {
            val verifiedOverview = SupabaseDataRepository.loadAdminDashboardOverview()
            val verifiedAudit = SupabaseDataRepository.loadAdminAuditLog()
            overview = verifiedOverview
            auditEvents = verifiedAudit
            status = ""
        } catch (error: Exception) {
            overview = null
            auditEvents = emptyList()
            status = error.message ?: "Admin service could not be reached."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        BackHeader("SECURE ADMIN ACCESS", onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Command access", color = ShellText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Server-verified, read-only operational overview.", color = ShellMuted, fontSize = 12.sp)
            }
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Text(if (loading) "CHECKING…" else "REFRESH", color = ShellCyan, fontSize = 10.sp)
            }
        }

        if (status.isNotBlank()) {
            val checking = status.startsWith("Checking")
            val notProvisioned = status.contains("403") || status.contains("not provisioned", ignoreCase = true)
            EmptyState(
                when {
                    checking -> "CHECKING ADMIN ACCESS"
                    notProvisioned -> "ADMIN ACCESS NOT PROVISIONED"
                    else -> "ADMIN SERVICE UNAVAILABLE"
                },
                when {
                    checking -> "Waiting for the authenticated backend to verify this account. No admin data is shown until the server confirms access."
                    notProvisioned -> "The backend denied admin access for this account. The project owner must explicitly provision the correct Supabase Auth user in admin_memberships. A password typed into the app cannot grant admin privileges.\n\n$status"
                    else -> "No live admin data is shown because server verification or the audit write failed. Retry after checking the backend.\n\n$status"
                }
            )
        }

        overview?.let { data ->
            Box(
                Modifier.fillMaxWidth().border(
                    1.dp,
                    Brush.linearGradient(listOf(Color(0x9955F4FF), Color(0x554F6FFF))),
                    RoundedCornerShape(20.dp)
                ).background(
                    Brush.verticalGradient(listOf(Color(0xFF14243B), Color(0xFF0C1323))),
                    RoundedCornerShape(20.dp)
                ).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("VERIFIED ADMIN SESSION", color = Color(0xFF65E5B0), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Role: ${data.role}", color = ShellText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Snapshot: ${data.generatedAt}", color = ShellMuted, fontSize = 10.sp)
                    Text("Read-only mode · privileged writes disabled", color = ShellMuted, fontSize = 11.sp)
                }
            }

            Text("LIVE BACKEND OVERVIEW", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
            listOf(
                "Registered profiles" to data.registeredProfiles,
                "Conversations" to data.conversations,
                "Messages" to data.messages,
                "Active calls" to data.activeCalls,
                "Users active in last 90 seconds" to data.activeUsersLast90Seconds
            ).forEach { (label, count) ->
                Row(
                    Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(14.dp))
                        .background(ShellPanel, RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = ShellMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(count.toString(), color = ShellCyan, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Text("PRIVILEGED OPERATIONS", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        EmptyState(
            "MODERATION AND ACCOUNT WRITES ARE DISABLED",
            "No ban, suspension, message inspection, or destructive operation is exposed. These controls remain unavailable until the owner approves the admin scope, authorization rules, audit fields, and retention policy."
        )

        Text("ADMIN AUDIT HISTORY", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        if (auditEvents.isEmpty()) {
            EmptyState("NO VERIFIED AUDIT EVENTS", "Audit entries appear here only after the backend authorizes this account and records the access event.")
        } else {
            auditEvents.take(20).forEach { event ->
                Column(
                    Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(14.dp))
                        .background(ShellPanel, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(event.action, color = ShellCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Actor: ${event.actorId}", color = ShellMuted, fontSize = 10.sp)
                    Text("Time: ${event.createdAt}", color = ShellMuted, fontSize = 10.sp)
                    if (event.targetType != null || event.targetId != null) {
                        Text("Target: ${event.targetType.orEmpty()} ${event.targetId.orEmpty()}", color = ShellMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹ BACK", color = ShellCyan, modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp), fontSize = 11.sp)
        Text(title, color = ShellMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(18.dp)).background(ShellPanel, RoundedCornerShape(18.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = ShellCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(body, color = ShellMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = ShellMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColorsForShell()
        )
    }
}

@Composable
private fun fieldColorsForShell() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ShellText,
    unfocusedTextColor = ShellText,
    focusedContainerColor = ShellPanel,
    unfocusedContainerColor = ShellPanel,
    focusedBorderColor = ShellCyan,
    unfocusedBorderColor = ShellLine,
    cursorColor = ShellCyan
)
