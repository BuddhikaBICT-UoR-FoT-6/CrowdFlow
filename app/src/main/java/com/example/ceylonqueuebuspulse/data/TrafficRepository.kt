// Edited: 2026-01-05
// Purpose: Repository managing traffic reports; persists to Room via DAO, maps entities to domain,
//          and exposes a Flow<List<TrafficReport>> for UI/ViewModel consumption. Includes seeding
//          and user update submission with clear documentation.

package com.example.ceylonqueuebuspulse.data

// Reactive streams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Room DAO + entity
import com.example.ceylonqueuebuspulse.data.local.TrafficReportDao
import com.example.ceylonqueuebuspulse.data.local.TrafficReportEntity

/**
 * Repository that acts as the single source of truth for traffic reports.
 *
 * Responsibilities:
 * - Read: Observe Room (DAO) as a Flow of entities and map them to domain models.
 * - Write: Seed historical reports and append user-submitted reports into Room.
 * - Map: Convert between domain [TrafficReport] and persistence [TrafficReportEntity].
 */
class TrafficRepository(
    // DAO dependency; provided by the Room database.
    private val dao: TrafficReportDao
) {

    // Stream of domain models consumed by ViewModel/UI. Mapping preserves reactivity from Room.
    val reports: Flow<List<TrafficReport>> =
        dao.observeReports().map { entities -> entities.map { it.toDomain() } }

    /**
     * Replaces all existing reports with the provided historical samples.
     * Typically used during bootstrap or periodic data refresh.
     */
    suspend fun seedHistoricalData(sample: List<TrafficReport>) {
        val entities = sample.map { it.toEntity() }
        dao.clearAll()
        dao.insertReports(entities)
    }

    /**
     * Converts a user location update into a traffic report and persists it.
     *
     * Notes:
     * - Route mapping is currently naive: falls back to "unknown" when routeId is null.
     * - Severity is a placeholder heuristic and can be replaced with a smarter algorithm.
     */
    suspend fun submitUserUpdate(update: UserLocationUpdate) {
        // Construct a minimal domain report from the user's location
        val report = TrafficReport(
            id = "user-${update.id}",
            // Associate the report with the route when known; otherwise mark as "unknown".
            routeId = update.routeId ?: "unknown",
            // Placeholder severity until congestion heuristic is implemented.
            severity = 3,
            // Use a single point as the segment for now.
            segment = listOf(LatLng(update.lat, update.lng)),
            // Use current time for the report timestamp; could also use update.timestamp.
            timestamp = System.currentTimeMillis(),
            // Source marked as USER for downstream filtering/aggregation.
            source = TrafficSource.USER
        )
        // Persist the report via Room; UI will react via the Flow mapping above.
        dao.insertReport(report.toEntity())
    }

    // --- Mapping helpers: Entity <-> Domain ---

    /** Maps a persistence entity into a domain model. */
    private fun TrafficReportEntity.toDomain(): TrafficReport {
        // NOTE: Current entity schema does not store path segments; default to empty list.
        return TrafficReport(
            id = id.toString(),
            routeId = routeId,
            severity = severity,
            source = TrafficSource.valueOf(source),
            segment = emptyList(),
            // Map entity timestampMs -> domain timestamp
            timestamp = timestampMs
        )
    }

    /** Maps a domain model into a persistence entity. */
    private fun TrafficReport.toEntity(): TrafficReportEntity {
        // NOTE: Current entity schema does not persist segments; only basic fields are stored.
        return TrafficReportEntity(
            routeId = routeId,
            severity = severity,
            source = source.name,
            // Map domain timestamp -> entity timestampMs
            timestampMs = timestamp
        )
    }
}
