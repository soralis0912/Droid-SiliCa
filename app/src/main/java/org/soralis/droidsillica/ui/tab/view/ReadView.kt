package org.soralis.droidsillica.ui.tab.view

import org.soralis.droidsillica.R
import org.soralis.droidsillica.databinding.FragmentTabReadBinding
import org.soralis.droidsillica.model.TabContent

class ReadView(
    private val readBinding: FragmentTabReadBinding,
    private val callbacks: Callbacks? = null
) : BaseTabView(readBinding.toTabUiComponents()) {

    interface Callbacks {
        fun onStartReading(blockNumbers: List<Int>, readLastErrorCommand: Boolean)
        fun onStopReading()
    }

    init {
        readBinding.readStartButton.setOnClickListener { handleStart() }
        readBinding.readStopButton.setOnClickListener { handleStop() }
    }

    override fun render(content: TabContent) {
        super.render(content)
        setReadingInProgress(false)
        readBinding.readResultText.text =
            readBinding.root.context.getString(R.string.read_result_placeholder)
    }

    private fun handleStart() {
        val shouldReadLastError = readBinding.readLastErrorCheckbox.isChecked
        val blockNumbers = if (shouldReadLastError) {
            selectedBlockNumbers() ?: return
        } else {
            emptyList()
        }
        val context = readBinding.root.context
        readBinding.readResultText.text = when {
            !shouldReadLastError -> context.getString(R.string.read_result_starting_basic)
            blockNumbers.isEmpty() -> context.getString(R.string.read_result_starting_default)
            else -> context.getString(
                R.string.read_result_starting,
                blockNumbers.joinToString(", ")
            )
        }
        setReadingInProgress(true)
        callbacks?.onStartReading(blockNumbers, shouldReadLastError)
    }

    private fun handleStop() {
        readBinding.readResultText.text =
            readBinding.root.context.getString(R.string.read_result_stopped)
        setReadingInProgress(false)
        callbacks?.onStopReading()
    }

    private fun selectedBlockNumbers(): List<Int>? {
        if (!readBinding.readIncludeBlocksCheckbox.isChecked) {
            return emptyList()
        }
        val manualBlocks = parseManualBlockInput() ?: return null
        return manualBlocks.distinct()
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
        readBinding.readStartButton.isEnabled = !inProgress
        readBinding.readStopButton.isEnabled = inProgress
    }

    fun showResultMessage(message: CharSequence) {
        readBinding.readResultText.text = message
    }
}
