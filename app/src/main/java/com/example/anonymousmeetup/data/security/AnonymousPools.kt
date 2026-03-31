package com.example.anonymousmeetup.data.security

import java.security.MessageDigest

object AnonymousPools {
    val PRIVATE_POOLS: List<String> = listOf(
        "private_pool_01",
        "private_pool_02",
        "private_pool_03",
        "private_pool_04"
    )

    val GROUP_POOLS: List<String> = listOf(
        "group_pool_01",
        "group_pool_02",
        "group_pool_03",
        "group_pool_04"
    )

    fun inboundPrivatePoolFor(publicKey: String): String = selectPool(PRIVATE_POOLS, publicKey)

    fun conversationPoolFor(conversationSeed: String): String = selectPool(PRIVATE_POOLS, "conv:$conversationSeed")

    fun groupPoolFor(groupLocalId: String): String = selectPool(GROUP_POOLS, groupLocalId)

    private fun selectPool(pools: List<String>, input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val index = (digest.first().toInt() and 0xFF) % pools.size
        return pools[index]
    }
}
