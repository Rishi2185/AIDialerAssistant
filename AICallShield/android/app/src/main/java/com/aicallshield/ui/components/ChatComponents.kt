package com.aicallshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicallshield.data.model.ChatMessage
import com.aicallshield.data.model.RiskLevel
import com.aicallshield.data.model.SenderType
import com.aicallshield.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-style chat bubble for displaying conversation messages.
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val isCallerMsg = message.sender == SenderType.CALLER
    val isAIMsg = message.sender == SenderType.AI
    val isUserMsg = message.sender == SenderType.USER
    val isSystemMsg = message.sender == SenderType.SYSTEM

    // Alignment & colors
    val alignment = when {
        isCallerMsg -> Alignment.Start
        isAIMsg -> Alignment.CenterHorizontally
        isUserMsg -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    val bubbleColor = when {
        isCallerMsg -> CallerBubble
        isAIMsg -> AIBubble
        isUserMsg -> UserBubble
        else -> SystemBubble
    }

    val bubbleShape = when {
        isCallerMsg -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
        isUserMsg -> RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else -> RoundedCornerShape(16.dp)
    }

    val senderLabel = when (message.sender) {
        SenderType.CALLER -> "📞 Caller"
        SenderType.AI -> "🤖 AI Shield"
        SenderType.USER -> "👤 You"
        SenderType.SYSTEM -> "ℹ️ System"
    }

    // System messages are centered and styled differently
    if (isSystemMsg) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SystemBubble)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = alignment,
    ) {
        // Sender label
        Text(
            text = senderLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
        )

        // Bubble
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray900
                )

                // Timestamp
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray600,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }

        // Alert badge if present
        if (message.isAlert && message.alertMessage != null) {
            SpamAlertBadge(
                alertMessage = message.alertMessage,
                spamScore = message.spamScore ?: 0f
            )
        }
    }
}

/**
 * Spam/scam alert badge shown beneath suspicious messages.
 */
@Composable
fun SpamAlertBadge(
    alertMessage: String,
    spamScore: Float,
    modifier: Modifier = Modifier
) {
    val alertColor = when {
        spamScore >= 0.8f -> SpamCritical
        spamScore >= 0.6f -> SpamHigh
        spamScore >= 0.35f -> SpamMedium
        else -> SpamLow
    }

    Card(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = alertColor.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = alertMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = alertColor
            )
        }
    }
}

/**
 * Spam score indicator bar.
 */
@Composable
fun SpamScoreIndicator(
    score: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        score >= 0.8f -> SpamCritical
        score >= 0.6f -> SpamHigh
        score >= 0.35f -> SpamMedium
        else -> SpamLow
    }

    val label = when {
        score >= 0.8f -> "🔴 CRITICAL"
        score >= 0.6f -> "🟠 HIGH RISK"
        score >= 0.35f -> "🟡 MEDIUM"
        else -> "🟢 LOW"
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scam Probability",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(score * 100).toInt()}% $label",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = score,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
    }
}

/**
 * Risk level chip display.
 */
@Composable
fun RiskLevelChip(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (riskLevel) {
        RiskLevel.LOW -> SpamLow to "Low Risk"
        RiskLevel.MEDIUM -> SpamMedium to "Medium Risk"
        RiskLevel.HIGH -> SpamHigh to "High Risk"
        RiskLevel.CRITICAL -> SpamCritical to "⚠ Critical"
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = color.copy(alpha = 0.3f),
        )
    )
}

// ── Utility ──────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
