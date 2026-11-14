package com.school.timetable.data.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.school.timetable.data.LessonView
import com.school.timetable.data.TimetableView
import com.school.timetable.domain.setup.DayNames
import java.io.OutputStream

/** One landscape A4 page per section. Uses the platform font, which includes Arabic glyphs. */
object TimetablePdf {

    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 30f
    private const val HEADER_HEIGHT = 28f
    private const val ROW_HEIGHT = 58f

    fun write(view: TimetableView, out: OutputStream) {
        val doc = PdfDocument()
        if (view.sections.isEmpty()) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawText(page.canvas, "لا توجد شعب في الجدول", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, 16f, true)
            doc.finishPage(page)
        }
        view.sections.forEachIndexed { index, section ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
            drawSection(page.canvas, view, section.id, section.label, section.shiftId)
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
    }

    private fun drawSection(canvas: Canvas, view: TimetableView, sectionId: Long, label: String, shiftId: Long) {
        drawText(canvas, "جدول الشعبة: $label", PAGE_WIDTH - MARGIN, 30f, 16f, true, alignRight = true)
        drawText(canvas, "الإصدار ${view.versionNumber}", MARGIN, 30f, 11f, false, alignRight = false)

        val days = view.days
        val columns = days + 1
        val columnWidth = (PAGE_WIDTH - 2 * MARGIN) / columns
        val periods = view.periods.filter { it.shiftId == shiftId }.sortedBy { it.indexNo }
        val lessons = view.lessons.filter { it.sectionId == sectionId }

        // Column 0 is the period label on the right; days follow to the left, as in Arabic reading order.
        fun left(column: Int): Float = PAGE_WIDTH - MARGIN - (column + 1) * columnWidth
        val top = 50f

        drawCell(canvas, left(0), top, columnWidth, HEADER_HEIGHT, "الحصة", bold = true)
        for (day in 0 until days) {
            drawCell(canvas, left(day + 1), top, columnWidth, HEADER_HEIGHT, DayNames.AR.getOrElse(day) { "" }, bold = true)
        }
        periods.forEachIndexed { rowIndex, period ->
            val y = top + HEADER_HEIGHT + rowIndex * ROW_HEIGHT
            drawCell(canvas, left(0), y, columnWidth, ROW_HEIGHT, "${period.indexNo}", bold = true)
            for (day in 0 until days) {
                val lesson: LessonView? = lessons.find {
                    it.dayIndex == day && it.periodIndex == period.indexNo && it.shiftId == shiftId
                }
                val cellLines = if (lesson == null) listOf("-") else listOf(lesson.subjectName, lesson.teacherName)
                drawCell(canvas, left(day + 1), y, columnWidth, ROW_HEIGHT, cellLines, bold = false)
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        text: String,
        bold: Boolean,
    ) = drawCell(canvas, left, top, width, height, listOf(text), bold)

    private fun drawCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        lines: List<String>,
        bold: Boolean,
    ) {
        val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = 0xFF555555.toInt() }
        canvas.drawRect(left, top, left + width, top + height, border)
        val paint = textPaint(10f, bold)
        val lineHeight = 14f
        val startY = top + height / 2 - (lines.size - 1) * lineHeight / 2 + 4
        lines.forEachIndexed { i, line ->
            canvas.drawText(fit(line, paint, width - 8f), left + width / 2, startY + i * lineHeight, paint)
        }
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        bold: Boolean,
        alignRight: Boolean = false,
    ) {
        val paint = textPaint(size, bold).apply {
            textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.CENTER
        }
        canvas.drawText(text, x, y, paint)
    }

    private fun textPaint(size: Float, bold: Boolean): Paint = Paint().apply {
        textSize = size
        isAntiAlias = true
        isFakeBoldText = bold
        color = 0xFF000000.toInt()
        textAlign = Paint.Align.CENTER
    }

    /** Shortens text with an ellipsis so it fits the cell width. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
