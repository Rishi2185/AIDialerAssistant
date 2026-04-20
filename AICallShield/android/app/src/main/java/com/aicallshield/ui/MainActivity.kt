package com.aicallshield.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aicallshield.data.model.CallStatus
import com.aicallshield.ui.screens.*
import com.aicallshield.ui.theme.AICallShieldTheme
import com.aicallshield.viewmodel.CallHistoryViewModel
import com.aicallshield.viewmodel.CallScreeningViewModel
import com.aicallshield.viewmodel.SettingsViewModel
import com.aicallshield.viewmodel.UserViewModel

/**
 * Main Activity — hosts the navigation and bottom bar.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                Log.i(TAG, "All permissions granted")
            } else {
                Log.w(TAG, "Some permissions denied: ${results.filter { !it.value }.keys}")
            }
        }

    private val dialerRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.i(TAG, "Default dialer role granted!")
                Toast.makeText(this, "AICallShield is now your default dialer ✅", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Default dialer role denied")
                Toast.makeText(this, "Default dialer required for call screening", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestEssentialPermissions()
        requestDefaultDialerRole()

        setContent {
            AICallShieldTheme {
                MainApp(
                    isDefaultDialer = isDefaultDialer(),
                    onRequestDialerRole = { requestDefaultDialerRole() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-compose after returning from system settings
        setContent {
            AICallShieldTheme {
                MainApp(
                    isDefaultDialer = isDefaultDialer(),
                    onRequestDialerRole = { requestDefaultDialerRole() }
                )
            }
        }
    }

    private fun requestEssentialPermissions() {
        val required = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * Request the default dialer role using RoleManager (API 29+).
     * This is REQUIRED for AIInCallService to receive call events
     * and for the app to answer/reject/end calls programmatically.
     */
    private fun requestDefaultDialerRole() {
        if (isDefaultDialer()) {
            Log.i(TAG, "Already the default dialer")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                dialerRoleLauncher.launch(intent)
            }
        } else {
            // Pre-Q fallback (shouldn't happen since minSdk=29)
            val intent = android.content.Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            dialerRoleLauncher.launch(intent)
        }
    }

    /**
     * Check whether this app is currently the default dialer.
     */
    fun isDefaultDialer(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage == packageName
    }
}

/**
 * Navigation routes.
 */
sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "Home")
    object History : Screen("history", "History")
    object Screening : Screen("screening/{callerNumber}", "Screening") {
        fun createRoute(number: String) = "screening/$number"
    }
    object CallDetail : Screen("call_detail/{callId}", "Detail") {
        fun createRoute(callId: String) = "call_detail/$callId"
    }
    object Settings : Screen("settings", "Settings")
    object PersonalDetails : Screen("personal_details", "Personal Details")
    object AssistantVoice : Screen("assistant_voice", "Assistant Voice")
    object CallForwarding : Screen("call_forwarding", "Call Forwarding")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    isDefaultDialer: Boolean = false,
    onRequestDialerRole: () -> Unit = {}
) {
    val navController = rememberNavController()
    val userViewModel: UserViewModel = viewModel()
    val userName by userViewModel.userName.collectAsState()
    val userGender by userViewModel.userGender.collectAsState()

    Scaffold(
        topBar = {
            // HomeScreen has its own custom top bar, so skip the default one
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    userName = userName,
                    isDefaultDialer = isDefaultDialer,
                    onRequestDialerRole = onRequestDialerRole,
                    onStartDemo = { number ->
                        navController.navigate(Screen.Screening.createRoute(number))
                    },
                    onViewHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route) { popUpTo(Screen.Home.route) }
                    }
                )
            }

            composable(Screen.History.route) {
                val historyViewModel: CallHistoryViewModel = viewModel()
                val calls by historyViewModel.calls.collectAsState()
                val searchQuery by historyViewModel.searchQuery.collectAsState()
                val isLoading by historyViewModel.isLoading.collectAsState()

                CallHistoryScreen(
                    calls = calls,
                    searchQuery = searchQuery,
                    onSearchQueryChange = historyViewModel::onSearchQueryChange,
                    onCallClick = { call ->
                        historyViewModel.selectCall(call)
                        navController.navigate(Screen.CallDetail.createRoute(call.id))
                    },
                    onDeleteCall = historyViewModel::deleteCall,
                    isLoading = isLoading
                )
            }

            composable(Screen.Screening.route) { backStackEntry ->
                val callerNumber = backStackEntry.arguments?.getString("callerNumber") ?: "Unknown"
                val screeningViewModel: CallScreeningViewModel = viewModel()

                LaunchedEffect(callerNumber) {
                    screeningViewModel.startScreening(callerNumber)
                }

                val callStatus by screeningViewModel.callStatus.collectAsState()
                val messages by screeningViewModel.messages.collectAsState()
                val spamScore by screeningViewModel.currentSpamScore.collectAsState()
                val riskLevel by screeningViewModel.currentRiskLevel.collectAsState()
                val isProcessing by screeningViewModel.isAIProcessing.collectAsState()
                val isLocalMode by screeningViewModel.isLocalMode.collectAsState()

                CallScreeningScreen(
                    callerNumber = callerNumber,
                    callStatus = callStatus,
                    messages = messages,
                    currentSpamScore = spamScore,
                    currentRiskLevel = riskLevel,
                    isAIProcessing = isProcessing,
                    isLocalMode = isLocalMode,
                    onJoinCall = screeningViewModel::joinCall,
                    onBlockCaller = screeningViewModel::blockCaller,
                    onEndCall = {
                        screeningViewModel.endCall()
                        navController.popBackStack()
                    },
                    onSendMessage = screeningViewModel::sendMessage
                )
            }

            composable(Screen.CallDetail.route) {
                val historyViewModel: CallHistoryViewModel = viewModel()
                val selectedCall by historyViewModel.selectedCall.collectAsState()

                selectedCall?.let { call ->
                    CallDetailScreen(
                        call = call,
                        onBack = { navController.popBackStack() },
                        onBlockCaller = { historyViewModel.blockCaller(call) }
                    )
                }
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    userName = userName,
                    onBack = { navController.popBackStack() },
                    onOpenPersonalDetails = {
                        navController.navigate(Screen.PersonalDetails.route)
                    },
                    onOpenAssistantVoice = {
                        navController.navigate(Screen.AssistantVoice.route)
                    },
                    onOpenCallForwarding = {
                        navController.navigate(Screen.CallForwarding.route)
                    }
                )
            }

            composable(Screen.CallForwarding.route) {
                CallForwardingScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PersonalDetails.route) {
                PersonalDetailsScreen(
                    initialName = userName,
                    initialGender = userGender,
                    onBack = { navController.popBackStack() },
                    onConfirm = { name, gender ->
                        userViewModel.saveProfile(name, gender)
                    }
                )
            }

            composable(Screen.AssistantVoice.route) {
                val settingsViewModel: SettingsViewModel = viewModel()
                val selectedVoice by settingsViewModel.selectedVoice.collectAsState()

                AssistantVoiceScreen(
                    userName = userName,
                    initialVoice = selectedVoice,
                    onBack = { navController.popBackStack() },
                    onConfirm = { voice ->
                        settingsViewModel.updateVoice(voice)
                    }
                )
            }
        }
    }
}
