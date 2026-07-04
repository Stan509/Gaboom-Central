package com.gaboom.agent.data.sync

import android.util.Log
import com.gaboom.agent.data.network.NetworkMonitor
import retrofit2.Response
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCoordinator @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {
    suspend fun <T> execute(
        networkCall: suspend () -> Response<T>,
        offlineFallback: suspend () -> T?
    ): Response<T> {
        val isOnline = networkMonitor.isCurrentlyOnline()
        if (!isOnline) {
            Log.d("OfflineCoordinator", "Offline: executing fallback")
            val fallbackData = offlineFallback()
            if (fallbackData != null) {
                return Response.success(fallbackData)
            }
            return Response.error(503, okhttp3.ResponseBody.create(null, "Offline and no fallback data available"))
        }

        return try {
            val response = networkCall()
            if (response.isSuccessful) {
                response
            } else {
                Log.d("OfflineCoordinator", "Server returned error: executing fallback")
                val fallbackData = offlineFallback()
                if (fallbackData != null) {
                    Response.success(fallbackData)
                } else {
                    response
                }
            }
        } catch (e: Exception) {
            when (e) {
                is UnknownHostException, is ConnectException, is SocketTimeoutException -> {
                    Log.e("OfflineCoordinator", "Network connection error, executing fallback", e)
                    val fallbackData = offlineFallback()
                    if (fallbackData != null) {
                        Response.success(fallbackData)
                    } else {
                        Response.error(503, okhttp3.ResponseBody.create(null, "Connection failed: ${e.message}"))
                    }
                }
                else -> {
                    Log.e("OfflineCoordinator", "Unexpected exception, propagating", e)
                    val fallbackData = offlineFallback()
                    if (fallbackData != null) {
                        Response.success(fallbackData)
                    } else {
                        Response.error(500, okhttp3.ResponseBody.create(null, "Internal error: ${e.message}"))
                    }
                }
            }
        }
    }
}
