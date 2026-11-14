package com.school.timetable.domain

object AssignmentSuggester {
    /**
     * Proposes homeroom-teacher assignments for each section: every plan subject the homeroom teacher
     * is allowed to teach, when the section has no assignment for it yet. Suggestions only; the director confirms.
     */
    fun homeroomSuggestions(data: SchoolData): List<Assignment> {
        val out = ArrayList<Assignment>()
        for (section in data.sections) {
            val teacherId = section.homeroomTeacherId ?: continue
            val teacher = data.teacherById[teacherId] ?: continue
            for (item in data.plan.filter { it.gradeId == section.gradeId }) {
                if (item.subjectId !in teacher.subjectIds) continue
                if (data.assignments.any { it.sectionId == section.id && it.subjectId == item.subjectId }) continue
                out += Assignment(teacherId, item.subjectId, section.id, item.weeklyPeriods)
            }
        }
        return out
    }
}
