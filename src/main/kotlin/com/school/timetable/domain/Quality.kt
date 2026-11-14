package com.school.timetable.domain

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Deduction(val label: String, val points: Double, val detail: String)

data class QualityReport(val score: Int, val deductions: List<Deduction>)

/**
 * Quality score out of 100. Each term is normalised to [0,1] against a worst case and multiplied by its weight.
 * Weights sum to 100. Hard rules are NOT part of the score: a schedule with hard violations is never scored as valid.
 */
object QualityScorer {
    const val W_GAPS = 20.0
    const val W_SUBJECT_BALANCE = 20.0
    const val W_REPETITION = 15.0
    const val W_PREFERRED_CONSECUTIVE = 10.0
    const val W_LOAD_BALANCE = 20.0
    const val W_PREFERENCES = 15.0

    fun score(data: SchoolData, entries: List<Entry>): QualityReport {
        val deductions = listOf(
            gaps(data, entries),
            subjectBalance(data, entries),
            repetition(entries),
            preferredConsecutive(data, entries),
            loadBalance(data, entries),
            preferences(data, entries),
        )
        val total = deductions.sumOf { it.points }
        return QualityReport((100.0 - total).roundToInt().coerceIn(0, 100), deductions)
    }

    /** Free periods between a teacher's first and last lesson of a day, within one shift. */
    private fun gaps(data: SchoolData, entries: List<Entry>): Deduction {
        var raw = 0
        for ((_, teacherList) in entries.groupBy { it.teacherId }) {
            for ((_, dayList) in teacherList.groupBy { it.slot.day }) {
                for ((_, shiftList) in dayList.groupBy { it.slot.shiftId }) {
                    val indices = shiftList.map { it.slot.periodIndex }
                    raw += (indices.max() - indices.min() + 1) - indices.distinct().size
                }
            }
        }
        val maxPeriods = data.shifts.maxOfOrNull { it.periods.size } ?: 1
        val cap = maxOf(1, data.teachers.size) * data.days * maxPeriods
        val norm = min(1.0, raw.toDouble() / cap)
        return Deduction("فراغات المعلمين", W_GAPS * norm, "فراغات: $raw")
    }

    /** Spread of each section-subject across days, against the most concentrated possible spread. */
    private fun subjectBalance(data: SchoolData, entries: List<Entry>): Deduction {
        val groups = entries.groupBy { it.sectionId to it.subjectId }.filter { it.value.size >= 2 }
        if (groups.isEmpty()) return Deduction("توازن المادة على الأيام", 0.0, "لا توجد مواد تحتاج موازنة")
        var sum = 0.0
        for ((_, list) in groups) {
            val perDay = IntArray(data.days)
            list.forEach { perDay[it.slot.day]++ }
            val worst = worstStdDev(list.size, data.days)
            if (worst > 0.0) sum += min(1.0, stdDev(perDay.toList()) / worst)
        }
        val norm = sum / groups.size
        return Deduction("توازن المادة على الأيام", W_SUBJECT_BALANCE * norm, "مجموعات مدروسة: ${groups.size}")
    }

    /** Same subject more than once in a section on one day. */
    private fun repetition(entries: List<Entry>): Deduction {
        if (entries.isEmpty()) return Deduction("تكرار المادة في اليوم", 0.0, "لا حصص")
        val extra = entries.groupBy { Triple(it.sectionId, it.slot.day, it.subjectId) }.values.sumOf { it.size - 1 }
        val norm = extra.toDouble() / entries.size
        return Deduction("تكرار المادة في اليوم", W_REPETITION * norm, "تكرارات زائدة: $extra")
    }

    private fun preferredConsecutive(data: SchoolData, entries: List<Entry>): Deduction {
        val rules = data.rules.filter { it.strictness == Strictness.PREFERRED }
        if (rules.isEmpty()) return Deduction("المتتالي المفضّل", 0.0, "لا توجد قواعد مفضّلة")
        var violations = 0
        var teacherDays = 0
        for ((teacherId, list) in entries.groupBy { it.teacherId }) {
            for ((_, dayList) in list.groupBy { it.slot.day }) {
                teacherDays++
                val run = longestConsecutiveRun(dayList.map { it.slot })
                for (rule in rules) {
                    if (rule.teacherId != null && rule.teacherId != teacherId) continue
                    if (run > rule.maxConsecutive) violations++
                }
            }
        }
        val norm = min(1.0, violations.toDouble() / maxOf(1, teacherDays))
        return Deduction("المتتالي المفضّل", W_PREFERRED_CONSECUTIVE * norm, "مخالفات: $violations")
    }

    /** Lessons placed outside the subject's preferred periods, among subjects that declare a preference. */
    private fun preferences(data: SchoolData, entries: List<Entry>): Deduction {
        val prefs = data.preferences.filter { it.preferredPeriods.isNotEmpty() }.associate { it.subjectId to it.preferredPeriods }
        val relevant = entries.filter { it.subjectId in prefs }
        if (relevant.isEmpty()) return Deduction("تفضيلات المادة", 0.0, "لا تفضيلات مُعرَّفة")
        val outside = relevant.count { it.slot.periodIndex !in prefs.getValue(it.subjectId) }
        val norm = outside.toDouble() / relevant.size
        return Deduction("تفضيلات المادة", W_PREFERENCES * norm, "خارج الأوقات المفضّلة: $outside من ${relevant.size}")
    }

    /** Spread of each teacher's daily load, against the most concentrated possible spread. */
    private fun loadBalance(data: SchoolData, entries: List<Entry>): Deduction {
        var sum = 0.0
        var counted = 0
        val byTeacher = entries.groupBy { it.teacherId }
        for (teacher in data.teachers) {
            val perDay = IntArray(data.days)
            byTeacher[teacher.id].orEmpty().forEach { perDay[it.slot.day]++ }
            val total = perDay.sum()
            if (total < 2) continue
            val worst = worstStdDev(total, data.days)
            if (worst <= 0.0) continue
            sum += min(1.0, stdDev(perDay.toList()) / worst)
            counted++
        }
        val norm = if (counted == 0) 0.0 else sum / counted
        return Deduction("توازن أعباء المعلمين", W_LOAD_BALANCE * norm, "معلمون مدروسون: $counted")
    }

    private fun stdDev(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    /** Standard deviation when all n items sit on one day. */
    private fun worstStdDev(n: Int, days: Int): Double {
        if (n == 0 || days <= 1) return 0.0
        val values = IntArray(days)
        values[0] = n
        return stdDev(values.toList())
    }
}
