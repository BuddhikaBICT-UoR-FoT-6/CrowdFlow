// Edited: 2026-01-06
// Purpose: Repository managing traffic reports; persists to Room via DAO, maps entities to domain,
//          and exposes reactive Flows for UI/ViewModel. Extended for remote sync + best-effort push.

package com.example.ceylonqueuebuspulse.data.repository

// --- Domain models and helpers ---
import com.example.ceylonqueuebuspulse.data.LatLng
import com.example.ceylonqueuebuspulse.data.TrafficReport
import com.example.ceylonqueuebuspulse.data.TrafficSource
import com.example.ceylonqueuebuspulse.data.UserLocationUpdate

// --- Android/DI ---
import android.content.Context
import javax.inject.Inject

// --- Coroutines + Flow ---
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// --- Networking (Remote API) ---
import com.example.ceylonqueuebuspulse.data.network.TrafficApi
import com.example.ceylonqueuebuspulse.data.network.model.TrafficReportDto
import com.example.ceylonqueuebuspulse.data.network.UserUpdateDto

// --- Room DAO + entity (local persistence) ---
import com.example.ceylonqueuebuspulse.data.local.TrafficReportDao
import com.example.ceylonqueuebuspulse.data.local.TrafficReportEntity

/**
 * Repository that acts as the single source of truth for traffic reports.
 *
 * Responsibilities:
 * - Read: Observe Room (DAO) as a Flow of entities and map them to domain models.
 * - Write: Seed historical reports and append user-submitted reports into Room.
 * - Remote: Sync remote -> Room and push user updates (best-effort).
 */
class TrafficRepository @Inject constructor(
    // DAO dependency; provided by the Room database.
    private val dao: TrafficReportDao,
    // IO dispatcher for Room and network work.
    private val io: CoroutineDispatcher = Dispatchers.IO,
    // Remote API injected via DI (Hilt)
    private val api: TrafficApi,
    // Application context (reserved for future needs; e.g., resources, connectivity)
    @Suppress("unused") private val appContext: Context
) {

    // Stream of domain models consumed by ViewModel/UI. Mapping preserves reactivity from Room.
    val reports: Flow<List<TrafficReport>> =
        dao.observeReports().map { entities -> entities.map { it.toDomain() } }

    // Expose Room flow directly if needed elsewhere (legacy/debug usage).
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
     * Convert a user location update into a traffic report and persist it locally.
     * Then best-effort push the update to the backend (fire-and-forget).
     *
     * Notes:
     * - Route mapping is currently naive: falls back to "unknown" when routeId is null.
     * - Severity is a placeholder heuristic and can be replaced with a smarter algorithm.
     */
    suspend fun submitUserUpdate(update: UserLocationUpdate) = withContext(io) {
        val report = TrafficReport(
            id = "user-${update.id}",
            routeId = update.routeId ?: "unknown",
            severity = 3, // TODO: derive from heuristics
            segment = listOf(LatLng(update.lat, update.lng)),
            timestamp = System.currentTimeMillis(),
            source = TrafficSource.USER
        )
        // Persist locally; UI will react via Room Flow
        dao.insertReport(report.toEntity())
        // Best-effort push to backend
        pushUserUpdateRemote(update)
    }

    /**
     * Fetch from backend and merge into Room (upsert semantics).
     * Optional until backend is live.
     */
    suspend fun sync(city: String? = null) = withContext(io) {
        val remote: List<TrafficReportDto> = api.getReports(city)
        val entities = remote.map { it.toEntity() }
        dao.upsertAll(entities)
    }

    // --- Mapping helpers: Entity <-> Domain ---

    /** Map a persistence entity into a domain model. */
    private fun TrafficReportEntity.toDomain(): TrafficReport =
        TrafficReport(
            id = id.toString(), // convert auto-generated Long id to String for domain
            routeId = routeId,
            severity = severity,
            source = TrafficSource.valueOf(source),
            segment = emptyList(), // segments not persisted in v1
            timestamp = timestampMs
        )

    /** Map a domain model into a persistence entity. */
    private fun TrafficReport.toEntity(): TrafficReportEntity =
        TrafficReportEntity(
            routeId = routeId,
            severity = severity,
            source = source.name,
            timestampMs = timestamp
        )

    /** Map DTO (remote) into local Room entity. */
    private fun TrafficReportDto.toEntity(): TrafficReportEntity =
        TrafficReportEntity(
            routeId = route,
            severity = severity,
            source = "HISTORICAL", // or derive from DTO if available
            timestampMs = updatedAt
        )

    // --- Remote helpers ---

    /** Push a user update to backend (best-effort) after saving locally. */
    private suspend fun pushUserUpdateRemote(update: UserLocationUpdate) = withContext(io) {
        runCatching {
            val dto = update.toDto()
            api.submitUserUpdate(dto)
        }
    }

    /** Map domain user update to network DTO. */
    private fun UserLocationUpdate.toDto(): UserUpdateDto =
        UserUpdateDto(
            userId = userId,
            routeId = routeId,
            lat = lat,
            lng = lng,
            timestampMs = timestamp
        )

    // --- Phase 3: examples for advanced flows (placeholders; safe to keep) ---

    /** Save locally then try to push to backend (skeleton; replaced by submitUserUpdate). */
    suspend fun saveUserUpdateAndPush(lat: Double, lng: Double, routeId: String) {
        // Example placeholder kept for backwards-compatibility with callers; prefer submitUserUpdate.
        val update = UserLocationUpdate(
            id = System.currentTimeMillis().toString(),
            userId = "anonymous",
            lat = lat,
            lng = lng,
            timestamp = System.currentTimeMillis(),
            routeId = routeId
        )
        submitUserUpdate(update)
    }

    /** Fetch remote and upsert into Room; return Unit for background/worker use. */
    suspend fun syncRemoteToLocal() {
        // Delegate to sync(); worker/UI callers don't need a return value.
        sync(city = null)
    }

    /**
     * Conflict resolution and severity aggregation example:
     * - If same route/segment: pick the latest timestamp
     * - Aggregate severity as a simple average between entries
     * Replace [ReportEntity] with your real Room entity if you add segments to schema.
     */
    private fun mergeConflictAware(
        local: List<ReportEntity>,
        remote: List<ReportEntity>
    ): List<ReportEntity> {
        val byKey = LinkedHashMap<String, ReportEntity>()
        // Use actual string interpolation so parameter 'e' is used and the key is meaningful
        fun keyOf(e: ReportEntity) = "${e.routeId}:${e.segmentKey}"

        (local + remote).forEach { e ->
            val k = keyOf(e)
            val existing = byKey[k]
            if (existing == null) {
                byKey[k] = e
            } else {
                val latestTs = maxOf(existing.timestampMs, e.timestampMs)
                val avgSeverity = ((existing.severity + e.severity).toDouble() / 2.0)
                    .toInt().coerceIn(0, 5)
                byKey[k] = existing.copy(
                    severity = avgSeverity,
                    timestampMs = latestTs
                )
            }
        }
        return byKey.values.toList()
    }
}

// Placeholder entity for conflict-resolution example (not used by Room).
data class ReportEntity(
    val routeId: String,
    val segmentKey: String,
    val severity: Int,
    val timestampMs: Long
)

