package com.mcosta.mybooks.data.backup

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

actual object RawDeflate {
    actual fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 64)
            val chunk = ByteArray(8192)
            while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    actual fun inflate(data: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            // nowrap mode needs an extra dummy byte after the data.
            inflater.setInput(data + 0)
            val out = ByteArray(expectedSize)
            var produced = 0
            while (produced < expectedSize) {
                val n = inflater.inflate(out, produced, expectedSize - produced)
                if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                produced += n
            }
            check(produced == expectedSize) { "inflated $produced of $expectedSize bytes" }
            return out
        } finally {
            inflater.end()
        }
    }
}
