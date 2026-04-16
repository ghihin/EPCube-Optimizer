package com.ghihin.epcubeoptimizer.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.ghihin.epcubeoptimizer.automation.Config
import com.ghihin.epcubeoptimizer.core.permission.PermissionHelper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarLogWriterTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var calendarLogWriter: CalendarLogWriter

    @Before
    fun setup() {
        context = spyk(ApplicationProvider.getApplicationContext())
        contentResolver = mockk()

        // ContentResolver のモック
        every { context.contentResolver } returns contentResolver
        
        // 権限チェックのモック (デフォルトで許可)
        mockkObject(PermissionHelper)
        every { PermissionHelper.hasCalendarPermissions(any()) } returns true
        
        calendarLogWriter = CalendarLogWriter(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setupMockCalendarId(id: Long = 1L) {
        val cursor = MatrixCursor(arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_NAME
        )).apply {
            addRow(arrayOf(id, "com.google", 1, Config.TARGET_CALENDAR_ACCOUNT_NAME))
        }

        every {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                any(),
                any(),
                any(),
                any()
            )
        } returns cursor
    }

    private fun setupMockDuplicateCheck(isDuplicate: Boolean) {
        val cursor = MatrixCursor(arrayOf(CalendarContract.Events._ID))
        if (isDuplicate) {
            cursor.addRow(arrayOf(100L))
        }

        every {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                any(),
                any(),
                any(),
                any()
            )
        } returns cursor
    }

    private val baseEvent = ExecutionCalendarEvent(
        isSuccess = true,
        executionTimeMillis = 1000L,
        settingMode = "スマートモード",
        targetSoc = 75,
        errorMessage = null,
        preExecSoc = 50,
        preExecMode = "グリーンモード",
        weatherDescription = "晴れ",
        shortwaveRadiationSum = 4500.0
    )

    @Test
    fun `isDuplicateEvent returns true when duplicate exists and insert is skipped`() = runBlocking {
        // T020.1 重複チェック検出時
        setupMockCalendarId()
        setupMockDuplicateCheck(isDuplicate = true)

        // ignoreDuplicate=false（デフォルト）で実行
        val result = calendarLogWriter.writeExecutionResult(baseEvent)

        assertFalse("Should return false because it's a duplicate", result)
        // insert が呼ばれていないことを確認
        verify(exactly = 0) { contentResolver.insert(CalendarContract.Events.CONTENT_URI, any()) }
    }

    @Test
    fun `writeExecutionResult inserts SUCCESS event with correct title`() = runBlocking {
        // T020.2 成功イベント書き込み
        setupMockCalendarId()
        setupMockDuplicateCheck(isDuplicate = false)
        every { contentResolver.insert(CalendarContract.Events.CONTENT_URI, any()) } returns Uri.parse("content://dummy/1")

        val result = calendarLogWriter.writeExecutionResult(baseEvent)

        assertTrue("Should return true on success", result)

        val valuesSlot = slot<ContentValues>()
        verify { contentResolver.insert(CalendarContract.Events.CONTENT_URI, capture(valuesSlot)) }
        assertEquals("✅ EPCUBE設定完了", valuesSlot.captured.getAsString(CalendarContract.Events.TITLE))
    }

    @Test
    fun `writeExecutionResult inserts FAILURE event with correct title`() = runBlocking {
        // T020.3 失敗イベント書き込み
        setupMockCalendarId()
        setupMockDuplicateCheck(isDuplicate = false)
        every { contentResolver.insert(CalendarContract.Events.CONTENT_URI, any()) } returns Uri.parse("content://dummy/2")

        val failEvent = baseEvent.copy(isSuccess = false, errorMessage = "ネットワークエラー")
        
        val result = calendarLogWriter.writeExecutionResult(failEvent)

        assertTrue("Should return true on success", result)

        val valuesSlot = slot<ContentValues>()
        verify { contentResolver.insert(CalendarContract.Events.CONTENT_URI, capture(valuesSlot)) }
        assertEquals("❌ EPCUBE設定失敗", valuesSlot.captured.getAsString(CalendarContract.Events.TITLE))
        assertTrue(valuesSlot.captured.getAsString(CalendarContract.Events.DESCRIPTION).contains("ネットワークエラー"))
    }

    @Test
    fun `buildDescription handles Green Mode (targetSoc=null) correctly`() = runBlocking {
        // T020.4 グリーンモード説明欄
        setupMockCalendarId()
        setupMockDuplicateCheck(isDuplicate = false)
        every { contentResolver.insert(CalendarContract.Events.CONTENT_URI, any()) } returns Uri.parse("content://dummy/3")

        val greenModeEvent = baseEvent.copy(targetSoc = null, settingMode = "グリーンモード")
        
        calendarLogWriter.writeExecutionResult(greenModeEvent)

        val valuesSlot = slot<ContentValues>()
        verify { contentResolver.insert(CalendarContract.Events.CONTENT_URI, capture(valuesSlot)) }
        
        val description = valuesSlot.captured.getAsString(CalendarContract.Events.DESCRIPTION)
        assertTrue("Description should explicitly mention no deep night charging", description.contains("(深夜充電なし)"))
    }

    @Test
    fun `buildDescription handles missing data (e-g- preExecSoc=null) gracefully`() = runBlocking {
        // T020.5 部分データ説明欄
        setupMockCalendarId()
        setupMockDuplicateCheck(isDuplicate = false)
        every { contentResolver.insert(CalendarContract.Events.CONTENT_URI, any()) } returns Uri.parse("content://dummy/4")

        val missingDataEvent = baseEvent.copy(preExecSoc = null, preExecMode = null, shortwaveRadiationSum = null)
        
        calendarLogWriter.writeExecutionResult(missingDataEvent)

        val valuesSlot = slot<ContentValues>()
        verify { contentResolver.insert(CalendarContract.Events.CONTENT_URI, capture(valuesSlot)) }
        
        val description = valuesSlot.captured.getAsString(CalendarContract.Events.DESCRIPTION)
        assertTrue("Should denote 取得不可 for missing soc", description.contains("取得SOC: 取得不可"))
        assertTrue("Should denote 取得不可 for missing mode", description.contains("運転モード: 取得不可"))
        assertTrue("Should denote 取得不可 for missing radiation", description.contains("予想日射量: 取得不可"))
    }
}
