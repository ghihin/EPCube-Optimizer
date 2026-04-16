package com.ghihin.epcubeoptimizer.calendar

import android.content.ContentUris
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
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startMillis)
        ContentUris.appendId(uriBuilder, endMillis)

        try {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)

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
