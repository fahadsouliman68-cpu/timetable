package com.school.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SetupDao {

    @Insert
    suspend fun insertSchool(school: SchoolEntity): Long

    @Insert
    suspend fun insertYear(year: AcademicYearEntity): Long

    @Insert
    suspend fun insertShift(shift: ShiftEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeriods(periods: List<PeriodEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBreaks(breaks: List<BreakEntity>)

    @Query("SELECT * FROM academic_years WHERE isActive = 1 LIMIT 1")
    suspend fun activeYear(): AcademicYearEntity?

    @Query("SELECT * FROM shifts WHERE yearId = :yearId")
    suspend fun shiftsOf(yearId: Long): List<ShiftEntity>

    @Query("SELECT * FROM periods WHERE yearId = :yearId ORDER BY shiftId, indexNo")
    suspend fun periodsOf(yearId: Long): List<PeriodEntity>

    @Query("SELECT COUNT(*) FROM academic_years")
    suspend fun yearCount(): Int
}
