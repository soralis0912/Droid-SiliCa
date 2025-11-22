package org.soralis.droidsilica.model

/**
 * Immutable model that describes the content for one tab in the MVC layout.
 */
data class TabContent(
    val key: String,
    val title: String,
    val requiresExpert: Boolean = false
)
