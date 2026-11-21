package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ads.AdManager
import com.example.ads.BannerAdSlot
import com.example.data.VaultCategory
import com.example.security.AppLockMonitorService
import com.example.security.PanicLockEventBus
import com.example.security.PanicLockSensorService
import com.example.security.PanicTriggerReason
import com.example.ui.components.VaultMediaViewerDialog
import com.example.ui.navigation.VaultRoute
import com.example.ui.screens.AppDisguiseScreen
import com.example.ui.screens.AppLockerScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PrivacyGuaranteeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartAiIntruderScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SystemConnectivityLockScreen
import com.example.ui.screens.TrashAndBackupScreen
import com.example.ui.screens.VaultCategoryScreen
import com.example.ui.theme.SmartAiLockerTheme
import com.example.viewmodel.AppLockPhase
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var adManager: AdManager

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adManager = (application as SmartAiApplication).adManager.apply {
            initializeInBackground()
            requestConsentInfo(this@MainActivity)
        }
        handleInterceptIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val config by viewModel.securityConfig.collectAsStateWithLifecycle()
            val lockPhase by viewModel.lockPhase.collectAsStateWithLifecycle()

            // Dynamically enforce FLAG_SECURE screenshot & recents protection
            LaunchedEffect(config.flagSecureEnabled) {
                if (config.flagSecureEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // SensorManager Panic Lock Service (monitors Shake & Face-Down while vault session is unlocked)
            LaunchedEffect(config.panicLockEnabled, lockPhase) {
                if (config.panicLockEnabled && lockPhase == AppLockPhase.UNLOCKED) {
                    PanicLockSensorService.start(applicationContext)
                } else {
                    PanicLockSensorService.stop(applicationContext)
                }
            }

            LaunchedEffect(config.panicLockEnabled) {
                if (!config.panicLockEnabled) return@LaunchedEffect
                PanicLockEventBus.events.collect { reason ->
                    viewModel.triggerPanicLock(reason)
                }
            }

            // Anti-kill & background AppState observer
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                        Lifecycle.Event.ON_START -> {
                            val wasUnlocked = viewModel.lockPhase.value == AppLockPhase.UNLOCKED
                            viewModel.onAppForegrounded()
                            if (wasUnlocked && viewModel.lockPhase.value == AppLockPhase.UNLOCKED) {
                                adManager.showAppOpenIfAvailable(this@MainActivity)
                            }
                        }
                        else -> Unit
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                }
            }

            SmartAiLockerTheme(
                themeMode = config.themeMode,
                accentTheme = config.accentTheme
            ) {
                SmartAiAppRoot(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    viewModel = viewModel,
                    adManager = adManager,
                    hostActivity = this@MainActivity,
                    lockPhase = lockPhase
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInterceptIntent(intent)
    }

    private fun handleInterceptIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PanicLockSensorService.EXTRA_PANIC_LOCK_TRIGGERED, false) == true) {
            val reason = intent.getStringExtra(PanicLockSensorService.EXTRA_PANIC_REASON)
                ?.let { name -> PanicTriggerReason.entries.firstOrNull { it.name == name } }
            viewModel.triggerPanicLock(reason)
            return
        }
        val pkg = intent?.getStringExtra(AppLockMonitorService.EXTRA_LOCKED_TARGET_PACKAGE)
        if (!pkg.isNullOrBlank()) {
            viewModel.onExternalLockedAppIntercepted(pkg)
        }
    }
}

