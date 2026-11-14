package com.school.timetable.domain

import java.util.Random

enum class StopReason { TIMEOUT, CANCELLED, NO_SOLUTION, INTERNAL_CHECK_FAILED }

sealed interface SolveResult {
    /** Complete schedule that passed the independent validator. */
    data class Solved(val entries: List<Entry>, val quality: QualityReport) : SolveResult

    /** Proven impossible before searching. */
    data class Infeasible(val issues: List<FeasibilityIssue>) : SolveResult

    /**
     * Search stopped early or proved there is no complete solution.
     * [entries] is the best partial schedule found: it contains no hard violation but is incomplete.
     */
    data class Partial(
        val entries: List<Entry>,
        val unplaced: List<LessonUnit>,
        val reason: StopReason,
        val topBlockers: List<Pair<ViolationType, Int>>,
    ) : SolveResult
}

fun interface CancelSignal {
    fun isCancelled(): Boolean
}

data class SolverConfig(
    val timeBudgetMs: Long,
    val cancel: CancelSignal = CancelSignal { false },
)

/**
 * Constraint solver for the weekly timetable.
 *
 * Model: one variable per [LessonUnit]; its domain is the set of slots that the incremental
 * [ConflictEngine] accepts given the fixed (locked/kept) entries.
 *
 * Search: backtracking with MRV (smallest domain first), Degree tie-break (static),
 * Forward Checking (prune domains of units sharing a section or teacher, and clear a teacher's
 * remaining units once the norm is reached), and LCV value ordering.
 * Every placement is re-checked by the engine, so pruning can only make search faster, never less correct.
 */
class CspSolver(private val data: SchoolData) {

    /** Places [units] around [fixed]. Fixed entries are never moved. */
    fun solve(fixed: List<Entry>, units: List<LessonUnit>, config: SolverConfig): SolveResult {
        // Configuration problems (plan vs assignments, norms) are refused up front: no search can fix them,
        // and a "solved" timetable built on them could never be approved.
        val configIssues = ScheduleValidator.validateConfiguration(data)
            .filter { it.severity != Severity.WARNING }
            .map { FeasibilityIssue(it.type.name, it.message, it.sectionId, it.teacherId) }
        if (configIssues.isNotEmpty()) return SolveResult.Infeasible(configIssues)

        val issues = FeasibilityChecker.check(data, fixed, units)
        if (issues.isNotEmpty()) return SolveResult.Infeasible(issues)

        val deadline = System.nanoTime() + config.timeBudgetMs * 1_000_000L
        val search = Search(data, fixed, units, deadline, config.cancel)
        if (search.run()) {
            val entries = search.currentEntries()
            val report = ScheduleValidator.validate(data, entries)
            if (report.blocking > 0) {
                return SolveResult.Partial(search.bestEntries(), search.bestUnplaced(), StopReason.INTERNAL_CHECK_FAILED, search.topBlockers())
            }
            return SolveResult.Solved(entries, QualityScorer.score(data, entries))
        }
        val reason = search.stopReason() ?: StopReason.NO_SOLUTION
        return SolveResult.Partial(search.bestEntries(), search.bestUnplaced(), reason, search.topBlockers())
    }

    /** Full generation: every assignment's lessons, around the locked entries. */
    fun solveAll(lockedEntries: List<Entry>, config: SolverConfig): SolveResult {
        val units = data.assignments.flatMap { a ->
            List(a.weeklyPeriods) { LessonUnit(a.sectionId, a.subjectId, a.teacherId) }
        }
        val fixed = lockedEntries.filter { it.locked }
        val remaining = units.toMutableList()
        for (e in fixed) {
            remaining.remove(LessonUnit(e.sectionId, e.subjectId, e.teacherId))
        }
        return solve(fixed, remaining, config)
    }

    /** Scoped redistribution: re-places the non-locked lessons that match [scope]; everything else stays. */
    fun redistribute(current: List<Entry>, scope: (Entry) -> Boolean, config: SolverConfig): SolveResult {
        val removable = current.filter { scope(it) && !it.locked }
        val fixed = current - removable.toSet()
        val units = removable.map { LessonUnit(it.sectionId, it.subjectId, it.teacherId) }
        return solve(fixed, units, config)
    }

