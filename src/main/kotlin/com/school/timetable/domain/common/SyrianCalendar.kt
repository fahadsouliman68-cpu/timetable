package com.school.timetable.domain.common

/** Gregorian calendar with the Syrian month names, as required by the design. Numbers stay Latin (1 2 3). */
object SyrianCalendar {
    val MONTHS: List<String> = listOf(
        "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
        "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول",
    )

    /** month is 1..12. Returns e.g. "14 تشرين الثاني 2026". */
    fun format(year: Int, month: Int, day: Int): String {
        require(month in 1..12) { "الشهر يجب أن يكون بين 1 و12" }
        require(day in 1..31) { "اليوم يجب أن يكون بين 1 و31" }
        return "$day ${MONTHS[month - 1]} $year"
    }
}
