// Firestore Quick Reference - Common Operations in Kotlin
// Location: Reference only - code already implemented in your app

package com.example.ceylonqueuebuspulse.reference

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Quick reference guide for Firestore operations.
 * Your app already implements these patterns - see:
 * - data/remote/firestore/FirestoreTrafficDataSource.kt
 * - data/respository/TrafficAggregationRepository.kt
 */
class FirestoreQuickReference {

    private val db = FirebaseFirestore.getInstance()

    // ========================================
    // 1. CREATE (Add Documents)
    // ========================================

    /**
     * Add document with auto-generated ID
     */
    suspend fun addDocumentAutoId() {
        val data = mapOf(
            "routeId" to "138",
            "severity" to 3.5,
            "timestamp" to System.currentTimeMillis()
        )

        val docRef = db.collection("samples")
            .add(data)
            .await()

        println("Document created with ID: ${docRef.id}")
    }

    /**
     * Set document with specific ID (upsert)
     */
    suspend fun setDocumentCustomId() {
        val data = mapOf(
            "routeId" to "138",
            "severityAvg" to 3.2,
            "sampleCount" to 47
        )

        db.collection("aggregates")
            .document("route_138_window_12345")
            .set(data)
            .await()

        println("Document set successfully")
    }

    /**
     * Add to nested collection (your app's pattern)
     */
    suspend fun addToNestedCollection() {
        val routeId = "138"
        val windowStartMs = System.currentTimeMillis()

        val data = mapOf(
            "severity" to 4.0,
            "userIdHash" to "abc123",
            "reportedAtMs" to System.currentTimeMillis()
        )

        db.collection("routes").document(routeId)
            .collection("windows").document(windowStartMs.toString())
            .collection("segments").document("_all")
            .collection("samples")
            .add(data)
            .await()
    }

    // ========================================
    // 2. READ (Query Documents)
    // ========================================

    /**
     * Get single document by ID
     */
    suspend fun getDocumentById() {
        val doc = db.collection("routes")
            .document("138")
            .get()
            .await()

        if (doc.exists()) {
            val name = doc.getString("name")
            val count = doc.getLong("count")
            println("Route: $name, Count: $count")
        }
    }

    /**
     * Get all documents in collection
     */
    suspend fun getAllDocuments() {
        val snapshot = db.collection("routes")
            .get()
            .await()

        for (doc in snapshot.documents) {
            println("${doc.id}: ${doc.data}")
        }
    }

