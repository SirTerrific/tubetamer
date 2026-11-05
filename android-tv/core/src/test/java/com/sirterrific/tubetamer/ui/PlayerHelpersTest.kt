package com.sirterrific.tubetamer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerHelpersTest {
    @Test fun resumesWhereStopped() = assertEquals(42_000L, resumeMs(42, 600))

    @Test fun startsOverNearTheEnd() = assertEquals(0L, resumeMs(595, 600))

    @Test fun noResume() = assertEquals(0L, resumeMs(0, 600))

    @Test fun unknownDurationStillResumes() = assertEquals(42_000L, resumeMs(42, 0))
}
