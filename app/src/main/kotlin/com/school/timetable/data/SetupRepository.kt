package com.school.timetable.data

import androidx.room.withTransaction
import com.school.timetable.data.db.AppDatabase
import com.school.timetable.data.db.AcademicYearEntity
import com.school.timetable.data.db.BreakEntity
import com.school.timetable.data.db.PeriodEntity
import com.school.timetable.data.db.SchoolEntity
import com.school.timetable.data.db.ShiftEntity
import com.school.timetable.domain.setup.SchoolSetupInput
import com.school.timetable.domain.setup.SetupBuilder
import com.school.timetable.domain.setup.SetupResult

/** Saves the setup wizard result in one transaction. Nothing is written if validation fails. */
class SetupRepository(private val database: AppDatabase) {

    suspend fun saveInitialSetup(input: SchoolSetupInput): SetupResult {
        val result = SetupBuilder.build(input)
        if (result !is SetupResult.Ok) return result
        val dao = database.setupDao()
        database.withTransaction {
            val schoolId = dao.insertSchool(SchoolEntity(name = input.schoolName.trim(), logoUri = null, principalName = input.principalName?.trim()))
            val yearId = dao.insertYear(
                AcademicYearEntity(schoolId = schoolId, label = input.academicYearLabel.trim(), activeDays = input.activeDays, isActive = true),
            )
            // Domain shift ids are the wizard ids; Room assigns numeric ids.
            for (shift in result.build.shifts) {
                val setup = input.shifts.first { it.id == shift.id }
                val shiftRowId = dao.insertShift(ShiftEntity(yearId = yearId, kind = shift.kind))
                dao.insertPeriods(
                    shift.periods.map { p ->
                        PeriodEntity(yearId = yearId, shiftId = shiftRowId, indexNo = p.index, startMin = p.time.startMin, endMin = p.time.endMin)
                    },
                )
                if (setup.breaks.isNotEmpty()) {
                    dao.insertBreaks(setup.breaks.map { BreakEntity(shiftId = shiftRowId, afterPeriod = it.afterPeriod, durationMin = it.durationMin) })
                }
            }
        }
        return result
    }

    /** Returns the saved school name, or null when no setup exists yet. */
    suspend fun savedSchoolName(): String? = database.setupDao().schoolName()
}
