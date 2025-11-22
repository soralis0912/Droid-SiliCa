package org.soralis.droidsilica.controller.tab

import android.content.Context
import org.soralis.droidsilica.model.TabContent
import org.soralis.droidsilica.util.HistoryLogger

class HistoryController {
    fun getContent(): TabContent = TabContent(
        key = "history",
        title = "History"
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
