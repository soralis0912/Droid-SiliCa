package org.soralis.droidsilica.controller

import org.soralis.droidsilica.controller.tab.HistoryController
import org.soralis.droidsilica.controller.tab.ManualController
import org.soralis.droidsilica.controller.tab.ReadController
import org.soralis.droidsilica.controller.tab.WriteController
import org.soralis.droidsilica.model.TabContent

/**
 * Acts as the Controller by exposing immutable tab models to the view layer.
 */
class TabController {

    private val tabContents = listOf(
        ReadController().getContent(),
        WriteController().getContent(),
        ManualController().getContent(),
        HistoryController().getContent()
    )

    fun getTabs(isExpertMode: Boolean): List<TabContent> = tabContents.filter {
        !it.requiresExpert || isExpertMode
    }
}
