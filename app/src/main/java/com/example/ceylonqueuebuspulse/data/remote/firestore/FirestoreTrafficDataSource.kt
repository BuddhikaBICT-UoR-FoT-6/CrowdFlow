package com.example.ceylonqueuebuspulse.data.remote.firestore

import com.example.ceylonqueuebuspulse.data.remote.firestore.dto.AggregatedTrafficDto
import com.example.ceylonqueuebuspulse.data.remote.firestore.dto.TrafficSampleDto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreTrafficDataSource(
    private val firestore: FirebaseFirestore
) {

    fun windowKey(windowStartMs: Long): String = windowStartMs.toString()
    fun segmentKey(segmentId: String?): String = segmentId ?: "_all"

    private fun samplesCollection(routeId: String, windowStartMs: Long, segmentId: String?) =
        firestore.collection(FirestoreSchema.COLL_ROUTES).document(routeId)
            .collection(FirestoreSchema.SUBCOLL_WINDOWS).document(windowKey(windowStartMs))
            .collection(FirestoreSchema.SUBCOLL_SEGMENTS).document(segmentKey(segmentId))
            .collection(FirestoreSchema.SUBCOLL_SAMPLES)

    private fun aggregateDoc(routeId: String, windowStartMs: Long, segmentId: String?) =
        firestore.collection(FirestoreSchema.COLL_ROUTES).document(routeId)
            .collection(FirestoreSchema.SUBCOLL_WINDOWS).document(windowKey(windowStartMs))
            .collection(FirestoreSchema.SUBCOLL_SEGMENTS).document(segmentKey(segmentId))
            .collection("stats").document(FirestoreSchema.DOC_AGGREGATE)

    suspend fun submitSample(sample: TrafficSampleDto) {
        val col = samplesCollection(sample.routeId, sample.windowStartMs, sample.segmentId)
        col.add(sample).await()
    }

    suspend fun fetchRecentSamples(
        routeId: String,
        windowStartMs: Long,
        segmentId: String?,
        sinceMs: Long,
        limit: Long = 500
    ): List<TrafficSampleDto> {
        val snap = samplesCollection(routeId, windowStartMs, segmentId)
            .whereGreaterThanOrEqualTo("reportedAtMs", sinceMs)
            .orderBy("reportedAtMs", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snap.documents.mapNotNull { it.toObject(TrafficSampleDto::class.java) }
    }

    suspend fun writeAggregate(dto: AggregatedTrafficDto) {
        aggregateDoc(dto.routeId, dto.windowStartMs, dto.segmentId)
            .set(dto)
            .await()
    }

    suspend fun fetchAggregatesForWindow(routeId: String, windowStartMs: Long): List<AggregatedTrafficDto> {
        val segments = firestore.collection(FirestoreSchema.COLL_ROUTES).document(routeId)
            .collection(FirestoreSchema.SUBCOLL_WINDOWS).document(windowKey(windowStartMs))
            .collection(FirestoreSchema.SUBCOLL_SEGMENTS)
            .get()
            .await()

        val out = ArrayList<AggregatedTrafficDto>()

        for (seg in segments.documents) {
            val agg = seg.reference.collection("stats").document(FirestoreSchema.DOC_AGGREGATE).get().await()
            agg.toObject(AggregatedTrafficDto::class.java)?.let(out::add)
        }

        return out
    }
}
