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
import org.soralis.droidsillica.model.RawExchange
import org.soralis.droidsillica.model.TabContent

/**
 * Mirrors the behavior of the legacy write.py script so we can update SiliCa system blocks
 * directly from the Android app via the 0x08 Write Without Encryption command.
 */
class WriteController {

    interface Listener {
        fun onWaitingForTag()
        fun onWriteSuccess(result: WriteResult)
        fun onWriteError(message: String)
        fun onWriteStopped()
        fun onNfcUnavailable()
    }

    sealed class WriteRequest {
        data class Idm(val idm: ByteArray, val pmm: ByteArray?) : WriteRequest()
        data class SystemCodes(val codes: List<Int>) : WriteRequest()
        data class ServiceCodes(val codes: List<Int>) : WriteRequest()
        data class RawBlock(val blockNumber: Int, val data: ByteArray) : WriteRequest()
    }

    class WriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class WriteResult(
        val rawExchanges: List<RawExchange>
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var nfcAdapter: NfcAdapter? = null
    private var readerModeEnabled = false
    private var activityRef: WeakReference<Activity>? = null
    private var sessionListener: Listener? = null
    private var pendingRequest: WriteRequest? = null

    fun getContent(): TabContent = TabContent(
        key = KEY,
        title = "Write",
        description = "Update the SiliCa IDm/PMm, system codes, service codes or raw system blocks.",
        actions = listOf(
            "Mirrors the CLI write.py workflow: IDm/PMm, system codes, service codes, or raw block data.",
            "Validates the provided hex before entering NFC-F writer mode.",
            "Hold a compatible SiliCa card to dispatch the Write Without Encryption command."
        )
    )

    fun startWriting(activity: Activity, request: WriteRequest, listener: Listener) {
        sessionListener = listener
        pendingRequest = request
        activityRef = WeakReference(activity)
        val adapter = nfcAdapter ?: NfcAdapter.getDefaultAdapter(activity).also { nfcAdapter = it }
        if (adapter == null) {
            listener.onNfcUnavailable()
            pendingRequest = null
            sessionListener = null
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

    fun stopWriting() {
        stopReaderModeInternal(notifyStopped = true)
    }

    @Throws(WriteException::class)
    private fun writeToTag(
        tag: Tag,
        request: WriteRequest,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MS
    ): WriteResult {
        val nfcF = NfcF.get(tag) ?: throw WriteException("Tag is not a FeliCa/NfcF tag")
        try {
            nfcF.connect()
            nfcF.timeout = timeoutMillis
            val (blockNumber, payload) = buildPayload(request)
            val idm = tag.id.copyOf()
            val writeCommand = buildWriteCommand(idm, blockNumber, payload)
            val requestSnapshot = writeCommand.copyOf()
            val response = nfcF.transceive(writeCommand)
            parseWriteResponse(response)
            val exchanges = listOf(
                RawExchange(
                    label = LABEL_WRITE_WITHOUT_ENCRYPTION,
                    request = requestSnapshot,
                    response = response.copyOf()
                )
            )
            return WriteResult(rawExchanges = exchanges)
        } catch (io: IOException) {
            throw WriteException("Unable to write the SiliCa system block", io)
        } finally {
            try {
                nfcF.close()
            } catch (_: IOException) {
                // Ignored
            }
        }
    }

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        val request = pendingRequest ?: return@ReaderCallback
        try {
            val result = writeToTag(tag, request)
            mainHandler.post {
                sessionListener?.onWriteSuccess(result)
                stopReaderModeInternal(notifyStopped = false)
            }
        } catch (exception: WriteException) {
            mainHandler.post {
                sessionListener?.onWriteError(exception.message ?: DEFAULT_ERROR)
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
        pendingRequest = null
        if (notifyStopped) {
            sessionListener?.onWriteStopped()
        }
        sessionListener = null
    }

    private fun buildPayload(request: WriteRequest): Pair<Int, ByteArray> =
        when (request) {
            is WriteRequest.Idm -> {
                val idmBytes = request.idm
                require(idmBytes.size == IDM_LENGTH) { "IDm must be 8 bytes" }
                val pmmBytes = request.pmm ?: DEFAULT_PMM
                val finalPmm = if (pmmBytes.size == PMM_LENGTH) pmmBytes else DEFAULT_PMM
                BLOCK_IDM to (idmBytes + finalPmm)
            }

            is WriteRequest.SystemCodes -> {
                val payload = ByteArray(BLOCK_SIZE)
                var offset = 0
                request.codes.forEach { code ->
                    if (offset + 1 >= payload.size) return@forEach
                    payload[offset++] = ((code shr 8) and 0xFF).toByte()
                    payload[offset++] = (code and 0xFF).toByte()
                }
                BLOCK_SYSTEM_CODES to payload
            }

            is WriteRequest.ServiceCodes -> {
                val payload = ByteArray(BLOCK_SIZE)
                var offset = 0
                request.codes.forEach { code ->
                    if (offset + 1 >= payload.size) return@forEach
                    payload[offset++] = (code and 0xFF).toByte()
                    payload[offset++] = ((code shr 8) and 0xFF).toByte()
                }
                BLOCK_SERVICE_CODES to payload
            }

            is WriteRequest.RawBlock -> {
                require(request.blockNumber in 0..MAX_RAW_BLOCK) { "Invalid block number" }
                require(request.data.size == BLOCK_SIZE) { "Raw data must be 16 bytes" }
                request.blockNumber to request.data
            }
        }

    private fun buildWriteCommand(idm: ByteArray, blockNumber: Int, payload: ByteArray): ByteArray {
        require(idm.size == IDM_LENGTH) { "Unexpected IDm length: ${idm.size}" }
        require(payload.size == BLOCK_SIZE) { "Unexpected payload length: ${payload.size}" }
        val buffer = ByteArray(1 + 1 + IDM_LENGTH + 1 + SERVICE_CODE_SIZE + 1 + BLOCK_LIST_ELEMENT_SIZE + BLOCK_SIZE)
        var offset = 0
        buffer[offset++] = buffer.size.toByte()
        buffer[offset++] = COMMAND_WRITE
        System.arraycopy(idm, 0, buffer, offset, IDM_LENGTH)
        offset += IDM_LENGTH
        buffer[offset++] = 0x01
        buffer[offset++] = (SERVICE_CODE and 0xFF).toByte()
        buffer[offset++] = ((SERVICE_CODE shr 8) and 0xFF).toByte()
        buffer[offset++] = 0x01
        buffer[offset++] = BLOCK_LIST_ACCESS_MODE
        buffer[offset++] = blockNumber.coerceIn(0, 0xFF).toByte()
        System.arraycopy(payload, 0, buffer, offset, BLOCK_SIZE)
        return buffer
    }

    @Throws(WriteException::class)
    private fun parseWriteResponse(response: ByteArray) {
        if (response.size < RESPONSE_HEADER_SIZE) {
            throw WriteException("Response too short: ${response.size} bytes")
        }
        val responseCode = response[1]
        if (responseCode != RESPONSE_WRITE) {
            throw WriteException(
                String.format(
                    Locale.US,
                    "Unexpected response code 0x%02X",
                    responseCode.toPositiveInt()
                )
            )
        }
        val statusFlag1 = response[10].toPositiveInt()
        val statusFlag2 = response[11].toPositiveInt()
        if (statusFlag1 != 0 || statusFlag2 != 0) {
            throw WriteException("FeliCa error status: $statusFlag1, $statusFlag2")
        }
    }

    private fun Byte.toPositiveInt(): Int = toInt() and 0xFF

    companion object {
        const val MAX_SYSTEM_CODES = 4
        const val MAX_SERVICE_CODES = 4
        const val MAX_RAW_BLOCK = 11
        private const val KEY = "write"
        private const val DEFAULT_TIMEOUT_MS = 1000
        private const val IDM_LENGTH = 8
        private const val PMM_LENGTH = 8
        private const val BLOCK_SIZE = 16
        private const val RESPONSE_HEADER_SIZE = 12
        private const val SERVICE_CODE = 0xFFFF
        private const val SERVICE_CODE_SIZE = 2
        private const val BLOCK_LIST_ELEMENT_SIZE = 2
        private val BLOCK_LIST_ACCESS_MODE = 0x80.toByte()
        private val COMMAND_WRITE = 0x08.toByte()
        private val RESPONSE_WRITE = 0x09.toByte()
        private const val BLOCK_IDM = 0x83
        private const val BLOCK_SERVICE_CODES = 0x84
        private const val BLOCK_SYSTEM_CODES = 0x85
        private val DEFAULT_PMM = byteArrayOf(
            0x00.toByte(),
            0x01.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte()
        )
        private const val DEFAULT_ERROR = "Write failed"
        private const val LABEL_WRITE_WITHOUT_ENCRYPTION = "Write Without Encryption"
    }
}
