// Edited: 2026-01-08
// Purpose: Utility to initialize Firestore with test data and verify Firebase connection

package com.example.ceylonqueuebuspulse.util

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firebase Test Utility
 * 
 * Use this to:
 * 1. Verify Firebase connection
 * 2. Seed initial test data into Firestore
 * 3. Create collections and documents
 * 
 * Call from MainActivity.onCreate() once to populate database.
 */
object FirebaseTestUtil {
    
    private const val TAG = "FirebaseTest"
    
    /**
     * Test Firebase connection and create initial data.
     * Call this from MainActivity.onCreate() to populate Firestore.
     */
    suspend fun initializeFirestoreWithTestData() {
        val db = FirebaseFirestore.getInstance()
        
        try {
            Log.d(TAG, "🔥 Starting Firebase connection test...")
            
            // Test 1: Write a simple test document
            testSimpleWrite(db)
            
            // Test 2: Create route configuration
            createRouteConfigurations(db)
            
            // Test 3: Seed sample traffic data
            seedSampleTrafficData(db)
            
            // Test 4: Create initial sync metadata
            createSyncMetadata(db)
            
            Log.d(TAG, "✅ Firebase initialization complete! Check Firebase Console.")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase initialization failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Test 1: Write a simple document to verify connection
     */
    private suspend fun testSimpleWrite(db: FirebaseFirestore) {
        val testData = hashMapOf(
            "message" to "Hello from CeylonQueueBusPulse!",
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.0.0"
        )
        
        db.collection("_test")
            .document("connection_test")
            .set(testData)
            .await()
        
        Log.d(TAG, "✅ Test document written to /_test/connection_test")
    }
    
    /**
     * Test 2: Create route configurations
     */
    private suspend fun createRouteConfigurations(db: FirebaseFirestore) {
        val routes = listOf(
            hashMapOf(
                "routeId" to "138",
                "name" to "Colombo - Piliyandala",
                "description" to "Main route via Kottawa",
                "isActive" to true,
                "createdAt" to System.currentTimeMillis()
            ),
            hashMapOf(
                "routeId" to "174",
                "name" to "Colombo - Panadura",
                "description" to "Coastal route",
                "isActive" to true,
                "createdAt" to System.currentTimeMillis()
            ),
            hashMapOf(
                "routeId" to "177",
                "name" to "Colombo - Horana",
                "description" to "Express route",
                "isActive" to true,
                "createdAt" to System.currentTimeMillis()
            ),
            hashMapOf(
                "routeId" to "120",
                "name" to "Colombo - Kaduwela",
                "description" to "Eastern route",
                "isActive" to true,
                "createdAt" to System.currentTimeMillis()
            )
        )
        
        for (route in routes) {
            val routeId = route["routeId"] as String
            db.collection("routes")
                .document(routeId)
                .set(route)
                .await()
            
            Log.d(TAG, "✅ Route $routeId created")
        }
    }
    
    /**
     * Test 3: Seed sample traffic data for each route
     */
    private suspend fun seedSampleTrafficData(db: FirebaseFirestore) {
        val nowMs = System.currentTimeMillis()
        val windowSizeMs = 15 * 60 * 1000L // 15 minutes
        val currentWindowMs = (nowMs / windowSizeMs) * windowSizeMs
        
        val routes = listOf("138", "174", "177", "120")
        
        for (routeId in routes) {
            // Create 5 sample traffic reports for current window
            for (i in 1..5) {
                val sample = hashMapOf(
                    "routeId" to routeId,
                    "windowStartMs" to currentWindowMs,
                    "segmentId" to null as String?, // null = "_all" segment
                    "severity" to (2.0 + Math.random() * 3.0), // Random 2.0 - 5.0
                    "accuracyM" to (10.0 + Math.random() * 40.0),
                    "userIdHash" to "test_user_$i",
                    "reportedAtMs" to (nowMs - (Math.random() * 10 * 60 * 1000).toLong()),
                    "deviceId" to "test_device",
                    "appVersion" to "1.0.0"
                )
                
                db.collection("routes").document(routeId)
                    .collection("windows").document(currentWindowMs.toString())
                    .collection("segments").document("_all")
                    .collection("samples")
                    .add(sample)
                    .await()
            }
            
            Log.d(TAG, "✅ Created 5 sample traffic reports for route $routeId")
            
            // Create initial aggregate document
            val aggregate = hashMapOf(
                "routeId" to routeId,
                "windowStartMs" to currentWindowMs,
                "segmentId" to "_all",
                "severityAvg" to 3.2,
                "severityP50" to 3.0,
                "severityP90" to 4.5,
                "sampleCount" to 5L,
                "lastAggregatedAtMs" to nowMs
            )
            
            db.collection("routes").document(routeId)
                .collection("windows").document(currentWindowMs.toString())
                .collection("segments").document("_all")
                .collection("stats").document("aggregate")
                .set(aggregate)
                .await()
            
            Log.d(TAG, "✅ Created initial aggregate for route $routeId")
        }
    }
    
    /**
     * Test 4: Create initial sync metadata
     */
    private suspend fun createSyncMetadata(db: FirebaseFirestore) {
        val nowMs = System.currentTimeMillis()
        val windowSizeMs = 15 * 60 * 1000L
        val currentWindowMs = (nowMs / windowSizeMs) * windowSizeMs
        
        val routes = listOf("138", "174", "177", "120")
        
        for (routeId in routes) {
            val syncMeta = hashMapOf(
                "key" to "sync_route_$routeId",
                "lastSyncAtMs" to nowMs,
                "lastWindowStartMs" to currentWindowMs,
                "deviceId" to "test_device",
                "status" to "initialized"
            )
            
            db.collection("syncMeta")
                .document("sync_route_$routeId")
                .set(syncMeta)
                .await()
            
            Log.d(TAG, "✅ Sync metadata created for route $routeId")
        }
        
        // Create global sync metadata
        val globalMeta = hashMapOf(
            "key" to "global",
            "lastSyncAtMs" to nowMs,
            "lastWindowStartMs" to currentWindowMs,
            "totalRoutes" to routes.size,
            "status" to "operational"
        )
        
        db.collection("syncMeta")
            .document("global")
            .set(globalMeta)
            .await()
        
        Log.d(TAG, "✅ Global sync metadata created")
    }
    
    /**
     * Quick test: just write one document to verify connection
     */
    suspend fun quickConnectionTest() {
        val db = FirebaseFirestore.getInstance()
        
        val testDoc = hashMapOf(
            "test" to "Hello Firestore!",
            "timestamp" to System.currentTimeMillis()
        )
        
        db.collection("_test")
            .document("quick_test")
            .set(testDoc)
            .await()
        
        Log.d(TAG, "✅ Quick test document created")
    }
    
    /**
     * Read test: verify we can read back data
     */
    suspend fun verifyData() {
        val db = FirebaseFirestore.getInstance()
        
        val doc = db.collection("_test")
            .document("connection_test")
            .get()
            .await()
        
        if (doc.exists()) {
            Log.d(TAG, "✅ Successfully read document: ${doc.data}")
        } else {
            Log.w(TAG, "⚠️ Document not found - may need to initialize data")
        }
    }
}

