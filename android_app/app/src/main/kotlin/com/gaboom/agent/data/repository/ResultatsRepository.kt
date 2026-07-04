package com.gaboom.agent.data.repository

import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.model.ResultatsResponse
import com.gaboom.agent.data.sync.OfflineCoordinator
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResultatsRepository @Inject constructor(
    private val apiService: AgentApiService,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val offlineCoordinator: OfflineCoordinator
) {
    suspend fun getResultats(): Response<ResultatsResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.getResultats()
                if (response.isSuccessful && response.body()?.success == true) {
                    agentConfigDataStore.saveCachedResultats(response.body()?.resultats ?: emptyList())
                }
                response
            },
            offlineFallback = {
                val cached = agentConfigDataStore.getCachedResultats()
                ResultatsResponse(
                    success = true,
                    resultats = cached,
                    error = null
                )
            }
        )
    }
}
