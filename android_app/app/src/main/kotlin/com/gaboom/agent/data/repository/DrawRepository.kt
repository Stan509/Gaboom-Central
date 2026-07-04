package com.gaboom.agent.data.repository

import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.local.DrawCacheDao
import com.gaboom.agent.data.model.Tirage
import com.gaboom.agent.data.model.TiragesResponse
import com.gaboom.agent.data.sync.DrawExpiryManager
import com.gaboom.agent.data.sync.DrawSynchronizer
import com.gaboom.agent.data.sync.OfflineCoordinator
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrawRepository @Inject constructor(
    private val apiService: AgentApiService,
    private val drawCacheDao: DrawCacheDao,
    private val drawSynchronizer: DrawSynchronizer,
    private val drawExpiryManager: DrawExpiryManager,
    private val offlineCoordinator: OfflineCoordinator
) {
    suspend fun getTiragesActifs(): Response<TiragesResponse> {
        // Enforce expiration check and purge expired draws
        drawExpiryManager.purgeExpiredDraws()

        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.getTiragesActifs()
                if (response.isSuccessful && response.body()?.success == true) {
                    drawSynchronizer.synchronizeDraws()
                }
                response
            },
            offlineFallback = {
                val cached = drawCacheDao.getAllDraws()
                TiragesResponse(
                    success = true,
                    tirages = cached.map {
                        Tirage(
                            id = it.id,
                            nom = it.nom,
                            type = it.type,
                            heureOuverture = it.heureOuverture,
                            heureFermeture = it.heureFermeture,
                            heureTirage = it.heureTirage,
                            etat = it.etat,
                            jours = it.jours,
                            sessionKey = it.sessionKey
                        )
                    },
                    serverTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                    error = null
                )
            }
        )
    }
}
