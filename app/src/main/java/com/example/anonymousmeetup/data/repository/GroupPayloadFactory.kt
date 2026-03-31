package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupKeyRecord
import com.example.anonymousmeetup.data.security.AnonymousMessageCodec
import com.example.anonymousmeetup.data.security.AnonymousPools
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NewGroupBundle(
    val group: Group,
    val groupKeyRecord: GroupKeyRecord
)

@Singleton
class GroupPayloadFactory @Inject constructor(
    private val anonymousMessageCodec: AnonymousMessageCodec
) {
    fun createNewGroup(
        name: String,
        description: String,
        category: String,
        isPrivate: Boolean,
        createdAt: Long = System.currentTimeMillis(),
        groupId: String = UUID.randomUUID().toString()
    ): NewGroupBundle {
        val poolId = AnonymousPools.groupPoolFor(groupId)
        val groupKeyRecord = GroupKeyRecord(
            groupLocalId = groupId,
            localGroupName = name,
            poolId = poolId,
            groupKey = anonymousMessageCodec.generateGroupKey(),
            createdAt = createdAt,
            updatedAt = createdAt
        )
        return NewGroupBundle(
            group = Group(
                id = groupId,
                name = name,
                description = description,
                category = category,
                isPrivate = isPrivate,
                groupHash = anonymousMessageCodec.shortHash(groupId),
                poolId = poolId,
                joinToken = anonymousMessageCodec.buildGroupJoinToken(groupKeyRecord),
                createdAt = createdAt
            ),
            groupKeyRecord = groupKeyRecord
        )
    }

    fun createMigrationBundle(
        group: Group,
        createdAt: Long = group.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    ): NewGroupBundle {
        val poolId = group.poolId.ifBlank { AnonymousPools.groupPoolFor(group.id) }
        val groupKeyRecord = GroupKeyRecord(
            groupLocalId = group.id,
            localGroupName = group.name.ifBlank { "Группа" },
            poolId = poolId,
            groupKey = anonymousMessageCodec.generateGroupKey(),
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        return NewGroupBundle(
            group = group.copy(
                groupHash = group.groupHash.ifBlank { anonymousMessageCodec.shortHash(group.id) },
                poolId = poolId,
                joinToken = anonymousMessageCodec.buildGroupJoinToken(groupKeyRecord),
                createdAt = createdAt
            ),
            groupKeyRecord = groupKeyRecord
        )
    }
}
