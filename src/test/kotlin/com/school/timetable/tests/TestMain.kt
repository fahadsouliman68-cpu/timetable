package com.school.timetable.tests

import com.school.timetable.domain.*
import com.school.timetable.domain.common.SyrianCalendar
import com.school.timetable.domain.setup.*
import kotlin.random.Random
import kotlin.system.exitProcess

private fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

private fun blockingTypes(list: List<Violation>): Set<ViolationType> =
    list.filter { it.severity == Severity.BLOCKING }.map { it.type }.toSet()

private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

private val tests: List<Pair<String, () -> Unit>> = listOf(

    // ---------- Conflict engine ----------
    "engine: teacher double booked in two sections at once" to {
        val data = smallSchool()
        val current = listOf(entry("S1A", 0, 1, "MAT", "T1"))
        val v = ConflictEngine.checkPlacement(data, current, entry("S1B", 0, 1, "ARA", "T1"))
        expect(ViolationType.TEACHER_DOUBLE_BOOKED in blockingTypes(v), "expected TEACHER_DOUBLE_BOOKED, got $v")
    },
    "engine: section slot already occupied" to {
        val data = smallSchool()
        val current = listOf(entry("S1A", 0, 1, "MAT", "T2"))
        val v = ConflictEngine.checkPlacement(data, current, entry("S1A", 0, 1, "ARA", "T1"))
        expect(ViolationType.SLOT_OCCUPIED in blockingTypes(v), "expected SLOT_OCCUPIED, got $v")
    },
    "engine: norm exceeded" to {
        val data = smallSchool(t1Norm = 1)
        val current = listOf(entry("S1A", 0, 1, "MAT", "T1"))
        val v = ConflictEngine.checkPlacement(data, current, entry("S1A", 1, 1, "ARA", "T1"))
        expect(ViolationType.NORM_EXCEEDED in blockingTypes(v), "expected NORM_EXCEEDED, got $v")
    },
    "engine: unavailable teacher and subject not allowed" to {
        val data = smallSchool(t1Unavailable = setOf(Slot(2, "M", 3)))
        val unavailable = ConflictEngine.checkPlacement(data, emptyList(), entry("S1A", 2, 3, "MAT", "T1"))
        expect(ViolationType.TEACHER_UNAVAILABLE in blockingTypes(unavailable), "expected TEACHER_UNAVAILABLE")
        val notAllowed = ConflictEngine.checkPlacement(data, emptyList(), entry("S1A", 0, 1, "ARA", "T2"))
        expect(ViolationType.SUBJECT_NOT_ALLOWED in blockingTypes(notAllowed), "expected SUBJECT_NOT_ALLOWED")
    },
    "engine: assignment exceeded and missing assignment" to {
        val data = smallSchool()
        val current = listOf(entry("S1A", 0, 1, "ARA", "T1"), entry("S1A", 1, 1, "ARA", "T1"))
        val exceeded = ConflictEngine.checkPlacement(data, current, entry("S1A", 2, 1, "ARA", "T1"))
        expect(ViolationType.ASSIGNMENT_EXCEEDED in blockingTypes(exceeded), "expected ASSIGNMENT_EXCEEDED")
        val noAssignment = ConflictEngine.checkPlacement(data, emptyList(), entry("S1A", 0, 1, "ARA", "T2"))
        expect(ViolationType.NO_ASSIGNMENT in blockingTypes(noAssignment), "expected NO_ASSIGNMENT")
    },
    "engine: cross-shift overlap blocks when windows overlap" to {
        val data = overlappingShiftsSchool(canWorkBoth = true)
        val current = listOf(entry("S1", 0, 2, "MAT", "T", shift = "M"))  // 530-575 in M? 480+50=530..575
        // E period 1 runs 500-545, overlapping M period 2 (530-575).
        val v = ConflictEngine.checkPlacement(data, current, entry("S2", 0, 1, "ARA", "T", shift = "E"))
        expect(ViolationType.TEACHER_DOUBLE_BOOKED in blockingTypes(v), "expected overlap across shifts, got $v")
        val ok = ConflictEngine.checkPlacement(data, current, entry("S2", 0, 3, "ARA", "T", shift = "E"))
        expect(ViolationType.TEACHER_DOUBLE_BOOKED !in blockingTypes(ok), "non-overlapping cross-shift lesson must pass")
    },
    "engine: shift policy blocks second shift when teacher may not work both" to {
        val data = overlappingShiftsSchool(canWorkBoth = false)
        val current = listOf(entry("S1", 0, 1, "MAT", "T", shift = "M"))
        val v = ConflictEngine.checkPlacement(data, current, entry("S2", 1, 3, "ARA", "T", shift = "E"))
        expect(ViolationType.SHIFT_POLICY in blockingTypes(v), "expected SHIFT_POLICY, got $v")
    },
    "engine: mandatory consecutive is blocking, preferred is only a warning" to {
        val base = smallSchool()
        val mandatory = base.copyWithRules(listOf(ConsecutiveRule(null, 2, Strictness.MANDATORY)))
        val preferred = base.copyWithRules(listOf(ConsecutiveRule(null, 2, Strictness.PREFERRED)))
        val current = listOf(entry("S1A", 0, 1, "MAT", "T1"), entry("S1A", 0, 2, "ARA", "T1"))
        val candidate = entry("S1B", 0, 3, "ARA", "T1")
        expect(ViolationType.MANDATORY_CONSECUTIVE in blockingTypes(ConflictEngine.checkPlacement(mandatory, current, candidate)), "mandatory not blocking")
        val pv = ConflictEngine.checkPlacement(preferred, current, candidate)
        expect(ViolationType.PREFERRED_CONSECUTIVE in pv.map { it.type }, "preferred warning missing")
        expect(blockingTypes(pv).isEmpty(), "preferred rule must not block")
    },

    // ---------- Validator ----------
    "validator: incomplete schedule can be a draft but not approved" to {
        val data = smallSchool()
        val report = ScheduleValidator.validate(data, listOf(entry("S1A", 0, 1, "MAT", "T1")))
        expect(report.canSaveDraft, "partial but non-contradictory schedule must save as draft")
        expect(report.missing > 0 && !report.canApprove, "incomplete schedule must not be approved")
    },
    "validator: complete valid schedule can be approved" to {
        val data = smallSchool()
        val complete = listOf(
            entry("S1A", 0, 1, "MAT", "T1"), entry("S1A", 0, 2, "MAT", "T1"),
            entry("S1A", 1, 1, "ARA", "T1"), entry("S1A", 1, 2, "ARA", "T1"),
            entry("S1B", 0, 1, "MAT", "T2"), entry("S1B", 0, 2, "MAT", "T2"),
            entry("S1B", 2, 1, "ARA", "T1"), entry("S1B", 2, 2, "ARA", "T1"),
        )
        val report = ScheduleValidator.validate(data, complete)
        expect(report.blocking == 0 && report.missing == 0, "expected clean schedule, got ${report.violations}")
        expect(report.canApprove, "clean complete schedule must be approvable")
    },
    "validator: configuration plan vs assignments mismatch is detected" to {
        val base = smallSchool()
        val broken = SchoolData(
            days = base.days, grades = base.grades, shifts = base.shifts, sections = base.sections,
            subjects = base.subjects, teachers = base.teachers,
            plan = listOf(PlanItem("G1", "MAT", 1), PlanItem("G1", "ARA", 2)),
            assignments = base.assignments,
        )
        val types = ScheduleValidator.validateConfiguration(broken).map { it.type }
        expect(ViolationType.PLAN_EXCEEDED in types, "plan exceeded by assignments must be reported")
    },

    // ---------- Property: engine and validator agree on hard violations ----------
    "property: engine and validator agree on 2000 random schedules" to {
        val data = smallSchool(t1Norm = 5, t1Unavailable = setOf(Slot(3, "M", 2))).copyWithRules(
            listOf(ConsecutiveRule(null, 2, Strictness.MANDATORY)),
        )
        val rnd = Random(2026)
        val sections = listOf("S1A", "S1B", "NOPE")
        val subjects = listOf("MAT", "ARA", "XXX")
        val teachers = listOf("T1", "T2", "T9")
        val shifts = listOf("M", "M", "M", "E")
        var blockingCases = 0
        repeat(2000) { iteration ->
            val size = rnd.nextInt(0, 12)
            val raw = List(size) {
                Entry(
                    sections.random(rnd), Slot(rnd.nextInt(-1, 6), shifts.random(rnd), rnd.nextInt(0, 6)),
                    subjects.random(rnd), teachers.random(rnd),
                )
            }
            val sequential = ArrayList<Entry>()
            var engineBlocks = false
            for (e in raw) {
                if (ConflictEngine.checkPlacement(data, sequential, e).any { it.severity == Severity.BLOCKING }) engineBlocks = true
                sequential += e
            }
            val validatorBlocks = ScheduleValidator.validateEntries(data, raw).blocking > 0
            expect(engineBlocks == validatorBlocks, "disagreement at iteration $iteration: engine=$engineBlocks validator=$validatorBlocks raw=$raw")
            if (validatorBlocks) blockingCases++
        }
        expect(blockingCases in 1 until 2000, "generator must produce both valid and invalid cases (got $blockingCases invalid)")
    },

    "validator: partial time overlap across two shifts is detected" to {
        val data = overlappingShiftsSchool(canWorkBoth = true)
        // M period 2 runs 530-575; E period 1 runs 500-545. They overlap for 15 minutes, but the slots differ.
        val entries = listOf(entry("S1", 0, 2, "MAT", "T", shift = "M"), entry("S2", 0, 1, "ARA", "T", shift = "E"))
        val report = ScheduleValidator.validateEntries(data, entries)
        expect(ViolationType.TEACHER_DOUBLE_BOOKED in blockingTypes(report.violations), "partial overlap must be flagged, got ${report.violations}")
    },
    "property: engine and validator agree on overlapping shifts (2000 cases)" to {
        val data = overlappingShiftsSchool(canWorkBoth = true).copyWithRules(listOf(ConsecutiveRule(null, 1, Strictness.MANDATORY)))
        val rnd = Random(4242)
        var invalid = 0
        repeat(2000) { iteration ->
            val raw = List(rnd.nextInt(0, 8)) {
                Entry(listOf("S1", "S2").random(rnd), Slot(rnd.nextInt(0, 3), listOf("M", "E").random(rnd), rnd.nextInt(1, 4)),
                    listOf("MAT", "ARA").random(rnd), "T")
            }
            val sequential = ArrayList<Entry>()
            var engineBlocks = false
            for (e in raw) {
                if (ConflictEngine.checkPlacement(data, sequential, e).any { it.severity == Severity.BLOCKING }) engineBlocks = true
                sequential += e
            }
            val validatorBlocks = ScheduleValidator.validateEntries(data, raw).blocking > 0
            expect(engineBlocks == validatorBlocks, "disagreement at $iteration: engine=$engineBlocks validator=$validatorBlocks raw=$raw")
            if (validatorBlocks) invalid++
        }
        expect(invalid in 1 until 2000, "generator should yield both valid and invalid cases")
    },

    // ---------- Feasibility ----------
    "feasibility: section needs more lessons than free slots" to {
        val data = smallSchool()
        val units = List(5) { LessonUnit("S1A", "MAT", "T1") }  // 4 periods x 5 days = 20 slots, so use fixed to exhaust
        val fixed = (0 until 5).flatMap { day -> (1..4).map { idx -> entry("S1A", day, idx, "ARA", "T1") } }
        val issues = FeasibilityChecker.check(data, fixed, units)
        expect(issues.any { it.code == "SECTION_SLOTS" }, "expected SECTION_SLOTS, got $issues")
    },
    "feasibility: teacher units exceed remaining norm" to {
        val data = smallSchool(t1Norm = 10)
        val units = List(6) { LessonUnit("S1A", "MAT", "T1") } + List(5) { LessonUnit("S1B", "ARA", "T1") }
        val issues = FeasibilityChecker.check(data, emptyList(), units)
        expect(issues.any { it.code == "TEACHER_NORM" }, "expected TEACHER_NORM, got $issues")
    },

    // ---------- Solver ----------
    "solver: solves a small school completely and validly" to {
        val data = smallSchool()
        val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = 10_000))
        expect(result is SolveResult.Solved, "expected Solved, got $result")
        val entries = (result as SolveResult.Solved).entries
        expect(ScheduleValidator.validate(data, entries).canApprove, "solver output must pass independent validator")
        expect(result.quality.score in 0..100, "quality out of range")
    },
    "solver: cross-shift teacher is never double-booked" to {
        val data = overlappingShiftsSchool(canWorkBoth = true)
        val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = 10_000))
        expect(result is SolveResult.Solved, "expected Solved, got $result")
        expect(ScheduleValidator.validate(data, (result as SolveResult.Solved).entries).blocking == 0, "double booking across shifts")
    },
    "solver: infeasible configuration is reported with reasons, instantly" to {
        val data = DemoSchool.build(gradeCount = 6, specialistNorm = 10)
        val start = System.nanoTime()
        val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = 60_000))
        expect(result is SolveResult.Infeasible, "expected Infeasible, got $result")
        expect(elapsedMs(start) < 2_000, "infeasibility must be detected in under 2 seconds")
        expect((result as SolveResult.Infeasible).issues.isNotEmpty(), "reasons must be listed")
    },
    "solver: zero budget stops with a valid partial schedule" to {
        val data = smallSchool()
        val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = 0))
        expect(result is SolveResult.Partial && result.reason == StopReason.TIMEOUT, "expected TIMEOUT, got $result")
        val partial = result as SolveResult.Partial
        expect(ScheduleValidator.validate(data, partial.entries).blocking == 0, "partial schedule must have no hard violation")
        expect(partial.unplaced.isNotEmpty(), "timeout should leave lessons unplaced")
    },
    "solver: cancellation stops the search" to {
        val data = smallSchool()
        val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = 60_000, cancel = CancelSignal { true }))
        expect(result is SolveResult.Partial && result.reason == StopReason.CANCELLED, "expected CANCELLED, got $result")
    },
    "solver: locked lessons are preserved" to {
        val data = smallSchool()
        val locked = listOf(entry("S1A", 4, 4, "MAT", "T1", locked = true))
        val result = CspSolver(data).solveAll(locked, SolverConfig(timeBudgetMs = 10_000))
        expect(result is SolveResult.Solved, "expected Solved, got $result")
        val entries = (result as SolveResult.Solved).entries
        expect(entries.any { it.locked && it.slot == Slot(4, "M", 4) && it.subjectId == "MAT" }, "locked lesson moved or lost")
    },
    "solver: scoped redistribution keeps other sections unchanged" to {
        val data = smallSchool()
        val solver = CspSolver(data)
        val solved = (solver.solveAll(emptyList(), SolverConfig(10_000)) as SolveResult.Solved).entries
        val before = solved.filter { it.sectionId == "S1A" }.toSet()
        val result = solver.redistribute(solved, { it.sectionId == "S1B" }, SolverConfig(10_000))
        expect(result is SolveResult.Solved, "expected Solved, got $result")
        val after = (result as SolveResult.Solved).entries
        expect(after.filter { it.sectionId == "S1A" }.toSet() == before, "section S1A changed during scoped redistribution")
        expect(ScheduleValidator.validate(data, after).canApprove, "redistributed schedule must be valid and complete")
    },
    "solver: local search never lowers quality and stays valid" to {
        val data = smallSchool()
        val solver = CspSolver(data)
        val start = (solver.solveAll(emptyList(), SolverConfig(10_000)) as SolveResult.Solved)
        val (improved, report) = solver.improve(start.entries, totalBudgetMs = 1_500, cancel = CancelSignal { false })
        expect(report.score >= start.quality.score, "score dropped: ${start.quality.score} -> ${report.score}")
        expect(ScheduleValidator.validate(data, improved).canApprove, "improved schedule must stay valid")
    },

    "robustness: 300 random tight schools never yield an invalid timetable" to {
        val rnd = Random(99)
        var solved = 0; var partial = 0; var infeasible = 0
        repeat(300) { iteration ->
            val data = randomSchool(rnd)
            val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = 2_000))
            val entries = when (result) {
                is SolveResult.Solved -> { solved++; result.entries }
                is SolveResult.Partial -> { partial++; result.entries }
                is SolveResult.Infeasible -> { infeasible++; emptyList() }
            }
            // Partial/Solved timetables must contain no hard violation of their own.
            val tableReport = ScheduleValidator.validateEntries(data, entries)
            expect(tableReport.blocking == 0, "iteration $iteration produced hard violations: ${tableReport.violations.take(3)}")
            if (result is SolveResult.Solved) {
                expect(ScheduleValidator.validate(data, entries).canApprove, "iteration $iteration: Solved but not approvable")
            }
            // A configuration problem must be refused before any search, with a reason.
            if (result is SolveResult.Infeasible && ScheduleValidator.validateConfiguration(data).any { it.severity != Severity.WARNING }) {
                expect(result.issues.isNotEmpty(), "iteration $iteration: config error without reasons")
            }
        }
        println("  random: solved=$solved partial=$partial infeasible=$infeasible")
        expect(solved > 0, "generator should produce some solvable schools")
    },

    // ---------- Quality ----------
    "quality: weights sum to 100 and score equals 100 minus deductions" to {
        val weights = listOf(
            QualityScorer.W_GAPS, QualityScorer.W_SUBJECT_BALANCE, QualityScorer.W_REPETITION,
            QualityScorer.W_PREFERRED_CONSECUTIVE, QualityScorer.W_LOAD_BALANCE, QualityScorer.W_PREFERENCES,
        )
        expect(weights.sum() == 100.0, "weights sum to ${weights.sum()}")
        val data = smallSchool()
        val solved = (CspSolver(data).solveAll(emptyList(), SolverConfig(10_000)) as SolveResult.Solved)
        val total = solved.quality.deductions.sumOf { it.points }
        expect(solved.quality.score == kotlin.math.round(100.0 - total).toInt().coerceIn(0, 100), "score inconsistent with deductions")
    },

    "quality: subject preferences are scored, and fully honoured schedules lose nothing" to {
        val base = smallSchool()
        val solved = (CspSolver(base).solveAll(emptyList(), SolverConfig(10_000)) as SolveResult.Solved).entries
        val honoured = base.copyWithPreferences(listOf(SubjectPreference("MAT", setOf(1, 2, 3, 4)), SubjectPreference("ARA", setOf(1, 2, 3, 4))))
        expect(QualityScorer.score(honoured, solved).deductions.first { it.label == "تفضيلات المادة" }.points == 0.0, "all in preferred periods must cost 0")
        val violated = base.copyWithPreferences(listOf(SubjectPreference("MAT", setOf(99)), SubjectPreference("ARA", setOf(99))))
        expect(QualityScorer.score(violated, solved).deductions.first { it.label == "تفضيلات المادة" }.points > 0.0, "violated preferences must cost points")
    },
    "suggestions: homeroom teachers are proposed for every core subject, nothing else" to {
        val demo = DemoSchool.build(gradeCount = 6)
        val stripped = SchoolData(demo.days, demo.grades, demo.shifts, demo.sections, demo.subjects, demo.teachers, demo.plan, emptyList())
        val suggested = AssignmentSuggester.homeroomSuggestions(stripped)
        expect(suggested.size == demo.sections.size * 5, "expected 5 core subjects per section, got ${suggested.size}")
        expect(suggested.all { it.teacherId.startsWith("H-") }, "only homeroom teachers may be suggested")
    },
    // ---------- Phase 1: setup wizard rules ----------
    "setup: default morning shift computes period times with gaps and breaks" to {
        val input = SchoolSetupInput("مدرسة", "2026-2027", null, 5, listOf(
            ShiftSetup("M", ShiftKind.MORNING, 480, 6, 45, gapMin = 5, breaks = listOf(BreakSetup(3, 15))),
        ))
        val result = SetupBuilder.build(input)
        expect(result is SetupResult.Ok, "expected Ok, got $result")
        val shift = (result as SetupResult.Ok).build.shifts.single()
        expect(shift.periods.size == 6, "expected 6 periods")
        expect(shift.period(1)!!.time == TimeRange(480, 525), "period 1 wrong: ${shift.period(1)}")
        expect(shift.period(4)!!.time.startMin == 645, "break after period 3 must shift period 4 to 645, got ${shift.period(4)}")
    },
    "setup: invalid input is refused with every reason listed" to {
        val input = SchoolSetupInput("", " ", null, 9, listOf(
            ShiftSetup("M", ShiftKind.MORNING, 480, 0, 45),
            ShiftSetup("E", ShiftKind.MORNING, 1500, 3, 45, breaks = listOf(BreakSetup(3, 15))),
        ))
        val result = SetupBuilder.build(input)
        expect(result is SetupResult.Invalid, "expected Invalid")
        val errors = (result as SetupResult.Invalid).errors
        expect(errors.any { it.contains("اسم المدرسة") }, "missing school name error")
        expect(errors.any { it.contains("عدد أيام") }, "missing days error")
        expect(errors.any { it.contains("تكرار نوع الوردية") }, "missing duplicate-kind error")
        expect(errors.any { it.contains("عدد الحصص") }, "missing period-count error")
        expect(errors.any { it.contains("وقت البداية") }, "missing start-time error")
        expect(errors.any { it.contains("الاستراحة") }, "missing break error")
    },
    "setup: shifts that run past midnight are refused" to {
        val input = SchoolSetupInput("مدرسة", "2026-2027", null, 5, listOf(
            ShiftSetup("E", ShiftKind.EVENING, 1380, 6, 60),
        ))
        val errors = SetupBuilder.validate(input)
        expect(errors.any { it.contains("منتصف الليل") }, "midnight overflow must be refused, got $errors")
    },
    "syrian calendar: months use the Syrian names and Latin digits" to {
        expect(SyrianCalendar.format(2026, 11, 14) == "14 تشرين الثاني 2026", "got ${SyrianCalendar.format(2026, 11, 14)}")
        expect(SyrianCalendar.MONTHS.size == 12, "twelve months required")
        expect(SyrianCalendar.format(2027, 1, 1) == "1 كانون الثاني 2027", "January name wrong")
    },

    // ---------- Benchmarks (demo data, real budgets from the design) ----------
    "benchmark: 12 sections within 60 s budget, output always valid" to {
        runBenchmark(gradeCount = 6, budgetMs = 60_000, label = "12 sections")
    },
    "benchmark: 18 sections within 120 s budget, output always valid" to {
        runBenchmark(gradeCount = 9, budgetMs = 120_000, label = "18 sections")
    },
    "stress: 12 sections, every slot full (30/30), 60 s budget" to {
        runBenchmark(gradeCount = 6, budgetMs = 60_000, label = "12 full", profile = "full")
    },
    "stress: 18 sections, every slot full (30/30), 120 s budget" to {
        runBenchmark(gradeCount = 9, budgetMs = 120_000, label = "18 full", profile = "full")
    },
)

