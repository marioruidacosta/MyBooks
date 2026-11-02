package com.mcosta.mybooks.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DatesTest {
    @Test
    fun dateTimeIsDayMonthHoursMinutes() {
        assertEquals("2 Nov, 10:15", formatDateTime(Instant.parse("2026-11-02T10:15:00Z"), TimeZone.UTC))
        assertEquals("12 Oct, 09:05", formatDateTime(Instant.parse("2026-10-12T08:05:00Z"), TimeZone.of("Europe/Lisbon")))
    }
}