    /**
     * Large Neighbourhood Search: repeatedly frees one section's lessons on one day, re-solves them,
     * and keeps the result only when the score strictly improves and the schedule stays valid.
     */
    fun improve(current: List<Entry>, totalBudgetMs: Long, cancel: CancelSignal, seed: Long = 7L): Pair<List<Entry>, QualityReport> {
        var best = current
        var bestScore = QualityScorer.score(data, current).score
        val rnd = Random(seed)
        val end = System.nanoTime() + totalBudgetMs * 1_000_000L
        while (System.nanoTime() < end && !cancel.isCancelled() && data.sections.isNotEmpty()) {
            val section = data.sections[rnd.nextInt(data.sections.size)]
            val day = rnd.nextInt(data.days)
            val removable = best.filter { it.sectionId == section.id && it.slot.day == day && !it.locked }
            if (removable.isEmpty()) continue
            val fixed = best - removable.toSet()
            val units = removable.map { LessonUnit(it.sectionId, it.subjectId, it.teacherId) }
            val result = solve(fixed, units, SolverConfig(timeBudgetMs = 2_000L, cancel = cancel))
            if (result is SolveResult.Solved && result.quality.score > bestScore) {
                best = result.entries
                bestScore = result.quality.score
            }
        }
        return best to QualityScorer.score(data, best)
    }

