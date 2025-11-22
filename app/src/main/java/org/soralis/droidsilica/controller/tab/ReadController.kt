package org.soralis.droidsilica.controller.tab

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import org.soralis.droidsilica.model.RawExchange
import org.soralis.droidsilica.model.TabContent
import org.soralis.droidsilica.util.toLegacyHexString

/**
 * Mirrors the behavior of the legacy read.py script by issuing FeliCa commands to inspect the
 * SiliCa system service (0xFFFF) and expose the parsed last error command and metadata.
 */
class ReadController {

    interface Listener {
        fun onWaitingForTag()
        fun onReadSuccess(result: ReadResult)
        fun onReadError(
            message: String,
            rawLog: List<RawExchange>,
            partialResult: PartialReadResult?
        )
        fun onReadingStopped()
        fun onNfcUnavailable()
    }

    data class PartialReadResult(
        val idm: ByteArray,
        val pmm: ByteArray,
        val systemCodes: List<Int>,
        val serviceCodes: List<Int>,
        val blockNumbers: List<Int>,
        val blockData: ByteArray
    ) {
        val formattedIdm: String = idm.toLegacyHexString()
        val formattedPmm: String = pmm.toLegacyHexString()
    }

    data class ReadResult(
        val idm: ByteArray,
        val pmm: ByteArray,
        val systemCodes: List<Int>,
        val serviceCodes: List<Int>,
        val statusFlag1: Int,
        val statusFlag2: Int,
        val blockData: ByteArray,
        val lastErrorCommand: ByteArray,
        val rawExchanges: List<RawExchange>,
        val blockNumbers: List<Int>
    ) {
        val formattedIdm: String = idm.toLegacyHexString()
        val formattedPmm: String = pmm.toLegacyHexString()
        val formattedCommand: String = lastErrorCommand.toLegacyHexString()
    }

