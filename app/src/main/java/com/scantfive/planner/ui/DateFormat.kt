package com.scantfive.planner.ui

import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

fun formatDay(epochDay: Long): String {
    val millis = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}