    /**
     * Query with filters
     */
    suspend fun queryWithFilters() {
        val snapshot = db.collection("samples")
            .whereEqualTo("routeId", "138")
            .whereGreaterThan("severity", 3.0)
            .orderBy("severity", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .await()

        for (doc in snapshot.documents) {
            println(doc.data)
        }
    }

    /**
     * Query nested collection (your app's pattern)
     */
    suspend fun queryNestedCollection() {
        val routeId = "138"
        val windowStartMs = 1736339400000L

        val samples = db.collection("routes").document(routeId)
            .collection("windows").document(windowStartMs.toString())
            .collection("segments").document("_all")
            .collection("samples")
            .whereGreaterThan("reportedAtMs", System.currentTimeMillis() - 30 * 60 * 1000)
            .orderBy("reportedAtMs", Query.Direction.DESCENDING)
            .limit(500)
            .get()
            .await()

        println("Found ${samples.size()} samples")
    }

    /**
     * Map document to data class
     */
    data class TrafficSample(
        val routeId: String = "",
        val severity: Double = 0.0,
        val reportedAtMs: Long = 0L
    )

    suspend fun mapToDataClass() {
        val snapshot = db.collection("samples")
            .limit(10)
            .get()
            .await()

        val samples = snapshot.documents.mapNotNull {
            it.toObject(TrafficSample::class.java)
        }

        samples.forEach { println(it) }
    }

    // ========================================
    // 3. UPDATE (Modify Documents)
    // ========================================

    /**
     * Update specific fields
     */
    suspend fun updateFields() {
        db.collection("aggregates")
            .document("route_138")
            .update(
                mapOf(
                    "severityAvg" to 3.5,
                    "sampleCount" to 52,
                    "lastUpdated" to System.currentTimeMillis()
                )
            )
            .await()
    }

    /**
     * Update or create (upsert)
     */
    suspend fun upsertDocument() {
        val data = mapOf(
            "severityAvg" to 3.2,
            "sampleCount" to 47
        )

        db.collection("aggregates")
            .document("route_138")
            .set(data)  // Creates if doesn't exist, replaces if exists
            .await()
    }

    /**
     * Increment a counter (atomic operation)
     */
    suspend fun incrementCounter() {
        db.collection("stats")
            .document("global")
            .update("reportCount", com.google.firebase.firestore.FieldValue.increment(1))
            .await()
    }

    // ========================================
    // 4. DELETE (Remove Documents)
    // ========================================

    /**
     * Delete single document
     */
    suspend fun deleteDocument() {
        db.collection("samples")
            .document("abc123")
            .delete()
            .await()
    }

    /**
     * Delete all documents in window (your app's pattern)
     */
    suspend fun deleteWindow(routeId: String, windowStartMs: Long) {
        val batch = db.batch()

        val samples = db.collection("routes").document(routeId)
            .collection("windows").document(windowStartMs.toString())
            .collection("segments").document("_all")
            .collection("samples")
            .get()
            .await()

        for (doc in samples.documents) {
            batch.delete(doc.reference)
        }

        batch.commit().await()
    }

    // ========================================
    // 5. REALTIME LISTENERS (Live Updates)
    // ========================================

    /**
     * Listen to document changes
     */
    fun listenToDocument() {
        db.collection("routes")
            .document("138")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Listen failed: $error")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    println("Current data: ${snapshot.data}")
                }
            }
    }

    /**
     * Listen to query results
     */
    fun listenToQuery() {
        db.collection("samples")
            .whereEqualTo("routeId", "138")
            .orderBy("reportedAtMs", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    println("Listen failed: $error")
                    return@addSnapshotListener
                }

                for (doc in snapshots!!.documents) {
                    println("Sample: ${doc.data}")
                }
            }
    }

    // ========================================
    // 6. BATCH OPERATIONS (Transactions)
    // ========================================

    /**
     * Batch write (up to 500 operations)
     */
    suspend fun batchWrite() {
        val batch = db.batch()

        val ref1 = db.collection("samples").document()
        batch.set(ref1, mapOf("severity" to 3.0))

        val ref2 = db.collection("samples").document()
        batch.set(ref2, mapOf("severity" to 4.0))

        val ref3 = db.collection("stats").document("global")
        batch.update(ref3, "count", com.google.firebase.firestore.FieldValue.increment(2))

        batch.commit().await()
        println("Batch write completed")
    }

    /**
     * Transaction (atomic read + write)
     */
    suspend fun runTransaction() {
        db.runTransaction { transaction ->
            val statsRef = db.collection("stats").document("global")
            val snapshot = transaction.get(statsRef)

            val currentCount = snapshot.getLong("count") ?: 0L
            val newCount = currentCount + 1

            transaction.update(statsRef, "count", newCount)

            newCount
        }.await()
    }

    // ========================================
    // 7. ADVANCED QUERIES
    // ========================================

    /**
     * Query with multiple conditions
     */
    suspend fun complexQuery() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 60 * 60 * 1000

        val results = db.collection("samples")
            .whereEqualTo("routeId", "138")
            .whereGreaterThanOrEqualTo("reportedAtMs", oneHourAgo)
            .whereLessThanOrEqualTo("reportedAtMs", now)
            .whereGreaterThan("severity", 2.0)
            .orderBy("severity", Query.Direction.DESCENDING)
            .orderBy("reportedAtMs", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()

        println("Found ${results.size()} matching documents")
    }

    /**
     * Pagination (cursor-based)
     */
    suspend fun paginateResults() {
        val firstPage = db.collection("samples")
            .orderBy("reportedAtMs", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .await()

        println("First page: ${firstPage.size()} documents")

        // Get next page
        if (!firstPage.isEmpty) {
            val lastDoc = firstPage.documents.last()

            val nextPage = db.collection("samples")
                .orderBy("reportedAtMs", Query.Direction.DESCENDING)
                .startAfter(lastDoc)
                .limit(20)
                .get()
                .await()

            println("Next page: ${nextPage.size()} documents")
        }
    }

    /**
     * Collection group query (query across all subcollections)
     */
    suspend fun collectionGroupQuery() {
        // Query all "samples" subcollections across all routes/windows
        val results = db.collectionGroup("samples")
            .whereGreaterThan("severity", 4.0)
            .orderBy("severity", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()

        println("Found ${results.size()} severe traffic samples across all routes")
    }
}

// ========================================
// 8. BEST PRACTICES
// ========================================

/**
 * Best Practices for Firestore in Android:
 *
 * 1. USE COROUTINES with .await()
 *    - Cleaner than callbacks
 *    - Easier error handling
 *    - Works well with ViewModels
 *
 * 2. OFFLINE PERSISTENCE
 *    - Enabled by default
 *    - Cache queries locally
 *    - Automatic sync when online
 *
 * 3. SECURITY RULES
 *    - Never trust client code
 *    - Always validate in rules
 *    - Use authentication
 *
 * 4. INDEXING
 *    - Create indexes for complex queries
 *    - Firestore logs URLs for needed indexes
 *    - Can also define in firebase.json
 *
 * 5. DATA MODELING
 *    - Denormalize when needed
 *    - Avoid deep nesting (3 levels max)
 *    - Use subcollections for large arrays
 *
 * 6. BATCHING
 *    - Batch multiple writes (up to 500)
 *    - Reduces billing costs
 *    - Atomic operations
 *
 * 7. ERROR HANDLING
 *    - Wrap in try-catch
 *    - Handle network errors gracefully
 *    - Show user-friendly messages
 *
 * 8. PAGINATION
 *    - Use cursor-based pagination
 *    - Limit results (10-50 per page)
 *    - Improves performance
 */

