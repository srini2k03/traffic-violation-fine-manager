package com.srini.trafficfine.domain.model

/**
 * Built-in offence catalogue. Amounts are indicative values based on the
 * Motor Vehicles (Amendment) Act, 2019; states may notify different
 * compounding amounts, so the catalogue is deliberately isolated here.
 */
object ViolationCatalogue {

    val all: List<ViolationType> = listOf(
        ViolationType("NO_HELMET", "Riding without helmet", "194D", 1_000, ViolationType.Category.SAFETY),
        ViolationType("NO_SEATBELT", "Driving without seat belt", "194B", 1_000, ViolationType.Category.SAFETY),
        ViolationType("TRIPLE_RIDING", "Triple riding on two-wheeler", "194C", 1_000, ViolationType.Category.SAFETY),
        ViolationType("OVER_SPEEDING", "Over-speeding (LMV)", "183", 2_000, ViolationType.Category.SPEED),
        ViolationType("RED_LIGHT", "Jumping red light / dangerous driving", "184", 5_000, ViolationType.Category.BEHAVIOUR),
        ViolationType("MOBILE_PHONE", "Using mobile phone while driving", "184", 5_000, ViolationType.Category.BEHAVIOUR),
        ViolationType("DRUNK_DRIVING", "Driving under the influence", "185", 10_000, ViolationType.Category.BEHAVIOUR),
        ViolationType("NO_LICENCE", "Driving without a valid licence", "181", 5_000, ViolationType.Category.DOCUMENTS),
        ViolationType("NO_INSURANCE", "Driving an uninsured vehicle", "196", 2_000, ViolationType.Category.DOCUMENTS),
        ViolationType("NO_PUC", "No valid pollution certificate", "190(2)", 10_000, ViolationType.Category.DOCUMENTS),
        ViolationType("WRONG_PARKING", "Parking in a no-parking zone", "177", 500, ViolationType.Category.PARKING),
        ViolationType("OVERLOADING", "Overloading of goods vehicle", "194(1)", 20_000, ViolationType.Category.VEHICLE),
        ViolationType("NO_REGISTRATION", "Driving an unregistered vehicle", "192", 5_000, ViolationType.Category.DOCUMENTS),
    )

    private val byCode: Map<String, ViolationType> = all.associateBy { it.code }

    fun find(code: String): ViolationType? = byCode[code]

    fun require(code: String): ViolationType =
        byCode[code] ?: throw IllegalArgumentException("Unknown violation code: $code")
}
