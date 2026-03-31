package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupCompatibilityReport
import com.example.anonymousmeetup.data.model.GroupCompatibilityStatus
import com.example.anonymousmeetup.data.security.AnonymousMessageCodec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupCompatibilityResolver @Inject constructor(
    private val anonymousMessageCodec: AnonymousMessageCodec
) {
    fun inspect(group: Group): GroupCompatibilityReport {
        if (group.id.isBlank() || group.name.isBlank()) {
            return GroupCompatibilityReport(
                status = GroupCompatibilityStatus.MALFORMED,
                userMessage = "Документ группы повреждён",
                details = "group.id or group.name is blank"
            )
        }

        val token = group.joinToken
        if (token.isNullOrBlank()) {
            return if (group.poolId.isBlank()) {
                GroupCompatibilityReport(
                    status = GroupCompatibilityStatus.LEGACY_REQUIRES_MIGRATION,
                    userMessage = "Группа старого формата, нужен перенос",
                    details = "joinToken and poolId are missing"
                )
            } else {
                GroupCompatibilityReport(
                    status = GroupCompatibilityStatus.MISSING_JOIN_TOKEN,
                    userMessage = "У группы отсутствует ключ подключения",
                    details = "poolId=${group.poolId}"
                )
            }
        }

        val keyRecord = anonymousMessageCodec.parseGroupJoinToken(token)
            ?: return GroupCompatibilityReport(
                status = GroupCompatibilityStatus.BROKEN_JOIN_TOKEN,
                userMessage = "Токен группы повреждён",
                details = "joinToken parsing failed"
            )

        if (keyRecord.groupLocalId != group.id) {
            return GroupCompatibilityReport(
                status = GroupCompatibilityStatus.BROKEN_JOIN_TOKEN,
                userMessage = "Токен группы повреждён",
                details = "token groupLocalId=${keyRecord.groupLocalId}, group.id=${group.id}"
            )
        }

        if (group.poolId.isNotBlank() && group.poolId != keyRecord.poolId) {
            return GroupCompatibilityReport(
                status = GroupCompatibilityStatus.BROKEN_JOIN_TOKEN,
                userMessage = "Токен группы повреждён",
                details = "token poolId=${keyRecord.poolId}, group.poolId=${group.poolId}"
            )
        }

        val resolvedGroup = group.copy(
            groupHash = group.groupHash.ifBlank { anonymousMessageCodec.shortHash(group.id) },
            poolId = group.poolId.ifBlank { keyRecord.poolId },
            joinToken = token
        )
        return GroupCompatibilityReport(
            status = GroupCompatibilityStatus.JOINABLE,
            userMessage = "Группа готова к подключению",
            resolvedGroup = resolvedGroup
        )
    }
}
