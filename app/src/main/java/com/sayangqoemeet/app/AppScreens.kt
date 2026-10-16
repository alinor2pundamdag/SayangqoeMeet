package com.sayangqoemeet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val ShellBg = Color(0xFF070B18)
private val ShellPanel = Color(0xFF10182B)
private val ShellCyan = Color(0xFF55F4FF)
private val ShellBlue = Color(0xFF5D8CFF)
private val ShellText = Color(0xFFF0F5FF)
private val ShellMuted = Color(0xFF9BAAC5)
private val ShellLine = Color(0xFF2A3856)

internal enum class AppDestination { HOME, PROFILE, CHATS, CHAT, CALL, ADMIN }

@Composable
internal fun SignedInShell(onSignOut: () -> Unit) {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var selectedConversation by remember { mutableStateOf<String?>(null) }

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
            TextButton(onClick = onSignOut) { Text("SIGN OUT", color = ShellMuted, fontSize = 11.sp) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    onProfile = { destination = AppDestination.PROFILE },
                    onChats = { destination = AppDestination.CHATS },
                    onCall = { destination = AppDestination.CALL },
                    onAdmin = { destination = AppDestination.ADMIN }
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
                AppDestination.CALL -> CallScreen(onBack = { destination = AppDestination.HOME })
                AppDestination.ADMIN -> AdminConsoleScreen(onBack = { destination = AppDestination.HOME })
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
private fun HomeScreen(onProfile: () -> Unit, onChats: () -> Unit, onCall: () -> Unit, onAdmin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("YOUR SPACE. YOUR PEOPLE.", color = ShellMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("Stay close.\nStay connected.", color = ShellText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
        Text("Your private communication space.", color = ShellMuted, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        ActionCard("MESSAGES", "Open your conversations and message history", "OPEN CHATS", onChats)
        ActionCard("YOUR PROFILE", "Set up the name people recognize", "EDIT PROFILE", onProfile)
        ActionCard("VOICE / VIDEO", "Call controls and connection status", "OPEN CALLS", onCall)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAdmin, modifier = Modifier.align(Alignment.End)) {
            Text("ADMIN CONSOLE  ›", color = ShellMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
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

@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Loading your profile…") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val profile = SupabaseDataRepository.loadProfile()
            if (profile != null) {
                displayName = profile.displayName
                username = profile.username.orEmpty()
                status = "Profile loaded."
            } else {
                status = "No profile yet. Create one below."
            }
        } catch (error: Exception) {
            status = error.message ?: "Could not load profile."
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackHeader("YOUR PROFILE", onBack)
        Text("Make it yours.", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Your profile is linked to your signed-in account.", color = ShellMuted, fontSize = 13.sp)
        Field("DISPLAY NAME", displayName, { displayName = it }, "How should people see you?")
        Field("USERNAME", username, { username = it }, "3–24 letters, numbers, _, ., -")
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
                            SupabaseDataRepository.saveProfile(cleanName, cleanUsername)
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
    }
}

@Composable
private fun ChatListScreen(onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<ConversationRecord>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<InvitationRecord>>(emptyList()) }
    var recipientUsername by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Loading conversations…") }
    var loading by remember { mutableStateOf(false) }

    suspend fun refresh() {
        try {
            conversations = SupabaseDataRepository.loadConversations()
            invitations = SupabaseDataRepository.loadPendingInvitations()
            status = if (conversations.isEmpty() && invitations.isEmpty()) "No conversations or pending invitations." else ""
        } catch (error: Exception) {
            status = error.message ?: "Could not load conversations and invitations."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("MESSAGES", onBack)
        Text("Your conversations", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Invite someone by username. They must accept before they become a member of the private chat.", color = ShellMuted, fontSize = 13.sp)
        Field("RECIPIENT USERNAME", recipientUsername, { recipientUsername = it }, "Enter exact username")
        Button(
            onClick = {
                if (recipientUsername.trim().length < 3) {
                    status = "Enter a valid username (at least 3 characters)."
                } else {
                    loading = true
                    scope.launch {
                        try {
                            SupabaseDataRepository.createConversation(recipientUsername)
                            recipientUsername = ""
                            status = "Invitation sent. Waiting for the recipient to accept."
                            refresh()
                        } catch (error: Exception) {
                            status = error.message ?: "Could not send invitation."
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
        ) { Text(if (loading) "SENDING…" else "SEND CHAT INVITATION", fontWeight = FontWeight.Bold) }

        if (invitations.isNotEmpty()) {
            Text("PENDING INVITATIONS", color = ShellCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            invitations.forEach { invite ->
                Column(
                    Modifier.fillMaxWidth().border(1.dp, Color(0xFF6C5B8D), RoundedCornerShape(16.dp))
                        .background(ShellPanel, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Invitation from member ${invite.inviterId.take(8)}…", color = ShellText, fontWeight = FontWeight.SemiBold)
                    Text("Accept to join this private conversation, or decline to leave it inaccessible.", color = ShellMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                loading = true
                                scope.launch {
                                    try {
                                        SupabaseDataRepository.respondToInvitation(invite.id, true)
                                        status = "Invitation accepted."
                                        refresh()
                                    } catch (error: Exception) {
                                        status = error.message ?: "Could not accept invitation."
                                    } finally { loading = false }
                                }
                            },
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
                        ) { Text("ACCEPT") }
                        Button(
                            onClick = {
                                loading = true
                                scope.launch {
                                    try {
                                        SupabaseDataRepository.respondToInvitation(invite.id, false)
                                        status = "Invitation declined."
                                        refresh()
                                    } catch (error: Exception) {
                                        status = error.message ?: "Could not decline invitation."
                                    } finally { loading = false }
                                }
                            },
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2539))
                        ) { Text("DECLINE", color = ShellText) }
                    }
                }
            }
        }

        if (status.isNotBlank()) Text(status, color = ShellMuted, fontSize = 12.sp)
        if (conversations.isEmpty()) {
            EmptyState("NO ACCEPTED CONVERSATIONS", "Accepted private chats will appear here. Pending invitations do not grant message access.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    Column(
                        Modifier.fillMaxWidth().border(1.dp, ShellLine, RoundedCornerShape(16.dp))
                            .background(ShellPanel, RoundedCornerShape(16.dp))
                            .clickable { onOpenConversation(conversation.id) }
                            .padding(16.dp)
                    ) {
                        Text(conversation.title?.takeIf { it.isNotBlank() } ?: if (conversation.isGroup) "Group conversation" else "Private conversation",
                            color = ShellText, fontWeight = FontWeight.SemiBold)
                        Text("Tap to open messages", color = ShellMuted, fontSize = 11.sp)
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
private fun CallScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(112.dp).background(Color(0x223F8CFF), CircleShape).border(1.dp, ShellCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("CALL", color = ShellCyan, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
        Spacer(Modifier.height(24.dp))
        Text("Voice & video", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Call state: idle", color = ShellMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text("The call control layout is ready, but WebRTC signaling/media are not connected yet. No call is being placed.", color = ShellMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB83250)),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("HANG UP / CANCEL", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun AdminConsoleScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("ADMIN CONSOLE", onBack)
        Text("Operations overview", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Admin console is now included in the master plan. This mobile entry is a locked navigation placeholder, not an authorized admin dashboard.", color = ShellMuted, fontSize = 13.sp)
        ActionCard("USER MANAGEMENT", "Account lookup, status, and moderation", "SERVER AUTHORIZATION REQUIRED", {})
        ActionCard("SAFETY & REPORTS", "Review reports and apply audited actions", "SERVER AUTHORIZATION REQUIRED", {})
        ActionCard("SYSTEM HEALTH", "Integration status and operational events", "SERVER AUTHORIZATION REQUIRED", {})
        EmptyState("ADMIN ACCESS LOCKED", "Do not expose privileged operations until a server-verified admin role and least-privilege backend endpoints are implemented.")
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
