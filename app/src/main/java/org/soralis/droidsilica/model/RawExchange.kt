package org.soralis.droidsilica.model

import org.soralis.droidsilica.util.toLegacyHexString

data class RawExchange(
    val label: String,
    val request: ByteArray,
    val response: ByteArray
) {
    val formattedRequest: String = request.toLegacyHexString()
    val formattedResponse: String = response.toLegacyHexString()
}
