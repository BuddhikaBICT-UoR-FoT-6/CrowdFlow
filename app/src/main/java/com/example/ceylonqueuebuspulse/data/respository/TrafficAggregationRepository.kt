package com.example.ceylonqueuebuspulse.data.repository

import com.example.ceylonqueuebuspulse.data.local.dao.AggregatedTrafficDao
import com.example.ceylonqueuebuspulse.data.local.dao.SyncMetaDao
import com.example.ceylonqueuebuspulse.data.local.entity.AggregatedTrafficEntity
import com.example.ceylonqueuebuspulse.data.local.entity.SyncMetaEntity
import com.example.ceylonqueuebuspulse.data.network.model.AggregateRequest
import com.example.ceylonqueuebuspulse.data.network.model.MongoApi
import com.example.ceylonqueuebuspulse.data.network.model.SubmitSampleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository managing traffic aggregation: submits user samples, computes time-windowed aggregates,
 * syncs with Firestore (treating remote as source of truth), and exposes aggregated data for UI.
 */
class TrafficAggregationRepository(
    private val mongoApi: MongoApi,
    private val aggregatedTrafficDao: AggregatedTrafficDao,
    private val syncMetaDao: SyncMetaDao,
){
    companion object {
        private fun metaKey(routeId: String): String = "sync_route_$routeId"
    }

    /**
     * Observe aggregated traffic data for a specific route, ordered by window (most recent first).
     */
    fun observeAggregatedTraffic(routeId: String): Flow<List<AggregatedTrafficEntity>> =
        aggregatedTrafficDao.observeAggregates(routeId)

    /**
     * Get sync metadata for a specific route.
     */
    suspend fun getSyncMeta(routeId: String): SyncMetaEntity? = withContext(Dispatchers.IO) {
        syncMetaDao.get(metaKey(routeId))
    }

    suspend fun submitUserSample(
        routeId: String,
        windowStartMs: Long,
        segmentId: String?,
        severity: Double,
        reportedAtMs: Long,
        userIdHash: String?
    ) = withContext(Dispatchers.IO) {
        val body = SubmitSampleRequest(
            routeId = routeId,
            windowStartMs = windowStartMs,
            segmentId = segmentId ?: "_all",
            severity = severity,
            reportedAtMs = reportedAtMs,
            userIdHash = userIdHash
        )
        mongoApi.submitSample(body)
    }

    /**
     * Reads samples, computes aggregate, writes to Firestore, then refreshes Room from Firestore aggregate.
     * Remote aggregate is treated as truth \-> Room window is overwritten.
     */
    suspend fun aggregateAndSyncWindow(
        routeId: String,
        windowStartMs: Long,
        segmentId: String?,
        nowMs: Long,
    ) = withContext(Dispatchers.IO) {
        // Ask server to compute and upsert aggregate
        mongoApi.aggregateWindow(
            AggregateRequest(routeId = routeId, windowStartMs = windowStartMs, segmentId = segmentId ?: "_all")
        )

        // Fetch aggregates for window
        val resp = mongoApi.getAggregates(routeId = routeId, windowStartMs = windowStartMs)
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
    }

    suspend fun refreshWindowFromRemote(routeId: String, windowStartMs: Long) = withContext(Dispatchers.IO) {
        val resp = mongoApi.getAggregates(routeId, windowStartMs)
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
    }
}