private fun runBenchmark(gradeCount: Int, budgetMs: Long, label: String, profile: String = "slack") {
    val data = DemoSchool.build(gradeCount, profile = profile)
    val teacherCount = data.teachers.size
    val lessons = data.assignments.sumOf { it.weeklyPeriods }
    println("  [$label] sections=${data.sections.size} teachers=$teacherCount lessons=$lessons")
    val start = System.nanoTime()
    val result = CspSolver(data).solveAll(emptyList(), SolverConfig(timeBudgetMs = budgetMs))
    val took = elapsedMs(start)
    when (result) {
        is SolveResult.Solved -> println("  [$label] SOLVED in ${took} ms, quality=${result.quality.score}")
        is SolveResult.Partial -> println("  [$label] PARTIAL (${result.reason}) in ${took} ms, placed=${lessons - result.unplaced.size}/$lessons, top blockers=${result.topBlockers}")
        is SolveResult.Infeasible -> println("  [$label] INFEASIBLE in ${took} ms: ${result.issues.take(3)}")
    }
    expect(took <= budgetMs + 5_000, "$label exceeded its budget: ${took} ms")
    val entries = when (result) {
        is SolveResult.Solved -> result.entries
        is SolveResult.Partial -> result.entries
        is SolveResult.Infeasible -> emptyList()
    }
    expect(ScheduleValidator.validate(data, entries).blocking == 0, "$label output has hard violations")
}

