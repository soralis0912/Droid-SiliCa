package org.soralis.droidsilica.ui.tab.view

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.soralis.droidsilica.R
import org.soralis.droidsilica.databinding.FragmentTabHistoryBinding
import org.soralis.droidsilica.databinding.ItemHistoryEntryBinding
import org.soralis.droidsilica.util.HistoryLogger

class HistoryView(
    private val binding: FragmentTabHistoryBinding,
    private val callbacks: Callbacks
) : BaseTabView(binding.toTabUiComponents()) {

    interface Callbacks {
        fun onDeleteEntry(timestamp: Long)
        fun onClearHistory()
        fun onExportEntry(timestamp: Long)
    }

    init {
        binding.buttonClearHistory.setOnClickListener {
            callbacks.onClearHistory()
        }
    }

    fun renderHistory(
        entries: List<HistoryLogger.HistoryLogEntry>,
        exportableTimestamps: Set<Long>,
        showExportButtons: Boolean
    ) {
        val container = binding.actionList
        val inflater = LayoutInflater.from(container.context)
        container.removeAllViews()
        val hasEntries = entries.isNotEmpty()
        binding.buttonClearHistory.isEnabled = hasEntries
        if (!hasEntries) {
            val emptyView =
                inflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView
            emptyView.text = container.context.getString(R.string.history_empty_state)
            container.addView(emptyView)
            container.visibility = View.VISIBLE
            return
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        entries.asReversed().forEach { entry ->
            val entryBinding = ItemHistoryEntryBinding.inflate(inflater, container, false)
            val operationLabel = formatOperation(entry.operation)
            val statusLabel = formatStatus(entry.status)
            val timestamp = formatter.format(Date(entry.timestamp))
            entryBinding.historyEntryTitle.text = binding.root.context.getString(
                R.string.history_entry_title_format,
                operationLabel,
                statusLabel,
                timestamp
            )
            val resultDetail = entry.data.optString("resultMessage").ifBlank {
                entry.summary.ifBlank {
                    binding.root.context.getString(R.string.history_empty_summary_fallback)
                }
            }
            val rawDetail = entry.data.optString("rawResult").ifBlank {
                binding.root.context.getString(R.string.history_empty_raw_fallback)
            }
            entryBinding.historyEntryResult.text = resultDetail
            entryBinding.historyEntryRaw.text = rawDetail
            val canExport = exportableTimestamps.contains(entry.timestamp) && showExportButtons
            entryBinding.buttonExportHistory.isVisible = canExport
            if (canExport) {
                entryBinding.buttonExportHistory.setOnClickListener {
                    callbacks.onExportEntry(entry.timestamp)
                }
            }
            entryBinding.buttonDeleteHistory.setOnClickListener {
                callbacks.onDeleteEntry(entry.timestamp)
            }
            container.addView(entryBinding.root)
        }
        container.visibility = View.VISIBLE
    }

    private fun formatOperation(operation: String): String {
        val context = binding.root.context
        return when (operation.lowercase(Locale.US)) {
            "read" -> context.getString(R.string.history_operation_read)
            "write" -> context.getString(R.string.history_operation_write)
            else -> operation
        }
    }

    private fun formatStatus(status: String): String {
        val context = binding.root.context
        return when (status.lowercase(Locale.US)) {
            "success" -> context.getString(R.string.history_status_success)
            "error" -> context.getString(R.string.history_status_error)
            "cancelled" -> context.getString(R.string.history_status_cancelled)
            else -> status
        }
    }

}
