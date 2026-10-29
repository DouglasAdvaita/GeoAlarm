package io.github.douglasadvaita.geoalarm.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object TrackDates {
    fun dayKey(ms: Long): Int =
        dayKey(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())

    fun dayKey(d: LocalDate): Int = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

    fun dateOf(key: Int): LocalDate =
        LocalDate.of(key / 10000, (key / 100) % 100, key % 100)

    fun weekRange(anyDay: LocalDate): Pair<Int, Int> {
        val monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return dayKey(monday) to dayKey(monday.plusDays(6))
    }

    fun monthRange(anyDay: LocalDate): Pair<Int, Int> {
        val first = anyDay.withDayOfMonth(1)
        val last = anyDay.with(TemporalAdjusters.lastDayOfMonth())
        return dayKey(first) to dayKey(last)
    }

    fun weekdayOf(key: Int): DayOfWeek = dateOf(key).dayOfWeek

    fun weekdayShort(key: Int): String =
        dateOf(key).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "UY"))
            .lowercase(Locale.US).removeSuffix(".")

    fun monthName(d: LocalDate): String =
        d.month.getDisplayName(TextStyle.FULL, Locale("es", "UY"))
            .replaceFirstChar { it.uppercase() }
}
