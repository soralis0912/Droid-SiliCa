package org.soralis.droidsillica.controller.tab

import org.soralis.droidsillica.model.TabContent

class ReadController {
    fun getContent(): TabContent = TabContent(
        key = "read",
        title = "Read",
        description = "Monitor inbound data streams and verify the most recent payloads.",
        actions = listOf(
            "Refresh the feed to pull new entries.",
            "Tap an item to open the detail inspector.",
            "Use long press to bookmark frequently referenced data."
        )
    )
}
