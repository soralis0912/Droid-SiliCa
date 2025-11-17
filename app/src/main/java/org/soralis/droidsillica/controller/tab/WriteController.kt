package org.soralis.droidsillica.controller.tab

import org.soralis.droidsillica.model.TabContent

class WriteController {
    fun getContent(): TabContent = TabContent(
        key = "write",
        title = "Write",
        description = "Compose outbound updates or broadcast a new command across devices.",
        actions = listOf(
            "Draft text or structured JSON before publishing.",
            "Validate the payload with the preview action.",
            "Send immediately or schedule the dispatch window."
        )
    )
}
