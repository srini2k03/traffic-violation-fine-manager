package com.srini.trafficfine.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
sealed interface Route {
    @Serializable data object Dashboard : Route
    @Serializable data object IssueViolation : Route
    @Serializable data object History : Route
    @Serializable data class ViolationDetail(val id: String) : Route
    @Serializable data object ActivityLog : Route
}
