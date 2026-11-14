package com.school.timetable.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

/** Setup wizard: school info, a morning shift, and an optional evening shift. Validation messages come from the domain layer. */
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

    var hasEvening by remember { mutableStateOf(false) }
    var eveningStart by remember { mutableStateOf("13:00") }
    var eveningCount by remember { mutableStateOf("6") }
    var eveningLength by remember { mutableStateOf("45") }
    var eveningGap by remember { mutableStateOf("5") }

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
        ScreenHeader("إعداد المدرسة")

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

        Text("الوردية الصباحية", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = firstStart,
            onValueChange = { firstStart = it },
            label = { Text("بداية الدوام (HH:MM)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ShiftFields(
            count = periodCount, onCount = { periodCount = it },
            length = periodLength, onLength = { periodLength = it },
            gap = gap, onGap = { gap = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("يوجد وردية مسائية", style = MaterialTheme.typography.titleMedium)
            Switch(checked = hasEvening, onCheckedChange = { hasEvening = it })
        }

        if (hasEvening) {
            Text("الوردية المسائية", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = eveningStart,
                onValueChange = { eveningStart = it },
                label = { Text("بداية الدوام المسائي (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ShiftFields(
                count = eveningCount, onCount = { eveningCount = it },
                length = eveningLength, onLength = { eveningLength = it },
                gap = eveningGap, onGap = { eveningGap = it },
            )
        }

        errors.forEach { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !saving,
            onClick = {
                val shifts = mutableListOf<ShiftSetup>()
                val morningStart = parseClockMinutes(firstStart)
                if (morningStart == null) {
                    errors = listOf("بداية الدوام الصباحي يجب أن تكون بصيغة HH:MM")
                    return@Button
                }
                shifts += ShiftSetup(
                    id = "morning",
                    kind = ShiftKind.MORNING,
                    firstStartMin = morningStart,
                    periodCount = periodCount.toIntOrNull() ?: -1,
                    periodLengthMin = periodLength.toIntOrNull() ?: -1,
                    gapMin = gap.toIntOrNull() ?: -1,
                )
                if (hasEvening) {
                    val eveningStartMin = parseClockMinutes(eveningStart)
                    if (eveningStartMin == null) {
                        errors = listOf("بداية الدوام المسائي يجب أن تكون بصيغة HH:MM")
                        return@Button
                    }
                    shifts += ShiftSetup(
                        id = "evening",
                        kind = ShiftKind.EVENING,
                        firstStartMin = eveningStartMin,
                        periodCount = eveningCount.toIntOrNull() ?: -1,
                        periodLengthMin = eveningLength.toIntOrNull() ?: -1,
                        gapMin = eveningGap.toIntOrNull() ?: -1,
                    )
                }
                val input = SchoolSetupInput(
                    schoolName = schoolName,
                    academicYearLabel = yearLabel,
                    principalName = principal.ifBlank { null },
                    activeDays = activeDays.toIntOrNull() ?: -1,
                    shifts = shifts,
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

@Composable
private fun ShiftFields(
    count: String,
    onCount: (String) -> Unit,
    length: String,
    onLength: (String) -> Unit,
    gap: String,
    onGap: (String) -> Unit,
) {
    OutlinedTextField(
        value = count,
        onValueChange = onCount,
        label = { Text("عدد الحصص") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = length,
        onValueChange = onLength,
        label = { Text("طول الحصة بالدقائق") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = gap,
        onValueChange = onGap,
        label = { Text("الفاصل بين الحصص بالدقائق") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
