package org.soralis.droidsillica.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.soralis.droidsillica.controller.tab.ReadController
import org.soralis.droidsillica.controller.tab.WriteController
import org.soralis.droidsillica.model.RawExchange
import kotlin.text.Charsets

/**
 * Persists read/write operations to an on-device SQLite database so we can inspect past activity
 * from the History tab.
 */
object HistoryLogger {
    private const val DATABASE_NAME = "history_log.db"
    private const val DATABASE_VERSION = 2
    private const val TABLE_HISTORY = "history_entries"
    private const val TABLE_SYSTEM_BLOCKS = "system_blocks"
    private const val COLUMN_TIMESTAMP = "timestamp"
    private const val COLUMN_OPERATION = "operation"
    private const val COLUMN_STATUS = "status"
    private const val COLUMN_SUMMARY = "summary"
    private const val COLUMN_DATA = "data"
    private const val COLUMN_BLOCK_IDM = "idm"
    private const val COLUMN_BLOCK_NUMBERS = "block_numbers"
    private const val COLUMN_BLOCK_PAYLOAD = "payload"
    private const val MAX_ENTRIES = 200
    private const val MAX_SYSTEM_BLOCK_ENTRIES = 200
    private const val BLOCK_SIZE = 16
    private const val OPERATION_READ = "read"
    private const val OPERATION_WRITE = "write"
    private const val STATUS_SUCCESS = "success"
    private const val STATUS_ERROR = "error"
    private const val STATUS_CANCELLED = "cancelled"
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    @Volatile
    private var databaseHelper: HistoryDatabaseHelper? = null

