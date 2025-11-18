package org.soralis.droidsillica.controller.tab

import android.content.Context
import org.soralis.droidsillica.model.TabContent
import org.soralis.droidsillica.util.HistoryLogger

class HistoryController {
    fun getContent(): TabContent = TabContent(
        key = "history",
        title = "History",
        description = "View the most recent NFC read/write operations.",
        actions = emptyList()
    )

    fun getHistory(context: Context): List<HistoryLogger.HistoryLogEntry> =
        HistoryLogger.loadEntries(context)

    fun deleteHistoryEntry(context: Context, timestamp: Long) {
        HistoryLogger.deleteEntry(context, timestamp)
    }

    fun clearHistory(context: Context) {
        HistoryLogger.clearHistory(context)
    }

    fun getSystemBlockTimestamps(context: Context): Set<Long> =
        HistoryLogger.getSystemBlockTimestamps(context)
}
