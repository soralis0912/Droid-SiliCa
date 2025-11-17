package org.soralis.droidsillica.ui.tab.view

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import org.soralis.droidsillica.databinding.FragmentTabBinding
import org.soralis.droidsillica.model.TabContent

open class BaseTabView(
    protected val binding: FragmentTabBinding
) : TabView {

    override fun render(content: TabContent) {
        binding.tabTitle.text = content.title
        binding.tabDescription.text = content.description
        renderActions(content.actions)
    }

    protected fun renderActions(actions: List<String>) {
        val container = binding.actionList
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        actions.forEachIndexed { index, action ->
            val actionView =
                inflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView
            actionView.text = "${index + 1}. $action"
            container.addView(actionView)
        }
        container.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE
    }
}
