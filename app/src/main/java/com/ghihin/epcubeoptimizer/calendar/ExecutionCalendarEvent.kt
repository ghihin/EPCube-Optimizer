package com.ghihin.epcubeoptimizer.calendar

data class ExecutionCalendarEvent(
    val isSuccess: Boolean,
    val executionTimeMillis: Long,
    val settingMode: String,
    val targetSoc: Int?,
    val errorMessage: String?,
    val preExecSoc: Int?,
    val preExecMode: String?,
    val weatherDescription: String?,
    val cloudiness: Int?,
    val precipitationProbability: Float?
)
