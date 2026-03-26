package com.ghihin.epcubeoptimizer.domain.repository

import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getSchedule(): Flow<UserSchedule>
    suspend fun saveSchedule(schedule: UserSchedule)
}
