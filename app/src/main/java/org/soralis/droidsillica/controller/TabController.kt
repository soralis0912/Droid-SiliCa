package org.soralis.droidsillica.controller

import org.soralis.droidsillica.controller.tab.ReadController
import org.soralis.droidsillica.controller.tab.WriteController
import org.soralis.droidsillica.model.TabContent

/**
 * Acts as the Controller by exposing immutable tab models to the view layer.
 */
class TabController {

    private val tabContents = listOf(
        ReadController().getContent(),
        WriteController().getContent()
    )

    fun getTabCount(): Int = tabContents.size

    fun getTab(position: Int): TabContent = tabContents[position]

    fun getTitle(position: Int): String = getTab(position).title
}
