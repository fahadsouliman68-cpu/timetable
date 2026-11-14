package com.school.timetable.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.school.timetable.data.GenerateResult
import com.school.timetable.data.TimetableRepository
import com.school.timetable.data.TimetableView
import com.school.timetable.domain.setup.DayNames
import kotlinx.coroutines.launch

/** Generates a draft timetable and shows one section's week. */
@Composable
fun TimetableScreen(repository: TimetableRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<TimetableView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pickedSection by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(reloadCount) {
        view = repository.latest()
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("الجدول", style = MaterialTheme.typography.headlineSmall)
        }

        Button(
            enabled = !generating,
            onClick = {
                scope.launch {
                    generating = true
                    message = null
                    try {
                        when (val result = repository.generate()) {
                            is GenerateResult.Done -> {
                                message = "تم توليد الإصدار ${result.versionNumber} بجودة ${result.quality} من 100"
                                reloadCount++
                            }
                            is GenerateResult.Failed -> message = result.messages.joinToString("\n")
                        }
                    } catch (e: Exception) {
                        message = "تعذر توليد الجدول: ${e.message ?: "خطأ غير معروف"}"
                    } finally {
                        generating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (generating) "جارٍ التوليد، انتظر..." else "توليد جدول جديد")
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!loaded) {
            CircularProgressIndicator()
        } else {
            val current = view
            if (current == null) {
                Text("لا يوجد جدول بعد. اضغط توليد جدول جديد.")
            } else {
                TimetableContent(current, pickedSection) { pickedSection = it }
            }
        }
    }
}

@Composable
private fun TimetableContent(view: TimetableView, picked: Long?, onPick: (Long) -> Unit) {
    Text("الإصدار ${view.versionNumber} - الجودة: ${view.quality ?: "-"} من 100")

    val section = view.sections.find { it.id == (picked ?: view.sections.firstOrNull()?.id) } ?: return

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        view.sections.forEach { s ->
            FilterChip(selected = s.id == section.id, onClick = { onPick(s.id) }, label = { Text(s.label) })
        }
    }

    val periods = view.periods.filter { it.shiftId == section.shiftId }.sortedBy { it.indexNo }
    val lessons = view.lessons.filter { it.sectionId == section.id }

    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            Cell("الحصة", bold = true)
            for (day in 0 until view.days) {
                Cell(DayNames.AR.getOrElse(day) { "" }, bold = true)
            }
        }
        periods.forEach { period ->
            Row {
                Cell("${period.indexNo}", bold = true)
                for (day in 0 until view.days) {
                    val lesson = lessons.find {
                        it.dayIndex == day && it.periodIndex == period.indexNo && it.shiftId == section.shiftId
                    }
                    Cell(if (lesson == null) "-" else "${lesson.subjectName}\n${lesson.teacherName}")
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, bold: Boolean = false) {
    Box(modifier = Modifier.width(110.dp).padding(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
        )
    }
}
