package com.srini.trafficfine.domain.demo

import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.model.Violation

/**
 * Whether this build offers the offline demo (sample data, no Firebase). Provided by the app
 * module from `BuildConfig.DEMO_MODE`.
 */
data class DemoModeConfig(val enabled: Boolean)

/** Sample records written to the local database when a demo session starts. */
data class DemoSeed(
    val violations: List<Violation>,
    val logs: List<OfficerLog>,
)

/**
 * Fixed identities used by demo sessions. Every demo uid starts with [UID_PREFIX] so demo
 * records can be told apart from real ones (and are never pushed to Firebase).
 */
object DemoData {
    const val UID_PREFIX = "demo-"

    val ADMIN = Officer(
        uid = "demo-admin",
        name = "SI Kavitha Menon",
        email = "admin@demo.echallan.example",
        badgeNumber = "TNP-0001",
        station = "Adyar Traffic",
        role = Role.ADMIN,
    )

    val OFFICER = Officer(
        uid = "demo-officer",
        name = "Insp. Priya Raman",
        email = "priya@demo.echallan.example",
        badgeNumber = "TNP-4821",
        station = "Adyar Traffic",
        role = Role.OFFICER,
    )

    val OFFICER_2 = Officer(
        uid = "demo-officer-2",
        name = "SI Rahul Verma",
        email = "rahul@demo.echallan.example",
        badgeNumber = "TNP-3307",
        station = "Adyar Traffic",
        role = Role.OFFICER,
    )

    val OFFICER_3 = Officer(
        uid = "demo-officer-3",
        name = "HC Meena Devi",
        email = "meena@demo.echallan.example",
        badgeNumber = "TNP-5590",
        station = "Adyar Traffic",
        role = Role.OFFICER,
        active = false,
    )

    /** The station roster shown in the admin console during a demo. */
    val officers: List<Officer> = listOf(ADMIN, OFFICER, OFFICER_2, OFFICER_3)

    fun isDemoUid(uid: String?): Boolean = uid != null && uid.startsWith(UID_PREFIX)

    fun officerFor(role: Role): Officer = when (role) {
        Role.ADMIN -> ADMIN
        Role.OFFICER -> OFFICER
    }

    fun officerByUid(uid: String?): Officer? = officers.firstOrNull { it.uid == uid }
}
