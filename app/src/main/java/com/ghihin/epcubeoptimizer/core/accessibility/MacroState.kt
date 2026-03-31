package com.ghihin.epcubeoptimizer.core.accessibility

enum class MacroState {
    IDLE,
    STARTING,
    WAITING_FOR_HOME,
    WAITING_FOR_SETTINGS,
    ADJUSTING_SOC,
    SAVING,
    WAITING_FOR_LOADING,
    WAITING_FOR_SUCCESS,
    COMPLETED,
    FAILED
}
