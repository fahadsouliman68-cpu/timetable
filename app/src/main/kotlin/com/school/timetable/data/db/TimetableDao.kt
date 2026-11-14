package com.school.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Stores generated timetable versions and their lessons. */
@Dao
interface TimetableDao {

    @Query("SELECT COALESCE(MAX(number), 0) FROM timetable_versions WHERE yearId = :yearId")
    suspend fun maxVersionNumber(yearId: Long): Int

    @Insert
    suspend fun insertVersion(version: TimetableVersionEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<TimetableEntryEntity>)

    @Query("SELECT * FROM timetable_versions WHERE yearId = :yearId ORDER BY number DESC LIMIT 1")
    suspend fun latestVersion(yearId: Long): TimetableVersionEntity?

    @Query("SELECT * FROM timetable_entries WHERE versionId = :versionId")
    suspend fun entries(versionId: Long): List<TimetableEntryEntity>

    @Query("SELECT * FROM timetable_versions WHERE yearId = :yearId ORDER BY number DESC")
    suspend fun versions(yearId: Long): List<TimetableVersionEntity>

    @Query("UPDATE timetable_versions SET status = :status, approvedAtMillis = :approvedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, approvedAt: Long?)

    @Query("UPDATE timetable_versions SET status = 'SUPERSEDED' WHERE yearId = :yearId AND status = 'APPROVED' AND id != :exceptId")
    suspend fun supersedeApproved(yearId: Long, exceptId: Long)
}
