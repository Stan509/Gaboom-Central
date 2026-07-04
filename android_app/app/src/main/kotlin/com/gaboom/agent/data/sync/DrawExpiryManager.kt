package com.gaboom.agent.data.sync

import com.gaboom.agent.data.local.DrawCacheDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrawExpiryManager @Inject constructor(
    private val drawCacheDao: DrawCacheDao
) {
    suspend fun purgeExpiredDraws() {
        val allDraws = drawCacheDao.getAllDraws()
        val expired = allDraws.filter { it.expiration > 0 && it.expiration < System.currentTimeMillis() }
        if (expired.isNotEmpty()) {
            val valid = allDraws.filter { it.expiration == 0L || it.expiration >= System.currentTimeMillis() }
            drawCacheDao.deleteAll()
            drawCacheDao.insertAll(valid)
        }
    }
}
