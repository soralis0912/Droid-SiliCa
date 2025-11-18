package org.soralis.droidsillica.util

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.soralis.droidsillica.controller.tab.ReadController
import org.soralis.droidsillica.controller.tab.WriteController
import org.soralis.droidsillica.model.RawExchange

/**
 * Persists read/write operations to a JSON file so we can inspect past activity from the History tab.
 */
object HistoryLogger {
    private const val HISTORY_FILE_NAME = "history_log.json"
    private const val MAX_ENTRIES = 200
    private const val OPERATION_READ = "read"
    private const val OPERATION_WRITE = "write"
    private const val STATUS_SUCCESS = "success"
    private const val STATUS_ERROR = "error"
    private const val STATUS_CANCELLED = "cancelled"
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    fun logReadSuccess(
        context: Context,
        result: ReadController.ReadResult,
        requestedBlocks: List<Int>? = null,
        readBlocks: Boolean = true,
        resultMessage: String,
        rawResult: String
    ) {
        val entry = HistoryLogEntry(
            timestamp = System.currentTimeMillis(),
            operation = OPERATION_READ,
            status = STATUS_SUCCESS,
            summary = "Read success (${result.formattedIdm})",
            data = JSONObject().apply {
                put("idm", result.formattedIdm)
                put("pmm", result.formattedPmm)
                put("systemCodes", result.systemCodes.toHexArray())
                put("serviceCodes", result.serviceCodes.toHexArray())
                put("lastErrorCommand", result.formattedCommand)
                put("statusFlag1", result.statusFlag1)
                put("statusFlag2", result.statusFlag2)
                put("blockData", result.blockData.toLegacyHexString())
                requestedBlocks?.let { put("requestedBlocks", it.toDecimalArray()) }
                put("readBlocks", readBlocks)
                put("rawLog", result.rawExchanges.toJsonArray())
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        persist(context, entry)
    }

    fun logReadError(
        context: Context,
        message: String,
        rawLog: List<RawExchange>,
        requestedBlocks: List<Int>? = null,
        readBlocks: Boolean = true,
        resultMessage: String,
        rawResult: String
    ) {
        val entry = HistoryLogEntry(
            timestamp = System.currentTimeMillis(),
            operation = OPERATION_READ,
            status = STATUS_ERROR,
            summary = "Read error: $message",
            data = JSONObject().apply {
                put("message", message)
                requestedBlocks?.let { put("requestedBlocks", it.toDecimalArray()) }
                put("readBlocks", readBlocks)
                put("rawLog", rawLog.toJsonArray())
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        persist(context, entry)
    }

    fun logReadCancelled(
        context: Context,
        requestedBlocks: List<Int>? = null,
        readBlocks: Boolean = true,
        resultMessage: String,
        rawResult: String
    ) {
        val entry = HistoryLogEntry(
            timestamp = System.currentTimeMillis(),
            operation = OPERATION_READ,
            status = STATUS_CANCELLED,
            summary = "Read cancelled",
            data = JSONObject().apply {
                requestedBlocks?.let { put("requestedBlocks", it.toDecimalArray()) }
                put("readBlocks", readBlocks)
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        persist(context, entry)
    }

    fun logWriteSuccess(
        context: Context,
        request: WriteController.WriteRequest?,
        rawLog: List<RawExchange>,
        resultMessage: String,
        rawResult: String
    ) {
        val (summary, details) = request.toSummary()
        val entry = HistoryLogEntry(
            timestamp = System.currentTimeMillis(),
            operation = OPERATION_WRITE,
            status = STATUS_SUCCESS,
            summary = summary ?: "Write success",
            data = JSONObject().apply {
                details?.let { put("request", it) }
                put("rawLog", rawLog.toJsonArray())
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        persist(context, entry)
    }

    fun logWriteError(
        context: Context,
        message: String,
        request: WriteController.WriteRequest?,
        rawLog: List<RawExchange>,
        resultMessage: String,
        rawResult: String
    ) {
        val (_, details) = request.toSummary()
        val entry = HistoryLogEntry(
            timestamp = System.currentTimeMillis(),
            operation = OPERATION_WRITE,
            status = STATUS_ERROR,
            summary = "Write error: $message",
            data = JSONObject().apply {
                put("message", message)
                details?.let { put("request", it) }
                put("rawLog", rawLog.toJsonArray())
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        persist(context, entry)
    }

    fun logWriteCancelled(
        context: Context,
        request: WriteController.WriteRequest?,
        resultMessage: String,
        rawResult: String
    ) {
        val (_, details) = request.toSummary()
        val entry = HistoryLogEntry(
            timestamp = System.currentTimeMillis(),
            operation = OPERATION_WRITE,
            status = STATUS_CANCELLED,
            summary = "Write cancelled",
            data = JSONObject().apply {
                details?.let { put("request", it) }
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        persist(context, entry)
    }

    fun loadEntries(context: Context): List<HistoryLogEntry> {
        val appContext = context.applicationContext
        val entries = mutableListOf<HistoryLogEntry>()
        synchronized(lock) {
            val file = File(appContext.filesDir, HISTORY_FILE_NAME)
            val existing = readExistingEntries(file)
            for (index in 0 until existing.length()) {
                val entryObject = existing.optJSONObject(index) ?: continue
                HistoryLogEntry.fromJson(entryObject)?.let { entries.add(it) }
            }
        }
        return entries
    }

    private fun persist(context: Context, entry: HistoryLogEntry) {
        val appContext = context.applicationContext
        executor.execute {
            synchronized(lock) {
                val file = File(appContext.filesDir, HISTORY_FILE_NAME)
                val existing = readExistingEntries(file)
                val trimmed = JSONArray()
                val startIndex = max(0, existing.length() - (MAX_ENTRIES - 1))
                for (index in startIndex until existing.length()) {
                    trimmed.put(existing.get(index))
                }
                trimmed.put(entry.toJson())
                try {
                    file.writeText(trimmed.toString())
                } catch (_: IOException) {
                    // Ignore logging errors to avoid impacting the UI flow.
                }
            }
        }
    }

    private fun readExistingEntries(file: File): JSONArray {
        if (!file.exists()) return JSONArray()
        val contents = try {
            file.readText()
        } catch (_: IOException) {
            ""
        }
        if (contents.isBlank()) return JSONArray()
        return try {
            JSONArray(contents)
        } catch (_: JSONException) {
            JSONArray()
        }
    }

    private fun List<RawExchange>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { exchange ->
            array.put(
                JSONObject().apply {
                    put("label", exchange.label)
                    put("request", exchange.request.toLegacyHexString())
                    put("response", exchange.response.toLegacyHexString())
                }
            )
        }
        return array
    }

    private fun List<Int>.toHexArray(): JSONArray {
        val array = JSONArray()
        forEach { code ->
            array.put(String.format(Locale.US, "0x%04X", code))
        }
        return array
    }

    private fun List<Int>.toDecimalArray(): JSONArray {
        val array = JSONArray()
        forEach { number ->
            array.put(number)
        }
        return array
    }

    private fun WriteController.WriteRequest?.toSummary(): Pair<String?, JSONObject?> {
        if (this == null) {
            return null to null
        }
        return when (this) {
            is WriteController.WriteRequest.Idm -> {
                val json = JSONObject().apply {
                    put("type", "idm")
                    put("idm", this@toSummary.idm.toLegacyHexString())
                    this@toSummary.pmm?.let { put("pmm", it.toLegacyHexString()) }
                }
                "Write success (IDm/PMm)" to json
            }

            is WriteController.WriteRequest.SystemCodes -> {
                val json = JSONObject().apply {
                    put("type", "systemCodes")
                    put("codes", this@toSummary.codes.toHexArray())
                }
                "Write success (system codes)" to json
            }

            is WriteController.WriteRequest.ServiceCodes -> {
                val json = JSONObject().apply {
                    put("type", "serviceCodes")
                    put("codes", this@toSummary.codes.toHexArray())
                }
                "Write success (service codes)" to json
            }

            is WriteController.WriteRequest.RawBlock -> {
                val json = JSONObject().apply {
                    put("type", "rawBlock")
                    put("blockNumber", this@toSummary.blockNumber)
                    put("data", this@toSummary.data.toLegacyHexString())
                }
                "Write success (raw block ${this.blockNumber})" to json
            }
        }
    }

    data class HistoryLogEntry(
        val timestamp: Long,
        val operation: String,
        val status: String,
        val summary: String,
        val data: JSONObject
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("timestamp", timestamp)
            put("operation", operation)
            put("status", status)
            put("summary", summary)
            put("data", data)
        }

        companion object {
            fun fromJson(json: JSONObject): HistoryLogEntry? {
                if (!json.has("timestamp") || !json.has("operation") || !json.has("status")) {
                    return null
                }
                val data = json.optJSONObject("data") ?: JSONObject()
                return HistoryLogEntry(
                    timestamp = json.optLong("timestamp"),
                    operation = json.optString("operation"),
                    status = json.optString("status"),
                    summary = json.optString("summary"),
                    data = data
                )
            }
        }
    }

}
