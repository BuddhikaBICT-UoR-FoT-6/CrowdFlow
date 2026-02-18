package com.example.ceylonqueuebuspulse.data.repository

import com.example.ceylonqueuebuspulse.data.local.dao.AggregatedTrafficDao
import com.example.ceylonqueuebuspulse.data.local.dao.SyncMetaDao
import com.example.ceylonqueuebuspulse.data.local.entity.AggregatedTrafficEntity
import com.example.ceylonqueuebuspulse.data.local.entity.SyncMetaEntity
import com.example.ceylonqueuebuspulse.data.network.model.MongoApi
import com.example.ceylonqueuebuspulse.data.network.model.SubmitSampleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository managing traffic aggregation: submits user samples, syncs remote aggregates into Room,
 * and exposes aggregated data for UI (Room is source of truth).
 */
class TrafficAggregationRepository(
    private val mongoApi: MongoApi,
    private val aggregatedTrafficDao: AggregatedTrafficDao,
    private val syncMetaDao: SyncMetaDao,
) {
    companion object {
        private fun metaKey(routeId: String): String = "sync_route_$routeId"
    }

    // Simple in-memory cache for most recent window fetches (fast UI / reduces duplicate calls)
    private val windowCache = ConcurrentHashMap<String, List<AggregatedTrafficEntity>>()

    /**
     * Observe aggregated traffic data for a specific route, ordered by window (most recent first).
     */
    fun observeAggregatedTraffic(routeId: String): Flow<List<AggregatedTrafficEntity>> =
        aggregatedTrafficDao.observeAggregates(routeId)

    /** Get sync metadata for a specific route. */
    suspend fun getSyncMeta(routeId: String): SyncMetaEntity? = withContext(Dispatchers.IO) {
        syncMetaDao.get(metaKey(routeId))
    }

    /**
     * Submit a user sample to backend.
     */
    suspend fun submitUserSample(
        routeId: String,
        windowStartMs: Long,
        segmentId: String?,
        severity: Double,
        reportedAtMs: Long,
        userIdHash: String?
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = SubmitSampleRequest(
                routeId = routeId,
                windowStartMs = windowStartMs,
                segmentId = segmentId ?: "_all",
                severity = severity,
                reportedAtMs = reportedAtMs,
                userIdHash = userIdHash
            )
            val resp = mongoApi.submitSample(body)
            if (resp.ok) AppResult.Ok(Unit)
            else AppResult.Err(AppError.Server(resp.error ?: resp.message ?: "Submit failed"))
        } catch (t: Throwable) {
            AppResult.Err(RepositoryErrorMapper.toAppError(t))
        }
    }

    /**
     * Fetch aggregates from remote and upsert into Room.
     * Room remains the source of truth; this just refreshes local storage.
     */
    suspend fun syncWindow(routeId: String, windowStartMs: Long, nowMs: Long): AppResult<List<AggregatedTrafficEntity>> =
        withContext(Dispatchers.IO) {
            val key = "$routeId:$windowStartMs"
            try {
                // Serve from cache if already fetched this window in-process
                windowCache[key]?.let { return@withContext AppResult.Ok(it) }

                val resp = mongoApi.getAggregates(routeId = routeId, windowStartMs = windowStartMs)
                if (!resp.ok) {
                    return@withContext AppResult.Err(AppError.Server(resp.error ?: resp.message ?: "Sync failed"))
                }

                val entities = (resp.data ?: emptyList()).map { dto ->
                    AggregatedTrafficEntity(
                        routeId = dto.routeId,
                        windowStartMs = dto.windowStartMs,
                        segmentId = dto.segmentId,
                        severityAvg = dto.severityAvg,
                        severityP50 = dto.severityP50,
                        severityP90 = dto.severityP90,
                        sampleCount = dto.sampleCount,
                        lastAggregatedAtMs = dto.lastAggregatedAtMs
                    )
                }

                aggregatedTrafficDao.overwriteWindow(routeId, windowStartMs, entities)

                syncMetaDao.upsert(
                    SyncMetaEntity(
                        key = metaKey(routeId),
                        lastSyncAtMs = nowMs,
                        lastWindowStartMs = windowStartMs
                    )
                )

                windowCache[key] = entities
                AppResult.Ok(entities)
            } catch (t: Throwable) {
                AppResult.Err(RepositoryErrorMapper.toAppError(t))
            }
        }

    /** Backwards compatible wrapper for existing callers. */
    suspend fun aggregateAndSyncWindow(
        routeId: String,
        windowStartMs: Long,
        segmentId: String?,
        nowMs: Long,
    ) {
        // segmentId is ignored: server already returns segmented aggregates; keep method for old API.
        syncWindow(routeId, windowStartMs, nowMs)
    }

    /**
     * Explicit refresh of a window from remote.
     */
    suspend fun refreshWindowFromRemote(routeId: String, windowStartMs: Long) = withContext(Dispatchers.IO) {
        syncWindow(routeId, windowStartMs, nowMs = System.currentTimeMillis())
    }
}
