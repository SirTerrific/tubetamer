package com.sirterrific.tubetamer.ui

import com.sirterrific.tubetamer.api.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchHelpersTest {
    private val a = VideoCard("aaaaaaaaaaa")
    private val b = VideoCard("bbbbbbbbbbb", status = "pending")

    @Test fun updatesListedStatuses() {
        val out = listOf(a, b).withStatuses(mapOf("aaaaaaaaaaa" to "pending", "bbbbbbbbbbb" to "approved"))
        assertEquals(listOf("pending", "approved"), out.map { it.status })
    }

    @Test fun keepsMissingAndEmpty() {
        val out = listOf(a, b).withStatuses(mapOf("bbbbbbbbbbb" to ""))
        assertSame(a, out[0])
        assertSame(b, out[1])
    }
}
