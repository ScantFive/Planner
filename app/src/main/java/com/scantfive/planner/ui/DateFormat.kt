package com.scantfive.planner.ui

import java.text.DateFormat
import java.util.Date

fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
