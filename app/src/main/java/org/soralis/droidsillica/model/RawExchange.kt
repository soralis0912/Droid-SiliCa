package org.soralis.droidsillica.model

import org.soralis.droidsillica.util.toLegacyHexString

data class RawExchange(
    val label: String,
    val request: ByteArray,
    val response: ByteArray
) {
    val formattedRequest: String = request.toLegacyHexString()
    val formattedResponse: String = response.toLegacyHexString()
}
