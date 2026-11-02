package com.mcosta.mybooks.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.number
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

private val shortDateTime = LocalDateTime.Format {
    day(Padding.NONE); chars(" "); monthName(MonthNames.ENGLISH_ABBREVIATED); chars(", "); hour(); char(':'); minute()
}

/** "2 Nov, 10:15" in [zone] (the device's by default). */
fun formatDateTime(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    instant.toLocalDateTime(zone).format(shortDateTime)

/** The month in progress as `YYYY-MM`, in the device's time zone. */
fun currentMonth(): String = today().let { "${it.year}-${it.month.number.toString().padStart(2, '0')}" }
