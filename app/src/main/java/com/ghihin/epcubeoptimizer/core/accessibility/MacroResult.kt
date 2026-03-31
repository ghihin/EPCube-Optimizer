package com.ghihin.epcubeoptimizer.core.accessibility

data class MacroResult(
    val isSuccess: Boolean,
    val targetSoc: Int,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
