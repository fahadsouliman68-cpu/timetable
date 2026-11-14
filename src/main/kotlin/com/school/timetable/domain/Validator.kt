package com.school.timetable.domain

data class ValidationReport(val violations: List<Violation>) {
    val blocking: Int = violations.count { it.severity == Severity.BLOCKING }
    val missing: Int = violations.count { it.severity == Severity.MISSING }
    val warnings: Int = violations.count { it.severity == Severity.WARNING }

    /** Drafts may be incomplete, but never contradictory. */
    val canSaveDraft: Boolean get() = blocking == 0

    /** Final approval requires a complete schedule with no hard violation. */
    val canApprove: Boolean get() = blocking == 0 && missing == 0
}

/**
 * Full, from-scratch check of a schedule. It does NOT reuse ConflictEngine or the solver:
 * it recomputes every rule with its own logic, so a bug in the solver cannot pass silently.
 */
object ScheduleValidator {

    /** Full check: the timetable itself plus the configuration it relies on. */
    fun validate(data: SchoolData, entries: List<Entry>): ValidationReport =
        ValidationReport(validateEntries(data, entries).violations + validateConfiguration(data))

    /** Timetable-only check: no configuration rules (plan, norms) are included. */
    fun validateEntries(data: SchoolData, entries: List<Entry>): ValidationReport {
        val out = ArrayList<Violation>()
        checkReferencesAndSlots(data, entries, out)
        checkSlotDuplicates(entries, out)
        checkTeachers(data, entries, out)
        checkAssignments(data, entries, out)
        checkConsecutiveRules(data, entries, out)
        return ValidationReport(out)
    }

    /** Configuration-level checks: plan vs assignments vs norms. Independent of any timetable. */
    fun validateConfiguration(data: SchoolData): List<Violation> {
        val out = ArrayList<Violation>()
        for (section in data.sections) {
            for (item in data.plan.filter { it.gradeId == section.gradeId }) {
                val assigned = data.assignments
                    .filter { it.sectionId == section.id && it.subjectId == item.subjectId }
                    .sumOf { it.weeklyPeriods }
                if (assigned > item.weeklyPeriods) {
                    out += Violation(ViolationType.PLAN_EXCEEDED, "التكليفات تتجاوز الخطة لهذه المادة", section.id, null, item.subjectId)
                } else if (assigned < item.weeklyPeriods) {
                    out += Violation(ViolationType.PLAN_MISSING, "التكليفات أقل من الخطة لهذه المادة", section.id, null, item.subjectId)
                }
            }
        }
        for (teacher in data.teachers) {
            val total = data.assignments.filter { it.teacherId == teacher.id }.sumOf { it.weeklyPeriods }
            if (total > teacher.weeklyNorm) {
                out += Violation(ViolationType.NORM_EXCEEDED, "مجموع تكليفات المعلم يتجاوز نصابه", null, teacher.id)
            }
        }
        return out
    }

    private fun checkReferencesAndSlots(data: SchoolData, entries: List<Entry>, out: MutableList<Violation>) {
        for (e in entries) {
            val section = data.sectionById[e.sectionId]
            val teacher = data.teacherById[e.teacherId]
            val subject = data.subjectById[e.subjectId]
            if (section == null || teacher == null || subject == null) {
                out += Violation(ViolationType.REFERENCE_UNKNOWN, "مرجع غير معروف", e.sectionId, e.teacherId, e.subjectId, e.slot)
                continue
            }
            val shift = data.shiftById[e.slot.shiftId]
            val valid = e.slot.day in 0 until data.days && shift != null &&
                shift.id == section.shiftId && shift.period(e.slot.periodIndex) != null
            if (!valid) out += Violation(ViolationType.OUTSIDE_SHIFT, "حصة خارج دوام الشعبة", section.id, teacher.id, subject.id, e.slot)
            if (e.slot in teacher.unavailable) {
                out += Violation(ViolationType.TEACHER_UNAVAILABLE, "المعلم غير متوفر", section.id, teacher.id, subject.id, e.slot)
            }
            if (subject.id !in teacher.subjectIds) {
                out += Violation(ViolationType.SUBJECT_NOT_ALLOWED, "المادة غير مسموحة للمعلم", section.id, teacher.id, subject.id, e.slot)
            }
            val hasAssignment = data.assignments.any {
                it.teacherId == teacher.id && it.subjectId == subject.id && it.sectionId == section.id
            }
            if (!hasAssignment) {
                out += Violation(ViolationType.NO_ASSIGNMENT, "حصة بلا تكليف", section.id, teacher.id, subject.id, e.slot)
            }
        }
    }

