package com.school.timetable.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.school.timetable.data.DataRepository
import com.school.timetable.data.SetupRepository
import com.school.timetable.data.TimetableRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TimetableApp).container
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot(container.setupRepository, container.dataRepository, container.timetableRepository)
                    }
                }
            }
        }
    }
}

private enum class Screen { HOME, DATA, TIMETABLE }

/** Shows setup until a school is saved, then the home screen. Reads the database, so it survives restarts. */
@Composable
private fun AppRoot(
    setupRepository: SetupRepository,
    dataRepository: DataRepository,
    timetableRepository: TimetableRepository,
) {
    var loaded by remember { mutableStateOf(false) }
    var schoolName by remember { mutableStateOf<String?>(null) }
    var reloadCount by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(reloadCount) {
        schoolName = setupRepository.savedSchoolName()
        loaded = true
    }

    when {
        !loaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        schoolName == null -> SetupScreen(setupRepository) { reloadCount++ }
        screen == Screen.DATA -> DataScreen(dataRepository) { screen = Screen.HOME }
        screen == Screen.TIMETABLE -> TimetableScreen(timetableRepository) { screen = Screen.HOME }
        else -> HomeScreen(
            schoolName = schoolName ?: "",
            onOpenData = { screen = Screen.DATA },
            onOpenTimetable = { screen = Screen.TIMETABLE },
        )
    }
}

@Composable
private fun HomeScreen(schoolName: String, onOpenData: () -> Unit, onOpenTimetable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "مرحباً بك في $schoolName",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenData, modifier = Modifier.fillMaxWidth()) {
            Text("الصفوف والشعب والمواد والمعلمون")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenTimetable, modifier = Modifier.fillMaxWidth()) {
            Text("الجدول")
        }
    }
}
