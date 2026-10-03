package com.dev.alex.portfolio.domain

import java.time.LocalDate

val MONTH_LABELS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val DATE_ONLY = Regex("""^(\d{4})-(\d{2})(?:-(\d{2}))?""")

/**
 * A calendar month read straight off an API string ('YYYY-MM' or 'YYYY-MM-DD').
 * Never goes through an instant, so no timezone can move a dividend into the previous
 * month (the web learnt that the hard way — see lib/dates.ts).
 */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    /** 'YYYY-MM', the key the dividend endpoints use */
    val key: String get() = "${year.toString().padStart(4, '0')}-${(month + 1).toString().padStart(2, '0')}"
    val label: String get() = MONTH_LABELS[month]
    val title: String get() = "$label $year"

    fun shift(by: Int): MonthKey {
        val index = year * 12 + month + by
        return MonthKey(Math.floorDiv(index, 12), Math.floorMod(index, 12))
    }

    override fun compareTo(other: MonthKey): Int =
        compareValuesBy(this, other, MonthKey::year, MonthKey::month)

    companion object {
        fun parse(value: String?): MonthKey? {
            val match = DATE_ONLY.find(value?.trim() ?: return null) ?: return null
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt() - 1
            return if (month in 0..11) MonthKey(year, month) else null
        }

        fun of(date: LocalDate) = MonthKey(date.year, date.monthValue - 1)

        /** `count` consecutive months starting at `month` (0-based) of `year` */
        fun range(year: Int, month: Int, count: Int): List<MonthKey> =
            (0 until count).map { MonthKey(year, month).shift(it) }
    }
}

/** Calendar year of an API date string. */
fun yearOf(value: String?): Int? = MonthKey.parse(value)?.year

/** 'YYYY-MM-DD' part of a date string, for grouping by day. */
fun dayOf(value: String?): String = value?.trim()?.take(10).orEmpty()
