package com.aicallshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicallshield.ui.theme.*

// ── Data class for a conversation item ───────────────────────────
data class ConversationItem(
    val id: String,
    val callerName: String,
    val summary: String,
    val time: String,
    val isUnread: Boolean = false
)

/**
 * Home screen — Equal AI inspired design.
 *
 * Layout:
 *  1. Top bar with app logo + settings icon
 *  2. Welcome greeting
 *  3. Setup status card (if default dialer not set)
 *  4. AI Assistant status toggle card
 *  5. "Talk to your assistant / Call Now" hero card
 *  6. Recent conversations list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String = "",
    isDefaultDialer: Boolean = false,
    onRequestDialerRole: () -> Unit = {},
    onStartDemo: (String) -> Unit,
    onViewHistory: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // AI toggle state
    var isAIEnabled by remember { mutableStateOf(true) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var selectedPauseDuration by remember { mutableStateOf<String?>(null) }

    // Sample conversations (later wired to ViewModel)
    val conversations = remember {
        listOf(
            ConversationItem(
                id = "1",
                callerName = "Unknown Caller",
                summary = "Quotation for insurance plan",
                time = "3:20 pm",
                isUnread = true
            ),
            ConversationItem(
                id = "2",
                callerName = "HDFC Bank",
                summary = "Service call from HDFC Bank",
                time = "11:53 am",
                isUnread = true
            ),
            ConversationItem(
                id = "3",
                callerName = "Spam Caller",
                summary = "Blocked — high risk spam detected",
                time = "Yesterday",
                isUnread = true
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── 1. Top Bar ───────────────────────────────────────────
        item {
            TopBarSection(onOpenSettings = onOpenSettings)
        }

        // ── 2. Welcome Greeting ──────────────────────────────────
        item {
            WelcomeSection(displayName = userName.ifBlank { "User" })
        }

        // ── 2.5. Setup Status Card ───────────────────────────────
        if (!isDefaultDialer) {
            item {
                SetupStatusCard(
                    isDefaultDialer = isDefaultDialer,
                    onRequestDialerRole = onRequestDialerRole
                )
            }
        }

        // ── 3. AI Assistant Toggle Card ──────────────────────────
        item {
            AIAssistantToggleCard(
                isEnabled = isAIEnabled,
                onToggle = { enabled ->
                    if (!enabled) {
                        // User wants to turn OFF → show pause duration dialog
                        showPauseDialog = true
                    } else {
                        // User turns ON → enable immediately
                        isAIEnabled = true
                        selectedPauseDuration = null
                    }
                }
            )
        }

        // ── Pause duration dialog ────────────────────────────────
        if (showPauseDialog) {
            item {
                PauseDurationDialog(
                    onDismiss = { showPauseDialog = false },
                    onConfirm = { duration ->
                        selectedPauseDuration = duration
                        isAIEnabled = false
                        showPauseDialog = false
                    }
                )
            }
        }

        // ── 4. Talk to Assistant / Call Now Card ─────────────────
        item {
            TalkToAssistantCard(
                onCallNow = { onStartDemo("+1 (555) 123-4567") }
            )
        }

        // ── 5. Conversations Header ─────────────────────────────
        item {
            Text(
                text = "CONVERSATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Gray600,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        // ── 6. Conversations List ────────────────────────────────
        items(conversations) { conversation ->
            ConversationRow(
                conversation = conversation,
                onClick = { onStartDemo(conversation.callerName) }
            )
        }

        // ── 7. View All History Link ─────────────────────────────
        item {
            TextButton(
                onClick = onViewHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "View All Call History →",
                    color = Green600,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// Sub-components
// ═════════════════════════════════════════════════════════════════

/**
 * Setup status card shown when the app hasn't been set as the default dialer.
 * This is required for AI call screening to actually work.
 */
@Composable
private fun SetupStatusCard(
    isDefaultDialer: Boolean,
    onRequestDialerRole: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0) // Light orange / warning
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⚙️", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Complete Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "To screen and answer calls automatically, AICallShield needs to be set as your default phone app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray800,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Checklist items
            SetupCheckItem(
                label = "Default dialer role",
                isComplete = isDefaultDialer,
                description = if (isDefaultDialer) "Active" else "Required to answer calls"
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!isDefaultDialer) {
                Button(
                    onClick = onRequestDialerRole,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange500
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set as Default Dialer",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCheckItem(
    label: String,
    isComplete: Boolean,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isComplete) Green500 else Gray200),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isComplete) Green700 else Gray800
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isComplete) Green600 else Orange500
            )
        }
    }
}

@Composable
private fun TopBarSection(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App logo area
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Shield icon box
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Green600),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "AICallShield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Green700
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Beta badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Green600,
                modifier = Modifier.height(22.dp)
            ) {
                Text(
                    text = "beta",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        // Settings icon
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = Gray800,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun WelcomeSection(displayName: String = "User") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User avatar placeholder
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Green100),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Green700,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Welcome ",
                style = MaterialTheme.typography.titleMedium,
                color = Gray800
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Gray900
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "👋", fontSize = 20.sp)
        }
    }
}

@Composable
private fun AIAssistantToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MintLight else Gray100
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(text = "📱✨", fontSize = 28.sp)

            Spacer(modifier = Modifier.width(12.dp))

            // Texts
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) "AI Assistant is On" else "AI Assistant is Off",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                )
                Text(
                    text = if (isEnabled)
                        "AICallShield is managing your calls!"
                    else
                        "Turn on to screen your calls with AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray600
                )
            }

            // Toggle switch
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Green500,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Gray400
                )
            )
        }
    }
}

@Composable
private fun TalkToAssistantCard(onCallNow: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar / illustration placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Green100),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🤖", fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Talk to your assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onCallNow,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green600
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Call Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sparkle icon in yellow circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ConversationIconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✨", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Caller info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.callerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Gray900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = conversation.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray600
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(UnreadDot)
                        )
                    }
                }
            }
        }
    }

    // Divider
    Divider(
        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = Gray200
    )
}

// ═════════════════════════════════════════════════════════════════
// Pause Duration Dialog
// ═════════════════════════════════════════════════════════════════

@Composable
private fun PauseDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val options = listOf("15 mins", "1 hr", "6 hrs", "For the rest of today", "Until I turn it on")
    var selectedOption by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "Want to take some calls yourself?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Gray900
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose how long your assistant stays off:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Green600,
                                unselectedColor = Gray400
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Gray900
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedOption?.let { onConfirm(it) }
                },
                enabled = selectedOption != null
            ) {
                Text(
                    text = "OK",
                    color = if (selectedOption != null) Green600 else Gray400,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Gray900,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
