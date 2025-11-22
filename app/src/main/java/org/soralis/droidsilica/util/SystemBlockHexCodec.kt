package org.soralis.droidsilica.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemBlockHexCodec {

    private const val BLOCK_SIZE_BYTES = 16

    data class BlockPayload(val blockNumber: Int, val data: ByteArray)

    data class HexPayload(
        val idm: String?,
        val blocks: List<BlockPayload>
    )

    fun encode(
        idm: String,
        timestamp: Long,
        blocks: List<HistoryLogger.SystemBlockEntry.BlockChunk>
    ): String {
        val builder = StringBuilder()
        builder.appendLine("# Droid-SiliCa system block export")
        builder.appendLine("# Timestamp: ${formatTimestamp(timestamp)}")
        builder.appendLine("IDM: ${idm.uppercase(Locale.US)}")
        if (blocks.isNotEmpty()) {
            val blockList = blocks.joinToString(", ") { chunk ->
                String.format(Locale.US, "%02X", chunk.blockNumber and 0xFF)
            }
            builder.appendLine("Blocks: $blockList")
        }
        blocks.forEach { chunk ->
            val bytes = chunk.data.joinToString(" ") { byte ->
                String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
            }
            builder.appendLine(
                String.format(Locale.US, "%02X: %s", chunk.blockNumber and 0xFF, bytes)
            )
        }
        return builder.toString()
    }

    fun decode(raw: String): HexPayload? {
        val blocks = mutableListOf<BlockPayload>()
        var idm: String? = null
        val lines = raw.lineSequence()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("IDM", ignoreCase = true)) {
                idm = trimmed.substringAfter(':', "").trim().replace(" ", "")
                continue
            }
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex == -1) {
                continue
            }
            val label = trimmed.substring(0, colonIndex).trim()
            val value = trimmed.substring(colonIndex + 1).trim()
            if (label.equals("Blocks", ignoreCase = true)) {
                continue
            }
            val blockNumber = parseBlockNumber(label) ?: return null
            val bytes = parseHexBytes(value) ?: return null
            if (bytes.size != BLOCK_SIZE_BYTES) {
                return null
            }
            blocks += BlockPayload(blockNumber, bytes)
        }
        if (blocks.isEmpty()) {
            return null
        }
        return HexPayload(idm, blocks.toList())
    }

    private fun parseBlockNumber(label: String): Int? {
        return try {
            label.removePrefix("0x").removePrefix("0X").toInt(16)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseHexBytes(value: String): ByteArray? {
        val sanitized = value.replace(" ", "").replace("\t", "")
        if (sanitized.length % 2 != 0 || sanitized.isEmpty()) return null
        val bytes = ByteArray(sanitized.length / 2)
        var index = 0
        while (index < sanitized.length) {
            val byteString = sanitized.substring(index, index + 2)
            val byteValue = try {
                byteString.toInt(16)
            } catch (_: NumberFormatException) {
                return null
            }
            bytes[index / 2] = byteValue.toByte()
            index += 2
        }
        return bytes
    }

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
}
