package com.school.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Read and write access for grades, sections, subjects and teachers of the active year. */
@Dao
interface DataDao {

    @Query("SELECT * FROM grades WHERE yearId = :yearId ORDER BY sortOrder, id")
    suspend fun grades(yearId: Long): List<GradeEntity>

    @Insert
    suspend fun insertGrade(grade: GradeEntity): Long

    @Query("DELETE FROM grades WHERE id = :id")
    suspend fun deleteGrade(id: Long)

    @Query("SELECT * FROM sections WHERE yearId = :yearId ORDER BY gradeId, name")
    suspend fun sections(yearId: Long): List<SectionEntity>

    @Insert
    suspend fun insertSection(section: SectionEntity): Long

    @Query("DELETE FROM sections WHERE id = :id")
    suspend fun deleteSection(id: Long)

    @Query("SELECT * FROM subjects WHERE yearId = :yearId ORDER BY name")
    suspend fun subjects(yearId: Long): List<SubjectEntity>

    @Insert
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubject(id: Long)

    @Query("SELECT * FROM teachers WHERE yearId = :yearId ORDER BY name")
    suspend fun teachers(yearId: Long): List<TeacherEntity>

    @Insert
    suspend fun insertTeacher(teacher: TeacherEntity): Long

    @Query("DELETE FROM teachers WHERE id = :id")
    suspend fun deleteTeacher(id: Long)

    @Insert
    suspend fun insertTeacherSubjects(links: List<TeacherSubjectEntity>)

    @Query("SELECT ts.* FROM teacher_subjects ts JOIN teachers t ON t.id = ts.teacherId WHERE t.yearId = :yearId")
    suspend fun teacherSubjects(yearId: Long): List<TeacherSubjectEntity>
}
