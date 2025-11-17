package org.soralis.droidsillica.controller

import org.soralis.droidsillica.controller.tab.HistoryController
import org.soralis.droidsillica.controller.tab.ManualController
import org.soralis.droidsillica.controller.tab.ReadController
import org.soralis.droidsillica.controller.tab.WriteController
import org.soralis.droidsillica.model.TabContent

/**
 * Acts as the Controller by exposing immutable tab models to the view layer.
 */
class TabController {

    private val controllers = listOf(
        ReadController(),
        WriteController(),
        ManualController(),
        HistoryController()
    )

    fun getTabCount(): Int = controllers.size

    fun getTab(position: Int): TabContent = controllers[position].getContent()

    fun getTitle(position: Int): String = getTab(position).title
}