@Composable
private fun SmartAiAppRoot(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: MainViewModel,
    adManager: AdManager,
    hostActivity: Activity,
    lockPhase: AppLockPhase
) {
    val config by viewModel.securityConfig.collectAsStateWithLifecycle()
    val failedAttempts by viewModel.failedAttempts.collectAsStateWithLifecycle()
    val authErrorMessage by viewModel.authErrorMessage.collectAsStateWithLifecycle()
    val isDecoySession by viewModel.isDecoySession.collectAsStateWithLifecycle()
    val activeItems by viewModel.activeVaultItems.collectAsStateWithLifecycle()
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val lockedApps by viewModel.lockedApps.collectAsStateWithLifecycle()
    val intruderLogs by viewModel.intruderLogs.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val aiCandidates by viewModel.aiCandidates.collectAsStateWithLifecycle()
    val previewPayload by viewModel.previewPayload.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val isPanicSensorMonitoring by viewModel.isPanicSensorMonitoring.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusToast) {
        val msg = statusToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusToast()
    }

    AnimatedContent(
        targetState = lockPhase,
        transitionSpec = {
            fadeIn(animationSpec = tween(320)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "lockPhase"
    ) { phase ->
        when (phase) {
            AppLockPhase.SPLASH -> SplashScreen()

            AppLockPhase.ONBOARDING -> {
                OnboardingScreen(
                    onComplete = { pin, pinLength, pattern, q1, a1, q2, a2, bio, decoy ->
                        viewModel.completeOnboarding(
                            pin = pin,
                            pinLength = pinLength,
                            pattern = pattern,
                            question1 = q1,
                            answer1 = a1,
                            question2 = q2,
                            answer2 = a2,
                            enableBiometric = bio,
                            decoyPin = decoy
                        )
                    }
                )
            }

            AppLockPhase.LOCKED -> {
                val lockedTargetAppName by viewModel.lockedTargetAppLabel.collectAsStateWithLifecycle()
                LockScreen(
                    config = config,
                    failedAttempts = failedAttempts,
                    authErrorMessage = authErrorMessage,
                    lockedTargetAppName = lockedTargetAppName,
                    onDismissFakeCrashToHome = {
                        viewModel.dismissLockedTargetAfterCancel()
                        hostActivity.moveTaskToBack(true)
                    },
                    onCalculatorTrigger = { expr -> viewModel.handleCalculatorEquals(expr) },
                    onPinSubmit = { pin, owner -> viewModel.submitPinAttempt(pin, owner) },
                    onPatternSubmit = { pattern, owner -> viewModel.submitPatternAttempt(pattern, owner) },
                    onBiometricUnlock = { viewModel.unlockViaBiometric() },
                    onVerifySecurityQuestions = { a1, a2 -> viewModel.verifySecurityAnswers(a1, a2) },
                    onResetMasterPin = { newPin, len, newPattern ->
                        viewModel.resetMasterPinAndPattern(newPin, len, newPattern)
                    },
                    onClearError = { viewModel.clearAuthError() }
                )
            }

            AppLockPhase.UNLOCKED -> {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                val useRail = widthSizeClass != WindowWidthSizeClass.Compact

                val topDestinations = listOf(
                    Triple(VaultRoute.Home, "Vault", Icons.Default.Home),
                    Triple(VaultRoute.AppLocker, "App Lock", Icons.Default.Apps),
                    Triple(VaultRoute.SmartAiIntruder, "SmartAI", Icons.Default.AutoAwesome),
                    Triple(VaultRoute.Settings, "Settings", Icons.Default.Settings)
                )

                val showPrimaryNav = currentDestination?.let { dest ->
                    dest.hasRoute<VaultRoute.Home>() ||
                        dest.hasRoute<VaultRoute.AppLocker>() ||
                        dest.hasRoute<VaultRoute.SmartAiIntruder>() ||
                        dest.hasRoute<VaultRoute.Settings>()
                } ?: true

                fun triggerRewardedUnlock(activity: Activity?) {
                    adManager.showRewarded(
                        activity = activity,
                        onRewardEarned = { viewModel.unlockProWithReward() },
                        onUnavailable = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Rewarded ad loading. Try again in a moment.")
                            }
                        }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useRail && showPrimaryNav) {
                            Column {
                                BannerAdSlot(adManager = adManager)
                                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                    topDestinations.forEach { (route, label, icon) ->
                                        val selected = when (route) {
                                            VaultRoute.Home -> currentDestination?.hasRoute<VaultRoute.Home>() == true
                                            VaultRoute.AppLocker -> currentDestination?.hasRoute<VaultRoute.AppLocker>() == true
                                            VaultRoute.SmartAiIntruder -> currentDestination?.hasRoute<VaultRoute.SmartAiIntruder>() == true
                                            VaultRoute.Settings -> currentDestination?.hasRoute<VaultRoute.Settings>() == true
                                            else -> false
                                        }
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                navController.navigate(route) {
                                                    popUpTo(VaultRoute.Home) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Icon(icon, contentDescription = label) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail && showPrimaryNav) {
                            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                topDestinations.forEach { (route, label, icon) ->
                                    val selected = when (route) {
                                        VaultRoute.Home -> currentDestination?.hasRoute<VaultRoute.Home>() == true
                                        VaultRoute.AppLocker -> currentDestination?.hasRoute<VaultRoute.AppLocker>() == true
                                        VaultRoute.SmartAiIntruder -> currentDestination?.hasRoute<VaultRoute.SmartAiIntruder>() == true
                                        VaultRoute.Settings -> currentDestination?.hasRoute<VaultRoute.Settings>() == true
                                        else -> false
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(VaultRoute.Home) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (isBusy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                NavHost(
                                    navController = navController,
                                    startDestination = VaultRoute.Home
                                ) {
                                    composable<VaultRoute.Home> {
                                        HomeDashboardScreen(
                                            config = config,
                                            permissionStatus = permissionStatus,
                                            activeItems = activeItems,
                                            trashCount = trashItems.size,
                                            lockedApps = lockedApps,
                                            intruderLogs = intruderLogs,
                                            isDecoySession = isDecoySession,
                                            isPanicSensorMonitoring = isPanicSensorMonitoring,
                                            adManager = adManager,
                                            onOpenCategory = { cat ->
                                                navController.navigate(VaultRoute.CategoryVault(cat.name))
                                            },
                                            onOpenAppLocker = {
                                                navController.navigate(VaultRoute.AppLocker)
                                            },
                                            onOpenConnectivityLock = {
                                                navController.navigate(VaultRoute.ConnectivityLock)
                                            },
                                            onOpenSmartAiIntruder = {
                                                navController.navigate(VaultRoute.SmartAiIntruder)
                                            },
                                            onOpenTrashAndBackup = {
                                                navController.navigate(VaultRoute.TrashAndBackup)
                                            },
                                            onOpenSettings = {
                                                navController.navigate(VaultRoute.Settings)
                                            },
                                            onLockNow = { viewModel.lockVaultNow() }
                                        )
                                    }

                                    composable<VaultRoute.CategoryVault> { entry ->
                                        val route = entry.toRoute<VaultRoute.CategoryVault>()
                                        val category = VaultCategory.entries.firstOrNull {
                                            it.name == route.categoryName
                                        } ?: VaultCategory.PHOTOS
                                        val catItems = activeItems.filter { it.category == category.name }

                                        VaultCategoryScreen(
                                            category = category,
                                            items = catItems,
                                            onBack = {
                                                adManager.maybeShowInterstitial(hostActivity) {
                                                    navController.popBackStack()
                                                }
                                            },
                                            onImportUris = { uris ->
                                                viewModel.importFilesToVault(uris, category)
                                            },
                                            onOpenItem = { item -> viewModel.openVaultItem(item) },
                                            onUnhideItem = { item -> viewModel.unhideItem(item) },
                                            onSoftDeleteItem = { item -> viewModel.softDeleteItem(item) },
                                            onPermanentDeleteItem = { item -> viewModel.permanentDeleteItem(item) }
                                        )
                                    }

                                    composable<VaultRoute.AppLocker> {
                                        AppLockerScreen(
                                            config = config,
                                            permissionStatus = permissionStatus,
                                            installedApps = installedApps,
                                            onBack = { navController.popBackStack() },
                                            onRefresh = { viewModel.refreshPermissionsAndApps() },
                                            onToggleAppLock = { app, locked ->
                                                viewModel.toggleAppLock(app, locked)
                                            },
                                            onToggleSettingsLock = { locked ->
                                                viewModel.toggleConnectivityLock(settingsApp = locked)
                                            },
                                            onToggleNotificationLock = { locked ->
                                                viewModel.toggleConnectivityLock(notificationPanel = locked)
                                            }
                                        )
                                    }

                                    composable<VaultRoute.ConnectivityLock> {
                                        SystemConnectivityLockScreen(
                                            config = config,
                                            onBack = { navController.popBackStack() },
                                            onUpdateLock = { wifi, data, bt, usb, settings, notif ->
                                                viewModel.toggleConnectivityLock(
                                                    wifi = wifi,
                                                    mobileData = data,
                                                    bluetooth = bt,
                                                    usb = usb,
                                                    settingsApp = settings,
                                                    notificationPanel = notif
                                                )
                                            }
                                        )
                                    }

                                    composable<VaultRoute.SmartAiIntruder> {
                                        SmartAiIntruderScreen(
                                            config = config,
                                            permissionStatus = permissionStatus,
                                            aiCandidates = aiCandidates,
                                            intruderLogs = intruderLogs,
                                            onBack = { navController.popBackStack() },
                                            onScanUris = { uris -> viewModel.scanCandidateUris(uris) },
                                            onAutoHideCandidate = { cand -> viewModel.autoHideCandidate(cand) },
                                            onAutoHideAll = { viewModel.autoHideAllSuggested() },
                                            onReclassifyVault = { viewModel.reclassifyVaultItems() },
                                            onToggleIntruderSelfie = { enabled ->
                                                viewModel.setIntruderSelfieEnabled(enabled)
                                            },
                                            onTestIntruderSelfie = { owner ->
                                                viewModel.triggerManualIntruderSelfieTest(owner)
                                            },
                                            onLoadIntruderPhoto = { file ->
                                                viewModel.loadDecryptedIntruderPhoto(file)
                                            },
                                            onDeleteIntruderLog = { log ->
                                                viewModel.deleteIntruderLog(log)
                                            },
                                            onClearIntruderLogs = { viewModel.clearAllIntruderLogs() },
                                            onSaveDecoyPin = { pin, enabled ->
                                                viewModel.updateDecoyPin(pin, enabled)
                                            },
                                            onToggleDecoy = { enabled -> viewModel.toggleDecoyVault(enabled) },
                                            onRefreshPermissions = { viewModel.refreshPermissionStatus() },
                                            onWatchRewardedUnlock = { act -> triggerRewardedUnlock(act) }
                                        )
                                    }

                                    composable<VaultRoute.TrashAndBackup> {
                                        TrashAndBackupScreen(
                                            trashItems = trashItems,
                                            onBack = { navController.popBackStack() },
                                            onExportBackup = { pass, uri ->
                                                viewModel.exportEncryptedBackup(pass, uri)
                                            },
                                            onImportBackup = { uri, pass ->
                                                viewModel.importEncryptedBackup(uri, pass)
                                            },
                                            onRestoreFromTrash = { item ->
                                                viewModel.restoreItemFromTrash(item)
                                            },
                                            onPermanentDelete = { item ->
                                                viewModel.permanentDeleteItem(item)
                                            }
                                        )
                                    }

                                    composable<VaultRoute.Settings> {
                                        SettingsScreen(
                                            config = config,
                                            isPanicSensorMonitoring = isPanicSensorMonitoring,
                                            onBack = { navController.popBackStack() },
                                            onSetThemeMode = { viewModel.setThemeMode(it) },
                                            onSetAccentTheme = { viewModel.setAccentTheme(it) },
                                            onSetPreferredAuth = { viewModel.setPreferredAuthMethod(it) },
                                            onSetBiometricEnabled = { viewModel.setBiometricEnabled(it) },
                                            onSetIntruderSelfieEnabled = { viewModel.setIntruderSelfieEnabled(it) },
                                            onSetAutoLockTimer = { preset, custom ->
                                                viewModel.setAutoLockTimer(preset, custom)
                                            },
                                            onSetKeepUnlockedDuringPlayback = {
                                                viewModel.setKeepUnlockedDuringPlayback(it)
                                            },
                                            onSetBackgroundAudioPlay = {
                                                viewModel.setBackgroundAudioPlay(it)
                                            },
                                            onSetFlagSecure = { viewModel.setFlagSecureEnabled(it) },
                                            onSetRandomizeKeypad = { viewModel.setRandomizeKeypad(it) },
                                            onSetFakeCrashEnabled = { viewModel.setFakeCrashEnabled(it) },
                                            onSetPanicLockEnabled = { viewModel.setPanicLockEnabled(it) },
                                            onOpenAppDisguise = {
                                                navController.navigate(VaultRoute.AppDisguise)
                                            },
                                            onOpenPrivacyGuarantee = {
                                                navController.navigate(VaultRoute.PrivacyGuarantee)
                                            },
                                            onVerifyCurrentPin = { pin ->
                                                viewModel.verifyMasterPinDirect(pin)
                                            },
                                            onUpdatePinAndPattern = { newPin, len, newPattern ->
                                                viewModel.resetMasterPinAndPattern(newPin, len, newPattern)
                                            },
                                            onUpdateSecurityQuestions = { q1, a1, q2, a2 ->
                                                viewModel.updateSecurityQuestions(q1, a1, q2, a2)
                                            },
                                            onWatchRewardedUnlock = { act -> triggerRewardedUnlock(act) }
                                        )
                                    }

                                    composable<VaultRoute.AppDisguise> {
                                        AppDisguiseScreen(
                                            config = config,
                                            onBack = { navController.popBackStack() },
                                            onSelectDisguiseMode = { mode -> viewModel.setDisguiseMode(mode) },
                                            onSetRandomizeKeypad = { viewModel.setRandomizeKeypad(it) },
                                            onSetFakeCrashEnabled = { viewModel.setFakeCrashEnabled(it) }
                                        )
                                    }

                                    composable<VaultRoute.PrivacyGuarantee> {
                                        PrivacyGuaranteeScreen(
                                            adManager = adManager,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }
                            }

                            if (useRail && showPrimaryNav) {
                                BannerAdSlot(adManager = adManager)
                            }
                        }
                    }

                    VaultMediaViewerDialog(
                        payload = previewPayload,
                        backgroundAudioPlay = config.backgroundAudioPlay,
                        onPlaybackActiveChanged = { viewModel.setMediaPlaying(it) },
                        onClose = { viewModel.closePreview() }
                    )
                }
            }
        }
    }
}
