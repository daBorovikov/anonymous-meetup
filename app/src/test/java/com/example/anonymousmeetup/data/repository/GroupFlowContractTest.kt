package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupCompatibilityStatus
import com.example.anonymousmeetup.data.remote.GroupDocumentContract
import com.example.anonymousmeetup.data.security.AnonymousMessageCodec
import com.example.anonymousmeetup.data.security.EncryptionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupFlowContractTest {
    private val codec = AnonymousMessageCodec(EncryptionService())
    private val payloadFactory = GroupPayloadFactory(codec)
    private val compatibilityResolver = GroupCompatibilityResolver(codec)

    @Test
    fun createNewGroupBuildsValidPayloadAndJoinTokenRoundTrip() {
        val bundle = payloadFactory.createNewGroup(
            name = "Test Group",
            description = "A test group",
            category = "Debug",
            isPrivate = false,
            createdAt = 1234L,
            groupId = "group-1"
        )

        val payload = GroupDocumentContract.toFirestorePayload(bundle.group)
        assertTrue(GroupDocumentContract.validatePayload(payload).isEmpty())

        val parsed = codec.parseGroupJoinToken(bundle.group.joinToken.orEmpty())
        assertNotNull(parsed)
        assertEquals("group-1", parsed?.groupLocalId)
        assertEquals(bundle.group.poolId, parsed?.poolId)
        assertEquals(bundle.groupKeyRecord.groupKey, parsed?.groupKey)
    }

    @Test
    fun legacyGroupWithoutJoinTokenRequiresMigration() {
        val report = compatibilityResolver.inspect(
            Group(
                id = "legacy-1",
                name = "Legacy",
                description = "Old format",
                category = "Old",
                isPrivate = false,
                groupHash = "",
                poolId = "",
                joinToken = null,
                createdAt = 1L
            )
        )

        assertEquals(GroupCompatibilityStatus.LEGACY_REQUIRES_MIGRATION, report.status)
        assertFalse(report.isJoinable)
    }

    @Test
    fun groupWithoutJoinTokenButWithPoolIdIsReportedSafely() {
        val report = compatibilityResolver.inspect(
            Group(
                id = "group-2",
                name = "Broken",
                description = "No token",
                category = "Debug",
                isPrivate = false,
                groupHash = "hash",
                poolId = "group_pool_01",
                joinToken = null,
                createdAt = 1L
            )
        )

        assertEquals(GroupCompatibilityStatus.MISSING_JOIN_TOKEN, report.status)
        assertFalse(report.isJoinable)
    }

    @Test
    fun brokenJoinTokenIsRejected() {
        val report = compatibilityResolver.inspect(
            Group(
                id = "group-3",
                name = "Broken token",
                description = "Bad token",
                category = "Debug",
                isPrivate = false,
                groupHash = "hash",
                poolId = "group_pool_01",
                joinToken = "not-a-token",
                createdAt = 1L
            )
        )

        assertEquals(GroupCompatibilityStatus.BROKEN_JOIN_TOKEN, report.status)
        assertFalse(report.isJoinable)
    }

    @Test
    fun validJoinTokenCanRecoverBlankPoolId() {
        val bundle = payloadFactory.createNewGroup(
            name = "Recovered",
            description = "Pool from token",
            category = "Debug",
            isPrivate = false,
            createdAt = 55L,
            groupId = "group-4"
        )
        val report = compatibilityResolver.inspect(bundle.group.copy(poolId = ""))

        assertEquals(GroupCompatibilityStatus.JOINABLE, report.status)
        assertEquals(bundle.group.poolId, report.resolvedGroup?.poolId)
    }

    @Test
    fun malformedPayloadIsRejectedByContractValidator() {
        val payload = mapOf(
            "id" to "group-5",
            "name" to "Broken",
            "description" to 42,
            "category" to "Debug",
            "isPrivate" to false,
            "groupHash" to "hash",
            "poolId" to "group_pool_09",
            "createdAt" to "now"
        )

        val errors = GroupDocumentContract.validatePayload(payload)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("description must be string") })
        assertTrue(errors.any { it.contains("poolId is not in allowed anonymous pools") })
    }

    @Test
    fun legacyPrivateFieldIsMappedSafely() {
        val group = GroupDocumentContract.fromMap(
            documentId = "legacy-2",
            raw = mapOf(
                "name" to "Legacy Private",
                "description" to "Old schema",
                "category" to "Archive",
                "private" to true,
                "memberIds" to listOf("u1", "u2"),
                "createdAt" to 99L
            )
        )

        assertNotNull(group)
        assertTrue(group?.isPrivate == true)
        assertEquals("legacy-2", group?.id)
    }
}
