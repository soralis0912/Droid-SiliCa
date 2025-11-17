package org.soralis.droidsillica.controller.tab

import android.nfc.Tag
import android.nfc.tech.NfcF
import java.io.IOException
import java.util.Locale
import org.soralis.droidsillica.model.TabContent

/**
 * Mirrors the behavior of the legacy read.py script by issuing a "Read Without Encryption" (0x06)
 * command against the SiliCa system service (0xFFFF) and exposing the parsed last error command.
 */
class ReadController {

    data class LastErrorCommandResult(
        val idm: ByteArray,
        val statusFlag1: Int,
        val statusFlag2: Int,
        val blockData: ByteArray,
        val lastErrorCommand: ByteArray
    ) {
        val formattedCommand: String = lastErrorCommand.toLegacyHexString()
    }

    class ReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun getContent(): TabContent = TabContent(
        key = KEY,
        title = "Read",
        description = "Read the SiliCa system block via NFC-F and inspect the last error command.",
        actions = listOf(
            "Tap a compatible SiliCa card; we wrap the 0x06 Read Without Encryption command.",
            "Blocks 0xE0/0xE1 are retrieved from service 0xFFFF and parsed like the CLI tool.",
            "The bytes are exposed as uppercase hex (\"AA BB CC\") for troubleshooting."
        )
    )

    /**
     * Connects to a FeliCa tag and retrieves the system block that contains the last error command.
     */
    @Throws(ReadException::class)
    fun readLastErrorCommand(tag: Tag, timeoutMillis: Int = DEFAULT_TIMEOUT_MS): LastErrorCommandResult {
        val nfcF = NfcF.get(tag) ?: throw ReadException("Tag is not a FeliCa/NfcF tag")
        try {
            nfcF.connect()
            nfcF.timeout = timeoutMillis
            val response = nfcF.transceive(buildReadCommand(tag.id))
            return parseReadResponse(response)
        } catch (e: IOException) {
            throw ReadException("Unable to read the SiliCa system block", e)
        } finally {
            try {
                nfcF.close()
            } catch (_: IOException) {
                // Ignored – the link is already closed/closing.
            }
        }
    }

    private fun buildReadCommand(idm: ByteArray): ByteArray {
        require(idm.size == IDM_LENGTH) { "Unexpected IDm length: ${idm.size}" }
        val commandSize = 1 + 1 + idm.size + 1 + SERVICE_CODE_LIST.size * SERVICE_CODE_SIZE + 1 + BLOCK_LIST.size
        val buffer = ByteArray(commandSize)
        var offset = 0
        buffer[offset++] = commandSize.toByte()
        buffer[offset++] = COMMAND_READ
        System.arraycopy(idm, 0, buffer, offset, idm.size)
        offset += idm.size
        buffer[offset++] = SERVICE_CODE_LIST.size.toByte()
        SERVICE_CODE_LIST.forEach { service ->
            buffer[offset++] = (service and 0xFF).toByte()
            buffer[offset++] = ((service shr 8) and 0xFF).toByte()
        }
        val blockCount = BLOCK_LIST.size / BLOCK_LIST_ELEMENT_SIZE
        buffer[offset++] = blockCount.toByte()
        BLOCK_LIST.copyInto(buffer, offset)
        return buffer
    }

    private fun parseReadResponse(response: ByteArray): LastErrorCommandResult {
        if (response.size < RESPONSE_HEADER_SIZE) {
            throw ReadException("Response too short: ${response.size} bytes")
        }
        val responseCode = response[1]
        if (responseCode != RESPONSE_READ) {
            throw ReadException(
                String.format(
                    Locale.US,
                    "Unexpected response code 0x%02X",
                    responseCode.toPositiveInt()
                )
            )
        }
        val idm = response.copyOfRange(2, 2 + IDM_LENGTH)
        val statusFlag1 = response[10].toPositiveInt()
        val statusFlag2 = response[11].toPositiveInt()
        if (statusFlag1 != 0 || statusFlag2 != 0) {
            throw ReadException("FeliCa error status: $statusFlag1, $statusFlag2")
        }
        val blockCount = response[12].toPositiveInt()
        val payloadOffset = RESPONSE_HEADER_SIZE
        val payloadLength = blockCount * BLOCK_SIZE
        if (response.size < payloadOffset + payloadLength) {
            throw ReadException("Incomplete block payload (expected $payloadLength bytes)")
        }
        val blockData = response.copyOfRange(payloadOffset, payloadOffset + payloadLength)
        val commandLength = if (blockData.isNotEmpty()) blockData[0].toPositiveInt() else 0
        val maxCommandSize = if (blockData.size > 1) blockData.size - 1 else 0
        val safeLength = commandLength.coerceIn(0, maxCommandSize)
        val lastCommand = if (safeLength > 0) {
            blockData.copyOfRange(1, 1 + safeLength)
        } else {
            ByteArray(0)
        }
        return LastErrorCommandResult(
            idm = idm,
            statusFlag1 = statusFlag1,
            statusFlag2 = statusFlag2,
            blockData = blockData,
            lastErrorCommand = lastCommand
        )
    }

    private fun Byte.toPositiveInt(): Int = toInt() and 0xFF

    companion object {
        private const val KEY = "read"
        private const val DEFAULT_TIMEOUT_MS = 1000
        private const val IDM_LENGTH = 8
        private const val SERVICE_CODE_SIZE = 2
        private const val BLOCK_LIST_ELEMENT_SIZE = 2
        private const val BLOCK_SIZE = 16
        private const val RESPONSE_HEADER_SIZE = 13
        private val COMMAND_READ = 0x06.toByte()
        private val RESPONSE_READ = 0x07.toByte()
        private val SERVICE_CODE_LIST = intArrayOf(0xFFFF)
        private val BLOCK_LIST = byteArrayOf(
            0x80.toByte(), 0xE0.toByte(),
            0x80.toByte(), 0xE1.toByte()
        )
    }
}

private fun ByteArray.toLegacyHexString(): String =
    joinToString(" ") { byte -> String.format(Locale.US, "%02X", byte.toInt() and 0xFF) }
