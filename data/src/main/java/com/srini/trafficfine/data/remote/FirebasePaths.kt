package com.srini.trafficfine.data.remote

/** Single source of truth for the Realtime Database / Storage layout (see firebase/database.rules.json). */
object FirebasePaths {
    const val OFFICERS = "officers"
    const val VIOLATIONS = "violations"
    const val OFFICER_LOGS = "officerLogs"
    const val EVIDENCE = "evidence"

    const val ROLE_CLAIM = "role"
}
