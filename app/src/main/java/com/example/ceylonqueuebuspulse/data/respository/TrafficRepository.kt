// Edited: 2026-01-05
// Purpose: Repository managing traffic reports; persists to Room via DAO, maps entities to domain,
//          and exposes a Flow<List<TrafficReport>> for UI/ViewModel consumption. Includes seeding
//          and user update submission with clear documentation.

package com.example.ceylonqueuebuspulse.data.repository

// Domain models and helpers
import com.example.ceylonqueuebuspulse.data.LatLng
import com.example.ceylonqueuebuspulse.data.TrafficReport
import com.example.ceylonqueuebuspulse.data.TrafficSource
import com.example.ceylonqueuebuspulse.data.UserLocationUpdate

// Coroutines + Flow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// Networking (Phase 2; optional during offline prototyping)
import com.example.ceylonqueuebuspulse.data.network.RetrofitProvider
import com.example.ceylonqueuebuspulse.data.network.model.TrafficReportDto

// Room DAO + entity (Phase 1 persistence layer)
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
    private val dao: TrafficReportDao,
    // IO dispatcher for Room and network work.
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    // Stream of domain models consumed by ViewModel/UI. Mapping preserves reactivity from Room.
    val reports: Flow<List<TrafficReport>> =
        dao.observeReports().map { entities -> entities.map { it.toDomain() } }

    // Expose Room flow directly if needed elsewhere (legacy usage).
    fun observeReports(): Flow<List<TrafficReportEntity>> = dao.observeReports()

    /**
     * Replace all existing reports with the provided historical samples.
     * Typically used during bootstrap or periodic data refresh.
     */
    suspend fun seedHistoricalData(sample: List<TrafficReport>) = withContext(io) {
        val entities = sample.map { it.toEntity() }
        dao.clearAll()
        dao.insertReports(entities)
    }

    /**
     * Convert a user location update into a traffic report and persist it.
     *
     * Notes:
     * - Route mapping is currently naive: falls back to "unknown" when routeId is null.
     * - Severity is a placeholder heuristic and can be replaced with a smarter algorithm.
     */
    suspend fun submitUserUpdate(update: UserLocationUpdate) = withContext(io) {
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

    // Fetch from backend and merge into Room (upsert semantics). Optional until backend live.
    suspend fun sync(city: String? = null) = withContext(io) {
        val remote: List<TrafficReportDto> = RetrofitProvider.api.getReports(city)
        val entities = remote.map { it.toEntity() }
        dao.upsertAll(entities)
    }

    // --- Mapping helpers: Entity <-> Domain ---

    /** Maps a persistence entity into a domain model. */
    private fun TrafficReportEntity.toDomain(): TrafficReport {
        // NOTE: Current entity schema does not store path segments; default to empty list.
        return TrafficReport(
            id = id.toString(), // convert auto-generated Long id to String for domain
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
        // NOTE: Do not pass domain id into entity; Room auto-generates Long PK.
        return TrafficReportEntity(
            routeId = routeId,
            severity = severity,
            source = source.name,
            // Map domain timestamp -> entity timestampMs
            timestampMs = timestamp
        )
    }

    /** Map DTO (remote) into local Room entity. */
    private fun TrafficReportDto.toEntity(): TrafficReportEntity =
        TrafficReportEntity(
            routeId = route,
            severity = severity,
            source = "HISTORICAL", // or derive from DTO if available
            timestampMs = updatedAt
        )
}