    class ReadException(
        message: String,
        cause: Throwable? = null,
        val rawLog: List<RawExchange> = emptyList(),
        val partialResult: PartialReadResult? = null
    ) : Exception(message, cause)

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
        val rawLog = mutableListOf<RawExchange>()
        var partialResult: PartialReadResult? = null
        try {
            nfcF.connect()
            nfcF.timeout = timeoutMillis
            val (idm, pmm) = performPolling(nfcF, rawLog)
            val systemCodes = requestSystemCodes(nfcF, idm, rawLog)
            val serviceCodes = requestServiceCodes(nfcF, idm, rawLog)
            val blocksToRead = pendingBlockNumbers.ifEmpty { DEFAULT_BLOCKS }
            partialResult = PartialReadResult(
                idm = idm,
                pmm = pmm,
                systemCodes = systemCodes,
                serviceCodes = serviceCodes,
                blockNumbers = blocksToRead.toList(),
                blockData = ByteArray(0)
            )
            if (!readBlocks) {
                return ReadResult(
                    idm = idm,
                    pmm = pmm,
                    systemCodes = systemCodes,
                    serviceCodes = serviceCodes,
                    statusFlag1 = 0,
                    statusFlag2 = 0,
                    blockData = ByteArray(0),
                    lastErrorCommand = ByteArray(0),
                    rawExchanges = rawLog.toList(),
                    blockNumbers = emptyList()
                )
            }
            val combinedData = ByteArrayOutputStream()
            var lastChunkResult: ReadResult? = null
            var currentIndex = 0
            var aggregatedPartial = requireNotNull(partialResult)
            while (currentIndex < blocksToRead.size) {
                val chunkNumbers = blocksToRead.subList(
                    currentIndex,
                    minOf(currentIndex + MAX_BLOCKS_PER_COMMAND, blocksToRead.size)
                )
                val chunkPartial = aggregatedPartial.copy(
                    blockNumbers = chunkNumbers,
                    blockData = ByteArray(0)
                )
                val chunkResult = readChunk(
                    nfcF,
                    idm,
                    chunkNumbers,
                    chunkPartial,
                    aggregatedPartial,
                    rawLog
                )
                combinedData.write(chunkResult.blockData)
                currentIndex += chunkNumbers.size
                aggregatedPartial = aggregatedPartial.copy(blockData = combinedData.toByteArray())
                lastChunkResult = chunkResult
            }
            val payload = combinedData.toByteArray()
            partialResult = aggregatedPartial
            return lastChunkResult?.copy(
                blockData = payload,
                blockNumbers = blocksToRead.toList()
            ) ?: ReadResult(
                idm = idm,
                pmm = pmm,
                systemCodes = systemCodes,
                serviceCodes = serviceCodes,
                statusFlag1 = 0,
                statusFlag2 = 0,
                blockData = ByteArray(0),
                lastErrorCommand = ByteArray(0),
                rawExchanges = rawLog.toList(),
                blockNumbers = emptyList()
            )
        } catch (e: IOException) {
            throw ReadException(
                "Unable to read the SiliCa system block",
                e,
                rawLog.toList(),
                partialResult = partialResult
            )
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
        rawRequest: ByteArray,
        response: ByteArray,
        chunkPartial: PartialReadResult,
        errorSnapshot: PartialReadResult,
        rawLog: MutableList<RawExchange>
    ): ReadResult {
        rawLog += RawExchange(
            label = LABEL_READ_WITHOUT_ENCRYPTION,
            request = rawRequest.copyOf(),
            response = response.copyOf()
        )
        if (response.size < RESPONSE_HEADER_SIZE) {
            throw ReadException(
                "Response too short: ${response.size} bytes",
                rawLog = rawLog.toList(),
                partialResult = errorSnapshot
            )
        }
        val responseCode = response[1]
        if (responseCode != RESPONSE_READ) {
            throw ReadException(
                String.format(
                    Locale.US,
                    "Unexpected response code 0x%02X",
                    responseCode.toPositiveInt()
                ),
                rawLog = rawLog.toList(),
                partialResult = errorSnapshot
            )
        }
        val idm = response.copyOfRange(2, 2 + IDM_LENGTH)
        val statusFlag1 = response[10].toPositiveInt()
        val statusFlag2 = response[11].toPositiveInt()
        if (statusFlag1 != 0 || statusFlag2 != 0) {
            throw ReadException(
                "FeliCa error status: $statusFlag1, $statusFlag2",
                rawLog = rawLog.toList(),
                partialResult = errorSnapshot
            )
        }
        val blockCount = response[12].toPositiveInt()
        val payloadOffset = RESPONSE_HEADER_SIZE
        val payloadLength = blockCount * BLOCK_SIZE
        if (response.size < payloadOffset + payloadLength) {
            throw ReadException(
                "Incomplete block payload (expected $payloadLength bytes)",
                rawLog = rawLog.toList(),
                partialResult = errorSnapshot
            )
        }
        val blockData = response.copyOfRange(payloadOffset, payloadOffset + payloadLength)
        val lastCommand = extractLastErrorCommand(chunkPartial.blockNumbers, blockData)
        return ReadResult(
            idm = idm,
            pmm = chunkPartial.pmm,
            systemCodes = chunkPartial.systemCodes,
            serviceCodes = chunkPartial.serviceCodes,
            statusFlag1 = statusFlag1,
            statusFlag2 = statusFlag2,
            blockData = blockData,
            lastErrorCommand = lastCommand,
            rawExchanges = rawLog.toList(),
            blockNumbers = chunkPartial.blockNumbers.toList()
        )
    }

    private fun readChunk(
        nfcF: NfcF,
        idm: ByteArray,
        blockNumbers: List<Int>,
        chunkPartial: PartialReadResult,
        errorSnapshot: PartialReadResult,
        rawLog: MutableList<RawExchange>
    ): ReadResult {
        val request = buildReadCommand(idm, blockNumbers)
        val requestSnapshot = request.copyOf()
        val response = try {
            nfcF.transceive(request)
        } catch (io: IOException) {
            rawLog += RawExchange(
                label = LABEL_READ_WITHOUT_ENCRYPTION,
                request = requestSnapshot,
                response = ByteArray(0)
            )
            throw ReadException(
                "Unable to read the SiliCa system block",
                io,
                rawLog.toList(),
                partialResult = errorSnapshot
            )
        }
        return parseReadResponse(
            requestSnapshot,
            response,
            chunkPartial,
            errorSnapshot,
            rawLog
        )
    }

