package org.soralis.droidsillica.ui.tab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import java.util.Locale
import org.soralis.droidsillica.R
import org.soralis.droidsillica.controller.tab.ReadController
import org.soralis.droidsillica.controller.tab.WriteController
import org.soralis.droidsillica.databinding.FragmentTabHistoryBinding
import org.soralis.droidsillica.databinding.FragmentTabManualBinding
import org.soralis.droidsillica.databinding.FragmentTabReadBinding
import org.soralis.droidsillica.databinding.FragmentTabWriteBinding
import org.soralis.droidsillica.model.TabContent
import org.soralis.droidsillica.model.RawExchange
import org.soralis.droidsillica.ui.tab.view.BaseTabView
import org.soralis.droidsillica.ui.tab.view.HistoryView
import org.soralis.droidsillica.ui.tab.view.ManualView
import org.soralis.droidsillica.ui.tab.view.ReadView
import org.soralis.droidsillica.ui.tab.view.TabView
import org.soralis.droidsillica.ui.tab.view.WriteView
import org.soralis.droidsillica.ui.tab.view.toTabUiComponents

class TabFragment : Fragment() {

    private var _readBinding: FragmentTabReadBinding? = null
    private var _writeBinding: FragmentTabWriteBinding? = null
    private var _manualBinding: FragmentTabManualBinding? = null
    private var _historyBinding: FragmentTabHistoryBinding? = null
    private var tabView: TabView? = null
    private var readView: ReadView? = null
    private var writeView: WriteView? = null
    private val readController = ReadController()
    private val writeController = WriteController()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val key = requireArguments().getString(ARG_KEY).orEmpty()
        return when (key) {
            KEY_READ -> FragmentTabReadBinding.inflate(inflater, container, false).also {
                _readBinding = it
            }.root
            KEY_WRITE -> FragmentTabWriteBinding.inflate(inflater, container, false).also {
                _writeBinding = it
            }.root
            KEY_MANUAL -> FragmentTabManualBinding.inflate(inflater, container, false).also {
                _manualBinding = it
            }.root
            KEY_HISTORY -> FragmentTabHistoryBinding.inflate(inflater, container, false).also {
                _historyBinding = it
            }.root
            else -> FragmentTabWriteBinding.inflate(inflater, container, false).also {
                _writeBinding = it
            }.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val key = args.getString(ARG_KEY).orEmpty()
        val title = args.getString(ARG_TITLE).orEmpty()
        val description = args.getString(ARG_DESCRIPTION).orEmpty()
        val actions = args.getStringArray(ARG_ACTIONS)?.toList().orEmpty()

        val content = TabContent(
            key = key,
            title = title,
            description = description,
            actions = actions
        )

        tabView = createTabView(content.key)
        tabView?.render(content)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabView = null
        _readBinding = null
        _writeBinding = null
        _manualBinding = null
        _historyBinding = null
        readController.stopReading()
        readView = null
        writeController.stopWriting()
        writeView = null
    }

    private fun createTabView(key: String): TabView {
        if (key != KEY_READ) {
            readView = null
        }
        if (key != KEY_WRITE) {
            writeView = null
        }
        return when (key) {
            KEY_READ -> ReadView(
                _readBinding ?: error("Missing read binding"),
                readCallbacks
            ).also { readView = it }
            KEY_WRITE -> WriteView(
                _writeBinding ?: error("Missing write binding"),
                writeCallbacks
            ).also { writeView = it }
            KEY_MANUAL -> ManualView(_manualBinding ?: error("Missing manual binding"))
            KEY_HISTORY -> HistoryView(_historyBinding ?: error("Missing history binding"))
            else -> BaseTabView(
                _writeBinding?.toTabUiComponents()
                    ?: _manualBinding?.toTabUiComponents()
                    ?: _historyBinding?.toTabUiComponents()
                    ?: _readBinding?.toTabUiComponents()
                    ?: error("No binding available for $key tab")
            )
        }
    }

    private val readCallbacks = object : ReadView.Callbacks {
        override fun onStartReading(blockNumbers: List<Int>, readLastErrorCommand: Boolean) {
            val activity = activity ?: return
            readController.startReading(activity, blockNumbers, readLastErrorCommand, readListener)
        }

        override fun onStopReading() {
            readController.stopReading()
        }
    }

