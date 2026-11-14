package com.school.timetable.domain

enum class Severity { BLOCKING, MISSING, WARNING }

enum class ViolationType(val severity: Severity) {
    REFERENCE_UNKNOWN(Severity.BLOCKING),
    OUTSIDE_SHIFT(Severity.BLOCKING),
    SLOT_OCCUPIED(Severity.BLOCKING),
    TEACHER_DOUBLE_BOOKED(Severity.BLOCKING),
    TEACHER_UNAVAILABLE(Severity.BLOCKING),
    SUBJECT_NOT_ALLOWED(Severity.BLOCKING),
    NO_ASSIGNMENT(Severity.BLOCKING),
    ASSIGNMENT_EXCEEDED(Severity.BLOCKING),
    NORM_EXCEEDED(Severity.BLOCKING),
    PLAN_EXCEEDED(Severity.BLOCKING),
    SHIFT_POLICY(Severity.BLOCKING),
    MANDATORY_CONSECUTIVE(Severity.BLOCKING),
    ASSIGNMENT_MISSING(Severity.MISSING),
    PLAN_MISSING(Severity.MISSING),
    PREFERRED_CONSECUTIVE(Severity.WARNING),
}

data class Violation(
    val type: ViolationType,
    val message: String,
    val sectionId: String? = null,
    val teacherId: String? = null,
    val subjectId: String? = null,
    val slot: Slot? = null,
) {
    val severity: Severity get() = type.severity
}

/**
 * Incremental checker: "may this one lesson be placed now, given the lessons already placed?"
 * Used by the manual editor, before saving, and inside the CSP search. It never mutates anything.
 */
object ConflictEngine {

    fun checkPlacement(data: SchoolData, current: Collection<Entry>, candidate: Entry): List<Violation> {
        val section = data.sectionById[candidate.sectionId]
        val teacher = data.teacherById[candidate.teacherId]
        val subject = data.subjectById[candidate.subjectId]
        if (section == null || teacher == null || subject == null) {
            return listOf(Violation(ViolationType.REFERENCE_UNKNOWN, "مرجع غير معروف في الحصة", candidate.sectionId, candidate.teacherId, candidate.subjectId, candidate.slot))
        }
        val slot = candidate.slot
        val out = ArrayList<Violation>()
        fun add(type: ViolationType, message: String) =
            out.add(Violation(type, message, section.id, teacher.id, subject.id, slot))

        val shift = data.shiftById[slot.shiftId]
        val inside = slot.day in 0 until data.days && shift != null &&
            slot.shiftId == section.shiftId && shift.period(slot.periodIndex) != null
        if (!inside) add(ViolationType.OUTSIDE_SHIFT, "الحصة خارج دوام الشعبة")

        if (current.any { it.sectionId == section.id && it.slot == slot }) {
            add(ViolationType.SLOT_OCCUPIED, "الشعبة لديها درس في هذه الخانة")
        }
        if (slot in teacher.unavailable) add(ViolationType.TEACHER_UNAVAILABLE, "المعلم غير متوفر في هذه الحصة")
        if (subject.id !in teacher.subjectIds) add(ViolationType.SUBJECT_NOT_ALLOWED, "المادة غير مسموحة لهذا المعلم")

        val assignment = data.assignments.firstOrNull {
            it.teacherId == teacher.id && it.subjectId == subject.id && it.sectionId == section.id
        }
        if (assignment == null) {
            add(ViolationType.NO_ASSIGNMENT, "لا يوجد تكليف لهذا المعلم بهذه المادة لهذه الشعبة")
        } else {
            val used = current.count { it.teacherId == teacher.id && it.subjectId == subject.id && it.sectionId == section.id }
            if (used + 1 > assignment.weeklyPeriods) add(ViolationType.ASSIGNMENT_EXCEEDED, "تجاوز حصص التكليف")
        }

        val teacherEntries = current.filter { it.teacherId == teacher.id }
        if (teacherEntries.size + 1 > teacher.weeklyNorm) add(ViolationType.NORM_EXCEEDED, "تجاوز النصاب الأسبوعي للمعلم")

        if (!teacher.canWorkBothShifts && teacherEntries.any { it.slot.shiftId != slot.shiftId }) {
            add(ViolationType.SHIFT_POLICY, "المعلم لا يعمل في الورديتين")
        }

        val myTime = data.timeOf(slot)
        if (myTime != null) {
            val overlaps = teacherEntries.any { e ->
                e.slot.day == slot.day && data.timeOf(e.slot)?.overlaps(myTime) == true
            }
            if (overlaps) add(ViolationType.TEACHER_DOUBLE_BOOKED, "المعلم في مكانين في الوقت نفسه")
        }

        val sameDaySlots = teacherEntries.filter { it.slot.day == slot.day }.map { it.slot } + slot
        for (rule in data.rules) {
            if (rule.teacherId != null && rule.teacherId != teacher.id) continue
            if (longestConsecutiveRun(sameDaySlots) > rule.maxConsecutive) {
                if (rule.strictness == Strictness.MANDATORY) {
                    add(ViolationType.MANDATORY_CONSECUTIVE, "تجاوز الحد الإلزامي للحصص المتتالية")
                } else {
                    add(ViolationType.PREFERRED_CONSECUTIVE, "تجاوز الحد المفضّل للحصص المتتالية")
                }
            }
        }
        return out
    }
}

/** Longest run of consecutive period indices (same shift) among slots that share one day. */
internal fun longestConsecutiveRun(slots: Collection<Slot>): Int {
    var best = 0
    for ((_, group) in slots.groupBy { it.shiftId }) {
        val indices = group.map { it.periodIndex }.distinct().sorted()
        var run = 0
        var prev = Int.MIN_VALUE
        for (index in indices) {
            run = if (index == prev + 1) run + 1 else 1
            if (run > best) best = run
            prev = index
        }
    }
    return best
}
