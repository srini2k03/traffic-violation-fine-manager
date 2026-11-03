package com.srini.trafficfine.domain.fine

import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationStatus

object PriorOffenceCounter {
    /**
     * Counts earlier offences per violation code. Rows may come from both the local cache and
     * the server, so they are de-duplicated by id; cancelled challans and rows older than
     * [sinceMillis] do not count.
     */
    fun count(rows: List<Violation>, sinceMillis: Long): Map<String, Int> =
        rows.asSequence()
            .distinctBy { it.id }
            .filter { it.issuedAt >= sinceMillis && it.status != ViolationStatus.CANCELLED }
            .flatMap { it.typeCodes.asSequence() }
            .groupingBy { it }
            .eachCount()
}
