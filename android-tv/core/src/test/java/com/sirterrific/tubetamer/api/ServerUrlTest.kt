package com.sirterrific.tubetamer.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun addsSchemeAndSlash() = assertEquals("http://192.168.1.10:8080/", ServerUrl.normalize(" 192.168.1.10:8080 "))

    @Test fun keepsHttps() = assertEquals("https://tv.example.org/", ServerUrl.normalize("https://tv.example.org"))

    @Test fun keepsSubPath() = assertEquals("http://nas/tubetamer/", ServerUrl.normalize("nas/tubetamer"))

    @Test fun dropsQueryAndFragment() = assertEquals("http://nas:8080/", ServerUrl.normalize("http://nas:8080/?x=1#y"))

    @Test fun rejectsEmpty() = assertNull(ServerUrl.normalize("   "))

    @Test fun rejectsGarbage() = assertNull(ServerUrl.normalize("http://"))
}
