package com.example.ceylonqueuebuspulse.data.remote.firestore

object FirestoreSchema {
    const val COLL_ROUTES = "routes"

    // Subcollections under /routes/{routeId}/
    const val SUBCOLL_WINDOWS = "windows"      // /routes/{routeId}/windows/{windowKey}
    const val SUBCOLL_SEGMENTS = "segments"    // /routes/{routeId}/windows/{windowKey}/segments/{segmentKey}
    const val SUBCOLL_SAMPLES = "samples"      // /.../samples/{sampleId}
    const val DOC_AGGREGATE = "aggregate"      // /.../{segmentKey}/aggregate

    // Metadata
    const val COLL_SYNC_META = "syncMeta"      // /syncMeta/{deviceOrGlobalKey}
}
