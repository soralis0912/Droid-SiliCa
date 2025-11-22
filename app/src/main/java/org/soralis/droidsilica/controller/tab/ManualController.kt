package org.soralis.droidsilica.controller.tab

import org.soralis.droidsilica.model.TabContent

class ManualController {
    fun getContent(): TabContent = TabContent(
        key = "manual",
        title = "Manual",
        requiresExpert = true
    )
}
