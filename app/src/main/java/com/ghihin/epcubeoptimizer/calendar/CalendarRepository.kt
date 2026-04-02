package com.ghihin.epcubeoptimizer.calendar

import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(val title: String, val startTimeMillis: Long)

@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getEventsForNextNDays(days: Int): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()
        val startMillis = System.currentTimeMillis()
        val endMillis = startMillis + days * 24 * 60 * 60 * 1000L

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)

                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIndex) ?: ""
                    val start = cursor.getLong(startIndex)
                    events.add(CalendarEvent(title, start))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 権限がない場合などは空リストを返す（Safety First）
        }
        return@withContext events
    }
}
