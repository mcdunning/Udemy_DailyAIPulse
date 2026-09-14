package com.dailyaipulse.articles.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DateFormatterTest {

    @Test
    fun `formats a date less than a minute old as Just now`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-13T11:59:30Z")

        assertEquals("Just now", formatDisplayDate(published.toString(), now))
    }

    @Test
    fun `formats a date under an hour old in minutes`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-13T11:45:00Z")

        assertEquals("15m ago", formatDisplayDate(published.toString(), now))
    }

    @Test
    fun `formats a date under twelve hours old in hours`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-13T05:00:00Z")

        assertEquals("7h ago", formatDisplayDate(published.toString(), now))
    }

    @Test
    fun `formats a date twelve or more hours old as an absolute date`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-12T23:00:00Z")

        // Computed the same way the production code computes it, rather than
        // hardcoded, so this test isn't dependent on the machine's timezone.
        val expected = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(published)

        assertEquals(expected, formatDisplayDate(published.toString(), now))
    }
}
