package org.soralis.droidsillica.controller.tab

import org.soralis.droidsillica.model.TabContent

class ManualController {
    fun getContent(): TabContent = TabContent(
        key = "manual",
        title = "Manual",
        description = "Step-by-step procedures and reference documentation for operators.",
        actions = listOf(
            "Search chapters with the toolbar action.",
            "Bookmark frequently used procedures for quick recall.",
            "Share manual excerpts with the support team."
        ),
        requiresExpert = true
    )
}
