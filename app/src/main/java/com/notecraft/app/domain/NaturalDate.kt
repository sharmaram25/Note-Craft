package com.notecraft.app.domain

import com.notecraft.app.data.db.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Lightweight natural-language parser for reminder input, e.g.
 * "Call mom tomorrow at 6pm", "Standup every weekday 9:30", "Pay rent on 1 dec", "Stretch in 20 min".
 */
object NaturalDate {

    data class Result(val title: String, val dueAt: Long?, val repeat: Int)

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthPattern = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val dayNames = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val dayPattern = "(mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:r(?:s(?:day)?)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)"

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val rRelative = rx("""\bin\s+(\d{1,3}|an?|one|two|three|half an?)\s*(minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w)\b""")
    private val rEvery = rx("""\b(?:every\s+(day|weekday|week|month|year|$dayPattern)|(daily|weekdays|weekly|monthly|yearly|annually))\b""")
    private val rTime = rx("""\b(?:at\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)\b|\bat\s+(\d{1,2})(?:[:.](\d{2}))?\b|\b(\d{1,2})[:.](\d{2})\b""")
    private val rPartOfDay = rx("""\b(tonight|this evening|this morning|this afternoon|(?:in the\s+)?morning|(?:in the\s+)?afternoon|(?:in the\s+)?evening|noon|midday|midnight)\b""")
    private val rDayWord = rx("""\b(today|tomorrow|tmrw|tmr|day after tomorrow|next week|next month|this weekend|weekend)\b""")
    private val rWeekday = rx("""\b(?:on\s+|next\s+|this\s+)?$dayPattern\b""")
    private val rDateDm = rx("""\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?$monthPattern\b""")
    private val rDateMd = rx("""\b(?:on\s+)?$monthPattern\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val rDateNum = rx("""\b(?:on\s+)?(\d{1,2})[/\-](\d{1,2})(?:[/\-](\d{2,4}))?\b""")

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): Result {
        var text = " $input "
        var date: LocalDate? = null
        var time: LocalTime? = null
        var repeat = Repeat.NONE
        var relative: LocalDateTime? = null

        fun consume(m: MatchResult) {
            text = text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1)
        }

        rRelative.find(text)?.let { m ->
            val nRaw = m.groupValues[1].lowercase()
            val unit = m.groupValues[2].lowercase()
            val n: Long = when {
                nRaw.startsWith("half") -> -1
                nRaw == "a" || nRaw == "an" || nRaw == "one" -> 1
                nRaw == "two" -> 2
                nRaw == "three" -> 3
                else -> nRaw.toLongOrNull() ?: 1
            }
            relative = when {
                unit.startsWith("m") -> now.plusMinutes(n)
                unit.startsWith("h") -> if (n == -1L) now.plusMinutes(30) else now.plusHours(n)
                unit.startsWith("d") -> now.plusDays(n)
                else -> now.plusWeeks(n)
            }
            consume(m)
        }

        rEvery.find(text)?.let { m ->
            val v = (m.groupValues[1].ifEmpty { m.groupValues[3] }).lowercase()
            repeat = when {
                v == "day" || v == "daily" -> Repeat.DAILY
                v == "weekday" || v == "weekdays" -> Repeat.WEEKDAYS
                v == "week" || v == "weekly" -> Repeat.WEEKLY
                v == "month" || v == "monthly" -> Repeat.MONTHLY
                v == "year" || v == "yearly" || v == "annually" -> Repeat.YEARLY
                else -> {
                    dayNames[v.take(3)]?.let { dow -> date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow)) }
                    Repeat.WEEKLY
                }
            }
            consume(m)
        }

        rTime.find(text)?.let { m ->
            val g = m.groupValues
            var h: Int; val min: Int
            if (g[1].isNotEmpty()) {
                h = g[1].toInt(); min = g[2].toIntOrNull() ?: 0
                val pm = g[3].lowercase().startsWith("p")
                if (h == 12) h = 0
                if (pm) h += 12
            } else if (g[4].isNotEmpty()) {
                h = g[4].toInt(); min = g[5].toIntOrNull() ?: 0
                // "at 5" with no am/pm: assume the next sensible occurrence (5 -> 17:00 if it's afternoon-ish)
                if (h in 1..7) h += 12
            } else {
                h = g[6].toInt(); min = g[7].toInt()
            }
            if (h in 0..23 && min in 0..59) { time = LocalTime.of(h, min); consume(m) }
        }

        rPartOfDay.find(text)?.let { m ->
            val v = m.groupValues[1].lowercase()
            val t = when {
                "night" in v -> LocalTime.of(20, 0)
                "evening" in v -> LocalTime.of(18, 0)
                "afternoon" in v -> LocalTime.of(14, 0)
                "morning" in v -> LocalTime.of(9, 0)
                "midnight" in v -> LocalTime.of(23, 59)
                else -> LocalTime.NOON
            }
            if (time == null) time = t
            if (v.startsWith("this") || v == "tonight") date = date ?: now.toLocalDate()
            consume(m)
        }

        rDayWord.find(text)?.let { m ->
            val v = m.groupValues[1].lowercase()
            val today = now.toLocalDate()
            date = when (v) {
                "today" -> today
                "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
                "day after tomorrow" -> today.plusDays(2)
                "next week" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                "next month" -> today.plusMonths(1).withDayOfMonth(1)
                else -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            }
            if (v.contains("weekend") && time == null) time = LocalTime.of(10, 0)
            consume(m)
        }

        if (date == null) rDateDm.find(text)?.let { m ->
            date = buildDate(m.groupValues[1].toInt(), monthIndex(m.groupValues[2]), null, now.toLocalDate()); if (date != null) consume(m)
        }
        if (date == null) rDateMd.find(text)?.let { m ->
            date = buildDate(m.groupValues[2].toInt(), monthIndex(m.groupValues[1]), null, now.toLocalDate()); if (date != null) consume(m)
        }
        if (date == null) rDateNum.find(text)?.let { m ->
            // Day-first (dd/mm), falling back to mm/dd when the first number can't be a month day order.
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            val y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            val d = if (b <= 12) buildDate(a, b, y, now.toLocalDate()) else buildDate(b, a, y, now.toLocalDate())
            if (d != null) { date = d; consume(m) }
        }
        if (date == null) rWeekday.find(text)?.let { m ->
            val dow = dayNames[m.groupValues[1].lowercase().take(3)]
            if (dow != null) {
                val isNext = m.value.trim().lowercase().startsWith("next")
                val today = now.toLocalDate()
                date = if (isNext) today.with(TemporalAdjusters.next(dow))
                else today.with(TemporalAdjusters.nextOrSame(dow))
                consume(m)
            }
        }

        val title = text.replace(Regex("""\s+"""), " ").trim()
            .replace(Regex("""\s+(at|on|by|in|every|for)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(remind me to|remind me|to)\s+""", RegexOption.IGNORE_CASE), "")
            .trim().replaceFirstChar { it.uppercase() }

        val due: LocalDateTime? = when {
            relative != null -> relative
            date != null || time != null -> {
                var dt = LocalDateTime.of(date ?: now.toLocalDate(), time ?: LocalTime.of(9, 0))
                if (dt.isBefore(now)) {
                    dt = if (date == null) dt.plusDays(1)
                    else if (dt.toLocalDate() == now.toLocalDate() && time == null) now.plusHours(1).withMinute(0)
                    else if (date!!.isBefore(now.toLocalDate()) && date!!.year == now.year && repeat == Repeat.NONE) dt.plusYears(1)
                    else dt
                }
                dt
            }
            repeat != Repeat.NONE -> LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0))
            else -> null
        }
        val millis = due?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        return Result(title, millis, repeat)
    }

    private fun monthIndex(s: String) = months.indexOf(s.lowercase().take(3)) + 1

    private fun buildDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching {
            var d = LocalDate.of(year ?: today.year, month, day)
            if (year == null && d.isBefore(today)) d = d.plusYears(1)
            d
        }.getOrNull()
    }

    fun defaultDue(now: LocalDateTime = LocalDateTime.now()): Long {
        val dt = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
