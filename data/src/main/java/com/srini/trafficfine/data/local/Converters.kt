package com.srini.trafficfine.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromPendingOp(op: PendingOp?): String? = op?.name

    @TypeConverter
    fun toPendingOp(value: String?): PendingOp? = value?.let { runCatching { PendingOp.valueOf(it) }.getOrNull() }
}
