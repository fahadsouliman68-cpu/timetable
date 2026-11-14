package com.school.timetable.data.backup

import android.database.Cursor
import com.school.timetable.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Exports every user table to one JSON file with a SHA-256 checksum,
 * and restores it inside a single transaction: on any failure nothing changes.
 */
class BackupService(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase

    /** Blocking database work: call from a background dispatcher. */
    fun export(): ByteArray {
        val payload = JSONObject()
        for (table in userTables()) {
            val rows = JSONArray()
            db.query("SELECT * FROM `$table`").use { c ->
                val columns = c.columnNames
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in columns.indices) row.put(columns[i], cellValue(c, i))
                    rows.put(row)
                }
            }
            payload.put(table, rows)
        }
        val payloadText = payload.toString()
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("checksum", sha256(payloadText))
            .put("payload", payloadText)
        return envelope.toString().toByteArray(Charsets.UTF_8)
    }

    /** Blocking database work: call from a background dispatcher. Throws with an Arabic message on bad input. */
    fun restore(bytes: ByteArray) {
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.optString("format") == FORMAT) { "هذا الملف ليس نسخة احتياطية صالحة" }
        require(envelope.optInt("schemaVersion") == SCHEMA_VERSION) { "إصدار النسخة غير متوافق مع هذا التطبيق" }
        val payloadText = envelope.getString("payload")
        require(sha256(payloadText) == envelope.getString("checksum")) {
            "الملف تالف أو تم تعديله، لم تُستعد أي بيانات"
        }
        val payload = JSONObject(payloadText)

        db.execSQL("PRAGMA foreign_keys = OFF")
        try {
            db.beginTransaction()
            try {
                for (table in userTables()) db.execSQL("DELETE FROM `$table`")
                for (table in payload.keys().asSequence().toList()) {
                    val rows = payload.getJSONArray(table)
                    for (r in 0 until rows.length()) {
                        val row = rows.getJSONObject(r)
                        val columns = row.keys().asSequence().toList()
                        val sql = "INSERT INTO `$table` (" +
                            columns.joinToString(",") { "`$it`" } +
                            ") VALUES (" + columns.joinToString(",") { "?" } + ")"
                        val args = columns.map { key ->
                            when {
                                row.isNull(key) -> null
                                else -> when (val v = row.get(key)) {
                                    is Int -> v.toLong()
                                    else -> v
                                }
                            }
                        }.toTypedArray()
                        db.execSQL(sql, args)
                    }
                }
                db.query("PRAGMA foreign_key_check").use { c ->
                    require(c.count == 0) { "علاقات البيانات غير سليمة في النسخة، لم تُستعد أي بيانات" }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private fun userTables(): List<String> {
        val names = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) names += c.getString(0)
        }
        return names.filter { !it.startsWith("sqlite_") && it != "android_metadata" && it != "room_master_table" }
    }

    private fun cellValue(c: Cursor, index: Int): Any = when (c.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(index)
        Cursor.FIELD_TYPE_STRING -> c.getString(index)
        else -> throw IllegalStateException("نوع بيانات غير مدعوم في النسخة الاحتياطية")
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val FORMAT = "timetable-backup"
        const val SCHEMA_VERSION = 1
    }
}
