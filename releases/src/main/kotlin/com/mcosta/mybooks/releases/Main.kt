package com.mcosta.mybooks.releases

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.system.exitProcess

/** `releases --data-dir <path> [--today YYYY-MM-DD]`: one harvest run; exits non-zero (writing nothing) on failure. */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { it[0] to it.getOrElse(1) { "" } }
    val dataDir = options["--data-dir"]?.takeIf(String::isNotEmpty) ?: run {
        System.err.println("usage: releases --data-dir <path> [--today YYYY-MM-DD]")
        exitProcess(2)
    }
    val today = options["--today"]?.let(LocalDate::parse) ?: LocalDate.now(ZoneOffset.UTC)
    val summary = ReleasesJob(HttpOaiSource(), DataStore(Path.of(dataDir)), today, Instant.now()).run()
    println("Harvested ${summary.pages} page(s) in ${summary.windows} window(s); ${summary.newBooks} new book(s).")
}
