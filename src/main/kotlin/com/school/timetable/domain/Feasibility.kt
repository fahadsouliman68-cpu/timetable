package com.school.timetable.domain

/** One lesson still waiting for a slot. */
data class LessonUnit(val sectionId: String, val subjectId: String, val teacherId: String)

data class FeasibilityIssue(
    val code: String,
    val message: String,
    val sectionId: String? = null,
    val teacherId: String? = null,
)

/**
 * Cheap necessary conditions, checked before any search. Every check is a relaxation:
 * if it reports an issue, no timetable can exist. It may miss infeasibility that needs search.
 */
object FeasibilityChecker {

    fun check(data: SchoolData, fixed: List<Entry>, units: List<LessonUnit>): List<FeasibilityIssue> {
        val issues = ArrayList<FeasibilityIssue>()

        for ((sectionId, list) in units.groupBy { it.sectionId }) {
            val section = data.sectionById[sectionId]
            if (section == null) {
                issues += FeasibilityIssue("UNKNOWN_SECTION", "شعبة غير معروفة: $sectionId", sectionId = sectionId)
                continue
            }
            val shift = data.shiftById.getValue(section.shiftId)
            val total = data.days * shift.periods.size
            val free = total - fixed.count { it.sectionId == sectionId }
            if (list.size > free) {
                issues += FeasibilityIssue(
                    "SECTION_SLOTS",
                    "الشعبة ${section.name} تحتاج ${list.size} حصة والخانات المتاحة $free فقط",
                    sectionId = sectionId,
                )
            }
        }

        for ((teacherId, list) in units.groupBy { it.teacherId }) {
            val teacher = data.teacherById[teacherId]
            if (teacher == null) {
                issues += FeasibilityIssue("UNKNOWN_TEACHER", "معلم غير معروف: $teacherId", teacherId = teacherId)
                continue
            }
            val fixedMine = fixed.filter { it.teacherId == teacherId }
            if (fixedMine.size + list.size > teacher.weeklyNorm) {
                issues += FeasibilityIssue(
                    "TEACHER_NORM",
                    "المعلم ${teacher.name} يحتاج ${fixedMine.size + list.size} حصة ونصابه ${teacher.weeklyNorm}",
                    teacherId = teacherId,
                )
            }
            for (u in list) {
                val allowed = teacher.subjectIds.contains(u.subjectId) && data.assignments.any {
                    it.teacherId == teacherId && it.subjectId == u.subjectId && it.sectionId == u.sectionId
                }
                if (!allowed) {
                    issues += FeasibilityIssue("NO_ASSIGNMENT", "لا يوجد تكليف أو المادة غير مسموحة للمعلم ${teacher.name}", u.sectionId, teacherId)
                    break
                }
            }
            val freeForTeacher = teacherFreeSlots(data, teacher, fixedMine).size
            if (list.size > freeForTeacher) {
                issues += FeasibilityIssue(
                    "TEACHER_SLOTS",
                    "المعلم ${teacher.name} يحتاج ${list.size} حصة والخانات المتاحة له $freeForTeacher فقط",
                    teacherId = teacherId,
                )
            }
        }
        return issues
    }

    /** Slots the teacher could still take: not unavailable, not overlapping their fixed lessons, respecting the shift policy. */
    fun teacherFreeSlots(data: SchoolData, teacher: Teacher, fixedMine: List<Entry>): List<Slot> {
        val fixedShifts = fixedMine.map { it.slot.shiftId }.toSet()
        val result = ArrayList<Slot>()
        for (shift in data.shifts) {
            if (!teacher.canWorkBothShifts && fixedShifts.isNotEmpty() && shift.id !in fixedShifts) continue
            for (slot in data.slotsOfShift(shift)) {
                if (slot in teacher.unavailable) continue
                val time = data.timeOf(slot) ?: continue
                val blocked = fixedMine.any { e ->
                    e.slot.day == slot.day && data.timeOf(e.slot)?.overlaps(time) == true
                }
                if (!blocked) result += slot
            }
        }
        return result
    }
}