    private fun checkSlotDuplicates(entries: List<Entry>, out: MutableList<Violation>) {
        val byCell = entries.groupBy { it.sectionId to it.slot }
        for ((key, list) in byCell) {
            if (list.size > 1) {
                out += Violation(ViolationType.SLOT_OCCUPIED, "أكثر من درس في خانة واحدة", key.first, null, null, key.second)
            }
        }
    }

    private fun checkTeachers(data: SchoolData, entries: List<Entry>, out: MutableList<Violation>) {
        for ((teacherId, list) in entries.groupBy { it.teacherId }) {
            val teacher = data.teacherById[teacherId] ?: continue
            if (list.size > teacher.weeklyNorm) {
                out += Violation(ViolationType.NORM_EXCEEDED, "المعلم تجاوز نصابه", null, teacherId)
            }
            if (!teacher.canWorkBothShifts && list.map { it.slot.shiftId }.toSet().size > 1) {
                out += Violation(ViolationType.SHIFT_POLICY, "المعلم يعمل في الورديتين رغم منع ذلك", null, teacherId)
            }
            for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    val a = list[i]
                    val b = list[j]
                    if (a.slot.day != b.slot.day) continue
                    val ta = data.timeOf(a.slot) ?: continue
                    val tb = data.timeOf(b.slot) ?: continue
                    if (ta.overlaps(tb)) {
                        out += Violation(ViolationType.TEACHER_DOUBLE_BOOKED, "المعلم في مكانين في الوقت نفسه", a.sectionId, teacherId, null, a.slot)
                    }
                }
            }
        }
    }

    private fun checkAssignments(data: SchoolData, entries: List<Entry>, out: MutableList<Violation>) {
        val counts = entries.groupingBy { Triple(it.teacherId, it.subjectId, it.sectionId) }.eachCount()
        for (a in data.assignments) {
            val c = counts[Triple(a.teacherId, a.subjectId, a.sectionId)] ?: 0
            when {
                c > a.weeklyPeriods -> out += Violation(ViolationType.ASSIGNMENT_EXCEEDED, "حصص أكثر من التكليف", a.sectionId, a.teacherId, a.subjectId)
                c < a.weeklyPeriods -> out += Violation(ViolationType.ASSIGNMENT_MISSING, "حصص أقل من التكليف (${a.weeklyPeriods - c} ناقصة)", a.sectionId, a.teacherId, a.subjectId)
            }
        }
    }

    /** Counts the longest run by walking period indices, a different method from the engine's. */
    private fun checkConsecutiveRules(data: SchoolData, entries: List<Entry>, out: MutableList<Violation>) {
        if (data.rules.isEmpty()) return
        for ((teacherId, list) in entries.groupBy { it.teacherId }) {
            for (day in 0 until data.days) {
                val cells = list.filter { it.slot.day == day }.map { it.slot.shiftId to it.slot.periodIndex }.toSet()
                if (cells.isEmpty()) continue
                val run = cells.maxOf { (shiftId, index) ->
                    var length = 1
                    while (cells.contains(shiftId to index + length)) length++
                    if (cells.contains(shiftId to index - 1)) 0 else length
                }
                for (rule in data.rules) {
                    if (rule.teacherId != null && rule.teacherId != teacherId) continue
                    if (run > rule.maxConsecutive) {
                        val type = if (rule.strictness == Strictness.MANDATORY) ViolationType.MANDATORY_CONSECUTIVE else ViolationType.PREFERRED_CONSECUTIVE
                        out += Violation(type, "تجاوز الحد المتتالي ($run)", null, teacherId, null, null)
                    }
                }
            }
        }
    }
}
