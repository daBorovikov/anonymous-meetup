package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.debug.DebugTraceLogger
import com.example.anonymousmeetup.data.local.ChatSessionStore
import com.example.anonymousmeetup.data.local.ConversationSecretStore
import com.example.anonymousmeetup.data.local.EncounterStore
import com.example.anonymousmeetup.data.local.GroupKeyStore
import com.example.anonymousmeetup.data.local.JoinedGroupsStore
import com.example.anonymousmeetup.data.local.LocalConversationStore
import com.example.anonymousmeetup.data.local.LocalMessageStore
import com.example.anonymousmeetup.data.local.ProcessedEnvelopeStore
import com.example.anonymousmeetup.data.model.DebugTraceEntry
import com.example.anonymousmeetup.data.model.Group
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugToolsRepository @Inject constructor(
    private val localConversationStore: LocalConversationStore,
    private val localMessageStore: LocalMessageStore,
    private val processedEnvelopeStore: ProcessedEnvelopeStore,
    private val joinedGroupsStore: JoinedGroupsStore,
    private val groupKeyStore: GroupKeyStore,
    private val conversationSecretStore: ConversationSecretStore,
    private val chatSessionStore: ChatSessionStore,
    private val encounterStore: EncounterStore,
    private val groupRepository: GroupRepository,
    private val groupChatRepository: GroupChatRepository,
    private val debugTraceLogger: DebugTraceLogger
) {
    fun traces(): Flow<List<DebugTraceEntry>> = debugTraceLogger.traces()

    suspend fun resetLocalAnonymousState() {
        debugTraceLogger.debug(TAG, "Reset local anonymous state requested")
        localConversationStore.clear()
        localMessageStore.clearAllHistory()
        processedEnvelopeStore.clearProcessed()
        joinedGroupsStore.clearAll()
        groupKeyStore.clearAll()
        conversationSecretStore.clearAll()
        chatSessionStore.clearAll()
        encounterStore.clearAll()
        debugTraceLogger.debug(TAG, "Reset local anonymous state completed")
    }

    suspend fun clearLegacyCaches() {
        debugTraceLogger.debug(TAG, "Clear legacy caches requested")
        chatSessionStore.clearAll()
        encounterStore.clearAll()
        debugTraceLogger.debug(TAG, "Clear legacy caches completed")
    }

    suspend fun resyncGroupsFromServer(): Int {
        val localGroups = joinedGroupsStore.getJoinedGroups()
        var updated = 0
        localGroups.forEach { localGroup ->
            val remote = runCatching { groupRepository.getGroup(localGroup.id) }.getOrNull() ?: return@forEach
            joinedGroupsStore.join(remote)
            updated += 1
        }
        debugTraceLogger.debug(TAG, "resyncGroupsFromServer updated=$updated local=${localGroups.size}")
        return updated
    }

    suspend fun runGroupVerificationScenario(): String {
        val scenarioId = System.currentTimeMillis().toString()
        val groupName = "Debug Group $scenarioId"
        val groupDescription = "Integration-like verification $scenarioId"
        val testMessage = "debug verification message $scenarioId"
        debugTraceLogger.debug(TAG, "Group verification scenario started id=$scenarioId")

        val groupId = groupRepository.createGroup(
            name = groupName,
            description = groupDescription,
            category = "Debug",
            isPrivate = false
        )
        val createdGroup = waitForSearchHit(groupName, groupId)
        debugTraceLogger.debug(TAG, "Group verification search hit groupId=${createdGroup.id}")

        groupRepository.leaveGroupLocally(groupId)
        groupRepository.joinGroupLocally(createdGroup)
        debugTraceLogger.debug(TAG, "Group verification join succeeded groupId=$groupId")

        val sendStartedAt = System.currentTimeMillis()
        groupChatRepository.sendGroupMessage(groupId, testMessage, senderAlias = "Debug")

        waitUntil(timeoutAttempts = 12) {
            processedEnvelopeStore.getLastSeen(createdGroup.poolId) >= sendStartedAt
        }
        val messageStored = waitUntil(timeoutAttempts = 12) {
            localMessageStore.getMessages(groupId).any { it.text == testMessage }
        }
        if (!messageStored) {
            throw IllegalStateException("Test message was not observed in local history")
        }

        debugTraceLogger.debug(TAG, "Group verification scenario finished id=$scenarioId")
        return "—ценарий выполнен: группа создана, найдена, подключена и сообщение записано локально"
    }

    fun clearTraceLog() {
        debugTraceLogger.clear()
    }

    private suspend fun waitForSearchHit(query: String, expectedGroupId: String): Group {
        repeat(12) {
            val results = groupRepository.searchGroups(query).first()
            val hit = results.firstOrNull { it.id == expectedGroupId }
            if (hit != null) {
                return hit
            }
            delay(500)
        }
        throw IllegalStateException("Created group did not appear in search results")
    }

    private suspend fun waitUntil(timeoutAttempts: Int, predicate: suspend () -> Boolean): Boolean {
        repeat(timeoutAttempts) {
            if (predicate()) {
                return true
            }
            delay(500)
        }
        return false
    }

    private companion object {
        const val TAG = "DebugToolsRepository"
    }
}
