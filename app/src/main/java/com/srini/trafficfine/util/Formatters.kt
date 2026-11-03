package com.srini.trafficfine.util

import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(indianLocale).apply {
        maximumFractionDigits = 0
    }
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy · hh:mm a", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val plateValidator = VehicleNumberValidator()

    fun rupees(amount: Long): String = currency.format(amount)

    fun dateTime(millis: Long): String = dateTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun time(millis: Long): String = time.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun plate(normalized: String): String =
        (plateValidator.validate(normalized) as? VehicleNumberValidator.Result.Valid)?.formatted ?: normalized
}
