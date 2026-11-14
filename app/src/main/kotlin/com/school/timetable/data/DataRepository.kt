package com.school.timetable.data

import androidx.room.withTransaction
import com.school.timetable.data.db.AppDatabase
import com.school.timetable.data.db.GradeEntity
import com.school.timetable.data.db.SectionEntity
import com.school.timetable.data.db.ShiftEntity
import com.school.timetable.data.db.SubjectEntity
import com.school.timetable.data.db.TeacherEntity
import com.school.timetable.data.db.TeacherSubjectEntity

/** Everything the data screen needs for the active year, loaded in one pass. */
data class YearData(
    val yearId: Long,
    val shifts: List<ShiftEntity>,
    val grades: List<GradeEntity>,
    val sections: List<SectionEntity>,
    val subjects: List<SubjectEntity>,
    val teachers: List<TeacherEntity>,
    val teacherSubjects: List<TeacherSubjectEntity>,
)

/** Reads and writes school data. Throws on constraint violations; the UI turns them into messages. */
class DataRepository(private val database: AppDatabase) {

    private val dao get() = database.dataDao()

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
}
