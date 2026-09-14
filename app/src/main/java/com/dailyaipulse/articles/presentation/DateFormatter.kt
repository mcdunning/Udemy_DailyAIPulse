package com.dailyaipulse.articles.presentation

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatDisplayDate(isoDate: String, now: Instant = Instant.now()): String {
    val publishedInstant = Instant.parse(isoDate)
    val duration = Duration.between(publishedInstant, now)
    return if (duration.toHours() < 12) {
        when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            else -> "${duration.toHours()}h ago"
        }
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(publishedInstant)
    }
}
