package com.sirterrific.tubetamer.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeResponseTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun parse(s: String) = json.decodeFromString(TimeResponse.serializer(), s)

    @Test fun dailyBudgetRoundsUp() {
        val t = parse("""{"categories":null,"daily":{"limit_min":60,"remaining_sec":61,"exceeded":false}}""")
        assertEquals(2, t.minutesLeft())
    }

    @Test fun tightestCategoryWins() {
        val t = parse("""{"categories":{"edu":{"limit_min":30,"remaining_sec":1200},"fun":{"limit_min":20,"remaining_sec":300}},"daily":null}""")
        assertEquals(5, t.minutesLeft())
    }

    @Test fun unlimitedIsNull() {
        assertNull(parse("""{"categories":null,"daily":null}""").minutesLeft())
        assertNull(parse("""{"categories":{"fun":{"limit_min":0,"remaining_sec":0}},"daily":null}""").minutesLeft())
    }
}
