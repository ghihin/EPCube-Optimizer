package com.ghihin.epcubeoptimizer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ghihin.epcubeoptimizer.domain.model.UserSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [ScheduleRepositoryImpl] の単体テスト。
 * PreferenceDataStoreFactory を使用してリアルなDataStoreで検証する。
 */
class ScheduleRepositoryImplTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { File(tmpFolder.root, "test.preferences_pb") }
        )
    }

    @Test
    fun `getSchedule - 初期値は月〜金が出社、土日が在宅`() = runBlocking {
        val repository = ScheduleRepositoryImpl(dataStore)
        val schedule = repository.getSchedule().first()

        assertTrue("月曜は出社", schedule.defaultWeeklySchedule[DayOfWeek.MONDAY] == true)
        assertTrue("火曜は出社", schedule.defaultWeeklySchedule[DayOfWeek.TUESDAY] == true)
        assertTrue("水曜は出社", schedule.defaultWeeklySchedule[DayOfWeek.WEDNESDAY] == true)
        assertTrue("木曜は出社", schedule.defaultWeeklySchedule[DayOfWeek.THURSDAY] == true)
        assertTrue("金曜は出社", schedule.defaultWeeklySchedule[DayOfWeek.FRIDAY] == true)
        assertFalse("土曜は在宅", schedule.defaultWeeklySchedule[DayOfWeek.SATURDAY] == true)
        assertFalse("日曜は在宅", schedule.defaultWeeklySchedule[DayOfWeek.SUNDAY] == true)
    }

    @Test
    fun `saveSchedule と getSchedule - 週間スケジュールを正しく保存・読み込む`() = runBlocking {
        val repository = ScheduleRepositoryImpl(dataStore)

        // 全曜日在宅に変更
        val allRemote = UserSchedule(
            defaultWeeklySchedule = DayOfWeek.values().associateWith { false }
        )
        repository.saveSchedule(allRemote)
        var schedule = repository.getSchedule().first()
        DayOfWeek.values().forEach { day ->
            assertFalse("$day は在宅であるべき", schedule.defaultWeeklySchedule[day] == true)
        }

        // 月〜金のみ出社に変更
        val weekdayCommute = UserSchedule(
            defaultWeeklySchedule = DayOfWeek.values().associateWith { day ->
                day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
            }
        )
        repository.saveSchedule(weekdayCommute)
        schedule = repository.getSchedule().first()
        assertTrue("月曜は出社", schedule.defaultWeeklySchedule[DayOfWeek.MONDAY] == true)
        assertFalse("土曜は在宅", schedule.defaultWeeklySchedule[DayOfWeek.SATURDAY] == true)
    }

    @Test
    fun `saveSchedule と getSchedule - 日付上書きを正しく保存・読み込む`() = runBlocking {
        val repository = ScheduleRepositoryImpl(dataStore)

        val targetDate = LocalDate.of(2026, 4, 1)
        val scheduleWithOverride = UserSchedule(
            defaultWeeklySchedule = DayOfWeek.values().associateWith { day ->
                day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
            },
            dateOverrides = mapOf(targetDate to false) // 出社日を在宅に上書き
        )
        repository.saveSchedule(scheduleWithOverride)

        val schedule = repository.getSchedule().first()
        assertTrue("日付上書きが存在する", schedule.dateOverrides.containsKey(targetDate))
        assertFalse("上書き日は在宅であるべき", schedule.dateOverrides[targetDate] == true)
        assertFalse("isCommuteDay も在宅を返す", schedule.isCommuteDay(targetDate))
    }

    @Test
    fun `saveSchedule - 日付上書きを新しいスケジュールで上書き保存すると古い上書きは消える`() = runBlocking {
        val repository = ScheduleRepositoryImpl(dataStore)

        val date1 = LocalDate.of(2026, 4, 1)
        val date2 = LocalDate.of(2026, 4, 2)

        // 最初に date1 を上書き
        repository.saveSchedule(
            UserSchedule(
                defaultWeeklySchedule = DayOfWeek.values().associateWith { true },
                dateOverrides = mapOf(date1 to false)
            )
        )
        assertEquals(1, repository.getSchedule().first().dateOverrides.size)

        // 次に date2 だけの上書きで保存（date1 は消えるはず）
        repository.saveSchedule(
            UserSchedule(
                defaultWeeklySchedule = DayOfWeek.values().associateWith { true },
                dateOverrides = mapOf(date2 to false)
            )
        )
        val schedule = repository.getSchedule().first()
        assertFalse("date1 の上書きは消えている", schedule.dateOverrides.containsKey(date1))
        assertTrue("date2 の上書きが存在する", schedule.dateOverrides.containsKey(date2))
    }
}
