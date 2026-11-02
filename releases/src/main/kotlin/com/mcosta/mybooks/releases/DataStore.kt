package com.mcosta.mybooks.releases

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class MonthFile(val version: Int = 1, val month: String, val updated: String, val books: List<ReleaseBook>)

@Serializable
data class IndexMonth(val month: String, val complete: Boolean, val total: Int, val genres: Map<String, Int>)

@Serializable
data class IndexFile(val version: Int = 1, val updated: String, val months: List<IndexMonth>)

@Serializable
data class JobState(val harvestedUntil: String)

/**
 * The job's data directory (the `releases-data` branch): `state.json` and `seen.txt` at the root, the
 * published `releases/index.json` and `releases/<YYYY-MM>.json` below.
 */
class DataStore(private val dir: Path) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val releases = dir.resolve("releases")
    private val monthName = Regex("""\d{4}-\d{2}\.json""")

    fun state(): JobState? = dir.resolve("state.json").takeIf { it.exists() }?.let { json.decodeFromString(it.readText()) }

    fun seen(): Set<String> =
        dir.resolve("seen.txt").takeIf { it.exists() }?.readLines()?.map(String::trim)?.filter(String::isNotEmpty)?.toSet() ?: emptySet()

    fun month(month: String): MonthFile? =
        releases.resolve("$month.json").takeIf { it.exists() }?.let { json.decodeFromString(it.readText()) }

    fun index(): IndexFile? = releases.resolve("index.json").takeIf { it.exists() }?.let { json.decodeFromString(it.readText()) }

    /** The months that have a file, oldest first. */
    fun months(): List<String> =
        if (!releases.exists()) emptyList()
        else releases.listDirectoryEntries().map { it.name }.filter { monthName.matches(it) }.map { it.removeSuffix(".json") }.sorted()

    /** Writes everything for one run; each file is replaced whole (written aside, then moved). */
    fun write(state: JobState, seen: Set<String>, months: Map<String, MonthFile>, index: IndexFile, remove: List<String>) {
        releases.createDirectories()
        for ((month, file) in months) put(releases.resolve("$month.json"), json.encodeToString(file))
        put(releases.resolve("index.json"), json.encodeToString(index))
        put(dir.resolve("seen.txt"), seen.sorted().joinToString("\n", postfix = "\n"))
        for (month in remove) releases.resolve("$month.json").deleteIfExists()
        put(dir.resolve("state.json"), json.encodeToString(state))
    }

    private fun put(target: Path, text: String) {
        val temp = target.resolveSibling(target.name + ".tmp")
        temp.writeText(text)
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
