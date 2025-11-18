package org.soralis.droidsillica.ui.tab.view

import androidx.annotation.StringRes
import org.soralis.droidsillica.R
import org.soralis.droidsillica.databinding.FragmentTabReadBinding
import org.soralis.droidsillica.model.TabContent

class ReadView(
    private val readBinding: FragmentTabReadBinding,
    private val callbacks: Callbacks? = null
) : BaseTabView(readBinding.toTabUiComponents()) {

    private var exportEnabled = false
    private var readingInProgress = false
    private var fullDumpButtonEnabled = true
    private var fullDumpResetEnabled = false

    interface Callbacks {
        fun onStartReading(blockNumbers: List<Int>, readLastErrorCommand: Boolean)
        fun onStopReading()
        fun onExportSystemBlocks()
        fun onFullDumpRequested()
        fun onFullDumpReset()
    }

    init {
        readBinding.readStartButton.setOnClickListener { handleStart() }
        readBinding.readStopButton.setOnClickListener { handleStop() }
        readBinding.readExportHexButton.setOnClickListener { callbacks?.onExportSystemBlocks() }
        readBinding.readFullDumpButton.setOnClickListener { handleFullDump() }
        readBinding.readFullDumpResetButton.setOnClickListener { callbacks?.onFullDumpReset() }
    }

    override fun render(content: TabContent) {
        super.render(content)
        setReadingInProgress(false)
        readBinding.readResultText.text =
            readBinding.root.context.getString(R.string.read_result_placeholder)
        readBinding.readRawLogText.text =
            readBinding.root.context.getString(R.string.read_raw_log_placeholder)
        setExportEnabled(false)
        setFullDumpButtonText(R.string.read_action_full_dump)
        setFullDumpButtonEnabled(true)
        setFullDumpResetEnabled(false)
    }

    private fun handleStart() {
        val shouldReadLastError = readBinding.readLastErrorCheckbox.isChecked
        val manualBlocks = if (readBinding.readIncludeBlocksCheckbox.isChecked) {
            parseManualBlockInput()?.distinct() ?: return
        } else {
            emptyList()
        }
        val shouldReadBlocks = shouldReadLastError || manualBlocks.isNotEmpty()
        val context = readBinding.root.context
        readBinding.readResultText.text = when {
            manualBlocks.isNotEmpty() -> context.getString(
                R.string.read_result_starting,
                manualBlocks.joinToString(", ")
            )
            shouldReadLastError -> context.getString(R.string.read_result_starting_default)
            else -> context.getString(
                R.string.read_result_starting_basic
            )
        }
        readBinding.readRawLogText.text = context.getString(R.string.read_raw_log_placeholder)
        setReadingInProgress(true)
        callbacks?.onStartReading(manualBlocks, shouldReadBlocks)
    }

    private fun handleStop() {
        readBinding.readResultText.text =
            readBinding.root.context.getString(R.string.read_result_stopped)
        setReadingInProgress(false)
        callbacks?.onStopReading()
    }

    private fun parseManualBlockInput(): List<Int>? {
        val text = readBinding.readBlockInput.text?.toString().orEmpty()
        if (text.isBlank()) {
            return emptyList()
        }
        val manualBlocks = mutableListOf<Int>()
        val tokens = text.split(',', ' ', '\n', '\t')
        tokens.forEach { token ->
            val value = token.trim()
            if (value.isEmpty()) return@forEach
            val blockNumber = value.toIntOrNull()
            if (blockNumber == null || blockNumber !in 0..255) {
                readBinding.readResultText.text =
                    readBinding.root.context.getString(R.string.read_block_input_invalid, value)
                return null
            }
            manualBlocks += blockNumber
        }
        return manualBlocks
    }

    fun setReadingInProgress(inProgress: Boolean) {
        readingInProgress = inProgress
        readBinding.readStartButton.isEnabled = !inProgress
        readBinding.readStopButton.isEnabled = inProgress
        updateFullDumpControls()
        updateExportButtonState()
    }

    fun setExportEnabled(enabled: Boolean) {
        exportEnabled = enabled
        updateExportButtonState()
    }

    private fun updateExportButtonState() {
        readBinding.readExportHexButton.isEnabled = exportEnabled && !readingInProgress
    }

    private fun updateFullDumpControls() {
        readBinding.readFullDumpButton.isEnabled = fullDumpButtonEnabled && !readingInProgress
        readBinding.readFullDumpResetButton.isEnabled = fullDumpResetEnabled && !readingInProgress
    }

    private fun handleFullDump() {
        callbacks?.onFullDumpRequested()
    }

    fun setFullDumpButtonText(@StringRes textRes: Int, vararg args: Any) {
        readBinding.readFullDumpButton.text =
            readBinding.root.context.getString(textRes, *args)
    }

    fun setFullDumpButtonText(text: CharSequence) {
        readBinding.readFullDumpButton.text = text
    }

    fun setFullDumpButtonEnabled(enabled: Boolean) {
        fullDumpButtonEnabled = enabled
        updateFullDumpControls()
    }

    fun setFullDumpResetEnabled(enabled: Boolean) {
        fullDumpResetEnabled = enabled
        updateFullDumpControls()
    }

    fun showResultMessage(message: CharSequence) {
        readBinding.readResultText.text = message
    }

    fun showRawLog(message: CharSequence) {
        readBinding.readRawLogText.text = message
    }
}
