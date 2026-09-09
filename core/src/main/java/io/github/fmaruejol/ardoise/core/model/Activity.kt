package io.github.fmaruejol.ardoise.core.model

import java.time.Instant

enum class ActivityType {
    UPDATE_GROUP,
    CREATE_EXPENSE,
    UPDATE_EXPENSE,
    DELETE_EXPENSE,
    UNKNOWN,
    ;

    companion object {
        /** Unknown types degrade to [UNKNOWN]: the activity feed is display only. */
        fun fromWire(value: String): ActivityType =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

data class Activity(
    val id: String,
    val groupId: String,
    val time: Instant,
    val activityType: ActivityType,
    val participantId: String?,
    val expenseId: String?,
    /** Free-form payload; for expense activities this is the expense title. */
    val data: String?,
)

data class ActivityPage(
    val activities: List<Activity>,
    /** Whether the server has more beyond this page. */
    val hasMore: Boolean,
)
