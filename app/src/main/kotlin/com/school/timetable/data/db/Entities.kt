package com.school.timetable.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.school.timetable.domain.ShiftKind

// Room ids are Long. The domain uses String ids, so the mapping layer converts with toString().
// Every table that belongs to a school year carries yearId, so years stay isolated.

@Entity(tableName = "schools")
data class SchoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val logoUri: String?,
    val principalName: String?,
)

@Entity(
    tableName = "academic_years",
    foreignKeys = [ForeignKey(entity = SchoolEntity::class, parentColumns = ["id"], childColumns = ["schoolId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["schoolId", "label"], unique = true)],
)
data class AcademicYearEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schoolId: Long,
    val label: String,
    val activeDays: Int,
    val isActive: Boolean,
)

@Entity(
    tableName = "shifts",
    foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["yearId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["yearId", "kind"], unique = true)],
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val kind: ShiftKind,
)

@Entity(
    tableName = "periods",
    foreignKeys = [ForeignKey(entity = ShiftEntity::class, parentColumns = ["id"], childColumns = ["shiftId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["shiftId", "indexNo"], unique = true)],
)
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val shiftId: Long,
    val indexNo: Int,
    val startMin: Int,
    val endMin: Int,
)

@Entity(
    tableName = "breaks",
    foreignKeys = [ForeignKey(entity = ShiftEntity::class, parentColumns = ["id"], childColumns = ["shiftId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["shiftId", "afterPeriod"], unique = true)],
)
data class BreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val afterPeriod: Int,
    val durationMin: Int,
)

@Entity(
    tableName = "grades",
    foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["yearId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["yearId", "name"], unique = true)],
)
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(entity = GradeEntity::class, parentColumns = ["id"], childColumns = ["gradeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ShiftEntity::class, parentColumns = ["id"], childColumns = ["shiftId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TeacherEntity::class, parentColumns = ["id"], childColumns = ["homeroomTeacherId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["yearId", "gradeId", "name"], unique = true), Index(value = ["homeroomTeacherId"])],
)
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val gradeId: Long,
    val shiftId: Long,
    val name: String,
    val homeroomTeacherId: Long?,
)

@Entity(
    tableName = "subjects",
    foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["yearId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["yearId", "name"], unique = true)],
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val name: String,
)

@Entity(
    tableName = "grade_subject_plan",
    foreignKeys = [
        ForeignKey(entity = GradeEntity::class, parentColumns = ["id"], childColumns = ["gradeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["yearId", "gradeId", "subjectId"], unique = true)],
)
data class GradeSubjectPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val gradeId: Long,
    val subjectId: Long,
    val weeklyPeriods: Int,
)

@Entity(
    tableName = "teachers",
    foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["yearId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["yearId", "name"], unique = true)],
)
data class TeacherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val name: String,
    val weeklyNorm: Int,
    val canWorkBothShifts: Boolean,
)

@Entity(
    tableName = "teacher_subjects",
    primaryKeys = ["teacherId", "subjectId"],
    foreignKeys = [
        ForeignKey(entity = TeacherEntity::class, parentColumns = ["id"], childColumns = ["teacherId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["subjectId"])],
)
data class TeacherSubjectEntity(
    val teacherId: Long,
    val subjectId: Long,
)

@Entity(
    tableName = "teaching_assignments",
    foreignKeys = [
        ForeignKey(entity = TeacherEntity::class, parentColumns = ["id"], childColumns = ["teacherId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["yearId", "sectionId", "subjectId"], unique = true), Index(value = ["teacherId"])],
)
data class TeachingAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val teacherId: Long,
    val subjectId: Long,
    val sectionId: Long,
    val weeklyPeriods: Int,
)

@Entity(
    tableName = "timetable_versions",
    foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["yearId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["yearId", "number"], unique = true)],
)
data class TimetableVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearId: Long,
    val number: Int,
    val status: String,
    val qualityScore: Int?,
    val note: String,
    val createdAtMillis: Long,
    val approvedAtMillis: Long?,
)

@Entity(
    tableName = "timetable_entries",
    foreignKeys = [
        ForeignKey(entity = TimetableVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PeriodEntity::class, parentColumns = ["id"], childColumns = ["periodId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TeacherEntity::class, parentColumns = ["id"], childColumns = ["teacherId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["versionId", "sectionId", "dayIndex", "periodId"], unique = true),
        Index(value = ["versionId", "teacherId", "dayIndex"]),
    ],
)
data class TimetableEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: Long,
    val sectionId: Long,
    val dayIndex: Int,
    val periodId: Long,
    val subjectId: Long,
    val teacherId: Long,
    val isLocked: Boolean,
    val source: String,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
