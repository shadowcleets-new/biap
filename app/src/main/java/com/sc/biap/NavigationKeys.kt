package com.sc.biap

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object AddDesign : NavKey
@Serializable data class DesignDetail(val designId: String) : NavKey
@Serializable data object Profile : NavKey
@Serializable data object AccessManagement : NavKey
