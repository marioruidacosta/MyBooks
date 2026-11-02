package com.mcosta.mybooks.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val shortDate = LocalDate.Format {
    day(); chars(" "); monthName(MonthNames.ENGLISH_ABBREVIATED); chars(" "); year()
}

/** "31 Oct 2026" in the device's time zone. */
fun formatDate(epochMs: Long): String = localDate(epochMs).format(shortDate)

fun localDate(epochMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date

fun today(): LocalDate = localDate(Clock.System.now().toEpochMilliseconds())
