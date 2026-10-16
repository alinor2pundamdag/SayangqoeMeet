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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Profile form ready. Saving is not connected until profile repository integration is completed.") }

    Column(
        Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackHeader("YOUR PROFILE", onBack)
        Text("Make it yours.", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("This profile belongs to your signed-in SayangqoeMeet account.", color = ShellMuted, fontSize = 13.sp)
        Field("DISPLAY NAME", displayName, { displayName = it }, "How should people see you?")
        Field("USERNAME", username, { username = it }, "3–24 characters")
        Text(status, color = ShellMuted, fontSize = 12.sp)
        Button(
            onClick = { status = "Not saved: the profile write is intentionally disabled until authenticated database calls are wired and tested." },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
        ) { Text("SAVE PROFILE", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ChatListScreen(onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("MESSAGES", onBack)
        Text("Your conversations", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Private chats appear here after secure conversation queries are connected.", color = ShellMuted, fontSize = 13.sp)
        EmptyState("NO CONVERSATIONS LOADED", "This screen will show real conversations from Supabase. No demo chats are being shown as real.")
        Button(
            onClick = { onOpenConversation("") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShellPanel)
        ) { Text("OPEN CHAT VIEW (INTEGRATION PENDING)", color = ShellCyan, fontSize = 10.sp) }
    }
}

@Composable
private fun ConversationScreen(conversationId: String?, onBack: () -> Unit, onCall: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ BACK", color = ShellCyan, modifier = Modifier.clickable(onClick = onBack).padding(8.dp), fontSize = 11.sp)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("PRIVATE CHAT", color = ShellText, fontWeight = FontWeight.Bold)
                Text(if (conversationId.isNullOrBlank()) "No conversation selected" else "Conversation: ${conversationId.take(8)}…", color = ShellMuted, fontSize = 10.sp)
            }
            TextButton(onClick = onCall) { Text("CALL", color = ShellCyan) }
        }
        Spacer(Modifier.height(12.dp))
        EmptyState("MESSAGE HISTORY NOT LOADED", "Message history and sending will use the authenticated conversation membership. This view does not fabricate messages.")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message…", color = ShellMuted) },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColorsForShell()
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { draft = "" },
                enabled = draft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ShellBlue)
            ) { Text("SEND") }
        }
        Text("Sending is disabled until the authenticated message repository is connected.", color = ShellMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
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
        Text("No call is active.", color = ShellMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text("WebRTC signaling and media are not connected yet. This screen will not pretend to place a call.", color = ShellMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = ShellPanel)) {
            Text("END / BACK", color = ShellCyan)
        }
    }
}

@Composable
private fun AdminConsoleScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("ADMIN CONSOLE", onBack)
        Text("Operations overview", color = ShellText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Admin console was not defined in the current master plan. This entry is a safe access placeholder, not an authorized admin dashboard.", color = ShellMuted, fontSize = 13.sp)
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
