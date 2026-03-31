package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.local.EncounterStore
import com.example.anonymousmeetup.data.local.LocalConversationStore
import com.example.anonymousmeetup.data.model.EncounterLocal
import com.example.anonymousmeetup.data.model.LocalContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val localConversationStore: LocalConversationStore,
    private val encounterStore: EncounterStore
) {
    fun getEncounters(): Flow<List<EncounterLocal>> = encounterStore.observeEncounters()

    fun getFriends(): Flow<List<LocalContact>> {
        return localConversationStore.observeConversations().map { conversations ->
            conversations.map { conversation ->
                LocalContact(
                    sessionId = conversation.conversationId,
                    alias = conversation.localAlias ?: "Собеседник",
                    peerPublicKey = conversation.peerPublicKey,
                    currentChatHash = conversation.poolId,
                    addedAt = conversation.createdAt
                )
            }
        }
    }
}
