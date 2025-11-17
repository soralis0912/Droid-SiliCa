package org.soralis.droidsillica.ui.tab.view

import org.soralis.droidsillica.R
import org.soralis.droidsillica.databinding.FragmentTabReadBinding
import org.soralis.droidsillica.model.TabContent

class ReadView(
    private val readBinding: FragmentTabReadBinding,
    private val callbacks: Callbacks? = null
) : BaseTabView(readBinding.toTabUiComponents()) {

    interface Callbacks {
        fun onStartReading(selectedOptions: List<String>)
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
        val selected = selectedOptions()
        if (selected.isEmpty()) {
            readBinding.readResultText.text =
                readBinding.root.context.getString(R.string.read_result_need_selection)
            return
        }
        readBinding.readResultText.text = readBinding.root.context.getString(
            R.string.read_result_starting,
            selected.joinToString(", ")
        )
        setReadingInProgress(true)
        callbacks?.onStartReading(selected)
    }

    private fun handleStop() {
        readBinding.readResultText.text =
            readBinding.root.context.getString(R.string.read_result_stopped)
        setReadingInProgress(false)
        callbacks?.onStopReading()
    }

    private fun selectedOptions(): List<String> {
        val options = listOf(
            readBinding.readOptionLastError,
            readBinding.readOptionBlockE0,
            readBinding.readOptionBlockE1
        )
        return options.filter { it.isChecked }.map { it.text.toString() }
    }

    fun setReadingInProgress(inProgress: Boolean) {
        readBinding.readStartButton.isEnabled = !inProgress
        readBinding.readStopButton.isEnabled = inProgress
    }

    fun showResultMessage(message: CharSequence) {
        readBinding.readResultText.text = message
    }
}
