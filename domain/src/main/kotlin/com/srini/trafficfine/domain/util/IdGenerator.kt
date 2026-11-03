package com.srini.trafficfine.domain.util

import java.util.UUID

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val UUID_BASED = IdGenerator { UUID.randomUUID().toString() }
    }
}
