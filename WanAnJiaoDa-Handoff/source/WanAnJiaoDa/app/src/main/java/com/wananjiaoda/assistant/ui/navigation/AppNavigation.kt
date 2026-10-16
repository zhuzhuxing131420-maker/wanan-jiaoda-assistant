package com.wananjiaoda.assistant.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wananjiaoda.assistant.ui.component.AppBottomBar
import com.wananjiaoda.assistant.ui.screen.HistoryScreen
import com.wananjiaoda.assistant.ui.screen.HomeScreen
import com.wananjiaoda.assistant.ui.screen.SettingsScreen
import com.wananjiaoda.assistant.ui.screen.PermissionGuideScreen
import com.wananjiaoda.assistant.ui.screen.AccessibilityTestScreen
import com.wananjiaoda.assistant.ui.screen.WeChatObserverScreen
import com.wananjiaoda.assistant.ui.viewmodel.HomeViewModel
import com.wananjiaoda.assistant.ui.viewmodel.SettingsViewModel
import com.wananjiaoda.assistant.ui.viewmodel.HistoryViewModel
import com.wananjiaoda.assistant.permission.PermissionSettingsNavigator
import com.wananjiaoda.assistant.permission.PermissionViewModel
import com.wananjiaoda.assistant.service.AccessibilityEventLogger
import com.wananjiaoda.assistant.automation.WeChatObserver
import com.wananjiaoda.assistant.automation.WeChatTreeDiagnostics
import com.wananjiaoda.assistant.automation.WeChatVisionDiagnostics
import com.wananjiaoda.assistant.automation.SingleSceneCheckInTest
import com.wananjiaoda.assistant.automation.WeChatNavigationTest
import kotlinx.coroutines.launch
import com.wananjiaoda.assistant.scheduler.ScheduledExecution