    private val readListener = object : ReadController.Listener {
        override fun onWaitingForTag() {
            readView?.showResultMessage(getString(R.string.read_result_waiting_for_tag))
        }

        override fun onReadSuccess(result: ReadController.ReadResult) {
            val commandText = if (result.lastErrorCommand.isNotEmpty()) {
                result.formattedCommand
            } else {
                getString(R.string.read_result_no_blocks)
            }
            val rawLogText = formatRawLog(result.rawExchanges)
            val systemCodesText = formatCodeList(
                result.systemCodes,
                getString(R.string.read_result_no_system_codes)
            )
            val serviceCodesText = formatCodeList(
                result.serviceCodes,
                getString(R.string.read_result_no_service_codes)
            )
            readView?.showResultMessage(
                getString(
                    R.string.read_result_success,
                    result.formattedIdm,
                    result.formattedPmm,
                    systemCodesText,
                    serviceCodesText,
                    commandText,
                    rawLogText
                )
            )
            readView?.setReadingInProgress(false)
        }

        override fun onReadError(message: String) {
            readView?.showResultMessage(getString(R.string.read_result_error, message))
            readView?.setReadingInProgress(false)
        }

        override fun onReadingStopped() {
            readView?.setReadingInProgress(false)
        }

        override fun onNfcUnavailable() {
            readView?.showResultMessage(getString(R.string.read_result_no_nfc))
            readView?.setReadingInProgress(false)
        }
    }

    private val writeCallbacks = object : WriteView.Callbacks {
        override fun onStartWriting(request: WriteController.WriteRequest) {
            val activity = activity ?: return
            writeController.startWriting(activity, request, writeListener)
        }

        override fun onCancelWriting() {
            writeController.stopWriting()
        }
    }

    private val writeListener = object : WriteController.Listener {
        override fun onWaitingForTag() {
            writeView?.showResultMessage(getString(R.string.write_result_waiting))
        }

        override fun onWriteSuccess(result: WriteController.WriteResult) {
            val rawLogText = formatRawLog(result.rawExchanges)
            writeView?.showResultMessage(
                getString(
                    R.string.write_result_success_with_raw,
                    rawLogText
                )
            )
            writeView?.setWritingInProgress(false)
        }

        override fun onWriteError(message: String) {
            writeView?.showResultMessage(getString(R.string.write_result_error, message))
            writeView?.setWritingInProgress(false)
        }

        override fun onWriteStopped() {
            writeView?.showResultMessage(getString(R.string.write_result_cancelled))
            writeView?.setWritingInProgress(false)
        }

        override fun onNfcUnavailable() {
            writeView?.showResultMessage(getString(R.string.write_result_no_nfc))
            writeView?.setWritingInProgress(false)
        }
    }

    private fun formatCodeList(codes: List<Int>, emptyText: String): String {
        if (codes.isEmpty()) return emptyText
        return codes.joinToString(", ") { code ->
            String.format(Locale.US, "0x%04X", code)
        }
    }

    private fun formatRawLog(exchanges: List<RawExchange>): String {
        if (exchanges.isEmpty()) {
            return getString(R.string.raw_data_unavailable)
        }
        return exchanges.joinToString(separator = "\n\n") { exchange ->
            getString(
                R.string.raw_log_entry,
                exchange.label,
                exchange.formattedRequest.ifEmpty { getString(R.string.raw_data_unavailable) },
                exchange.formattedResponse.ifEmpty { getString(R.string.raw_data_unavailable) }
            )
        }
    }

    companion object {
        private const val ARG_KEY = "arg_key"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"
        private const val ARG_ACTIONS = "arg_actions"
        private const val KEY_READ = "read"
        private const val KEY_WRITE = "write"
        private const val KEY_MANUAL = "manual"
        private const val KEY_HISTORY = "history"

        fun newInstance(content: TabContent): TabFragment = TabFragment().apply {
            arguments = bundleOf(
                ARG_KEY to content.key,
                ARG_TITLE to content.title,
                ARG_DESCRIPTION to content.description,
                ARG_ACTIONS to content.actions.toTypedArray()
            )
        }
    }
}
