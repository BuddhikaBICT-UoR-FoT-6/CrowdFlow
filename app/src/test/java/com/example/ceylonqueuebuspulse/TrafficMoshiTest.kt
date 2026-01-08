package com.example.ceylonqueuebuspulse

import com.example.ceylonqueuebuspulse.data.network.model.TrafficReportDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrafficMoshiTest {
    @Test
    fun canParseTrafficReportList() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val type = Types.newParameterizedType(List::class.java, TrafficReportDto::class.java)
        val adapter = moshi.adapter<List<TrafficReportDto>>(type)

        val json = """
            [
              {"id":"1","route":"138","severity":3,"points":10,"updatedAt":1736300000000}
            ]
        """.trimIndent()

        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(1, parsed.size)
        assertEquals("138", parsed[0].route)
        assertEquals(3, parsed[0].severity)
    }
}

