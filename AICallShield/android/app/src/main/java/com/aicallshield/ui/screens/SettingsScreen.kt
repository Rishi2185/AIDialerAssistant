package com.aicallshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicallshield.ui.theme.*

/**
 * Settings screen — Equal AI inspired clean design.
 *
 * Layout:
 *  1. Back arrow + "Settings" title
 *  2. User profile card (avatar, name, phone)
 *  3. Personal Details row
 *  4. Assistant Voice row
 *  5. 24/7 Priority Support card
 *  6. Version info at bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String = "",
    onBack: () -> Unit = {},
    onOpenPersonalDetails: () -> Unit = {},
    onOpenAssistantVoice: () -> Unit = {},
    onOpenCallForwarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 1. Top Bar ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Gray900
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Gray900
            )
        }

        // ── 2. User Profile Card ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MintSurface)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Green600),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = userName.ifBlank { "User" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Gray900
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (userName.isBlank()) "Setup your profile" else "Manage your profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 3. Personal Details ──────────────────────────────────
        SettingsRow(
            icon = Icons.Default.Person,
            title = "Personal Details",
            subtitle = "Name & Gender",
            onClick = onOpenPersonalDetails
        )

        // ── 4. Assistant Voice ───────────────────────────────────
        SettingsRow(
            icon = Icons.Default.RecordVoiceOver,
            title = "Assistant Voice",
            subtitle = "Select Assistant Voice",
            onClick = onOpenAssistantVoice
        )

        SettingsRow(
            icon = Icons.Default.Phone,
            title = "Call Forwarding Assistant",
            subtitle = "Set up Twilio forwarding",
            onClick = onOpenCallForwarding
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 5. 24/7 Priority Support Card ────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Gray50),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "24/7 Priority Support",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Gray900
                    )
                    Text(
                        text = "You can call us for any help.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray600
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MintLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call Support",
                        tint = Green600,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── 6. Version Info ──────────────────────────────────────
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "v2026.02.14",
            style = MaterialTheme.typography.bodySmall,
            color = Gray400,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp, top = 24.dp)
        )
    }
}

// ═════════════════════════════════════════════════════════════════
// Sub-components
// ═════════════════════════════════════════════════════════════════

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gray800,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Gray900
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray600
            )
        }
    }
}


