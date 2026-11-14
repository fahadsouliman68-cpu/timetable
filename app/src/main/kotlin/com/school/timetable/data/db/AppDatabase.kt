package com.school.timetable.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SchoolEntity::class,
        AcademicYearEntity::class,
        ShiftEntity::class,
        PeriodEntity::class,
        BreakEntity::class,
        GradeEntity::class,
        SectionEntity::class,
        SubjectEntity::class,
        GradeSubjectPlanEntity::class,
        TeacherEntity::class,
        TeacherSubjectEntity::class,
        TeachingAssignmentEntity::class,
        TimetableVersionEntity::class,
        TimetableEntryEntity::class,
        AppSettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun setupDao(): SetupDao
    abstract fun dataDao(): DataDao
    abstract fun timetableDao(): TimetableDao
}
