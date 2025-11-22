package org.soralis.droidsilica.ui.tab.view

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.soralis.droidsilica.databinding.FragmentTabHistoryBinding
import org.soralis.droidsilica.databinding.FragmentTabManualBinding
import org.soralis.droidsilica.databinding.FragmentTabReadBinding
import org.soralis.droidsilica.databinding.FragmentTabWriteBinding
import org.soralis.droidsilica.model.TabContent

open class BaseTabView(
    private val ui: TabUiComponents
) : TabView {

    override fun render(content: TabContent) {
        ui.tabTitle.text = content.title
        ui.tabDescription.visibility = View.GONE
        ui.actionList.visibility = View.GONE
    }
}

data class TabUiComponents(
    val tabTitle: TextView,
    val tabDescription: TextView,
    val actionList: LinearLayout
)

fun FragmentTabReadBinding.toTabUiComponents(): TabUiComponents =
    TabUiComponents(tabTitle, tabDescription, actionList)

fun FragmentTabWriteBinding.toTabUiComponents(): TabUiComponents =
    TabUiComponents(tabTitle, tabDescription, actionList)

fun FragmentTabManualBinding.toTabUiComponents(): TabUiComponents =
    TabUiComponents(tabTitle, tabDescription, actionList)

fun FragmentTabHistoryBinding.toTabUiComponents(): TabUiComponents =
    TabUiComponents(tabTitle, tabDescription, actionList)
