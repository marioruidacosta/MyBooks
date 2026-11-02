package com.mcosta.mybooks.data.backup

/** Raw DEFLATE (no zlib header), as used inside ZIP files. Android: java.util.zip; iOS: zlib. */
expect object RawDeflate {
    fun deflate(data: ByteArray): ByteArray

    /** Throws if [data] isn't valid DEFLATE data that inflates to exactly [expectedSize] bytes. */
    fun inflate(data: ByteArray, expectedSize: Int): ByteArray
}

/**
 * Minimal ZIP reader/writer for backups: STORED and DEFLATE entries, UTF-8 names, no ZIP64.
 * Reads archives written by java.util.zip (sizes and CRCs come from the central directory).
 */
object Zip {
    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL_DIR = 0x06054b50
    private const val STORED = 0
    private const val DEFLATED = 8
    private const val UTF8_NAMES = 0x0800
    private const val DOS_DATE_1980_01_01 = 0x0021

    fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes.int32(0) == LOCAL_HEADER

    fun write(entries: List<Pair<String, ByteArray>>, compress: Boolean = true): ByteArray {
        val out = ByteSink()
        val central = ByteSink()
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data).toInt()
            val method = if (compress) DEFLATED else STORED
            val stored = if (compress) RawDeflate.deflate(data) else data
            val offset = out.size
            out.int32(LOCAL_HEADER); out.int16(20); out.int16(UTF8_NAMES); out.int16(method)
            out.int16(0); out.int16(DOS_DATE_1980_01_01)
            out.int32(crc); out.int32(stored.size); out.int32(data.size)
            out.int16(nameBytes.size); out.int16(0); out.bytes(nameBytes); out.bytes(stored)

            central.int32(CENTRAL_HEADER); central.int16(20); central.int16(20); central.int16(UTF8_NAMES); central.int16(method)
            central.int16(0); central.int16(DOS_DATE_1980_01_01)
            central.int32(crc); central.int32(stored.size); central.int32(data.size)
            central.int16(nameBytes.size); central.int16(0); central.int16(0) // name, extra, comment lengths
            central.int16(0); central.int16(0); central.int32(0) // disk, internal and external attributes
            central.int32(offset); central.bytes(nameBytes)
        }
        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        out.int32(END_OF_CENTRAL_DIR); out.int16(0); out.int16(0)
        out.int16(entries.size); out.int16(entries.size)
        out.int32(centralBytes.size); out.int32(centralOffset); out.int16(0)
        return out.toByteArray()
    }

    /** Entries in archive order. Throws [BackupFormatException] for anything that isn't a valid ZIP. */
    fun read(bytes: ByteArray): List<Pair<String, ByteArray>> = try {
        readEntries(bytes)
    } catch (e: BackupFormatException) {
        throw e
    } catch (e: Exception) {
        throw BackupFormatException("Damaged backup file", e)
    }

    private fun readEntries(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val end = findEndOfCentralDirectory(bytes) ?: throw BackupFormatException("Not a MyBooks backup file")
        val count = bytes.int16(end + 10)
        var pos = bytes.int32(end + 16)
        return List(count) {
            if (bytes.int32(pos) != CENTRAL_HEADER) throw BackupFormatException("Damaged backup file")
            val method = bytes.int16(pos + 10)
            val crc = bytes.int32(pos + 16).toUInt()
            val compressedSize = bytes.int32(pos + 20)
            val size = bytes.int32(pos + 24)
            val nameLength = bytes.int16(pos + 28)
            val entryLength = 46 + nameLength + bytes.int16(pos + 30) + bytes.int16(pos + 32)
            val localOffset = bytes.int32(pos + 42)
            val name = bytes.decodeToString(pos + 46, pos + 46 + nameLength)
            pos += entryLength

            if (bytes.int32(localOffset) != LOCAL_HEADER) throw BackupFormatException("Damaged backup file")
            val dataStart = localOffset + 30 + bytes.int16(localOffset + 26) + bytes.int16(localOffset + 28)
            val raw = bytes.copyOfRange(dataStart, dataStart + compressedSize)
            val data = when (method) {
                STORED -> raw
                DEFLATED -> RawDeflate.inflate(raw, size)
                else -> throw BackupFormatException("Unsupported compression in backup file")
            }
            if (data.size != size || crc32(data) != crc) throw BackupFormatException("Damaged backup file")
            name to data
        }
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int? {
        val lowest = maxOf(0, bytes.size - 22 - 0xFFFF)
        for (i in bytes.size - 22 downTo lowest) if (bytes.int32(i) == END_OF_CENTRAL_DIR) return i
        return null
    }

    private fun ByteArray.int16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.int32(at: Int): Int = int16(at) or (int16(at + 2) shl 16)

    private class ByteSink {
        private var buffer = ByteArray(1024)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
        }

        fun int16(v: Int) { ensure(2); buffer[size++] = v.toByte(); buffer[size++] = (v shr 8).toByte() }
        fun int32(v: Int) { int16(v and 0xFFFF); int16(v ushr 16) }
        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buffer, size); size += b.size }
        fun toByteArray() = buffer.copyOf(size)
    }
}

private val crcTable = UIntArray(256) { n ->
    var c = n.toUInt()
    repeat(8) { c = if (c and 1u != 0u) 0xEDB88320u xor (c shr 1) else c shr 1 }
    c
}

/** CRC-32 (IEEE 802.3), as stored in ZIP entries. */
fun crc32(data: ByteArray): UInt {
    var c = 0xFFFFFFFFu
    for (b in data) c = crcTable[((c xor b.toUInt()) and 0xFFu).toInt()] xor (c shr 8)
    return c xor 0xFFFFFFFFu
}