    fun logReadSuccess(
        context: Context,
        result: ReadController.ReadResult,
        requestedBlocks: List<Int>? = null,
        readBlocks: Boolean = true,
        resultMessage: String,
        rawResult: String
    ): Long {
        val timestamp = System.currentTimeMillis()
        val entry = HistoryLogEntry(
            timestamp = timestamp,
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
                if (result.blockNumbers.isNotEmpty()) {
                    put("blockNumbers", result.blockNumbers.toDecimalArray())
                }
                put("readBlocks", readBlocks)
                put("rawLog", result.rawExchanges.toJsonArray())
                put("resultMessage", resultMessage)
                put("rawResult", rawResult)
            }
        )
        val blockEntry = if (result.blockData.isNotEmpty() && result.blockNumbers.isNotEmpty()) {
            SystemBlockEntry(
                timestamp = timestamp,
                idm = result.formattedIdm,
                blockNumbers = result.blockNumbers,
                blockData = result.blockData
            )
        } else {
            null
        }
        persist(context, entry, blockEntry)
        return timestamp
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
            val db = getHelper(appContext).readableDatabase
            val cursor = db.query(
                TABLE_HISTORY,
                arrayOf(
                    COLUMN_TIMESTAMP,
                    COLUMN_OPERATION,
                    COLUMN_STATUS,
                    COLUMN_SUMMARY,
                    COLUMN_DATA
                ),
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP ASC"
            )
            cursor.use {
                val timestampIndex = it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
                val operationIndex = it.getColumnIndexOrThrow(COLUMN_OPERATION)
                val statusIndex = it.getColumnIndexOrThrow(COLUMN_STATUS)
                val summaryIndex = it.getColumnIndexOrThrow(COLUMN_SUMMARY)
                val dataIndex = it.getColumnIndexOrThrow(COLUMN_DATA)
                while (it.moveToNext()) {
                    val data = parseData(it.getString(dataIndex))
                    entries.add(
                        HistoryLogEntry(
                            timestamp = it.getLong(timestampIndex),
                            operation = it.getString(operationIndex),
                            status = it.getString(statusIndex),
                            summary = it.getString(summaryIndex),
                            data = data
                        )
                    )
                }
            }
        }
        return entries
    }

    fun deleteEntry(context: Context, timestamp: Long) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val db = getHelper(appContext).writableDatabase
            db.delete(TABLE_HISTORY, "$COLUMN_TIMESTAMP = ?", arrayOf(timestamp.toString()))
        }
    }

    fun clearHistory(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val db = getHelper(appContext).writableDatabase
            db.delete(TABLE_HISTORY, null, null)
        }
    }

    private fun persist(context: Context, entry: HistoryLogEntry, blockEntry: SystemBlockEntry? = null) {
        val appContext = context.applicationContext
        executor.execute {
            synchronized(lock) {
                val db = getHelper(appContext).writableDatabase
                db.beginTransaction()
                try {
                    db.insert(TABLE_HISTORY, null, entry.toContentValues())
                    trimEntries(db)
                    if (blockEntry != null) {
                        insertSystemBlockEntry(db, blockEntry)
                        trimSystemBlocks(db)
                    }
                    db.setTransactionSuccessful()
                } catch (_: Exception) {
                    // Ignore logging errors so UI flow is unaffected.
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    private fun trimEntries(db: SQLiteDatabase) {
        val totalEntries = DatabaseUtils.queryNumEntries(db, TABLE_HISTORY)
        if (totalEntries <= MAX_ENTRIES) return
        val toRemove = (totalEntries - MAX_ENTRIES).toInt()
        val cursor = db.query(
            TABLE_HISTORY,
            arrayOf(COLUMN_TIMESTAMP),
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC",
            toRemove.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                db.delete(TABLE_HISTORY, "$COLUMN_TIMESTAMP = ?", arrayOf(timestamp.toString()))
            }
        }
    }

    private fun insertSystemBlockEntry(db: SQLiteDatabase, entry: SystemBlockEntry) {
        val expectedSize = entry.blockNumbers.size * BLOCK_SIZE
        if (entry.blockNumbers.isEmpty() || entry.blockData.size < expectedSize) {
            return
        }
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, entry.timestamp)
            put(COLUMN_BLOCK_IDM, entry.idm)
            put(COLUMN_BLOCK_NUMBERS, entry.blockNumbers.joinToString(","))
            put(COLUMN_BLOCK_PAYLOAD, entry.blockData)
        }
        db.insert(TABLE_SYSTEM_BLOCKS, null, values)
    }

    private fun trimSystemBlocks(db: SQLiteDatabase) {
        val totalEntries = DatabaseUtils.queryNumEntries(db, TABLE_SYSTEM_BLOCKS)
        if (totalEntries <= MAX_SYSTEM_BLOCK_ENTRIES) return
        val toRemove = (totalEntries - MAX_SYSTEM_BLOCK_ENTRIES).toInt()
        val cursor = db.query(
            TABLE_SYSTEM_BLOCKS,
            arrayOf(COLUMN_TIMESTAMP),
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC",
            toRemove.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                db.delete(TABLE_SYSTEM_BLOCKS, "$COLUMN_TIMESTAMP = ?", arrayOf(timestamp.toString()))
            }
        }
    }

    fun getLatestSystemBlockEntry(context: Context): SystemBlockEntry? {
        val appContext = context.applicationContext
        synchronized(lock) {
            val db = getHelper(appContext).readableDatabase
            val cursor = db.query(
                TABLE_SYSTEM_BLOCKS,
                arrayOf(
                    COLUMN_TIMESTAMP,
                    COLUMN_BLOCK_IDM,
                    COLUMN_BLOCK_NUMBERS,
                    COLUMN_BLOCK_PAYLOAD
                ),
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC",
                "1"
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return it.toSystemBlockEntry()
                }
            }
        }
        return null
    }

    fun getSystemBlockEntry(context: Context, timestamp: Long): SystemBlockEntry? {
        val appContext = context.applicationContext
        synchronized(lock) {
            val db = getHelper(appContext).readableDatabase
            val cursor = db.query(
                TABLE_SYSTEM_BLOCKS,
                arrayOf(
                    COLUMN_TIMESTAMP,
                    COLUMN_BLOCK_IDM,
                    COLUMN_BLOCK_NUMBERS,
                    COLUMN_BLOCK_PAYLOAD
                ),
                "$COLUMN_TIMESTAMP = ?",
                arrayOf(timestamp.toString()),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return it.toSystemBlockEntry()
                }
            }
        }
        return null
    }

    fun exportSystemBlockEntry(
        context: Context,
        entry: SystemBlockEntry,
        uri: Uri
    ): Boolean {
        val appContext = context.applicationContext
        val payloads = entry.toBlockChunks()
        if (payloads.isEmpty()) return false
        val content = SystemBlockHexCodec.encode(entry.idm, entry.timestamp, payloads)
        return try {
            appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (_: IOException) {
            false
        }
    }

    fun loadSystemBlockEntries(context: Context): List<SystemBlockEntry> {
        val appContext = context.applicationContext
        val entries = mutableListOf<SystemBlockEntry>()
        synchronized(lock) {
            val db = getHelper(appContext).readableDatabase
            val cursor = db.query(
                TABLE_SYSTEM_BLOCKS,
                arrayOf(
                    COLUMN_TIMESTAMP,
                    COLUMN_BLOCK_IDM,
                    COLUMN_BLOCK_NUMBERS,
                    COLUMN_BLOCK_PAYLOAD
                ),
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    entries += it.toSystemBlockEntry()
                }
            }
        }
        return entries
    }

    fun getSystemBlockTimestamps(context: Context): Set<Long> {
        val appContext = context.applicationContext
        val timestamps = mutableSetOf<Long>()
        synchronized(lock) {
            val db = getHelper(appContext).readableDatabase
            val cursor = db.query(
                TABLE_SYSTEM_BLOCKS,
                arrayOf(COLUMN_TIMESTAMP),
                null,
                null,
                null,
                null,
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    timestamps += it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                }
            }
        }
        return timestamps
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

            is WriteController.WriteRequest.RawBlockBatch -> {
                val blocks = JSONArray()
                this.blocks.forEach { block ->
                    blocks.put(
                        JSONObject().apply {
                            put("blockNumber", block.blockNumber)
                            put("data", block.data.toLegacyHexString())
                        }
                    )
                }
                val json = JSONObject().apply {
                    put("type", "rawBlockBatch")
                    put("blocks", blocks)
                }
                "Write success (raw block batch)" to json
            }
        }
    }

    private fun HistoryLogEntry.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_OPERATION, operation)
            put(COLUMN_STATUS, status)
            put(COLUMN_SUMMARY, summary)
            put(COLUMN_DATA, data.toString())
        }

    private fun parseData(rawData: String?): JSONObject {
        if (rawData.isNullOrBlank()) return JSONObject()
        return try {
            JSONObject(rawData)
        } catch (_: JSONException) {
            JSONObject()
        }
    }

    private fun getHelper(context: Context): HistoryDatabaseHelper {
        val existing = databaseHelper
        if (existing != null) {
            return existing
        }
        return synchronized(lock) {
            val current = databaseHelper
            current ?: HistoryDatabaseHelper(context).also { databaseHelper = it }
        }
    }

    private class HistoryDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            createHistoryTable(db)
            createSystemBlockTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createSystemBlockTable(db)
            }
        }

        private fun createHistoryTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                    $COLUMN_TIMESTAMP INTEGER PRIMARY KEY,
                    $COLUMN_OPERATION TEXT NOT NULL,
                    $COLUMN_STATUS TEXT NOT NULL,
                    $COLUMN_SUMMARY TEXT NOT NULL,
                    $COLUMN_DATA TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun createSystemBlockTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SYSTEM_BLOCKS (
                    $COLUMN_TIMESTAMP INTEGER PRIMARY KEY,
                    $COLUMN_BLOCK_IDM TEXT NOT NULL,
                    $COLUMN_BLOCK_NUMBERS TEXT NOT NULL,
                    $COLUMN_BLOCK_PAYLOAD BLOB NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    data class HistoryLogEntry(
        val timestamp: Long,
        val operation: String,
        val status: String,
        val summary: String,
        val data: JSONObject
    )

    data class SystemBlockEntry(
        val timestamp: Long,
        val idm: String,
        val blockNumbers: List<Int>,
        val blockData: ByteArray
    ) {
        data class BlockChunk(val blockNumber: Int, val data: ByteArray)

        fun toBlockChunks(): List<BlockChunk> {
            if (blockNumbers.isEmpty() || blockData.isEmpty()) {
                return emptyList()
            }
            val expectedSize = blockNumbers.size * BLOCK_SIZE
            if (blockData.size < expectedSize) {
                return emptyList()
            }
            val chunks = mutableListOf<BlockChunk>()
            blockNumbers.forEachIndexed { index, blockNumber ->
                val start = index * BLOCK_SIZE
                val end = start + BLOCK_SIZE
                if (end <= blockData.size) {
                    val data = blockData.copyOfRange(start, end)
                    chunks += BlockChunk(blockNumber, data)
                }
            }
            return chunks
        }
    }

    private fun Cursor.toSystemBlockEntry(): SystemBlockEntry {
        val timestamp = getLong(getColumnIndexOrThrow(COLUMN_TIMESTAMP))
        val idm = getString(getColumnIndexOrThrow(COLUMN_BLOCK_IDM))
        val blockNumbersRaw = getString(getColumnIndexOrThrow(COLUMN_BLOCK_NUMBERS)).orEmpty()
        val numbers = blockNumbersRaw.split(',').mapNotNull { value ->
            value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        val payload = getBlob(getColumnIndexOrThrow(COLUMN_BLOCK_PAYLOAD))
        return SystemBlockEntry(
            timestamp = timestamp,
            idm = idm,
            blockNumbers = numbers,
            blockData = payload
        )
    }

}