    private fun performPolling(
        nfcF: NfcF,
        rawLog: MutableList<RawExchange>
    ): Pair<ByteArray, ByteArray> {
        val commandSize = 1 + 1 + 2 + 1 + 1
        val command = ByteArray(commandSize)
        var offset = 0
        command[offset++] = commandSize.toByte()
        command[offset++] = COMMAND_POLLING
        command[offset++] = (POLLING_SYSTEM_CODE and 0xFF).toByte()
        command[offset++] = ((POLLING_SYSTEM_CODE shr 8) and 0xFF).toByte()
        command[offset++] = POLLING_REQUEST_CODE
        command[offset] = POLLING_TIME_SLOT
        val requestSnapshot = command.copyOf()
        val response = nfcF.transceive(command)
        rawLog += RawExchange(
            label = LABEL_POLLING,
            request = requestSnapshot,
            response = response.copyOf()
        )
        if (response.size < RESPONSE_POLLING_MIN_SIZE) {
            throw ReadException(
                "Polling response too short: ${response.size} bytes",
                rawLog = rawLog.toList()
            )
        }
        val responseCode = response[1]
        if (responseCode != RESPONSE_POLLING) {
            throw ReadException(
                String.format(
                    Locale.US,
                    "Unexpected polling response code 0x%02X",
                    responseCode.toPositiveInt()
                ),
                rawLog = rawLog.toList()
            )
        }
        val idmStart = 2
        val idm = response.copyOfRange(idmStart, idmStart + IDM_LENGTH)
        val pmmStart = idmStart + IDM_LENGTH
        val pmm = response.copyOfRange(pmmStart, pmmStart + PMM_LENGTH)
        return idm to pmm
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
                    exception.message ?: exception.javaClass.simpleName,
                    exception.rawLog,
                    exception.partialResult
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

    private fun requestSystemCodes(
        nfcF: NfcF,
        idm: ByteArray,
        rawLog: MutableList<RawExchange>? = null
    ): List<Int> {
        val command = ByteArray(1 + 1 + IDM_LENGTH)
        command[0] = command.size.toByte()
        command[1] = COMMAND_REQUEST_SYSTEM_CODE
        System.arraycopy(idm, 0, command, 2, IDM_LENGTH)
        val requestSnapshot = command.copyOf()
        val response = try {
            nfcF.transceive(command)
        } catch (_: IOException) {
            return emptyList()
        }
        rawLog?.add(
            RawExchange(
                label = LABEL_REQUEST_SYSTEM_CODES,
                request = requestSnapshot,
                response = response.copyOf()
            )
        )
        val payloadOffset = RESPONSE_HEADER_BASE
        if (response.size < payloadOffset + 1 || response[1] != RESPONSE_SYSTEM_CODE) {
            return emptyList()
        }
        val codeCount = response[payloadOffset].toPositiveInt()
        val codes = mutableListOf<Int>()
        var offset = payloadOffset + 1
        repeat(codeCount) {
            if (offset + 1 >= response.size) return@repeat
            val code = (response[offset].toPositiveInt() shl 8) or response[offset + 1].toPositiveInt()
            codes += code
            offset += 2
        }
        return codes
    }

    private fun requestServiceCodes(
        nfcF: NfcF,
        idm: ByteArray,
        rawLog: MutableList<RawExchange>? = null
    ): List<Int> {
        val codes = mutableListOf<Int>()
        val payloadBase = RESPONSE_HEADER_BASE
        for (order in 0 until MAX_SERVICE_SEARCH) {
            val command = ByteArray(1 + 1 + IDM_LENGTH + 2)
            command[0] = command.size.toByte()
            command[1] = COMMAND_SEARCH_SERVICE_CODE
            System.arraycopy(idm, 0, command, 2, IDM_LENGTH)
            command[10] = (order and 0xFF).toByte()
            command[11] = ((order shr 8) and 0xFF).toByte()
            val requestSnapshot = command.copyOf()
            val response = try {
                nfcF.transceive(command)
            } catch (_: IOException) {
                break
            }
            rawLog?.add(
                RawExchange(
                    label = String.format(Locale.US, LABEL_SEARCH_SERVICE_TEMPLATE, order + 1),
                    request = requestSnapshot,
                    response = response.copyOf()
                )
            )
            if (response.size < payloadBase + 2 || response[1] != RESPONSE_SERVICE_CODE) {
                break
            }
            var offset = payloadBase
            if (response.size - offset >= 4) {
                val statusFlag1 = response[offset].toPositiveInt()
                val statusFlag2 = response[offset + 1].toPositiveInt()
                offset += 2
                if (statusFlag1 != 0 || statusFlag2 != 0) {
                    break
                }
            }
            if (response.size - offset < 2) {
                break
            }
            val serviceCode =
                response[offset].toPositiveInt() or (response[offset + 1].toPositiveInt() shl 8)
            if (serviceCode == 0xFFFF && codes.isNotEmpty()) {
                break
            }
            if (!codes.contains(serviceCode)) {
                codes += serviceCode
            } else {
                break
            }
        }
        return codes
    }

    private fun Byte.toPositiveInt(): Int = toInt() and 0xFF

    private fun extractLastErrorCommand(
        blockNumbers: List<Int>,
        blockData: ByteArray
    ): ByteArray {
        if (blockNumbers != DEFAULT_BLOCKS || blockData.isEmpty()) {
            return ByteArray(0)
        }
        if (blockData.all { it == UNWRITTEN_BLOCK_VALUE }) {
            return ByteArray(0)
        }
        val maxCommandSize = (blockData.size - 1).coerceAtLeast(0)
        if (maxCommandSize == 0) {
            return ByteArray(0)
        }
        val lengthByte = blockData[0]
        if (lengthByte == UNWRITTEN_BLOCK_VALUE) {
            return ByteArray(0)
        }
        val commandLength = lengthByte.toPositiveInt()
        val safeLength = commandLength.coerceIn(0, maxCommandSize)
        return if (safeLength > 0) {
            blockData.copyOfRange(1, 1 + safeLength)
        } else {
            ByteArray(0)
        }
    }

    companion object {
        private const val KEY = "read"
        private const val DEFAULT_TIMEOUT_MS = 1000
        private const val IDM_LENGTH = 8
        private const val PMM_LENGTH = 8
        private const val SERVICE_CODE_SIZE = 2
        private const val BLOCK_LIST_ELEMENT_SIZE = 2
        private const val BLOCK_SIZE = 16
        private const val RESPONSE_HEADER_SIZE = 13
        private const val RESPONSE_HEADER_BASE = 2 + IDM_LENGTH
        private const val MAX_SERVICE_SEARCH = 32
        private const val MAX_BLOCKS_PER_COMMAND = 12
        private val COMMAND_READ = 0x06.toByte()
        private val RESPONSE_READ = 0x07.toByte()
        private val COMMAND_REQUEST_SYSTEM_CODE = 0x0C.toByte()
        private val RESPONSE_SYSTEM_CODE = 0x0D.toByte()
        private val COMMAND_SEARCH_SERVICE_CODE = 0x0A.toByte()
        private val RESPONSE_SERVICE_CODE = 0x0B.toByte()
        private val COMMAND_POLLING = 0x00.toByte()
        private val RESPONSE_POLLING = 0x01.toByte()
        private val SERVICE_CODE_LIST = intArrayOf(0xFFFF)
        private val DEFAULT_BLOCKS = listOf(0xE0, 0xE1)
        private val BLOCK_LIST_ACCESS_MODE = 0x80.toByte()
        private const val POLLING_SYSTEM_CODE = 0xFFFF
        private val POLLING_REQUEST_CODE = 0x00.toByte()
        private val POLLING_TIME_SLOT = 0x00.toByte()
        private const val RESPONSE_POLLING_MIN_SIZE = 18
        private const val LABEL_REQUEST_SYSTEM_CODES = "Request System Codes"
        private const val LABEL_SEARCH_SERVICE_TEMPLATE = "Search Service Codes #%d"
        private const val LABEL_READ_WITHOUT_ENCRYPTION = "Read Without Encryption"
        private const val LABEL_POLLING = "Polling"
        private val UNWRITTEN_BLOCK_VALUE = 0xFF.toByte()
    }
}
