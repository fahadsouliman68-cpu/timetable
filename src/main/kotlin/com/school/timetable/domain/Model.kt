package com.school.timetable.domain

enum class ShiftKind { MORNING, EVENING }

enum class Strictness { MANDATORY, PREFERRED }

/** Minutes from midnight. */
data class TimeRange(val startMin: Int, val endMin: Int) {
    init {
        require(endMin > startMin) { "نهاية الوقت يجب أن تكون بعد بدايته" }
    }

    fun overlaps(other: TimeRange): Boolean = startMin < other.endMin && other.startMin < endMin
}

data class Period(val index: Int, val time: TimeRange)

data class Shift(val id: String, val kind: ShiftKind, val periods: List<Period>) {
    private val byIndex: Map<Int, Period> = periods.associateBy { it.index }
    fun period(index: Int): Period? = byIndex[index]
}

/** A teaching slot: a day (0 until SchoolData.days) inside a shift, at a period index of that shift. */
data class Slot(val day: Int, val shiftId: String, val periodIndex: Int)

data class Grade(val id: String, val name: String)

data class Section(
    val id: String,
    val name: String,
    val gradeId: String,
    val shiftId: String,
    val homeroomTeacherId: String? = null,
)

data class Subject(val id: String, val name: String)

/** How many lessons per week a grade needs in a subject. */
data class PlanItem(val gradeId: String, val subjectId: String, val weeklyPeriods: Int)

data class Teacher(
    val id: String,
    val name: String,
    val weeklyNorm: Int,
    val canWorkBothShifts: Boolean,
    val subjectIds: Set<String>,
    val unavailable: Set<Slot> = emptySet(),
)

/** Teacher teaches a subject to one section, for weeklyPeriods lessons per week. */
data class Assignment(
    val teacherId: String,
    val subjectId: String,
    val sectionId: String,
    val weeklyPeriods: Int,
)

/** One placed lesson. */
data class Entry(
    val sectionId: String,
    val slot: Slot,
    val subjectId: String,
    val teacherId: String,
    val locked: Boolean = false,
)

/** Preferred period indices for a subject (soft: affects quality only). Empty set = no preference. */
data class SubjectPreference(val subjectId: String, val preferredPeriods: Set<Int>)

/** Max consecutive periods for a teacher on one day. teacherId == null applies to all teachers. */
data class ConsecutiveRule(
    val teacherId: String?,
    val maxConsecutive: Int,
    val strictness: Strictness,
)

class SchoolData(
    val days: Int,
    val grades: List<Grade>,
    val shifts: List<Shift>,
    val sections: List<Section>,
    val subjects: List<Subject>,
    val teachers: List<Teacher>,
    val plan: List<PlanItem>,
    val assignments: List<Assignment>,
    val rules: List<ConsecutiveRule> = emptyList(),
    val preferences: List<SubjectPreference> = emptyList(),
) {
    val shiftById: Map<String, Shift> = shifts.associateBy { it.id }
    val sectionById: Map<String, Section> = sections.associateBy { it.id }
    val teacherById: Map<String, Teacher> = teachers.associateBy { it.id }
    val subjectById: Map<String, Subject> = subjects.associateBy { it.id }
    private val planByKey: Map<Pair<String, String>, PlanItem> = plan.associateBy { it.gradeId to it.subjectId }

    init {
        require(days in 1..7) { "عدد أيام الدوام يجب أن يكون بين 1 و7" }
        requireUnique("الورديات", shifts.map { it.id })
        requireUnique("الصفوف", grades.map { it.id })
        requireUnique("الشعب", sections.map { it.id })
        requireUnique("المواد", subjects.map { it.id })
        requireUnique("المعلمون", teachers.map { it.id })
        for (section in sections) {
            require(section.shiftId in shiftById) { "الشعبة ${section.id} تشير إلى وردية غير موجودة" }
            require(grades.any { it.id == section.gradeId }) { "الشعبة ${section.id} تشير إلى صف غير موجود" }
        }
        for (teacher in teachers) {
            require(teacher.weeklyNorm >= 0) { "نصاب المعلم ${teacher.id} سالب" }
            require(teacher.subjectIds.all { it in subjectById }) { "المعلم ${teacher.id} مرتبط بمادة غير موجودة" }
        }
        for (item in plan) {
            require(item.weeklyPeriods >= 0) { "حصص الخطة سالبة" }
            require(grades.any { it.id == item.gradeId } && item.subjectId in subjectById) { "خطة تشير إلى مرجع غير موجود" }
        }
        for (assignment in assignments) {
            require(assignment.weeklyPeriods > 0) { "تكليف بحصص صفرية" }
            require(assignment.teacherId in teacherById && assignment.subjectId in subjectById && assignment.sectionId in sectionById) {
                "تكليف يشير إلى مرجع غير موجود"
            }
        }
    }

    fun planFor(gradeId: String, subjectId: String): PlanItem? = planByKey[gradeId to subjectId]

    /** Time of a slot, or null if the slot does not exist in its shift. */
    fun timeOf(slot: Slot): TimeRange? = shiftById[slot.shiftId]?.period(slot.periodIndex)?.time

    /** Every slot of a shift across all active days. */
    fun slotsOfShift(shift: Shift): List<Slot> =
        (0 until days).flatMap { day -> shift.periods.map { Slot(day, shift.id, it.index) } }

    private fun requireUnique(label: String, ids: List<String>) {
        require(ids.size == ids.toSet().size) { "معرّفات $label مكررة" }
    }
}
