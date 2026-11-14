package com.school.timetable.app

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.school.timetable.data.DataRepository
import com.school.timetable.data.YearData
import com.school.timetable.domain.ShiftKind
import kotlinx.coroutines.launch

private enum class DataTab(val title: String) {
    GRADES("الصفوف"),
    SECTIONS("الشعب"),
    SUBJECTS("المواد"),
    TEACHERS("المعلمون"),
}

private fun shiftLabel(kind: ShiftKind): String = if (kind == ShiftKind.MORNING) "صباحي" else "مسائي"

/** Manage grades, sections, subjects and teachers for the active year. */
@Composable
fun DataScreen(repository: DataRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<YearData?>(null) }
    var reloadCount by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(DataTab.GRADES) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadCount) {
        data = repository.load()
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                message = null
                reloadCount++
            } catch (e: Exception) {
                message = "تعذر تنفيذ العملية. ربما الاسم مكرر، أو العنصر مستخدم في مكان آخر."
            }
        }
    }

    val current = data
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("البيانات", style = MaterialTheme.typography.headlineSmall)
        }

        if (current == null) {
            Text("لا توجد سنة دراسية نشطة. احفظ الإعداد أولاً.")
        } else {
            TabRow(selectedTabIndex = tab.ordinal) {
                DataTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.title) })
                }
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    DataTab.GRADES -> GradesTab(
                        data = current,
                        onAdd = { name -> perform { repository.addGrade(current.yearId, name, current.grades.size + 1) } },
                        onDelete = { id -> perform { repository.deleteGrade(id) } },
                    )
                    DataTab.SECTIONS -> SectionsTab(
                        data = current,
                        onAdd = { gradeId, shiftId, name -> perform { repository.addSection(current.yearId, gradeId, shiftId, name) } },
                        onDelete = { id -> perform { repository.deleteSection(id) } },
                    )
                    DataTab.SUBJECTS -> SubjectsTab(
                        data = current,
                        onAdd = { name -> perform { repository.addSubject(current.yearId, name) } },
                        onDelete = { id -> perform { repository.deleteSubject(id) } },
                    )
                    DataTab.TEACHERS -> TeachersTab(
                        data = current,
                        onAdd = { name, norm, both, subjects ->
                            perform { repository.addTeacher(current.yearId, name, norm, both, subjects) }
                        },
                        onDelete = { id -> perform { repository.deleteTeacher(id) } },
                        onError = { text -> message = text },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(text: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, modifier = Modifier.weight(1f))
        TextButton(onClick = onDelete) { Text("حذف") }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun GradesTab(data: YearData, onAdd: (String) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم الصف، مثال: الأول") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            if (name.isNotBlank()) {
                onAdd(name.trim())
                name = ""
            }
        }) { Text("إضافة") }
    }
    data.grades.forEach { grade -> ItemRow(grade.name) { onDelete(grade.id) } }
}

@Composable
private fun SectionsTab(data: YearData, onAdd: (Long, Long, String) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pickedGrade by remember { mutableStateOf<Long?>(null) }
    var pickedShift by remember { mutableStateOf<Long?>(null) }
    val gradeId = pickedGrade ?: data.grades.firstOrNull()?.id
    val shiftId = pickedShift ?: data.shifts.firstOrNull()?.id
    val gradeNames = data.grades.associate { it.id to it.name }
    val shiftNames = data.shifts.associate { it.id to shiftLabel(it.kind) }

    if (data.grades.isEmpty()) {
        Text("أضف صفاً أولاً من تبويب الصفوف")
    } else {
        ChoiceRow(data.grades.map { it.id to it.name }, gradeId) { pickedGrade = it }
    }
    if (data.shifts.isNotEmpty()) {
        ChoiceRow(data.shifts.map { it.id to shiftLabel(it.kind) }, shiftId) { pickedShift = it }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم الشعبة، مثال: أ") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            if (gradeId != null && shiftId != null && name.isNotBlank()) {
                onAdd(gradeId, shiftId, name.trim())
                name = ""
            }
        }) { Text("إضافة") }
    }
    data.sections.forEach { section ->
        val label = "${gradeNames[section.gradeId] ?: ""} - ${section.name} (${shiftNames[section.shiftId] ?: ""})"
        ItemRow(label) { onDelete(section.id) }
    }
}

@Composable
private fun SubjectsTab(data: YearData, onAdd: (String) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم المادة، مثال: الرياضيات") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            if (name.isNotBlank()) {
                onAdd(name.trim())
                name = ""
            }
        }) { Text("إضافة") }
    }
    data.subjects.forEach { subject -> ItemRow(subject.name) { onDelete(subject.id) } }
}

@Composable
private fun TeachersTab(
    data: YearData,
    onAdd: (String, Int, Boolean, Set<Long>) -> Unit,
    onDelete: (Long) -> Unit,
    onError: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var norm by remember { mutableStateOf("24") }
    var bothShifts by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val subjectNames = data.subjects.associate { it.id to it.name }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("اسم المعلم") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = norm,
        onValueChange = { norm = it },
        label = { Text("النصاب الأسبوعي بالحصص (1 إلى 40)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("يعمل في الورديتين")
        Switch(checked = bothShifts, onCheckedChange = { bothShifts = it })
    }

    Text("المواد التي يدرّسها:")
    if (data.subjects.isEmpty()) {
        Text("أضف مواداً أولاً من تبويب المواد")
    }
    data.subjects.forEach { subject ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = subject.id in picked,
                onCheckedChange = { checked ->
                    picked = if (checked) picked + subject.id else picked - subject.id
                },
            )
            Text(subject.name)
        }
    }

    Button(
        onClick = {
            val weekly = norm.toIntOrNull()
            when {
                name.isBlank() -> onError("اسم المعلم مطلوب")
                weekly == null || weekly !in 1..40 -> onError("النصاب يجب أن يكون رقماً بين 1 و40")
                picked.isEmpty() -> onError("اختر مادة واحدة على الأقل")
                else -> {
                    onAdd(name.trim(), weekly, bothShifts, picked)
                    name = ""
                    picked = emptySet()
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("إضافة المعلم")
    }

    data.teachers.forEach { teacher ->
        val subjects = data.teacherSubjects
            .filter { it.teacherId == teacher.id }
            .mapNotNull { subjectNames[it.subjectId] }
            .joinToString("، ")
        ItemRow("${teacher.name} - ${teacher.weeklyNorm} حصة - $subjects") { onDelete(teacher.id) }
    }
}
