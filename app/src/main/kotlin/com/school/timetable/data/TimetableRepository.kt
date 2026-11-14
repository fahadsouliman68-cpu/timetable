package com.school.timetable.data

import com.school.timetable.data.db.AppDatabase
import com.school.timetable.data.db.PeriodEntity
import com.school.timetable.data.db.TimetableDao
import com.school.timetable.data.db.TimetableEntryEntity
import com.school.timetable.data.db.TimetableVersionEntity
import com.school.timetable.domain.Assignment
import com.school.timetable.domain.CspSolver
import com.school.timetable.domain.Grade
import com.school.timetable.domain.PlanItem
import com.school.timetable.domain.Period
import com.school.timetable.domain.SchoolData
import com.school.timetable.domain.Section
import com.school.timetable.domain.SolveResult
import com.school.timetable.domain.SolverConfig
import com.school.timetable.domain.Shift
import com.school.timetable.domain.Subject
import com.school.timetable.domain.Teacher
import com.school.timetable.domain.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SectionRow(val id: Long, val label: String, val shiftId: Long)

data class LessonView(
    val sectionId: Long,
    val dayIndex: Int,
    val periodIndex: Int,
    val shiftId: Long,
    val subjectName: String,
    val teacherName: String,
)

data class TimetableView(
    val versionNumber: Int,
    val quality: Int?,
    val days: Int,
    val sections: List<SectionRow>,
    val periods: List<PeriodEntity>,
    val lessons: List<LessonView>,
)

sealed interface GenerateResult {
    data class Done(val versionNumber: Int, val quality: Int) : GenerateResult
    data class Failed(val messages: List<String>) : GenerateResult
}

/** Builds the solver model from stored data, runs the solver, and stores the result as a draft version. */
class TimetableRepository(
    private val database: AppDatabase,
    private val dataRepository: DataRepository,
) {
    private val dao: TimetableDao get() = database.timetableDao()

    suspend fun generate(): GenerateResult {
        val year = database.setupDao().activeYear()
            ?: return GenerateResult.Failed(listOf("لا توجد سنة دراسية نشطة"))
        val data = dataRepository.load()
            ?: return GenerateResult.Failed(listOf("لا توجد بيانات للسنة الدراسية"))
        if (data.assignments.isEmpty()) {
            return GenerateResult.Failed(listOf("لا توجد تكليفات بعد. أضف الإسناد من تبويب الإسناد أولاً."))
        }
        val periods = database.setupDao().periodsOf(year.id)

        val model = try {
            buildSchoolData(year.activeDays, data, periods)
        } catch (e: IllegalArgumentException) {
            return GenerateResult.Failed(listOf(e.message ?: "بيانات غير صالحة"))
        }

        val outcome = withContext(Dispatchers.Default) {
            CspSolver(model).solveAll(emptyList(), SolverConfig(timeBudgetMs = 20_000L))
        }

        return when (outcome) {
            is SolveResult.Solved -> {
                val number = dao.maxVersionNumber(year.id) + 1
                val versionId = dao.insertVersion(
                    TimetableVersionEntity(
                        yearId = year.id,
                        number = number,
                        status = "DRAFT",
                        qualityScore = outcome.quality.score,
                        note = "",
                        createdAtMillis = System.currentTimeMillis(),
                        approvedAtMillis = null,
                    ),
                )
                val periodIdOf = periods.associate { (it.shiftId to it.indexNo) to it.id }
                dao.insertEntries(
                    outcome.entries.map { e ->
                        val periodId = periodIdOf[e.slot.shiftId.toLong() to e.slot.periodIndex]
                            ?: throw IllegalStateException("فترة غير موجودة للحصة المجدولة")
                        TimetableEntryEntity(
                            versionId = versionId,
                            sectionId = e.sectionId.toLong(),
                            dayIndex = e.slot.day,
                            periodId = periodId,
                            subjectId = e.subjectId.toLong(),
                            teacherId = e.teacherId.toLong(),
                            isLocked = false,
                            source = "SOLVER",
                        )
                    },
                )
                GenerateResult.Done(number, outcome.quality.score)
            }
            is SolveResult.Infeasible -> GenerateResult.Failed(outcome.issues.map { it.message })
            is SolveResult.Partial -> GenerateResult.Failed(
                listOf("لم يكتمل الجدول، بقيت ${outcome.unplaced.size} حصة بلا مكان. راجع الخطط والإسناد وأوقات الحصص."),
            )
        }
    }

    /** Loads the newest version with its lessons, or null when nothing was generated yet. */
    suspend fun latest(): TimetableView? {
        val year = database.setupDao().activeYear() ?: return null
        val version = dao.latestVersion(year.id) ?: return null
        val data = dataRepository.load() ?: return null
        val periods = database.setupDao().periodsOf(year.id)
        val periodById = periods.associateBy { it.id }
        val gradeName = data.grades.associate { it.id to it.name }
        val subjectName = data.subjects.associate { it.id to it.name }
        val teacherName = data.teachers.associate { it.id to it.name }

        val lessons = dao.entries(version.id).mapNotNull { e ->
            val period = periodById[e.periodId] ?: return@mapNotNull null
            LessonView(
                sectionId = e.sectionId,
                dayIndex = e.dayIndex,
                periodIndex = period.indexNo,
                shiftId = period.shiftId,
                subjectName = subjectName[e.subjectId] ?: "",
                teacherName = teacherName[e.teacherId] ?: "",
            )
        }
        return TimetableView(
            versionNumber = version.number,
            quality = version.qualityScore,
            days = year.activeDays,
            sections = data.sections.map {
                SectionRow(it.id, "${gradeName[it.gradeId] ?: ""} - ${it.name}", it.shiftId)
            },
            periods = periods,
            lessons = lessons,
        )
    }

    private fun buildSchoolData(days: Int, data: YearData, periods: List<PeriodEntity>): SchoolData {
        val shifts = data.shifts.map { shift ->
            Shift(
                id = shift.id.toString(),
                kind = shift.kind,
                periods = periods.filter { it.shiftId == shift.id }
                    .sortedBy { it.indexNo }
                    .map { Period(it.indexNo, TimeRange(it.startMin, it.endMin)) },
            )
        }
        return SchoolData(
            days = days,
            grades = data.grades.map { Grade(it.id.toString(), it.name) },
            shifts = shifts,
            sections = data.sections.map {
                Section(
                    id = it.id.toString(),
                    name = it.name,
                    gradeId = it.gradeId.toString(),
                    shiftId = it.shiftId.toString(),
                    homeroomTeacherId = it.homeroomTeacherId?.toString(),
                )
            },
            subjects = data.subjects.map { Subject(it.id.toString(), it.name) },
            teachers = data.teachers.map { t ->
                Teacher(
                    id = t.id.toString(),
                    name = t.name,
                    weeklyNorm = t.weeklyNorm,
                    canWorkBothShifts = t.canWorkBothShifts,
                    subjectIds = data.teacherSubjects
                        .filter { it.teacherId == t.id }
                        .map { it.subjectId.toString() }
                        .toSet(),
                )
            },
            plan = data.plans.map { PlanItem(it.gradeId.toString(), it.subjectId.toString(), it.weeklyPeriods) },
            assignments = data.assignments.map {
                Assignment(
                    teacherId = it.teacherId.toString(),
                    subjectId = it.subjectId.toString(),
                    sectionId = it.sectionId.toString(),
                    weeklyPeriods = it.weeklyPeriods,
                )
            },
        )
    }
}
