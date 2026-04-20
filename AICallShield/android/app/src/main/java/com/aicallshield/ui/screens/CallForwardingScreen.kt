package com.aicallshield.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aicallshield.ui.theme.Gray600
import com.aicallshield.ui.theme.Gray900
import com.aicallshield.ui.theme.Green600
import com.aicallshield.util.Constants

/**
 * On-device helper screen for carrier call forwarding setup.
 *
 * If CALL_PHONE permission is granted, forwarding codes are executed directly.
 * If permission is missing/blocked by device policy, the app falls back to dialer.
 */
@Composable
fun CallForwardingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var twilioNumber by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        twilioNumber = prefs.getString(Constants.PREF_FORWARDING_TWILIO_NUMBER, "") ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
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
                    tint = Gray900,
                )
            }
            Text(
                text = "Call Forwarding",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Gray900,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneForwarded,
                    contentDescription = null,
                    tint = Green600,
                )
                Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                Text(
                    text = "Enable assistant on Sankalp's normal number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Enter your Twilio number once, then use one-tap buttons below. After permission is granted, forwarding is triggered automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray600,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = twilioNumber,
                onValueChange = { twilioNumber = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Twilio Number") },
                placeholder = { Text("+15017122661") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val normalized = normalizePhoneNumber(twilioNumber)
                    if (normalized.isBlank()) {
                        Toast.makeText(context, "Enter a valid Twilio number", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    twilioNumber = normalized
                    prefs.edit().putString(Constants.PREF_FORWARDING_TWILIO_NUMBER, normalized).apply()
                    Toast.makeText(context, "Twilio number saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Twilio Number")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val code = buildForwardingCode(twilioNumber, "21")
                    if (code == null) {
                        Toast.makeText(context, "Save valid Twilio number first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    executeForwardingCode(context, code)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enable Assistant Automatically")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    val code = buildForwardingCode(twilioNumber, "61")
                    if (code == null) {
                        Toast.makeText(context, "Save valid Twilio number first", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    executeForwardingCode(context, code)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enable No-Answer Automatically")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { executeForwardingCode(context, "##21#") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disable Forwarding")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { executeForwardingCode(context, "*#21#") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check Forwarding Status")
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Note: on some devices/carriers, Android may still open dialer for confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Workflow: Ishan calls Sankalp -> carrier forwards to Twilio -> assistant attends.",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Quick commands: **21*number#, **61*number#, ##21#, *#21#",
                style = MaterialTheme.typography.bodySmall,
                color = Gray600,
            )
        }
    }
}

private fun normalizePhoneNumber(input: String): String {
    val compact = input.trim().replace(" ", "")
    val filtered = buildString {
        compact.forEachIndexed { index, c ->
            if (c.isDigit() || (c == '+' && index == 0)) {
                append(c)
            }
        }
    }
    return filtered
}

private fun buildForwardingCode(numberRaw: String, serviceCode: String): String? {
    val number = normalizePhoneNumber(numberRaw)
    if (number.isBlank()) {
        return null
    }
    return "**$serviceCode*$number#"
}

private fun executeForwardingCode(context: Context, code: String) {
    val encoded = Uri.parse("tel:${Uri.encode(code)}")
    val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE,
    ) == PackageManager.PERMISSION_GRANTED

    if (hasCallPermission) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = encoded
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(callIntent)
            return
        } catch (_: Exception) {
            // Fallback to dialer when device policy/carrier blocks direct MMI execution.
        }
    }

    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        data = encoded
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(dialIntent)
}
