package com.example.anonymousmeetup.data.model

enum class GroupCompatibilityStatus {
    JOINABLE,
    LEGACY_REQUIRES_MIGRATION,
    MISSING_JOIN_TOKEN,
    BROKEN_JOIN_TOKEN,
    MALFORMED
}

data class GroupCompatibilityReport(
    val status: GroupCompatibilityStatus,
    val userMessage: String,
    val details: String? = null,
    val resolvedGroup: Group? = null
) {
    val isJoinable: Boolean get() = status == GroupCompatibilityStatus.JOINABLE && resolvedGroup != null
}

class GroupJoinException(
    val report: GroupCompatibilityReport
) : IllegalStateException(report.userMessage)
