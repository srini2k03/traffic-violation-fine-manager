package com.srini.trafficfine.domain.model

/**
 * A chargeable offence.
 *
 * @property code stable identifier stored in the database.
 * @property section the section of the Motor Vehicles Act, 1988 (as amended in 2019).
 * @property baseFine fine for a first offence, in whole rupees.
 */
data class ViolationType(
    val code: String,
    val title: String,
    val section: String,
    val baseFine: Long,
    val category: Category,
) {
    enum class Category { SAFETY, DOCUMENTS, SPEED, PARKING, VEHICLE, BEHAVIOUR }
}
