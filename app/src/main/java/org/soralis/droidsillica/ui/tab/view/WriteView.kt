package org.soralis.droidsillica.ui.tab.view

import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputLayout
import java.security.SecureRandom
import java.util.Locale
import org.soralis.droidsillica.R
import org.soralis.droidsillica.controller.tab.WriteController
import org.soralis.droidsillica.databinding.FragmentTabWriteBinding
import org.soralis.droidsillica.model.TabContent

class WriteView(
    private val binding: FragmentTabWriteBinding,
    private val callbacks: Callbacks? = null
) : BaseTabView(binding.toTabUiComponents()) {

    interface Callbacks {
        fun onStartWriting(request: WriteController.WriteRequest)
        fun onCancelWriting()
    }

    private enum class CommandOption {
        IDM,
        SYSTEM,
        SERVICE,
        RAW
    }

    private var currentOption: CommandOption = CommandOption.IDM
    private val secureRandom = SecureRandom()

    init {
        setupCommandDropdown()
        binding.writeStartButton.setOnClickListener { handleStart() }
        binding.writeCancelButton.setOnClickListener { handleCancel() }
        binding.writeIdmRandomButton.setOnClickListener { randomizeIdm() }
        binding.writePmmRandomButton.setOnClickListener { randomizePmm() }
        showOption(currentOption)
        binding.writeResultText.text =
            binding.root.context.getString(R.string.write_result_placeholder)
    }

    override fun render(content: TabContent) {
        super.render(content)
        binding.writeResultText.text =
            binding.root.context.getString(R.string.write_result_placeholder)
    }

    private fun setupCommandDropdown() {
        val context = binding.root.context
        val options = context.resources.getStringArray(R.array.write_command_options)
        val adapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_1, options.toList())
        binding.writeCommandDropdown.setAdapter(adapter)
        binding.writeCommandDropdown.setOnItemClickListener { _, _, position, _ ->
            val option = CommandOption.values().getOrElse(position) { CommandOption.IDM }
            currentOption = option
            showOption(option)
        }
        binding.writeCommandDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.writeCommandDropdown.showDropDown()
            }
        }
        binding.writeCommandDropdown.setOnClickListener {
            binding.writeCommandDropdown.showDropDown()
        }
        // Default selection
        binding.writeCommandDropdown.setText(options.firstOrNull() ?: "", false)
        currentOption = CommandOption.IDM
        showOption(CommandOption.IDM)
    }

    private fun handleStart() {
        val request = buildWriteRequest() ?: return
        binding.writeResultText.text =
            binding.root.context.getString(R.string.write_result_waiting)
        setWritingInProgress(true)
        callbacks?.onStartWriting(request)
    }

    private fun handleCancel() {
        callbacks?.onCancelWriting()
    }

    private fun buildWriteRequest(): WriteController.WriteRequest? {
        clearErrors()
        return when (currentOption) {
            CommandOption.IDM -> buildIdmRequest()
            CommandOption.SYSTEM -> buildSystemRequest()
            CommandOption.SERVICE -> buildServiceRequest()
            CommandOption.RAW -> buildRawRequest()
        }
    }

    private fun buildIdmRequest(): WriteController.WriteRequest? {
        val idmText = binding.writeIdmInput.text?.toString().orEmpty()
        val idmBytes = parseHex(idmText) ?: run {
            binding.writeIdmInputLayout.error =
                binding.root.context.getString(R.string.write_error_invalid_hex)
            return null
        }
        if (idmBytes.size != 8) {
            binding.writeIdmInputLayout.error =
                binding.root.context.getString(R.string.write_error_idm_length)
            return null
        }
        val pmmText = binding.writePmmInput.text?.toString().orEmpty()
        val pmmBytes = if (pmmText.isBlank()) {
            null
        } else {
            val parsed = parseHex(pmmText) ?: run {
                binding.writePmmInputLayout.error =
                    binding.root.context.getString(R.string.write_error_invalid_hex)
                return null
            }
            if (parsed.size != 8) {
                binding.writePmmInputLayout.error =
                    binding.root.context.getString(R.string.write_error_pmm_length)
                return null
            }
            parsed
        }
        return WriteController.WriteRequest.Idm(idm = idmBytes, pmm = pmmBytes)
    }

    private fun buildSystemRequest(): WriteController.WriteRequest? {
        val codes = parseCodeList(
            binding.writeSystemCodesInput.text?.toString().orEmpty(),
            WriteController.MAX_SYSTEM_CODES,
            binding.writeSystemCodesLayout
        ) ?: return null
        return WriteController.WriteRequest.SystemCodes(codes)
    }

    private fun buildServiceRequest(): WriteController.WriteRequest? {
        val codes = parseCodeList(
            binding.writeServiceCodesInput.text?.toString().orEmpty(),
            WriteController.MAX_SERVICE_CODES,
            binding.writeServiceCodesLayout
        ) ?: return null
        return WriteController.WriteRequest.ServiceCodes(codes)
    }

    private fun buildRawRequest(): WriteController.WriteRequest? {
        val blockText = binding.writeBlockNumberInput.text?.toString().orEmpty()
        val blockNumber = blockText.toIntOrNull()
        if (blockNumber == null || blockNumber !in 0..WriteController.MAX_RAW_BLOCK) {
            binding.writeBlockNumberLayout.error =
                binding.root.context.getString(R.string.write_error_block_range)
            return null
        }
        val dataText = binding.writeRawDataInput.text?.toString().orEmpty()
        val dataBytes = parseHex(dataText) ?: run {
            binding.writeRawDataLayout.error =
                binding.root.context.getString(R.string.write_error_invalid_hex)
            return null
        }
        if (dataBytes.size != 16) {
            binding.writeRawDataLayout.error =
                binding.root.context.getString(R.string.write_error_raw_length)
            return null
        }
        return WriteController.WriteRequest.RawBlock(blockNumber = blockNumber, data = dataBytes)
    }

    private fun parseCodeList(
        rawInput: String,
        maxCodes: Int,
        layout: TextInputLayout
    ): List<Int>? {
        val tokens = rawInput.split(',', ' ', '\n', '\t')
        val codes = mutableListOf<Int>()
        tokens.forEach { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.length != 4) {
                layout.error = binding.root.context.getString(
                    R.string.write_error_code_invalid,
                    trimmed
                )
                return null
            }
            val value = trimmed.toIntOrNull(16)
            if (value == null || value !in 0..0xFFFF) {
                layout.error = binding.root.context.getString(
                    R.string.write_error_code_invalid,
                    trimmed
                )
                return null
            }
            codes += value
        }
        if (codes.isEmpty()) {
            layout.error =
                binding.root.context.getString(R.string.write_error_code_required)
            return null
        }
        if (codes.size > maxCodes) {
            layout.error = binding.root.context.getString(
                R.string.write_error_code_limit,
                maxCodes
            )
            return null
        }
        return codes
    }

    private fun parseHex(text: String): ByteArray? {
        val sanitized = text.replace("\\s".toRegex(), "").uppercase(Locale.US)
        if (sanitized.isEmpty()) return ByteArray(0)
        if (sanitized.length % 2 != 0) return null
        val data = ByteArray(sanitized.length / 2)
        var index = 0
        while (index < sanitized.length) {
            val byteValue = sanitized.substring(index, index + 2).toIntOrNull(16) ?: return null
            data[index / 2] = byteValue.toByte()
            index += 2
        }
        return data
    }

    private fun setFieldGroupEnabled(enabled: Boolean) {
        binding.writeCommandDropdown.isEnabled = enabled
        binding.writeIdmInputLayout.isEnabled = enabled
        binding.writePmmInputLayout.isEnabled = enabled
        binding.writeIdmRandomButton.isEnabled = enabled
        binding.writePmmRandomButton.isEnabled = enabled
        binding.writeSystemCodesLayout.isEnabled = enabled
        binding.writeServiceCodesLayout.isEnabled = enabled
        binding.writeBlockNumberLayout.isEnabled = enabled
        binding.writeRawDataLayout.isEnabled = enabled
    }

    fun setWritingInProgress(inProgress: Boolean) {
        binding.writeStartButton.isEnabled = !inProgress
        binding.writeCancelButton.isEnabled = inProgress
        setFieldGroupEnabled(!inProgress)
    }

    fun showResultMessage(message: CharSequence) {
        binding.writeResultText.text = message
    }

    private fun clearErrors() {
        binding.writeIdmInputLayout.error = null
        binding.writePmmInputLayout.error = null
        binding.writeSystemCodesLayout.error = null
        binding.writeServiceCodesLayout.error = null
        binding.writeBlockNumberLayout.error = null
        binding.writeRawDataLayout.error = null
    }

    private fun randomizeIdm() {
        binding.writeIdmInput.setText(generateRandomHex(IDM_BYTE_LENGTH))
        binding.writeIdmInputLayout.error = null
    }

    private fun randomizePmm() {
        binding.writePmmInput.setText(generateRandomHex(PMM_BYTE_LENGTH))
        binding.writePmmInputLayout.error = null
    }

    private fun generateRandomHex(byteLength: Int): String {
        val buffer = ByteArray(byteLength)
        secureRandom.nextBytes(buffer)
        return buffer.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
        }
    }

    private fun showOption(option: CommandOption) {
        binding.writeIdmGroup.isVisible = option == CommandOption.IDM
        binding.writeSystemGroup.isVisible = option == CommandOption.SYSTEM
        binding.writeServiceGroup.isVisible = option == CommandOption.SERVICE
        binding.writeRawGroup.isVisible = option == CommandOption.RAW
    }

    companion object {
        private const val IDM_BYTE_LENGTH = 8
        private const val PMM_BYTE_LENGTH = 8
    }
}
