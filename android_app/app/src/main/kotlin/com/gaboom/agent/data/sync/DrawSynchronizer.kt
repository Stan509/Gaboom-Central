package com.gaboom.agent.data.sync

import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.local.DrawCacheDao
import com.gaboom.agent.data.local.DrawCacheEntity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrawSynchronizer @Inject constructor(
    private val apiService: AgentApiService,
    private val drawCacheDao: DrawCacheDao
) {
    suspend fun synchronizeDraws(): Boolean {
        return try {
            val response = apiService.getTiragesActifs()
            if (response.isSuccessful && response.body()?.success == true) {
                val drawsList = response.body()?.tirages ?: emptyList()
                val cacheEntities = drawsList.map { draw ->
                    val version = 1
                    val content = "${draw.id}|${draw.nom}|${draw.type}|${draw.heureOuverture}|${draw.heureFermeture}|${draw.heureTirage}|${draw.etat}|$version"
                    val checksum = sha256(content)
                    
                    // Expiration set to 24 hours from now (1 day offline budget)
                    val expirationTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
                    
                    DrawCacheEntity(
                        id = draw.id,
                        nom = draw.nom,
                        type = draw.type,
                        heureOuverture = draw.heureOuverture,
                        heureFermeture = draw.heureFermeture,
                        heureTirage = draw.heureTirage,
                        etat = draw.etat,
                        jours = draw.jours,
                        sessionKey = draw.sessionKey,
                        signature = "", // Set signature if signing is enabled
                        version = version,
                        checksum = checksum,
                        expiration = expirationTime,
                        replayToken = java.util.UUID.randomUUID().toString()
                    )
                }
                
                // Validate all draws before saving
                val validEntities = cacheEntities.filter { DrawValidator.validateDraw(it) }
                if (validEntities.isNotEmpty()) {
                    drawCacheDao.deleteAll()
                    drawCacheDao.insertAll(validEntities)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
