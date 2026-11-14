package com.school.timetable.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.school.timetable.data.TimetableRepository
import com.school.timetable.data.backup.BackupService
import com.school.timetable.data.pdf.TimetablePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backup, restore and PDF export. Files are chosen by the user through the system file picker. */
@Composable
fun BackupScreen(backup: BackupService, timetable: TimetableRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun fail(e: Exception) {
        message = e.message ?: "حدث خطأ غير معروف"
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val bytes = withContext(Dispatchers.IO) { backup.export() }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("تعذر فتح الملف للكتابة")
                    }
                    message = "تم تصدير النسخة الاحتياطية بنجاح"
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    busy = false
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestore = false
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("تعذر فتح الملف للقراءة")
                        backup.restore(bytes)
                    }
                    message = "تمت الاستعادة. أغلق التطبيق وافتحه من جديد لعرض البيانات المستعادة."
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    busy = false
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val view = timetable.latest()
                    if (view == null) {
                        message = "لا يوجد جدول لتصديره. ولّد جدولاً أولاً."
                    } else {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { TimetablePdf.write(view, it) }
                                ?: error("تعذر فتح الملف للكتابة")
                        }
                        message = "تم تصدير الجدول PDF"
                    }
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("النسخ الاحتياطي والتقارير", onBack)

        Button(enabled = !busy, onClick = { exportLauncher.launch("timetable-backup.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("تصدير نسخة احتياطية")
        }

        Button(enabled = !busy, onClick = { pdfLauncher.launch("timetable.pdf") }, modifier = Modifier.fillMaxWidth()) {
            Text("تصدير الجدول الحالي PDF")
        }

        Text("الاستعادة تستبدل كل البيانات الحالية بالبيانات الموجودة في الملف.")
        if (!confirmRestore) {
            Button(enabled = !busy, onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                Text("استعادة من نسخة احتياطية")
            }
        } else {
            Button(
                enabled = !busy,
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("تأكيد: استبدال كل البيانات واختيار الملف")
            }
            TextButton(onClick = { confirmRestore = false }) { Text("إلغاء") }
        }

        if (busy) Text("جارٍ التنفيذ...")
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
