package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface VaultRoute {
    @Serializable
    data object Home : VaultRoute

    @Serializable
    data class CategoryVault(val categoryName: String) : VaultRoute

    @Serializable
    data object AppLocker : VaultRoute

    @Serializable
    data object ConnectivityLock : VaultRoute

    @Serializable
    data object SmartAiIntruder : VaultRoute

    @Serializable
    data object TrashAndBackup : VaultRoute

    @Serializable
    data object Settings : VaultRoute

    @Serializable
    data object AppDisguise : VaultRoute

    @Serializable
    data object PrivacyGuarantee : VaultRoute
}
