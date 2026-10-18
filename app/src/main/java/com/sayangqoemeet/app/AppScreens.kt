package com.sayangqoemeet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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

internal enum class AppDestination { HOME, PROFILE, CHATS, CHAT, CALL, ADMIN, USERS }

@Composable
internal fun SignedInShell(onSignOut: () -> Unit) {
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

    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var selectedConversation by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C1530), ShellBg)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE60B1225))
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
                    DropdownMenuItem(
                        text = { Text("About", color = ShellText) },
                        onClick = { showOverflow = false; showAbout = true }
                    )
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
                AppDestination.CHATS -> ChatListScreen(
                    onBack = { destination = AppDestination.HOME },
                    onOpenConversation = { id -> selectedConversation = id; destination = AppDestination.CHAT }
                )
                AppDestination.CHAT -> ConversationScreen(
                    conversationId = selectedConversation,
                    onBack = { destination = AppDestination.CHATS },
                    onCall = { destination = AppDestination.CALL }
                )
                AppDestination.CALL -> CallCenterScreen(onBack = { destination = AppDestination.HOME })
                AppDestination.ADMIN -> AdminConsoleScreen(onBack = { destination = AppDestination.HOME })
                AppDestination.USERS -> RegisteredUsersScreen(
                    onBack = { destination = AppDestination.HOME },
                    onOpenConversation = { id -> selectedConversation = id; destination = AppDestination.CHAT }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xF20A1020)).padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavAction("HOME", destination == AppDestination.HOME) { destination = AppDestination.HOME }
            NavAction("PROFILE", destination == AppDestination.PROFILE) { destination = AppDestination.PROFILE }
            NavAction("CHATS", destination == AppDestination.CHATS) { destination = AppDestination.CHATS }
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

        Box(
            Modifier.size(84.dp).align(Alignment.CenterHorizontally)
                .border(1.dp, ShellCyan, CircleShape)
                .background(ShellPanel, CircleShape)
                .clickable { status = "Avatar upload is not implemented yet — paste an image URL in the field below for now." },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (avatarUrl.isBlank()) "ADD\nPHOTO" else "CHANGE\nPHOTO",
                color = ShellCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Field("DISPLAY NAME", displayName, { displayName = it }, "How should people see you?")
        Field("USERNAME", username, { username = it }, "3–24 letters, numbers, _, ., -")
        Field("AVATAR URL", avatarUrl, { avatarUrl = it }, "Paste an image URL (upload not implemented yet)")
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
private fun RegisteredUsersScreen(onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<RegisteredUserRecord>>(emptyList()) }
    var status by remember { mutableStateOf("Loading registered users…") }
    var loadingUserId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

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
        while (true) {
            kotlinx.coroutines.delay(15_000)
            if (loadingUserId == null) {
                val refreshed = runCatching {
                    SupabaseDataRepository.loadRegisteredUsers()
                }.getOrNull()
                // Avoid invalidating the whole list when presence/data did not change.
                if (refreshed != null && refreshed != users) users = refreshed
            }
        }
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
        Text("Green = active within the last 90 seconds. Presence updates while the app is open.", color = ShellMuted, fontSize = 11.sp)
        if (status.isNotBlank()) Text(status, color = ShellMuted, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(users, key = { it.id }) { user ->
                Row(
                    Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(16.dp))
                        .background(ShellPanel, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).background(if (user.isOnline) Color(0xFF43E08A) else ShellMuted, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName.ifBlank { user.username ?: "SayangqoeMeet user" }, color = ShellText, fontWeight = FontWeight.SemiBold)
                        Text(user.username?.let { "@$it" } ?: "Username not set", color = ShellMuted, fontSize = 11.sp)
                        Text("Registered: ${formatRegistrationDate(user.createdAt)}", color = ShellMuted, fontSize = 10.sp)
                        Text(if (user.isOnline) "Active now" else "Offline", color = if (user.isOnline) Color(0xFF43E08A) else ShellMuted, fontSize = 10.sp)
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
private fun ConversationScreen(conversationId: String?, onBack: () -> Unit, onCall: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<MessageRecord>>(emptyList()) }
    var status by remember { mutableStateOf("Loading message history…") }
    var loading by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId) {
        currentUserId = SupabaseAuthRepository.currentUserId()
        if (conversationId.isNullOrBlank()) {
            status = "Select a real conversation first."
        } else {
            try {
                messages = SupabaseDataRepository.loadMessages(conversationId)
                status = if (messages.isEmpty()) "No messages yet. Say hello." else ""
            } catch (error: Exception) {
                status = error.message ?: "Could not load messages."
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ BACK", color = ShellCyan, modifier = Modifier.clickable(onClick = onBack).padding(8.dp), fontSize = 11.sp)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("PRIVATE CHAT", color = ShellText, fontWeight = FontWeight.Bold)
                Text(if (conversationId.isNullOrBlank()) "No conversation selected" else "Conversation: ${conversationId.take(8)}…", color = ShellMuted, fontSize = 10.sp)
            }
            TextButton(onClick = onCall) { Text("CALL", color = ShellCyan) }
        }
        if (status.isNotBlank()) Text(status, color = ShellMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == currentUserId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 260.dp)
                            .background(if (mine) Color(0xFF173B70) else ShellPanel, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(message.body, color = ShellText, fontSize = 14.sp)
                        Text(if (mine) "YOU" else "MEMBER", color = ShellMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
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
                                val sent = SupabaseDataRepository.sendMessage(conversationId, content)
                                messages = messages + sent
                                draft = ""
                                status = ""
                            } catch (error: Exception) {
                                status = error.message ?: "Message could not be sent."
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                enabled = !loading && draft.isNotBlank() && !conversationId.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
            ) { Text(if (loading) "…" else "SEND") }
        }
    }
}

@Composable
private fun AdminConsoleScreen(onBack: () -> Unit) {
    var adminPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        BackHeader("SECURE ADMIN ACCESS", onBack)
        Box(
            Modifier.fillMaxWidth().border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x9955F4FF), Color(0x554F6FFF), Color(0x44B58AFF))),
                RoundedCornerShape(24.dp)
            ).background(
                Brush.verticalGradient(listOf(Color(0xFF14243B), Color(0xFF0C1323))),
                RoundedCornerShape(24.dp)
            ).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(
                    Modifier.size(54.dp).border(1.dp, Color(0x6655F4FF), RoundedCornerShape(17.dp))
                        .background(Color(0x222EDFE4), RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SQ", color = ShellCyan, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Command access", color = ShellText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Administrator-only workspace for trust, safety, and session operations.",
                    color = ShellMuted, fontSize = 13.sp, lineHeight = 19.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).background(Color(0xFFFFCE79), CircleShape))
                    Text("SECURE VERIFICATION NOT CONNECTED", color = Color(0xFFFFCE79), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text("ADMIN PASSWORD", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = { adminPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter admin password", color = ShellMuted) },
                    singleLine = true,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "HIDE" else "SHOW", color = ShellCyan, fontSize = 9.sp)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColorsForShell()
                )
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShellCyan,
                        disabledContainerColor = Color(0xFF25354A),
                        disabledContentColor = ShellMuted
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("VERIFY ADMIN IDENTITY", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Text(
                    "Verification will be performed by the secure backend. This screen does not compare or store the password locally.",
                    color = ShellMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }
        }
        Text("CONSOLE MODULES", color = ShellMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        ActionCard("SESSION OPERATIONS", "Presence, grouped chat sessions, video calls, and voice calls", "BACKEND CONNECTION PENDING", {})
        ActionCard("TRUST & SAFETY", "Case review, evidence references, and documented moderation", "BACKEND CONNECTION PENDING", {})
        ActionCard("AUDIT & SECURITY", "Admin access history, role checks, and system status", "BACKEND CONNECTION PENDING", {})
        EmptyState(
            "ADMIN WORKSPACE LOCKED",
            "Live user data and privileged actions remain unavailable until server-verified admin authorization and audit logging are implemented."
        )
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
