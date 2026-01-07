package com.example.ceylonqueuebuspulse.data.aggregation

import com.example.ceylonqueuebuspulse.data.remote.firestore.dto.TrafficSampleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficAggregatorTest {

    @Test
    fun aggregate_empty_returnsNeutral() {
        val r = TrafficAggregator.aggregate(samples = emptyList(), nowMs = 1000L)
        assertEquals(0L, r.sampleCount)
        assertEquals(0.0, r.severityAvg, 0.0001)
        assertEquals(null, r.severityP50)
        assertEquals(null, r.severityP90)
    }

    @Test
    fun aggregate_weightedAverage_prefersRecent() {
        val now = 1_000_000L
        val old = TrafficSampleDto(routeId = "r", windowStartMs = 0L, severity = 0.0, reportedAtMs = now - (60 * 60 * 1000L))
        val recent = TrafficSampleDto(routeId = "r", windowStartMs = 0L, severity = 10.0, reportedAtMs = now - 1_000L)

        val r = TrafficAggregator.aggregate(samples = listOf(old, recent), nowMs = now, halfLifeMs = 10 * 60 * 1000L)

        // Recent sample should dominate -> average closer to 10 than to 0
        assertEquals(2L, r.sampleCount)
        assertNotNull(r.severityP50)
        assertNotNull(r.severityP90)
        assertTrue(r.severityAvg > 5.0)
    }

    @Test
    fun aggregate_percentiles_areComputed() {
        val now = 1_000_000L
        val samples = listOf(1.0, 2.0, 3.0, 4.0, 5.0).mapIndexed { i, v ->
            TrafficSampleDto(routeId = "r", windowStartMs = 0L, severity = v, reportedAtMs = now - i)
        }

        val r = TrafficAggregator.aggregate(samples = samples, nowMs = now)
        assertEquals(5L, r.sampleCount)
        assertEquals(3.0, r.severityP50!!, 0.0001)
        assertEquals(5.0, r.severityP90!!, 0.0001)
    }
}
