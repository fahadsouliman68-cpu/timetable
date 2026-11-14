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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.school.timetable.data.DataRepository
import com.school.timetable.data.SetupRepository
import com.school.timetable.data.TimetableRepository
import com.school.timetable.data.backup.BackupService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TimetableApp).container
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot(
                            container.setupRepository,
                            container.dataRepository,
                            container.timetableRepository,
                            container.backupService,
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { HOME, DATA, TIMETABLE, BACKUP }

/** Shows setup until a school is saved, then the home screen. Reads the database, so it survives restarts. */
@Composable
private fun AppRoot(
    setupRepository: SetupRepository,
    dataRepository: DataRepository,
    timetableRepository: TimetableRepository,
    backupService: BackupService,
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
        screen == Screen.BACKUP -> BackupScreen(backupService, timetableRepository) { screen = Screen.HOME }
        else -> HomeScreen(
            schoolName = schoolName ?: "",
            onOpenData = { screen = Screen.DATA },
            onOpenTimetable = { screen = Screen.TIMETABLE },
            onOpenBackup = { screen = Screen.BACKUP },
        )
    }
}

@Composable
private fun HomeScreen(
    schoolName: String,
    onOpenData: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp)) {
                Text(
                    text = "جداول المدرسة",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = schoolName, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeCard("الجدول", "توليد الجداول وعرضها واعتمادها", onOpenTimetable)
            HomeCard("الصفوف والشعب والمواد والمعلمون", "إدخال البيانات والخطط والإسناد", onOpenData)
            HomeCard("النسخ الاحتياطي والتقارير", "نسخة احتياطية واستعادة وتصدير PDF", onOpenBackup)
        }
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
