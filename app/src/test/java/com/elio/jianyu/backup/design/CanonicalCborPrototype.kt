package com.elio.jianyu.backup.design

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * PR09-13A design prototype for the restricted deterministic CBOR subset.
 *
 * Not a production API and not used by the app runtime.
 */
internal sealed interface PrototypeCborValue {
    data class Unsigned(val value: Long) : PrototypeCborValue
    data class Bytes(val value: ByteArray) : PrototypeCborValue {
        override fun equals(other: Any?): Boolean =
            other is Bytes && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()
    }
    data class Text(val value: String) : PrototypeCborValue
    data class ArrayValue(val values: List<PrototypeCborValue>) : PrototypeCborValue
    data class MapValue(val values: Map<Long, PrototypeCborValue>) : PrototypeCborValue
}

internal object CanonicalCborPrototype {
    fun encode(value: PrototypeCborValue): ByteArray {
        val output = ByteArrayOutputStream()
        writeValue(output, value)
        return output.toByteArray()
    }

    fun decodeCanonical(
        bytes: ByteArray,
        errorCode: BackupDesignErrorCode = BackupDesignErrorCode.INVALID_HEADER,
    ): PrototypeCborValue {
        val reader = Reader(bytes, errorCode)
        val value = reader.readValue()
        if (!reader.isAtEnd()) throw BackupDesignException(errorCode)
        if (!encode(value).contentEquals(bytes)) throw BackupDesignException(errorCode)
        return value
    }

    private fun writeValue(output: ByteArrayOutputStream, value: PrototypeCborValue) {
        when (value) {
            is PrototypeCborValue.Unsigned -> writeLength(output, 0, value.value)
            is PrototypeCborValue.Bytes -> {
                writeLength(output, 2, value.value.size.toLong())
                output.write(value.value)
            }
            is PrototypeCborValue.Text -> {
                val encoded = value.value.toByteArray(StandardCharsets.UTF_8)
                writeLength(output, 3, encoded.size.toLong())
                output.write(encoded)
            }
            is PrototypeCborValue.ArrayValue -> {
                writeLength(output, 4, value.values.size.toLong())
                value.values.forEach { writeValue(output, it) }
            }
            is PrototypeCborValue.MapValue -> {
                val entries = value.values.entries.sortedBy { it.key }
                writeLength(output, 5, entries.size.toLong())
                entries.forEach { (key, entryValue) ->
                    require(key >= 0L) { "CBOR map key must be unsigned" }
                    writeLength(output, 0, key)
                    writeValue(output, entryValue)
                }
            }
        }
    }

    private fun writeLength(output: ByteArrayOutputStream, major: Int, value: Long) {
        require(value >= 0L) { "CBOR value must be unsigned" }
        val prefix = major shl 5
        when {
            value < 24L -> output.write(prefix or value.toInt())
            value <= 0xffL -> {
                output.write(prefix or 24)
                output.write(value.toInt())
            }
            value <= 0xffffL -> {
                output.write(prefix or 25)
                output.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array())
            }
            value <= 0xffff_ffffL -> {
                output.write(prefix or 26)
                output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array())
            }
            else -> {
                output.write(prefix or 27)
                output.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array())
            }
        }
    }

    private class Reader(
        private val bytes: ByteArray,
        private val errorCode: BackupDesignErrorCode,
    ) {
        private var offset = 0

        fun isAtEnd(): Boolean = offset == bytes.size

        fun readValue(): PrototypeCborValue {
            val initial = readUnsignedByte()
            val major = initial ushr 5
            val additional = initial and 0x1f
            if (additional == 31) fail()
            return when (major) {
                0 -> PrototypeCborValue.Unsigned(readLength(additional))
                2 -> PrototypeCborValue.Bytes(readBytes(readLength(additional)))
                3 -> PrototypeCborValue.Text(decodeUtf8(readBytes(readLength(additional))))
                4 -> {
                    val count = checkedCount(readLength(additional))
                    PrototypeCborValue.ArrayValue(List(count) { readValue() })
                }
                5 -> {
                    val count = checkedCount(readLength(additional))
                    val map = linkedMapOf<Long, PrototypeCborValue>()
                    var previousKey = -1L
                    repeat(count) {
                        val key = (readValue() as? PrototypeCborValue.Unsigned)?.value ?: fail()
                        if (key <= previousKey || map.containsKey(key)) fail()
                        previousKey = key
                        map[key] = readValue()
                    }
                    PrototypeCborValue.MapValue(map)
                }
                else -> fail()
            }
        }

        private fun readLength(additional: Int): Long = when (additional) {
            in 0..23 -> additional.toLong()
            24 -> readUnsignedByte().toLong().also { if (it < 24L) fail() }
            25 -> readUnsignedShort().toLong().also { if (it <= 0xffL) fail() }
            26 -> readUnsignedInt().also { if (it <= 0xffffL) fail() }
            27 -> readLong().also { if (it < 0L || it <= 0xffff_ffffL) fail() }
            else -> fail()
        }

        private fun checkedCount(value: Long): Int {
            if (value > Int.MAX_VALUE) fail()
            return value.toInt()
        }

        private fun readBytes(length: Long): ByteArray {
            if (length > Int.MAX_VALUE) fail()
            val size = length.toInt()
            if (size < 0 || offset > bytes.size - size) fail()
            return bytes.copyOfRange(offset, offset + size).also { offset += size }
        }

        private fun readUnsignedByte(): Int {
            if (offset >= bytes.size) fail()
            return bytes[offset++].toInt() and 0xff
        }

        private fun readUnsignedShort(): Int {
            val raw = readBytes(2)
            return ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff
        }

        private fun readUnsignedInt(): Long {
            val raw = readBytes(4)
            return ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffff_ffffL
        }

        private fun readLong(): Long {
            val raw = readBytes(8)
            return ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN).long
        }

        private fun decodeUtf8(value: ByteArray): String = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString()
        } catch (_: Exception) {
            fail()
        }

        private fun fail(): Nothing = throw BackupDesignException(errorCode)
    }
}

internal fun PrototypeCborValue.MapValue.requireUnsigned(key: Long): Long =
    (values[key] as? PrototypeCborValue.Unsigned)?.value
        ?: throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)

internal fun PrototypeCborValue.MapValue.requireBytes(key: Long): ByteArray =
    (values[key] as? PrototypeCborValue.Bytes)?.value
        ?: throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)

internal fun PrototypeCborValue.MapValue.requireText(key: Long): String =
    (values[key] as? PrototypeCborValue.Text)?.value
        ?: throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
