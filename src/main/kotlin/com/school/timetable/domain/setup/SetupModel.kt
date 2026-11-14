package com.school.timetable.domain.setup

import com.school.timetable.domain.Period
import com.school.timetable.domain.Shift
import com.school.timetable.domain.ShiftKind
import com.school.timetable.domain.TimeRange

object SetupLimits {
    const val MAX_PERIODS = 12
    const val MIN_PERIOD_MIN = 20
    const val MAX_PERIOD_MIN = 90
    const val MAX_GAP_MIN = 30
    const val MIN_BREAK_MIN = 5
    const val MAX_BREAK_MIN = 60
    const val MINUTES_PER_DAY = 1440
}

/** Day index 0 = Sunday ... 6 = Saturday. The school week is a contiguous run starting at Sunday. */
object DayNames {
    val AR: List<String> = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
}

data class BreakSetup(val afterPeriod: Int, val durationMin: Int)

data class ShiftSetup(
    val id: String,
    val kind: ShiftKind,
    val firstStartMin: Int,
    val periodCount: Int,
    val periodLengthMin: Int,
    val gapMin: Int = 0,
    val breaks: List<BreakSetup> = emptyList(),
)

data class SchoolSetupInput(
    val schoolName: String,
    val academicYearLabel: String,
    val principalName: String? = null,
    val activeDays: Int = 5,
    val shifts: List<ShiftSetup>,
)

data class SetupBuild(val shifts: List<Shift>, val activeDays: Int)

sealed interface SetupResult {
    data class Ok(val build: SetupBuild) : SetupResult
    data class Invalid(val errors: List<String>) : SetupResult
}

object SetupBuilder {

    /** Validates the wizard input and, only if valid, builds the shifts with their periods. */
    fun build(input: SchoolSetupInput): SetupResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return SetupResult.Invalid(errors)
        return SetupResult.Ok(SetupBuild(input.shifts.map(::buildShift), input.activeDays))
    }

    fun validate(input: SchoolSetupInput): List<String> {
        val errors = ArrayList<String>()
        if (input.schoolName.isBlank()) errors += "اسم المدرسة مطلوب"
        if (input.academicYearLabel.isBlank()) errors += "اسم العام الدراسي مطلوب"
        if (input.activeDays !in 1..DayNames.AR.size) errors += "عدد أيام الدوام يجب أن يكون بين 1 و${DayNames.AR.size}"
        if (input.shifts.isEmpty()) errors += "يجب إدخال وردية واحدة على الأقل"
        if (input.shifts.map { it.kind }.toSet().size != input.shifts.size) errors += "لا يمكن تكرار نوع الوردية نفسه"
        if (input.shifts.map { it.id }.toSet().size != input.shifts.size) errors += "معرّف الوردية مكرر"

        for (shift in input.shifts) {
            val label = if (shift.kind == ShiftKind.MORNING) "الوردية الصباحية" else "الوردية المسائية"
            if (shift.firstStartMin !in 0 until SetupLimits.MINUTES_PER_DAY) errors += "$label: وقت البداية غير صالح"
            if (shift.periodCount !in 1..SetupLimits.MAX_PERIODS) errors += "$label: عدد الحصص بين 1 و${SetupLimits.MAX_PERIODS}"
            if (shift.periodLengthMin !in SetupLimits.MIN_PERIOD_MIN..SetupLimits.MAX_PERIOD_MIN) {
                errors += "$label: طول الحصة بين ${SetupLimits.MIN_PERIOD_MIN} و${SetupLimits.MAX_PERIOD_MIN} دقيقة"
            }
            if (shift.gapMin !in 0..SetupLimits.MAX_GAP_MIN) errors += "$label: الفاصل بين الحصص بين 0 و${SetupLimits.MAX_GAP_MIN} دقيقة"
            for (b in shift.breaks) {
                if (b.afterPeriod !in 1 until shift.periodCount) errors += "$label: الاستراحة يجب أن تكون بعد حصة من 1 إلى ${shift.periodCount - 1}"
                if (b.durationMin !in SetupLimits.MIN_BREAK_MIN..SetupLimits.MAX_BREAK_MIN) {
                    errors += "$label: مدة الاستراحة بين ${SetupLimits.MIN_BREAK_MIN} و${SetupLimits.MAX_BREAK_MIN} دقيقة"
                }
            }
            if (shift.periodCount in 1..SetupLimits.MAX_PERIODS && shift.periodLengthMin in SetupLimits.MIN_PERIOD_MIN..SetupLimits.MAX_PERIOD_MIN) {
                val lastEnd = buildShift(shift).periods.last().time.endMin
                if (lastEnd > SetupLimits.MINUTES_PER_DAY) errors += "$label: الحصص تتجاوز منتصف الليل"
            }
        }
        return errors
    }

    private fun buildShift(s: ShiftSetup): Shift {
        val periods = ArrayList<Period>()
        var cursor = s.firstStartMin
        for (i in 1..s.periodCount) {
            val start = cursor
            val end = start + s.periodLengthMin
            periods += Period(i, TimeRange(start, end))
            val breakMinutes = s.breaks.filter { it.afterPeriod == i }.sumOf { it.durationMin }
            cursor = end + s.gapMin + breakMinutes
        }
        return Shift(s.id, s.kind, periods)
    }
}
