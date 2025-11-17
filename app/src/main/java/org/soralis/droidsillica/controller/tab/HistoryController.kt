package org.soralis.droidsillica.controller.tab

import org.soralis.droidsillica.model.TabContent

class HistoryController {
    fun getContent(): TabContent = TabContent(
        key = "history",
        title = "History",
        description = "Audit trail of user edits and automated events with timestamps.",
        actions = listOf(
            "Filter by user, action type, or time range.",
            "Export a snapshot for compliance review.",
            "Restore a previous state after validation."
        )
    )
}
