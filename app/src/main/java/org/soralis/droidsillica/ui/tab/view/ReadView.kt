package org.soralis.droidsillica.ui.tab.view

import org.soralis.droidsillica.R
import org.soralis.droidsillica.databinding.FragmentTabReadBinding
import org.soralis.droidsillica.model.TabContent

class ReadView(private val readBinding: FragmentTabReadBinding) :
    BaseTabView(readBinding.toTabUiComponents()) {

    init {
        readBinding.readStartButton.setOnClickListener { handleStart() }
        readBinding.readStopButton.setOnClickListener { handleStop() }
    }

    override fun render(content: TabContent) {
        super.render(content)
        readBinding.readStopButton.isEnabled = false
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
        readBinding.readStopButton.isEnabled = true
    }

    private fun handleStop() {
        readBinding.readResultText.text =
            readBinding.root.context.getString(R.string.read_result_stopped)
        readBinding.readStopButton.isEnabled = false
    }

    private fun selectedOptions(): List<String> {
        val options = listOf(
            readBinding.readOptionLastError,
            readBinding.readOptionBlockE0,
            readBinding.readOptionBlockE1
        )
        return options.filter { it.isChecked }.map { it.text.toString() }
    }
}
