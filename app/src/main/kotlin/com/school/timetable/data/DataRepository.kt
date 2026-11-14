package com.school.timetable.data

import androidx.room.withTransaction
import com.school.timetable.data.db.AppDatabase
import com.school.timetable.data.db.DataDao
import com.school.timetable.data.db.GradeEntity
import com.school.timetable.data.db.GradeSubjectPlanEntity
import com.school.timetable.data.db.SectionEntity
import com.school.timetable.data.db.ShiftEntity
import com.school.timetable.data.db.SubjectEntity
import com.school.timetable.data.db.TeacherEntity
import com.school.timetable.data.db.TeacherSubjectEntity
import com.school.timetable.data.db.TeachingAssignmentEntity

/** Everything the data screen needs for the active year, loaded in one pass. */
data class YearData(
    val yearId: Long,
    val shifts: List<ShiftEntity>,
    val grades: List<GradeEntity>,
    val sections: List<SectionEntity>,
    val subjects: List<SubjectEntity>,
    val teachers: List<TeacherEntity>,
    val teacherSubjects: List<TeacherSubjectEntity>,
    val plans: List<GradeSubjectPlanEntity>,
    val assignments: List<TeachingAssignmentEntity>,
)

/** Reads and writes school data. Throws on constraint violations; the UI turns them into messages. */
class DataRepository(private val database: AppDatabase) {

    private val dao: DataDao get() = database.dataDao()

    /** Returns null when no school year exists yet. */
    suspend fun load(): YearData? {
        val year = database.setupDao().activeYear() ?: return null
        val yearId = year.id
        return YearData(
            yearId = yearId,
            shifts = database.setupDao().shiftsOf(yearId),
            grades = dao.grades(yearId),
            sections = dao.sections(yearId),
            subjects = dao.subjects(yearId),
            teachers = dao.teachers(yearId),
            teacherSubjects = dao.teacherSubjects(yearId),
            plans = dao.plans(yearId),
            assignments = dao.assignments(yearId),
        )
    }

    suspend fun addGrade(yearId: Long, name: String, sortOrder: Int) {
        dao.insertGrade(GradeEntity(yearId = yearId, name = name, sortOrder = sortOrder))
    }

    suspend fun deleteGrade(id: Long) = dao.deleteGrade(id)

    suspend fun addSection(yearId: Long, gradeId: Long, shiftId: Long, name: String) {
        dao.insertSection(
            SectionEntity(yearId = yearId, gradeId = gradeId, shiftId = shiftId, name = name, homeroomTeacherId = null),
        )
    }

    suspend fun deleteSection(id: Long) = dao.deleteSection(id)

    suspend fun addSubject(yearId: Long, name: String) {
        dao.insertSubject(SubjectEntity(yearId = yearId, name = name))
    }

    suspend fun deleteSubject(id: Long) = dao.deleteSubject(id)

    suspend fun addTeacher(yearId: Long, name: String, weeklyNorm: Int, canWorkBothShifts: Boolean, subjectIds: Set<Long>) {
        database.withTransaction {
            val teacherId = dao.insertTeacher(
                TeacherEntity(yearId = yearId, name = name, weeklyNorm = weeklyNorm, canWorkBothShifts = canWorkBothShifts),
            )
            dao.insertTeacherSubjects(subjectIds.map { TeacherSubjectEntity(teacherId = teacherId, subjectId = it) })
        }
    }

    suspend fun deleteTeacher(id: Long) = dao.deleteTeacher(id)

    /** Replaces the weekly plan of one grade. Subjects with zero periods are left out. */
    suspend fun savePlan(yearId: Long, gradeId: Long, periodsBySubject: Map<Long, Int>) {
        database.withTransaction {
            dao.deletePlansOfGrade(yearId, gradeId)
            val rows = periodsBySubject
                .filter { it.value > 0 }
                .map { (subjectId, periods) ->
                    GradeSubjectPlanEntity(yearId = yearId, gradeId = gradeId, subjectId = subjectId, weeklyPeriods = periods)
                }
            if (rows.isNotEmpty()) dao.insertPlans(rows)
        }
    }

    /** Replaces the assignments of one section with the given rows. */
    suspend fun saveAssignments(yearId: Long, sectionId: Long, rows: List<TeachingAssignmentEntity>) {
        database.withTransaction {
            dao.deleteAssignmentsOfSection(yearId, sectionId)
            if (rows.isNotEmpty()) dao.insertAssignments(rows)
        }
    }
}
