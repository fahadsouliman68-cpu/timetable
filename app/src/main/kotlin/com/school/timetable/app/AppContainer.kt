package com.school.timetable.app

import android.content.Context
import androidx.room.Room
import com.school.timetable.data.DataRepository
import com.school.timetable.data.SetupRepository
import com.school.timetable.data.db.AppDatabase

/** Manual dependency wiring. Hilt is deferred until the graph grows beyond a few repositories. */
class AppContainer(context: Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "timetable.db",
    ).build()

    val setupRepository: SetupRepository = SetupRepository(database)
    val dataRepository: DataRepository = DataRepository(database)
}