/** Random small school with tight norms, shared teachers and unavailable slots. Invalid cases are fine: the test only checks output safety. */
private fun randomSchool(rnd: Random): SchoolData {
    val periods = rnd.nextInt(3, 6)
    val days = rnd.nextInt(3, 6)
    val shift = Shift("M", ShiftKind.MORNING, (1..periods).map { Period(it, TimeRange(480 + (it - 1) * 50, 525 + (it - 1) * 50)) })
    val sectionCount = rnd.nextInt(2, 5)
    val sections = (1..sectionCount).map { Section("S$it", "$it", "G1", "M") }
    val subjects = listOf(Subject("A", "أ"), Subject("B", "ب"), Subject("C", "ت"))
    // One plan for the grade: every section needs the same weekly lessons per subject.
    val plan = subjects.filter { rnd.nextInt(3) != 0 }.map { PlanItem("G1", it.id, rnd.nextInt(1, 3)) }
    val teacherCount = rnd.nextInt(2, 4)
    val teachers = (1..teacherCount).map { t ->
        val unavailable = (0 until rnd.nextInt(0, 3)).map { Slot(rnd.nextInt(days), "M", rnd.nextInt(1, periods + 1)) }.toSet()
        val taught = subjects.map { it.id }.filter { rnd.nextBoolean() }.toSet()
        Teacher("T$t", "t$t", rnd.nextInt(2, 2 + periods * days / 2), false, taught, unavailable)
    }
    val assignments = sections.flatMap { section ->
        plan.map { item ->
            // Pick a teacher who teaches the subject; if none, give the first teacher that subject.
            val candidates = teachers.filter { item.subjectId in it.subjectIds }
            val teacher = if (candidates.isNotEmpty()) candidates.random(rnd) else teachers.first()
            Assignment(teacher.id, item.subjectId, section.id, item.weeklyPeriods)
        }
    }
    val fixedTeachers = teachers.map { t ->
        t.copy(subjectIds = t.subjectIds + assignments.filter { it.teacherId == t.id }.map { it.subjectId })
    }
    return SchoolData(
        days = days, grades = listOf(Grade("G1", "الأول")), shifts = listOf(shift), sections = sections,
        subjects = subjects, teachers = fixedTeachers, plan = plan, assignments = assignments,
    )
}

private fun SchoolData.copyWithPreferences(prefs: List<SubjectPreference>): SchoolData = SchoolData(
    days, grades, shifts, sections, subjects, teachers, plan, assignments, rules, prefs,
)

private fun SchoolData.copyWithRules(newRules: List<ConsecutiveRule>): SchoolData = SchoolData(
    days, grades, shifts, sections, subjects, teachers, plan, assignments, newRules,
)

fun main() {
    var failed = 0
    for ((name, body) in tests) {
        val start = System.nanoTime()
        try {
            body()
            println("PASS  $name (${elapsedMs(start)} ms)")
        } catch (t: Throwable) {
            failed++
            println("FAIL  $name -> ${t.message ?: t}")
        }
    }
    println("----")
    println("${tests.size - failed}/${tests.size} passed")
    if (failed > 0) exitProcess(1)
}