private const val ENTRY_ROUTE = "entry"

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val permissionViewModel: PermissionViewModel = viewModel()
    val guideSeen by permissionViewModel.guideSeen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsNavigator = remember(context) { PermissionSettingsNavigator(context) }
    val scope = rememberCoroutineScope()
    val currentRoute = entry?.destination?.route
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (AppDestination.entries.any { it.route == currentRoute && it.showInBottomBar }) {
            AppBottomBar(currentRoute) { destination ->
                navController.navigate(destination.route) {
                    popUpTo(AppDestination.HOME.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = ENTRY_ROUTE, modifier = Modifier.padding(padding)) {
            composable(ENTRY_ROUTE) {
                LaunchedEffect(guideSeen) {
                    guideSeen?.let { seen ->
                        navController.navigate(if (seen) AppDestination.HOME.route else AppDestination.PERMISSIONS.route) {
                            popUpTo(ENTRY_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            composable(AppDestination.HOME.route) {
                RefreshPermissionStateOnResume(permissionViewModel)
                val vm: HomeViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val permissions by permissionViewModel.permissionState.collectAsStateWithLifecycle()
                HomeScreen(state, permissions, onPermissionGuide = { navController.navigate(AppDestination.PERMISSIONS.route) },
                    onTestCheckIn = { navController.navigate(AppDestination.WECHAT_OBSERVER.route) })
            }
            composable(AppDestination.SETTINGS.route) {
                RefreshPermissionStateOnResume(permissionViewModel)
                val vm: SettingsViewModel = viewModel()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val nextTaskText by vm.nextTaskText.collectAsStateWithLifecycle()
                val exactAlarmAllowed by vm.exactAlarmAllowed.collectAsStateWithLifecycle()
                LifecycleResumeEffect(vm) {
                    vm.refreshScheduleState()
                    onPauseOrDispose { }
                }
                val permissions by permissionViewModel.permissionState.collectAsStateWithLifecycle()
                SettingsScreen(settings, vm::setTime, vm::setWeekday, vm::setAutoEnabled, vm::setMode,
                    nextTaskText = nextTaskText,
                    exactAlarmAllowed = exactAlarmAllowed,
                    onExactAlarmPermissionRequest = settingsNavigator::openExactAlarmSettings,
                    onTestTask = vm::triggerTestTask,
                    onPermissionGuide = { navController.navigate(AppDestination.PERMISSIONS.route) },
                    accessibilityEnabled = permissions?.accessibilityEnabled,
                    onAccessibilityTest = { navController.navigate(AppDestination.ACCESSIBILITY_TEST.route) },
                    onWeChatObserver = { navController.navigate(AppDestination.WECHAT_OBSERVER.route) })
            }
            composable(AppDestination.HISTORY.route) {
                val vm: HistoryViewModel = viewModel()
                val records by vm.records.collectAsStateWithLifecycle()
                HistoryScreen(records)
            }
            composable(AppDestination.PERMISSIONS.route) {
                RefreshPermissionStateOnResume(permissionViewModel)
                val state by permissionViewModel.permissionState.collectAsStateWithLifecycle()
                PermissionGuideScreen(
                    state = state,
                    firstRun = guideSeen != true,
                    onNotificationSettings = settingsNavigator::openNotificationSettings,
                    onAccessibilitySettings = settingsNavigator::openAccessibilitySettings,
                    onBatterySettings = settingsNavigator::requestBatteryOptimizationExemption,
                    onContinue = {
                        if (guideSeen == true) {
                            navController.popBackStack()
                        } else {
                            scope.launch {
                                permissionViewModel.markGuideSeen()
                                navController.navigate(AppDestination.HOME.route) {
                                    popUpTo(AppDestination.PERMISSIONS.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                )
            }
            composable(AppDestination.WECHAT_OBSERVER.route) {
                DisposableEffect(Unit) {
                    onDispose { if (!ScheduledExecution.ownsObservation) WeChatObserver.stop() }
                }
                val serviceConnected by AccessibilityEventLogger.connected.collectAsStateWithLifecycle()
                val observing by WeChatObserver.observing.collectAsStateWithLifecycle()
                val detected by WeChatObserver.detected.collectAsStateWithLifecycle()
                val events by WeChatObserver.entries.collectAsStateWithLifecycle()
                val nodeCaptureReport by WeChatTreeDiagnostics.report.collectAsStateWithLifecycle()
                val visionReport by WeChatVisionDiagnostics.report.collectAsStateWithLifecycle()
                val sceneTest by SingleSceneCheckInTest.uiState.collectAsStateWithLifecycle()
                WeChatObserverScreen(
                    serviceConnected = serviceConnected,
                    observing = observing,
                    detected = detected,
                    events = events,
                    nodeCaptureReport = nodeCaptureReport,
                    visionReport = visionReport,
                    sceneTest = sceneTest,
                    onStartSceneTest = { SingleSceneCheckInTest.start(context) },
                    navigationTest = WeChatNavigationTest.state.collectAsStateWithLifecycle().value,
                    onStartNavigationTest = { WeChatNavigationTest.start(context) },
                    onStartNavigationOnly = { WeChatNavigationTest.start(context, onlyNavigate = true) },
                    onProbeLaunch = { WeChatNavigationTest.probeLaunch(context) },
                    onBack = { navController.popBackStack() },
                    onStart = WeChatObserver::start,
                    onStop = WeChatObserver::stop,
                    onClear = WeChatObserver::clear,
                    onOpenAccessibilitySettings = settingsNavigator::openAccessibilitySettings,
                )
            }
            composable(AppDestination.ACCESSIBILITY_TEST.route) {
                RefreshPermissionStateOnResume(permissionViewModel)
                val permissions by permissionViewModel.permissionState.collectAsStateWithLifecycle()
                val logs by AccessibilityEventLogger.entries.collectAsStateWithLifecycle()
                val connected by AccessibilityEventLogger.connected.collectAsStateWithLifecycle()
                AccessibilityTestScreen(
                    accessibilityEnabled = permissions?.accessibilityEnabled,
                    serviceConnected = connected,
                    logs = logs,
                    onBack = { navController.popBackStack() },
                    onOpenAccessibilitySettings = settingsNavigator::openAccessibilitySettings,
                    onOpenSystemSettings = settingsNavigator::openSystemSettings,
                )
            }
        }
    }
}

@Composable
private fun RefreshPermissionStateOnResume(viewModel: PermissionViewModel) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
}


