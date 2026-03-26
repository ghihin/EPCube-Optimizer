package com.ghihin.epcubeoptimizer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import com.ghihin.epcubeoptimizer.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * [ScheduleRepository] の実装。
 * Preferences DataStore を使用してユーザーの週間出社スケジュールを永続化する。
 *
 * 保存形式:
 *  - 週間スケジュール: 曜日ごとに "schedule_MONDAY" 等のキーで true/false を保存
 *  - 日付上書き: "override_2026-03-27" 等のキーで true/false を保存
 */
class ScheduleRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ScheduleRepository {

    companion object {
        /** 週間スケジュール用のキープレフィックス */
        private const val WEEKLY_PREFIX = "schedule_"

        /** 日付上書き用のキープレフィックス */
        private const val OVERRIDE_PREFIX = "override_"

        /** DataStore のキーを生成する */
        fun weeklyKey(dayOfWeek: DayOfWeek) =
            booleanPreferencesKey("$WEEKLY_PREFIX${dayOfWeek.name}")

        fun overrideKey(date: LocalDate) =
            booleanPreferencesKey("$OVERRIDE_PREFIX$date")
    }

    override fun getSchedule(): Flow<UserSchedule> {
        return dataStore.data.map { preferences ->
            // 週間スケジュールを全曜日について読み込む（デフォルトは月〜金が出社）
            val defaultWeeklySchedule = DayOfWeek.values().associateWith { dayOfWeek ->
                preferences[weeklyKey(dayOfWeek)] ?: isDefaultCommute(dayOfWeek)
            }

            // 日付上書きを読み込む
            val dateOverrides = preferences.asMap()
                .entries
                .filter { it.key.name.startsWith(OVERRIDE_PREFIX) }
                .associate { entry ->
                    val dateStr = entry.key.name.removePrefix(OVERRIDE_PREFIX)
                    LocalDate.parse(dateStr) to (entry.value as Boolean)
                }

            UserSchedule(
                defaultWeeklySchedule = defaultWeeklySchedule,
                dateOverrides = dateOverrides
            )
        }
    }

    override suspend fun saveSchedule(schedule: UserSchedule) {
        dataStore.edit { preferences ->
            // 週間スケジュールを保存
            schedule.defaultWeeklySchedule.forEach { (dayOfWeek, isCommute) ->
                preferences[weeklyKey(dayOfWeek)] = isCommute
            }

            // 日付上書きを保存: 既存の上書きキーをすべてクリアしてから書き直す
            val overrideKeys = preferences.asMap().keys.filter { it.name.startsWith(OVERRIDE_PREFIX) }
            overrideKeys.forEach { key -> preferences.remove(key) }
            schedule.dateOverrides.forEach { (date, isCommute) ->
                preferences[overrideKey(date)] = isCommute
            }
        }
    }

    /**
     * 保存値が存在しない場合のデフォルト出社判定。
     * 月〜金を出社日（true）、土日を在宅（false）とする。
     */
    private fun isDefaultCommute(dayOfWeek: DayOfWeek): Boolean {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
    }
}
