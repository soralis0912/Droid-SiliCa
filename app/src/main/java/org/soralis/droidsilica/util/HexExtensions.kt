package org.soralis.droidsilica.util

import java.util.Locale

fun ByteArray.toLegacyHexString(): String =
    if (isEmpty()) {
        ""
    } else {
        joinToString(" ") { byte ->
            String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
        }
    }
