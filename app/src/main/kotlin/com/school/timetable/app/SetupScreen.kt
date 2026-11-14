package com.school.timetable.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.school.timetable.data.SetupRepository
import com.school.timetable.domain.ShiftKind
import com.school.timetable.domain.setup.SchoolSetupInput
import com.school.timetable.domain.setup.SetupResult
import com.school.timetable.domain.setup.ShiftSetup
import kotlinx.coroutines.launch

/** Parses "HH:MM" into minutes since midnight, or null when the text is not a valid time. */
internal fun parseClockMinutes(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

/** Setup wizard, phase 1: one morning shift. Validation messages come from the domain layer. */
@Composable
fun SetupScreen(repository: SetupRepository, onSaved: () -> Unit) {
    var schoolName by remember { mutableStateOf("") }
    var yearLabel by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var activeDays by remember { mutableStateOf("5") }
    var firstStart by remember { mutableStateOf("07:30") }
    var periodCount by remember { mutableStateOf("7") }
    var periodLength by remember { mutableStateOf("45") }
    var gap by remember { mutableStateOf("5") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("إعداد المدرسة", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = schoolName,
            onValueChange = { schoolName = it },
            label = { Text("اسم المدرسة") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = yearLabel,
            onValueChange = { yearLabel = it },
            label = { Text("العام الدراسي، مثال: 2026-2027") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = principal,
            onValueChange = { principal = it },
            label = { Text("اسم المدير (اختياري)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = activeDays,
            onValueChange = { activeDays = it },
            label = { Text("عدد أيام الدوام في الأسبوع (1 إلى 7)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = firstStart,
            onValueChange = { firstStart = it },
            label = { Text("بداية الدوام الصباحي (HH:MM)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = periodCount,
            onValueChange = { periodCount = it },
            label = { Text("عدد الحصص") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = periodLength,
            onValueChange = { periodLength = it },
            label = { Text("طول الحصة بالدقائق") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = gap,
            onValueChange = { gap = it },
            label = { Text("الفاصل بين الحصص بالدقائق") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        errors.forEach { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !saving,
            onClick = {
                val start = parseClockMinutes(firstStart)
                if (start == null) {
                    errors = listOf("بداية الدوام يجب أن تكون بصيغة HH:MM")
                    return@Button
                }
                val input = SchoolSetupInput(
                    schoolName = schoolName,
                    academicYearLabel = yearLabel,
                    principalName = principal.ifBlank { null },
                    activeDays = activeDays.toIntOrNull() ?: -1,
                    shifts = listOf(
                        ShiftSetup(
                            id = "morning",
                            kind = ShiftKind.MORNING,
                            firstStartMin = start,
                            periodCount = periodCount.toIntOrNull() ?: -1,
                            periodLengthMin = periodLength.toIntOrNull() ?: -1,
                            gapMin = gap.toIntOrNull() ?: -1,
                        ),
                    ),
                )
                saving = true
                scope.launch {
                    try {
                        when (val result = repository.saveInitialSetup(input)) {
                            is SetupResult.Ok -> onSaved()
                            is SetupResult.Invalid -> errors = result.errors
                        }
                    } catch (e: Exception) {
                        errors = listOf("تعذر الحفظ، حاول مرة أخرى")
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("حفظ الإعداد")
        }
    }
}
