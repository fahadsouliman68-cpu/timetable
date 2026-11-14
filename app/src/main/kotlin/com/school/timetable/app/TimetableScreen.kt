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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.school.timetable.data.ApproveResult
import com.school.timetable.data.GenerateResult
import com.school.timetable.data.TimetableRepository
import com.school.timetable.data.TimetableView
import com.school.timetable.domain.setup.DayNames
import kotlinx.coroutines.launch

private fun statusLabel(status: String): String = when (status) {
    "DRAFT" -> "مسودة"
    "APPROVED" -> "معتمد"
    "SUPERSEDED" -> "سابق"
    else -> status
}

/** Generates drafts, shows one section's week with the validation report, and approves a valid draft. */
@Composable
fun TimetableScreen(repository: TimetableRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<TimetableView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pickedSection by remember { mutableStateOf<Long?>(null) }
    var pickedVersion by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(reloadCount, pickedVersion) {
        view = repository.latest(pickedVersion)
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("الجدول", onBack)

        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    try {
                        when (val result = repository.generate()) {
                            is GenerateResult.Done -> {
                                message = "تم توليد الإصدار ${result.versionNumber} بجودة ${result.quality} من 100"
                                pickedVersion = null
                                reloadCount++
                            }
                            is GenerateResult.Failed -> message = result.messages.joinToString("\n")
                        }
                    } catch (e: Exception) {
                        message = "تعذر توليد الجدول: ${e.message ?: "خطأ غير معروف"}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "جارٍ التنفيذ، انتظر..." else "توليد جدول جديد")
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!loaded) {
            CircularProgressIndicator()
        } else {
            val current = view
            if (current == null) {
                Text("لا يوجد جدول بعد. اضغط توليد جدول جديد.")
            } else {
                TimetableContent(
                    view = current,
                    pickedSection = pickedSection,
                    busy = busy,
                    onPickSection = { pickedSection = it },
                    onPickVersion = { pickedVersion = it },
                    onApprove = {
                        scope.launch {
                            busy = true
                            try {
                                when (val result = repository.approve(current.versionId)) {
                                    is ApproveResult.Approved -> {
                                        message = null
                                        reloadCount++
                                    }
                                    is ApproveResult.Rejected -> message = result.messages.joinToString("\n")
                                }
                            } catch (e: Exception) {
                                message = "تعذر الاعتماد: ${e.message ?: "خطأ غير معروف"}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TimetableContent(
    view: TimetableView,
    pickedSection: Long?,
    busy: Boolean,
    onPickSection: (Long) -> Unit,
    onPickVersion: (Long) -> Unit,
    onApprove: () -> Unit,
) {
    Text("الإصدارات")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        view.versions.forEach { v ->
            FilterChip(
                selected = v.id == view.versionId,
                onClick = { onPickVersion(v.id) },
                label = { Text("${v.number} - ${statusLabel(v.status)} - ${v.quality ?: "-"}") },
            )
        }
    }

    Text("الإصدار ${view.versionNumber} (${statusLabel(view.status)}) - الجودة: ${view.quality ?: "-"} من 100")

    if (view.status == "DRAFT") {
        val canApprove = view.report?.canApprove == true
        Button(enabled = canApprove && !busy, onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
            Text("اعتماد هذا الجدول")
        }
        if (!canApprove) {
            Text("لا يمكن الاعتماد قبل إصلاح التعارضات والنواقص الموضحة في تقرير التحقق.")
        }
    }

    val section = view.sections.find { it.id == (pickedSection ?: view.sections.firstOrNull()?.id) }
    if (section != null) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            view.sections.forEach { s ->
                FilterChip(selected = s.id == section.id, onClick = { onPickSection(s.id) }, label = { Text(s.label) })
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

    view.report?.let { report ->
        Text("تقرير التحقق", style = MaterialTheme.typography.titleMedium)
        Text("تعارضات: ${report.blocking}   ناقص: ${report.missing}   تنبيهات: ${report.warnings}")
        report.lines.take(20).forEach { line ->
            Text(
                "• [${line.label}] ${line.message}",
                color = if (line.hard) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (report.lines.size > 20) Text("و${report.lines.size - 20} ملاحظة أخرى")

        Text("تفصيل الجودة", style = MaterialTheme.typography.titleMedium)
        report.deductions.forEach { Text("• $it") }
    }
}

@Composable
private fun Cell(text: String, bold: Boolean = false) {
    Box(modifier = Modifier.width(110.dp).padding(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else null,
        )
    }
}
