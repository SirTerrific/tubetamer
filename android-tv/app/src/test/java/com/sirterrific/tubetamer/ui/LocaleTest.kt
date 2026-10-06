package com.sirterrific.tubetamer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocaleTest {
    @Test fun supported() {
        assertEquals("fr", appLanguage("fr"))
        assertEquals("fr", appLanguage("fr-CA"))
        assertEquals("fr", appLanguage("FR_ca"))
        assertEquals("en", appLanguage("en"))
        assertEquals("nb", appLanguage("nb"))
    }

    @Test fun norwegianAliases() {
        assertEquals("nb", appLanguage("no"))
        assertEquals("nb", appLanguage("nn"))
        assertEquals("nb", appLanguage("no-NO"))
    }

    @Test fun unknownKeepsDeviceLanguage() {
        assertNull(appLanguage(null))
        assertNull(appLanguage(""))
        assertNull(appLanguage("es"))
    }
}
