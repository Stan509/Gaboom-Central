package com.gaboom.agent.data.repository

import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.model.DashboardResponse
import com.gaboom.agent.data.model.WithdrawCommissionResponse
import com.gaboom.agent.data.sync.OfflineCoordinator
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val apiService: AgentApiService,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val offlineCoordinator: OfflineCoordinator
) {
    suspend fun getDashboard(period: Int): Response<DashboardResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.getDashboard(period)
                if (response.isSuccessful && response.body()?.success == true) {
                    agentConfigDataStore.saveCachedDashboard(response.body()!!)
                }
                response
            },
            offlineFallback = {
                val cached = agentConfigDataStore.getCachedDashboard()
                cached ?: DashboardResponse(
                    success = false,
                    agent = null,
                    today = null,
                    period = null,
                    global = null,
                    error = "Offline"
                )
            }
        )
    }

    suspend fun withdrawCommission(): Response<WithdrawCommissionResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                apiService.withdrawCommission()
            },
            offlineFallback = {
                WithdrawCommissionResponse(
                    success = false,
                    amountWithdrawn = null,
                    newBalance = null,
                    entryId = null,
                    error = "Offline"
                )
            }
        )
    }
}
