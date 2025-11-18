package org.soralis.droidsillica.ui.tab.view

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.soralis.droidsillica.R
import org.soralis.droidsillica.databinding.FragmentTabHistoryBinding
import org.soralis.droidsillica.util.HistoryLogger

class HistoryView(
    private val binding: FragmentTabHistoryBinding
) : BaseTabView(binding.toTabUiComponents()) {

    fun renderHistory(entries: List<HistoryLogger.HistoryLogEntry>) {
        val container = binding.actionList
        val inflater = LayoutInflater.from(container.context)
        container.removeAllViews()
        if (entries.isEmpty()) {
            val emptyView =
                inflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView
            emptyView.text = container.context.getString(R.string.history_empty_state)
            container.addView(emptyView)
            container.visibility = View.VISIBLE
            return
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        entries.asReversed().forEach { entry ->
            val itemView = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
            val titleView = itemView.findViewById<TextView>(android.R.id.text1)
            val subtitleView = itemView.findViewById<TextView>(android.R.id.text2)
            val operationLabel = formatOperation(entry.operation)
            val statusLabel = formatStatus(entry.status)
            val timestamp = formatter.format(Date(entry.timestamp))
            titleView.text = binding.root.context.getString(
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
            subtitleView.text = binding.root.context.getString(
                R.string.history_entry_detail_format,
                resultDetail,
                rawDetail
            )
            container.addView(itemView)
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
