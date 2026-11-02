package com.mcosta.mybooks.data.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/** Raw DEFLATE via zlib with windowBits = -15 (no zlib header/trailer). */
@OptIn(ExperimentalForeignApi::class)
actual object RawDeflate {
    private const val RAW_WINDOW_BITS = -15

    actual fun deflate(data: ByteArray): ByteArray = memScoped {
        val stream = alloc<z_stream>()
        check(deflateInit2_(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, RAW_WINDOW_BITS, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().toInt()) == Z_OK) { "deflateInit failed" }
        try {
            val out = ByteArray(deflateBound(stream.ptr, data.size.toULong()).toInt() + 16)
            val input = if (data.isEmpty()) ByteArray(1) else data
            input.usePinned { inPin ->
                out.usePinned { outPin ->
                    stream.next_in = inPin.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_in = data.size.toUInt()
                    stream.next_out = outPin.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_out = out.size.toUInt()
                    check(deflate(stream.ptr, Z_FINISH) == Z_STREAM_END) { "deflate failed" }
                }
            }
            out.copyOf(stream.total_out.toInt())
        } finally {
            deflateEnd(stream.ptr)
        }
    }

    actual fun inflate(data: ByteArray, expectedSize: Int): ByteArray = memScoped {
        val stream = alloc<z_stream>()
        check(inflateInit2_(stream.ptr, RAW_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().toInt()) == Z_OK) { "inflateInit failed" }
        try {
            val out = ByteArray(expectedSize.coerceAtLeast(1))
            val input = if (data.isEmpty()) ByteArray(1) else data
            input.usePinned { inPin ->
                out.usePinned { outPin ->
                    stream.next_in = inPin.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_in = data.size.toUInt()
                    stream.next_out = outPin.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_out = out.size.toUInt()
                    val result = inflate(stream.ptr, Z_FINISH)
                    check(result == Z_STREAM_END || (result == Z_OK && stream.total_out.toInt() == expectedSize)) { "inflate failed ($result)" }
                }
            }
            check(stream.total_out.toInt() == expectedSize) { "inflated ${stream.total_out} of $expectedSize bytes" }
            out.copyOf(expectedSize)
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}
