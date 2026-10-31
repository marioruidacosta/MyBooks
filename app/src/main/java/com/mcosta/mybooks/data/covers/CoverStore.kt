package com.mcosta.mybooks.data.covers

import java.io.File
import java.util.UUID

/**
 * Cover photos taken in the app, stored as JPEG files in [dir]. Books refer to them as
 * `cover:<file name>` so the reference survives reinstalls and backup restores.
 */
class CoverStore(val dir: File) {

    fun isLocal(ref: String?): Boolean = ref != null && ref.startsWith(PREFIX)

    /** The file for a `cover:` reference, or null for web URLs and malformed references. */
    fun fileFor(ref: String?): File? {
        if (!isLocal(ref)) return null
        val name = ref!!.removePrefix(PREFIX)
        if (!isSafeName(name)) return null
        return File(dir, name)
    }

    /** What to give the image loader: the file for a local cover, the URL otherwise. */
    fun imageModel(ref: String?): Any? = fileFor(ref) ?: ref?.takeUnless { isLocal(it) }

    /** Stores a new photo and returns its `cover:` reference. */
    fun save(jpeg: ByteArray): String = put("${UUID.randomUUID()}.jpg", jpeg)

    /** Stores [jpeg] under [name] (used when restoring backups). */
    fun put(name: String, jpeg: ByteArray): String {
        require(isSafeName(name)) { "bad cover name: $name" }
        dir.mkdirs()
        File(dir, name).writeBytes(jpeg)
        return PREFIX + name
    }

    fun delete(ref: String?) {
        fileFor(ref)?.delete()
    }

    companion object {
        const val PREFIX = "cover:"

        fun isSafeName(name: String) = name.isNotEmpty() && name.matches(Regex("[A-Za-z0-9._-]+")) && name != "." && name != ".."
    }
}
