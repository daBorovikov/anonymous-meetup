package com.example.anonymousmeetup.data.repository

import com.example.anonymousmeetup.data.debug.DebugTraceLogger
import com.example.anonymousmeetup.data.local.GroupKeyStore
import com.example.anonymousmeetup.data.local.JoinedGroupsStore
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupCompatibilityReport
import com.example.anonymousmeetup.data.model.GroupCompatibilityStatus
import com.example.anonymousmeetup.data.model.GroupJoinException
import com.example.anonymousmeetup.data.model.GroupKeyRecord
import com.example.anonymousmeetup.data.remote.FirebaseService
import com.example.anonymousmeetup.data.security.AnonymousMessageCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val joinedGroupsStore: JoinedGroupsStore,
    private val groupKeyStore: GroupKeyStore,
    private val anonymousMessageCodec: AnonymousMessageCodec,
    private val groupPayloadFactory: GroupPayloadFactory,
    private val groupCompatibilityResolver: GroupCompatibilityResolver,
    private val debugTraceLogger: DebugTraceLogger
) {
    fun getGroups(): Flow<List<Group>> = joinedGroupsStore.observeJoinedGroups()

    fun getLocalJoinedGroups(): Flow<List<Group>> = joinedGroupsStore.observeJoinedGroups()

    fun searchGroups(query: String): Flow<List<Group>> = flow {
        val groups = firebaseService.searchGroups(query)
        emit(groups)
    }

    suspend fun createGroup(name: String, description: String, category: String, isPrivate: Boolean): String {
        val bundle = groupPayloadFactory.createNewGroup(
            name = name,
            description = description,
            category = category,
            isPrivate = isPrivate
        )
        debugTraceLogger.debug(
            TAG,
            "createGroup prepared id=${bundle.group.id} poolId=${bundle.group.poolId} hasJoinToken=${!bundle.group.joinToken.isNullOrBlank()}"
        )
        firebaseService.createGroup(bundle.group)
        groupKeyStore.upsertGroupKey(bundle.groupKeyRecord)
        joinedGroupsStore.join(bundle.group)
        return bundle.group.id
    }

    suspend fun joinGroup(groupId: String) {
        val group = firebaseService.getGroup(groupId) ?: throw IllegalStateException("Группа не найдена")
        joinGroupLocally(group)
    }

    suspend fun joinGroupLocally(group: Group) {
        val (resolvedGroup, keyRecord) = resolveJoinableGroup(group)
        groupKeyStore.upsertGroupKey(
            keyRecord.copy(localGroupName = resolvedGroup.name, updatedAt = System.currentTimeMillis())
        )
        joinedGroupsStore.join(resolvedGroup)
        debugTraceLogger.debug(
            TAG,
            "joinGroupLocally success groupId=${resolvedGroup.id} poolId=${resolvedGroup.poolId}"
        )
    }

    suspend fun leaveGroup(groupId: String) {
        leaveGroupLocally(groupId)
    }

    suspend fun leaveGroupLocally(groupId: String) {
        joinedGroupsStore.leave(groupId)
        groupKeyStore.removeGroupKey(groupId)
    }

    suspend fun getGroup(groupId: String): Group {
        return firebaseService.getGroup(groupId) ?: throw IllegalStateException("Группа не найдена")
    }

    suspend fun isGroupJoined(groupId: String): Boolean = joinedGroupsStore.isJoined(groupId)

    suspend fun updateGroup(group: Group) {
        firebaseService.updateGroup(group)
    }

    suspend fun deleteGroup(groupId: String) {
        firebaseService.deleteGroup(groupId)
        joinedGroupsStore.leave(groupId)
        groupKeyStore.removeGroupKey(groupId)
    }

    fun inspectGroup(group: Group): GroupCompatibilityReport = groupCompatibilityResolver.inspect(group)

    suspend fun migrateLegacyGroup(groupId: String): Group {
        val group = getGroup(groupId)
        return migrateLegacyGroup(group)
    }

    suspend fun migrateLegacyGroup(group: Group): Group {
        val compatibility = inspectGroup(group)
        if (compatibility.status == GroupCompatibilityStatus.JOINABLE && compatibility.resolvedGroup != null) {
            return compatibility.resolvedGroup
        }
        val bundle = groupPayloadFactory.createMigrationBundle(group)
        firebaseService.updateGroup(bundle.group)
        debugTraceLogger.debug(
            TAG,
            "migrateLegacyGroup updated groupId=${bundle.group.id} poolId=${bundle.group.poolId}"
        )
        return bundle.group
    }

    private suspend fun resolveJoinableGroup(group: Group): Pair<Group, GroupKeyRecord> {
        val report = inspectGroup(group)
        debugTraceLogger.debug(
            TAG,
            "joinGroup diagnose groupId=${group.id} status=${report.status} details=${report.details.orEmpty()}"
        )
        val resolvedGroup = report.resolvedGroup
        if (!report.isJoinable || resolvedGroup == null) {
            throw GroupJoinException(report)
        }

        val keyRecord = anonymousMessageCodec.parseGroupJoinToken(resolvedGroup.joinToken.orEmpty())
            ?: throw GroupJoinException(
                GroupCompatibilityReport(
                    status = GroupCompatibilityStatus.BROKEN_JOIN_TOKEN,
                    userMessage = "Токен группы повреждён",
                    details = "joinToken parsing failed after joinable check"
                )
            )
        return resolvedGroup to keyRecord
    }

    companion object {
        private const val TAG = "GroupRepository"
    }
}
