package com.school.timetable.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.school.timetable.data.SetupRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TimetableApp).container.setupRepository
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot(repository)
                    }
                }
            }
        }
    }
}

/** Shows the setup screen until a school is saved; after that, the home screen. Survives restarts because it reads the database. */
@Composable
private fun AppRoot(repository: SetupRepository) {
    var loaded by remember { mutableStateOf(false) }
    var schoolName by remember { mutableStateOf<String?>(null) }
    var reloadCount by remember { mutableStateOf(0) }

    LaunchedEffect(reloadCount) {
        schoolName = repository.savedSchoolName()
        loaded = true
    }

    when {
        !loaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        schoolName == null -> SetupScreen(repository) { reloadCount++ }
        else -> HomeScreen(schoolName ?: "")
    }
}

@Composable
private fun HomeScreen(schoolName: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "مرحباً بك في $schoolName\nتم حفظ إعداد المدرسة. شاشة الجداول قيد البناء.",
            textAlign = TextAlign.Center,
        )
    }
}