    private class Search(
        private val data: SchoolData,
        private val fixed: List<Entry>,
        private val units: List<LessonUnit>,
        private val deadlineNanos: Long,
        private val cancel: CancelSignal,
    ) {
        private val current = ArrayList<Entry>(fixed)
        private val placed = arrayOfNulls<Entry>(units.size)
        private var placedCount = 0
        private val domains: Array<HashSet<Slot>> = Array(units.size) { HashSet(initialDomain(units[it])) }
        private val degree = IntArray(units.size) { u ->
            units.indices.count { v -> v != u && (units[v].sectionId == units[u].sectionId || units[v].teacherId == units[u].teacherId) }
        }
        private val trail = ArrayList<Pair<Int, Slot>>()
        private val blockers = HashMap<ViolationType, Int>()
        private var stop: StopReason? = null
        private var bestPlaced = -1
        private var bestEntriesSnapshot: List<Entry> = fixed.toList()
        private var bestUnplacedSnapshot: List<LessonUnit> = units.toList()

        fun run(): Boolean = arcConsistency() && search()

        fun currentEntries(): List<Entry> = current.toList()
        fun bestEntries(): List<Entry> = bestEntriesSnapshot
        fun bestUnplaced(): List<LessonUnit> = bestUnplacedSnapshot
        fun stopReason(): StopReason? = stop
        fun topBlockers(): List<Pair<ViolationType, Int>> = blockers.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        private fun initialDomain(unit: LessonUnit): List<Slot> {
            val section = data.sectionById[unit.sectionId] ?: return emptyList()
            val shift = data.shiftById.getValue(section.shiftId)
            return data.slotsOfShift(shift).filter { slot ->
                val candidate = Entry(unit.sectionId, slot, unit.subjectId, unit.teacherId)
                ConflictEngine.checkPlacement(data, fixed, candidate).none { it.severity == Severity.BLOCKING }
            }
        }

        /**
         * AC-3 over pairs of units that share a section or a teacher. Removes root values that have
         * no compatible partner in a neighbour's domain. Sound: it only removes values that no
         * complete solution can use. Returns false on a wipe-out.
         */
        private fun arcConsistency(): Boolean {
            val neighbours = units.indices.map { u ->
                units.indices.filter { v -> v != u && (units[v].sectionId == units[u].sectionId || units[v].teacherId == units[u].teacherId) }
            }
            var changed = true
            while (changed) {
                changed = false
                for (u in units.indices) {
                    for (v in neighbours[u]) {
                        val support = domains[v]
                        val unsupported = domains[u].filter { s -> support.none { t -> compatible(u, s, v, t) } }
                        if (unsupported.isNotEmpty()) {
                            domains[u].removeAll(unsupported.toSet())
                            changed = true
                            if (domains[u].isEmpty()) return false
                        }
                    }
                }
            }
            return true
        }

        /** Two placements can coexist: different cells in a shared section, non-overlapping times for a shared teacher. */
        private fun compatible(u: Int, s: Slot, v: Int, t: Slot): Boolean {
            if (units[u].sectionId == units[v].sectionId && s == t) return false
            if (units[u].teacherId == units[v].teacherId && overlaps(s, t)) return false
            return true
        }

        private fun search(): Boolean {
            if (placedCount == units.size) return true
            if (budgetExhausted()) return false
            val u = selectUnit()
            for (slot in orderedValues(u)) {
                if (budgetExhausted()) return false
                val unit = units[u]
                val entry = Entry(unit.sectionId, slot, unit.subjectId, unit.teacherId)
                val blocking = ConflictEngine.checkPlacement(data, current, entry).filter { it.severity == Severity.BLOCKING }
                if (blocking.isNotEmpty()) {
                    for (v in blocking) blockers[v.type] = (blockers[v.type] ?: 0) + 1
                    continue
                }
                val mark = trail.size
                place(u, entry)
                if (forwardCheck(u, entry) && search()) return true
                unplace(u)
                undoTo(mark)
            }
            return false
        }

        private fun budgetExhausted(): Boolean {
            if (stop != null) return true
            if (cancel.isCancelled()) {
                stop = StopReason.CANCELLED
                return true
            }
            if (System.nanoTime() >= deadlineNanos) {
                stop = StopReason.TIMEOUT
                return true
            }
            return false
        }

        /** MRV, ties broken by static degree. */
        private fun selectUnit(): Int {
            var best = -1
            for (u in units.indices) {
                if (placed[u] != null) continue
                if (best == -1) {
                    best = u
                    continue
                }
                val size = domains[u].size
                val bestSize = domains[best].size
                if (size < bestSize || (size == bestSize && degree[u] > degree[best])) best = u
            }
            return best
        }

        /** Least constraining value first: slots that remove the fewest options from sibling units. */
        private fun orderedValues(u: Int): List<Slot> {
            val slots = domains[u].toList()
            val cost = HashMap<Slot, Int>(slots.size * 2)
            for (s in slots) {
                var c = 0
                for (v in units.indices) {
                    if (v == u || placed[v] != null) continue
                    if (units[v].sectionId != units[u].sectionId && units[v].teacherId != units[u].teacherId) continue
                    if (s in domains[v]) c++
                }
                cost[s] = c
            }
            return slots.sortedBy { cost[it] ?: 0 }
        }

        private fun place(u: Int, entry: Entry) {
            placed[u] = entry
            current += entry
            placedCount++
            if (placedCount > bestPlaced) {
                bestPlaced = placedCount
                bestEntriesSnapshot = current.toList()
                bestUnplacedSnapshot = units.filterIndexed { i, _ -> placed[i] == null }
            }
        }

        private fun unplace(u: Int) {
            placed[u] = null
            current.removeAt(current.size - 1)
            placedCount--
        }

        private fun remove(v: Int, slot: Slot) {
            if (domains[v].remove(slot)) trail += v to slot
        }

        private fun undoTo(mark: Int) {
            while (trail.size > mark) {
                val (v, slot) = trail.removeAt(trail.size - 1)
                domains[v].add(slot)
            }
        }

        /** Prunes sibling domains after placing [entry] for unit [u]. Returns false on a wipe-out. */
        private fun forwardCheck(u: Int, entry: Entry): Boolean {
            val teacher = data.teacherById.getValue(entry.teacherId)
            val teacherLoad = current.count { it.teacherId == teacher.id }
            val normReached = teacherLoad >= teacher.weeklyNorm
            for (v in units.indices) {
                if (placed[v] != null) continue
                val unit = units[v]
                if (unit.sectionId == entry.sectionId) remove(v, entry.slot)
                if (unit.teacherId == teacher.id) {
                    for (s in domains[v].toList()) {
                        if (normReached || overlaps(s, entry.slot)) remove(v, s)
                    }
                }
                if (!teacher.canWorkBothShifts && unit.teacherId == teacher.id) {
                    for (s in domains[v].toList()) if (s.shiftId != entry.slot.shiftId) remove(v, s)
                }
                if (domains[v].isEmpty()) return false
            }
            return true
        }

        private fun overlaps(a: Slot, b: Slot): Boolean {
            if (a.day != b.day) return false
            val ta = data.timeOf(a) ?: return false
            val tb = data.timeOf(b) ?: return false
            return ta.overlaps(tb)
        }
    }
}
