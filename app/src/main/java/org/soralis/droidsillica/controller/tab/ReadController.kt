package org.soralis.droidsillica.controller.tab

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import org.soralis.droidsillica.model.TabContent

/**
 * Mirrors the behavior of the legacy read.py script by issuing FeliCa commands to inspect the
 * SiliCa system service (0xFFFF) and expose the parsed last error command and metadata.
 */
class ReadController {

    interface Listener {
        fun onWaitingForTag()
        fun onReadSuccess(result: ReadResult)
        fun onReadError(message: String)
        fun onReadingStopped()
        fun onNfcUnavailable()
    }

    data class ReadResult(
        val idm: ByteArray,
        val pmm: ByteArray,
        val systemCodes: List<Int>,
        val serviceCodes: List<Int>,
        val statusFlag1: Int,
        val statusFlag2: Int,
        val blockData: ByteArray,
        val lastErrorCommand: ByteArray
    ) {
        val formattedIdm: String = idm.toLegacyHexString()
        val formattedPmm: String = pmm.toLegacyHexString()
        val formattedCommand: String = lastErrorCommand.toLegacyHexString()
    }

    class ReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var nfcAdapter: NfcAdapter? = null
    private var readerModeEnabled = false
    private var activityRef: WeakReference<Activity>? = null
    private var sessionListener: Listener? = null
    private var readBlocksRequested: Boolean = true
    private var pendingBlockNumbers: List<Int> = emptyList()

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
     * Starts NFC reader mode and waits for a FeliCa tag. Results are delivered to [Listener].
     */
    fun startReading(
        activity: Activity,
        blockNumbers: List<Int>,
        readLastErrorCommand: Boolean,
        listener: Listener
    ) {
        sessionListener = listener
        activityRef = WeakReference(activity)
        readBlocksRequested = readLastErrorCommand
        pendingBlockNumbers = blockNumbers
        val adapter = nfcAdapter ?: NfcAdapter.getDefaultAdapter(activity).also { nfcAdapter = it }
        if (adapter == null) {
            listener.onNfcUnavailable()
            sessionListener = null
            readBlocksRequested = true
            pendingBlockNumbers = emptyList()
            return
        }
        adapter.enableReaderMode(
            activity,
            readerCallback,
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        readerModeEnabled = true
        listener.onWaitingForTag()
    }

    fun stopReading() {
        stopReaderModeInternal(notifyStopped = true)
    }

    /**
     * Connects to a FeliCa tag and retrieves both metadata (system/service codes) and, optionally,
     * the system blocks that contain the last error command.
     */
    @Throws(ReadException::class)
    fun readLastErrorCommand(
        tag: Tag,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MS,
        readBlocks: Boolean = true
    ): ReadResult {
        val nfcF = NfcF.get(tag) ?: throw ReadException("Tag is not a FeliCa/NfcF tag")
        try {
            nfcF.connect()
            nfcF.timeout = timeoutMillis
            val idm = tag.id.copyOf()
            val pmm = nfcF.manufacturer.copyOf()
            val systemCodes = requestSystemCodes(nfcF, idm)
            val serviceCodes = requestServiceCodes(nfcF, idm)
            if (!readBlocks) {
                return ReadResult(
                    idm = idm,
                    pmm = pmm,
                    systemCodes = systemCodes,
                    serviceCodes = serviceCodes,
                    statusFlag1 = 0,
                    statusFlag2 = 0,
                    blockData = ByteArray(0),
                    lastErrorCommand = ByteArray(0)
                )
            }
            val response = nfcF.transceive(buildReadCommand(idm, pendingBlockNumbers))
            return parseReadResponse(response, pmm, systemCodes, serviceCodes)
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

    private fun buildReadCommand(idm: ByteArray, blockNumbers: List<Int>): ByteArray {
        require(idm.size == IDM_LENGTH) { "Unexpected IDm length: ${idm.size}" }
        val blocks = blockNumbers.ifEmpty { DEFAULT_BLOCKS }
        val blockList = buildBlockList(blocks)
        val commandSize =
            1 + 1 + idm.size + 1 + SERVICE_CODE_LIST.size * SERVICE_CODE_SIZE + 1 + blockList.size
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
        val blockCount = blockList.size / BLOCK_LIST_ELEMENT_SIZE
        buffer[offset++] = blockCount.toByte()
        blockList.copyInto(buffer, offset)
        return buffer
    }

    private fun buildBlockList(blockNumbers: List<Int>): ByteArray {
        val blockList = ByteArray(blockNumbers.size * BLOCK_LIST_ELEMENT_SIZE)
        var offset = 0
        blockNumbers.forEach { number ->
            val sanitized = number.coerceIn(0, 0xFF)
            blockList[offset++] = BLOCK_LIST_ACCESS_MODE
            blockList[offset++] = sanitized.toByte()
        }
        return blockList
    }

    private fun parseReadResponse(
        response: ByteArray,
        pmm: ByteArray,
        systemCodes: List<Int>,
        serviceCodes: List<Int>
    ): ReadResult {
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
        return ReadResult(
            idm = idm,
            pmm = pmm,
            systemCodes = systemCodes,
            serviceCodes = serviceCodes,
            statusFlag1 = statusFlag1,
            statusFlag2 = statusFlag2,
            blockData = blockData,
            lastErrorCommand = lastCommand
        )
    }

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        try {
            val result = readLastErrorCommand(tag, readBlocks = readBlocksRequested)
            mainHandler.post {
                sessionListener?.onReadSuccess(result)
                stopReaderModeInternal(notifyStopped = false)
            }
        } catch (exception: ReadException) {
            mainHandler.post {
                sessionListener?.onReadError(
                    exception.message ?: exception.javaClass.simpleName
                )
                stopReaderModeInternal(notifyStopped = false)
            }
        }
    }

    private fun stopReaderModeInternal(notifyStopped: Boolean) {
        if (readerModeEnabled) {
            activityRef?.get()?.let { activity ->
                nfcAdapter?.disableReaderMode(activity)
            }
        }
        readerModeEnabled = false
        readBlocksRequested = true
        pendingBlockNumbers = emptyList()
        if (notifyStopped) {
            sessionListener?.onReadingStopped()
        }
        sessionListener = null
    }

    private fun requestSystemCodes(nfcF: NfcF, idm: ByteArray): List<Int> {
        val command = ByteArray(1 + 1 + IDM_LENGTH)
        command[0] = command.size.toByte()
        command[1] = COMMAND_REQUEST_SYSTEM_CODE
        System.arraycopy(idm, 0, command, 2, IDM_LENGTH)
        val response = try {
            nfcF.transceive(command)
        } catch (_: IOException) {
            return emptyList()
        }
        if (response.size < RESPONSE_SYSTEM_CODE_HEADER || response[1] != RESPONSE_SYSTEM_CODE) {
            return emptyList()
        }
        val statusFlag1 = response[10].toPositiveInt()
        val statusFlag2 = response[11].toPositiveInt()
        if (statusFlag1 != 0 || statusFlag2 != 0) {
            return emptyList()
        }
        val codeCount = response[12].toPositiveInt()
        val codes = mutableListOf<Int>()
        var offset = 13
        repeat(codeCount) {
            if (offset + 1 >= response.size) return codes
            val code = response[offset].toPositiveInt() or (response[offset + 1].toPositiveInt() shl 8)
            codes += code
            offset += 2
        }
        return codes
    }

    private fun requestServiceCodes(nfcF: NfcF, idm: ByteArray): List<Int> {
        val codes = mutableListOf<Int>()
        for (order in 0 until MAX_SERVICE_SEARCH) {
            val command = ByteArray(1 + 1 + IDM_LENGTH + 2)
            command[0] = command.size.toByte()
            command[1] = COMMAND_SEARCH_SERVICE_CODE
            System.arraycopy(idm, 0, command, 2, IDM_LENGTH)
            command[10] = (order and 0xFF).toByte()
            command[11] = ((order shr 8) and 0xFF).toByte()
            val response = try {
                nfcF.transceive(command)
            } catch (_: IOException) {
                break
            }
            if (response.size < RESPONSE_SERVICE_CODE_SIZE || response[1] != RESPONSE_SERVICE_CODE) {
                break
            }
            val statusFlag1 = response[10].toPositiveInt()
            val statusFlag2 = response[11].toPositiveInt()
            if (statusFlag1 != 0 || statusFlag2 != 0) {
                break
            }
            val serviceCode =
                response[12].toPositiveInt() or (response[13].toPositiveInt() shl 8)
            codes += serviceCode
        }
        return codes
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
        private const val RESPONSE_SYSTEM_CODE_HEADER = 13
        private const val RESPONSE_SERVICE_CODE_SIZE = 14
        private const val MAX_SERVICE_SEARCH = 32
        private val COMMAND_READ = 0x06.toByte()
        private val RESPONSE_READ = 0x07.toByte()
        private val COMMAND_REQUEST_SYSTEM_CODE = 0x0C.toByte()
        private val RESPONSE_SYSTEM_CODE = 0x0D.toByte()
        private val COMMAND_SEARCH_SERVICE_CODE = 0x0A.toByte()
        private val RESPONSE_SERVICE_CODE = 0x0B.toByte()
        private val SERVICE_CODE_LIST = intArrayOf(0xFFFF)
        private val DEFAULT_BLOCKS = listOf(0xE0, 0xE1)
        private val BLOCK_LIST_ACCESS_MODE = 0x80.toByte()
    }
}

private fun ByteArray.toLegacyHexString(): String =
    joinToString(" ") { byte -> String.format(Locale.US, "%02X", byte.toInt() and 0xFF) }
