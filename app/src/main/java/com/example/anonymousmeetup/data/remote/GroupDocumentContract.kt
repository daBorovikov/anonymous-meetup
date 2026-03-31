package com.example.anonymousmeetup.data.remote

import com.example.anonymousmeetup.data.model.Group
import com.google.firebase.Timestamp

object GroupDocumentContract {
    private val allowedKeys = setOf(
        "id",
        "name",
        "description",
        "category",
        "isPrivate",
        "groupHash",
        "poolId",
        "joinToken",
        "createdAt"
    )

    fun toFirestorePayload(group: Group): Map<String, Any?> {
        return linkedMapOf(
            "id" to group.id,
            "name" to group.name,
            "description" to group.description,
            "category" to group.category,
            "isPrivate" to group.isPrivate,
            "groupHash" to group.groupHash,
            "poolId" to group.poolId,
            "joinToken" to group.joinToken,
            "createdAt" to group.createdAt
        )
    }

    fun validatePayload(payload: Map<String, Any?>): List<String> {
        val errors = mutableListOf<String>()
        val keys = payload.keys
        val unexpectedKeys = keys - allowedKeys
        if (unexpectedKeys.isNotEmpty()) {
            errors += "unexpected keys: ${unexpectedKeys.sorted().joinToString()}"
        }
        if ((payload["id"] as? String).isNullOrBlank()) errors += "id must be non-empty string"
        if (payload["name"] !is String) errors += "name must be string"
        if (payload["description"] !is String) errors += "description must be string"
        if (payload["category"] !is String) errors += "category must be string"
        if (payload["isPrivate"] !is Boolean) errors += "isPrivate must be boolean"
        if (payload["groupHash"] !is String) errors += "groupHash must be string"

        val poolId = payload["poolId"] as? String
        if (poolId.isNullOrBlank()) {
            errors += "poolId must be non-empty string"
        } else if (!isValidPoolId(poolId)) {
            errors += "poolId is not in allowed anonymous pools"
        }

        val joinToken = payload["joinToken"]
        if (joinToken != null && joinToken !is String) {
            errors += "joinToken must be string or null"
        }
        if (payload["createdAt"] !is Number) errors += "createdAt must be numeric"
        return errors
    }

    fun isValidPayload(payload: Map<String, Any?>): Boolean = validatePayload(payload).isEmpty()

    fun fromMap(documentId: String, raw: Map<String, Any?>?): Group? {
        if (raw == null) return null
        val id = (raw["id"] as? String)?.ifBlank { documentId } ?: documentId
        val name = (raw["name"] as? String)?.trim().orEmpty()
        if (name.isBlank()) return null

        return Group(
            id = id,
            name = name,
            description = (raw["description"] as? String).orEmpty(),
            category = (raw["category"] as? String).orEmpty(),
            isPrivate = (raw["isPrivate"] as? Boolean)
                ?: (raw["private"] as? Boolean)
                ?: false,
            groupHash = (raw["groupHash"] as? String).orEmpty(),
            poolId = (raw["poolId"] as? String).orEmpty(),
            joinToken = (raw["joinToken"] as? String)?.ifBlank { null },
            createdAt = coerceLong(raw["createdAt"])
        )
    }

    fun findLegacyFields(raw: Map<String, Any?>?): Set<String> {
        if (raw == null) return emptySet()
        return raw.keys - allowedKeys
    }

    private fun isValidPoolId(poolId: String): Boolean {
        return poolId in setOf(
            "private_pool_01",
            "private_pool_02",
            "private_pool_03",
            "private_pool_04",
            "group_pool_01",
            "group_pool_02",
            "group_pool_03",
            "group_pool_04"
        )
    }

    private fun coerceLong(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            else -> 0L
        }
    }
}
