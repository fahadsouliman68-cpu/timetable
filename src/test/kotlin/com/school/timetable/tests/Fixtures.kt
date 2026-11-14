package com.school.timetable.tests

import com.school.timetable.domain.*

fun entry(section: String, day: Int, index: Int, subject: String, teacher: String, shift: String = "M", locked: Boolean = false) =
    Entry(section, Slot(day, shift, index), subject, teacher, locked)

/** Small school: one shift with 4 periods, two sections in grade G1, two teachers. */
fun smallSchool(t1Norm: Int = 10, t1Unavailable: Set<Slot> = emptySet()): SchoolData {
    val shift = Shift("M", ShiftKind.MORNING, (1..4).map { Period(it, TimeRange(480 + (it - 1) * 50, 525 + (it - 1) * 50)) })
    return SchoolData(
        days = 5,
        grades = listOf(Grade("G1", "الصف الأول")),
        shifts = listOf(shift),
        sections = listOf(
            Section("S1A", "1/أ", "G1", "M"),
            Section("S1B", "1/ب", "G1", "M"),
        ),
        subjects = listOf(Subject("MAT", "الرياضيات"), Subject("ARA", "العربية")),
        teachers = listOf(
            Teacher("T1", "المعلم الأول", t1Norm, false, setOf("MAT", "ARA"), t1Unavailable),
            Teacher("T2", "المعلم الثاني", 10, false, setOf("MAT")),
        ),
        plan = listOf(PlanItem("G1", "MAT", 2), PlanItem("G1", "ARA", 2)),
        assignments = listOf(
            Assignment("T1", "MAT", "S1A", 2),
            Assignment("T1", "ARA", "S1A", 2),
            Assignment("T2", "MAT", "S1B", 2),
            Assignment("T1", "ARA", "S1B", 2),
        ),
    )
}

/** Two shifts whose time windows overlap; one teacher may work both. */
fun overlappingShiftsSchool(canWorkBoth: Boolean): SchoolData {
    val m = Shift("M", ShiftKind.MORNING, (1..3).map { Period(it, TimeRange(480 + (it - 1) * 50, 525 + (it - 1) * 50)) })
    val e = Shift("E", ShiftKind.EVENING, (1..3).map { Period(it, TimeRange(500 + (it - 1) * 50, 545 + (it - 1) * 50)) })
    return SchoolData(
        days = 3,
        grades = listOf(Grade("G1", "الأول"), Grade("G2", "الثاني")),
        shifts = listOf(m, e),
        sections = listOf(Section("S1", "1/أ", "G1", "M"), Section("S2", "2/أ", "G2", "E")),
        subjects = listOf(Subject("MAT", "الرياضيات"), Subject("ARA", "العربية")),
        teachers = listOf(Teacher("T", "المعلم", 6, canWorkBoth, setOf("MAT", "ARA"))),
        plan = listOf(PlanItem("G1", "MAT", 2), PlanItem("G2", "ARA", 2)),
        assignments = listOf(Assignment("T", "MAT", "S1", 2), Assignment("T", "ARA", "S2", 2)),
    )
}

/**
 * Demo school (synthetic, for tests and benchmarks only).
 * Grades alternate between the morning and evening shifts. Each section gets 24 lessons a week
 * from 6 periods x 5 days. Homeroom teachers cover five core subjects for their own section;
 * specialists (English, Art, PE) teach groups of up to six sections in the same shift.
 */
object DemoSchool {
    private val coreSubjects = listOf("ARA", "MAT", "SCI", "ISL", "SOC")
    private val specialistSubjects = listOf("ENG", "ART", "PED")
    private val slack = mapOf("ARA" to 5, "MAT" to 5, "SCI" to 3, "ISL" to 2, "SOC" to 2, "ENG" to 3, "ART" to 2, "PED" to 2) // 24
    private val full = mapOf("ARA" to 6, "MAT" to 6, "SCI" to 4, "ISL" to 3, "SOC" to 3, "ENG" to 4, "ART" to 2, "PED" to 2) // 30

    /** slack: 24 of 30 slots used (normal). full: every slot used (stress test). */
    fun build(gradeCount: Int, specialistNorm: Int = 20, profile: String = "slack"): SchoolData {
        val weekly = if (profile == "full") full else slack
        val homeroomNorm = if (profile == "full") 22 else 20
        val specNorm = if (profile == "full") 24 else specialistNorm
        val morning = Shift("M", ShiftKind.MORNING, (1..6).map { Period(it, TimeRange(480 + (it - 1) * 50, 525 + (it - 1) * 50)) })
        val evening = Shift("E", ShiftKind.EVENING, (1..6).map { Period(it, TimeRange(840 + (it - 1) * 50, 885 + (it - 1) * 50)) })
        val half = (gradeCount + 1) / 2
        val grades = (1..gradeCount).map { Grade("G$it", "الصف $it") }
        val sections = grades.flatMap { g ->
            val n = g.id.removePrefix("G").toInt()
            val shift = if (n <= half) "M" else "E"
            listOf("a", "b").map { letter -> Section("${g.id}$letter", "$n/$letter", g.id, shift, homeroomTeacherId = "H-${g.id}$letter") }
        }
        val subjects = listOf(
            "ARA" to "العربية", "MAT" to "الرياضيات", "SCI" to "العلوم", "ISL" to "التربية الإسلامية",
            "SOC" to "الاجتماعيات", "ENG" to "الإنجليزية", "ART" to "الفنون", "PED" to "الرياضة",
        ).map { Subject(it.first, it.second) }
        val plan = grades.flatMap { g -> weekly.map { (s, n) -> PlanItem(g.id, s, n) } }

        val teachers = ArrayList<Teacher>()
        val assignments = ArrayList<Assignment>()
        for (section in sections) {
            val homeroom = "H-${section.id}"
            teachers += Teacher(homeroom, "معلم صف ${section.name}", homeroomNorm, false, coreSubjects.toSet())
            for (s in coreSubjects) assignments += Assignment(homeroom, s, section.id, weekly.getValue(s))
        }
        for (shift in listOf("M", "E")) {
            val inShift = sections.filter { it.shiftId == shift }
            inShift.chunked(6).forEachIndexed { k, chunk ->
                for (s in specialistSubjects) {
                    val id = "S-$s-$shift$k"
                    teachers += Teacher(id, "اختصاص $s $shift$k", specNorm, false, setOf(s))
                    for (section in chunk) assignments += Assignment(id, s, section.id, weekly.getValue(s))
                }
            }
        }
        return SchoolData(5, grades, listOf(morning, evening), sections, subjects, teachers, plan, assignments)
    }
}
