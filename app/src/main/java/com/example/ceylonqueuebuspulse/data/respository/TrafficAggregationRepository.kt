package com.example.ceylonqueuebuspulse.data.repository

import com.example.ceylonqueuebuspulse.data.aggregation.TrafficAggregator
import com.example.ceylonqueuebuspulse.data.local.dao.AggregatedTrafficDao
import com.example.ceylonqueuebuspulse.data.local.dao.SyncMetaDao
import com.example.ceylonqueuebuspulse.data.local.entity.AggregatedTrafficEntity
import com.example.ceylonqueuebuspulse.data.local.entity.SyncMetaEntity
import com.example.ceylonqueuebuspulse.data.remote.firestore.FirestoreTrafficDataSource
import com.example.ceylonqueuebuspulse.data.remote.firestore.dto.AggregatedTrafficDto
import com.example.ceylonqueuebuspulse.data.remote.firestore.dto.TrafficSampleDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrafficAggregationRepository(
    private val remote: FirestoreTrafficDataSource,
    private val aggregatedTrafficDao: AggregatedTrafficDao,
    private val syncMetaDao: SyncMetaDao,
){
    companion object{
        private const val META_KEY = "global"
    }

    suspend fun submitUserSample(sample: TrafficSampleDto) {
        remote.submitSample(sample)
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
        sampleLookbackMs: Long = 30 * 60 * 1000L
    ) = withContext(Dispatchers.IO){
        val sinceMs = nowMs - sampleLookbackMs
        val samples = remote.fetchRecentSamples(
            routeId = routeId,
            windowStartMs = windowStartMs,
            segmentId = segmentId,
            sinceMs = sinceMs
        )

        val agg = TrafficAggregator.aggregate(samples, nowMs = nowMs)

        val dto = AggregatedTrafficDto(
            routeId = routeId,
            windowStartMs = windowStartMs,
            segmentId = segmentId,
            severityAvg = agg.severityAvg,
            severityP50 = agg.severityP50,
            severityP90 = agg.severityP90,
            sampleCount = agg.sampleCount,
            lastAggregatedAtMs = nowMs
        )

        remote.writeAggregate(dto)

        // Refresh local cache \= remote truth
        refreshWindowFromRemote(routeId, windowStartMs)

        syncMetaDao.upsert(
            SyncMetaEntity(
                key = META_KEY,
                lastSyncAtMs = nowMs,
                lastWindowStartMs = windowStartMs
            )
        )

    }

    suspend fun refreshWindowFromRemote(routeId: String, windowStartMs: Long) = withContext(Dispatchers.IO) {
        // Fetch aggregates from Firestore for this window.
        val aggregates: List<AggregatedTrafficDto> = remote.fetchAggregatesForWindow(routeId, windowStartMs)

        // Map remote DTO -> Room entity.
        val entities: List<AggregatedTrafficEntity> = aggregates.map { dto: AggregatedTrafficDto -> dto.toEntity() }

        // Overwrite ensures outdated local cache is removed (remote is treated as source of truth).
        aggregatedTrafficDao.overwriteWindow(routeId, windowStartMs, entities)
    }

    private fun AggregatedTrafficDto.toEntity(): AggregatedTrafficEntity {
        return AggregatedTrafficEntity(
            routeId = routeId,
            windowStartMs = windowStartMs,
            segmentId = segmentId ?: "_all",
            severityAvg = severityAvg,
            severityP50 = severityP50,
            severityP90 = severityP90,
            sampleCount = sampleCount,
            lastAggregatedAtMs = lastAggregatedAtMs
        )
    }

}
