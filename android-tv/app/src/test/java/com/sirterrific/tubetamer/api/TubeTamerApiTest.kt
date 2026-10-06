package com.sirterrific.tubetamer.api

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class TubeTamerApiTest {
    private lateinit var server: MockWebServer
    private val api = TubeTamerApi(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
    private val base get() = server.url("/").toString()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun reply(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test fun infoParsesAndIgnoresUnknownFields() = runBlocking {
        reply(200, """{"app":"tubetamer","version":"1.3.3","api_version":1,"local_playback":true,"locale":"fr","extra":42}""")
        val r = api.info(base) as ApiResult.Ok
        assertEquals(1, r.value.apiVersion)
        assertTrue(r.value.localPlayback)
        assertEquals("/api/v1/info", server.takeRequest().url.encodedPath)
    }

    @Test fun profilesParse() = runBlocking {
        reply(200, """{"profiles":[{"id":"default","display_name":"Alice","avatar_icon":"","avatar_color":null,"has_pin":true}]}""")
        val p = (api.profiles(base) as ApiResult.Ok).value.profiles.single()
        assertEquals("Alice", p.displayName)
        assertEquals("", p.avatarColor)
        assertTrue(p.hasPin)
    }

    @Test fun loginSendsJsonAndNoAuthHeader() = runBlocking {
        reply(200, """{"token":"tok","expires_at":"2027-01-01","profile":{"id":"default","display_name":"Alice"}}""")
        val r = api.login(base, "default", "1234", "Shield") as ApiResult.Ok
        assertEquals("tok", r.value.token)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertNull(req.headers["Authorization"])
        val body = req.body!!.utf8()
        assertTrue(body.contains("\"profile_id\":\"default\""))
        assertTrue(body.contains("\"device_name\":\"Shield\""))
    }

    @Test fun wrongPinIsHttpErrorWithCode() = runBlocking {
        reply(401, """{"error":"invalid_pin"}""")
        assertEquals(ApiResult.HttpError(401, "invalid_pin"), api.login(base, "default", "0000", "Shield"))
    }

    @Test fun meSendsBearer() = runBlocking {
        reply(200, """{"profile":{"id":"default","display_name":"Alice"}}""")
        api.me(base, "secret")
        assertEquals("Bearer secret", server.takeRequest().headers["Authorization"])
    }

    @Test fun htmlIsBadResponse() = runBlocking {
        reply(200, "<html>router login</html>")
        assertTrue(api.info(base) is ApiResult.BadResponse)
    }

    @Test fun deadServerIsNetworkError() = runBlocking {
        val url = base
        server.close()
        assertTrue(api.info(url) is ApiResult.NetworkError)
    }

    @Test fun keepsSubPathBase() = runBlocking {
        reply(200, """{"profiles":[]}""")
        api.profiles(server.url("/tubetamer/").toString())
        assertEquals("/tubetamer/api/v1/profiles", server.takeRequest().url.encodedPath)
    }

    @Test fun homeParsesRowsAndSendsBearer() = runBlocking {
        reply(200, """{"rows":[{"id":"edu","videos":[{"video_id":"abc12345678","title":"T","duration":61,
            "category":"edu","is_short":false,"progress_seconds":30,"thumbnail":"/thumb/abc12345678"}],
            "total":30,"has_more":true}],"channels":[{"id":"UCx","name":"Sci","video_count":3}],"shorts_enabled":false}""")
        val r = (api.home(base, "secret") as ApiResult.Ok).value
        val row = r.rows.single()
        assertEquals("edu", row.id)
        assertTrue(row.hasMore)
        assertEquals(30, row.videos.single().progressSeconds)
        assertEquals("Sci", r.channels.single().name)
        val req = server.takeRequest()
        assertEquals("Bearer secret", req.headers["Authorization"])
        assertEquals("/api/v1/home", req.url.encodedPath)
    }

    @Test fun catalogBuildsQuery() = runBlocking {
        reply(200, """{"videos":[],"total":0,"has_more":false}""")
        api.catalog(server.url("/tubetamer/").toString(), "t", RowId.ALL, offset = 24, channel = "UC a&b")
        val url = server.takeRequest().url
        assertEquals("/tubetamer/api/v1/catalog", url.encodedPath)
        assertEquals("all", url.queryParameter("row"))
        assertEquals("24", url.queryParameter("offset"))
        assertEquals("UC a&b", url.queryParameter("channel"))
    }

    @Test fun expiredTokenIs401() = runBlocking {
        reply(401, """{"error":"unauthorized"}""")
        val r = api.home(base, "old") as ApiResult.HttpError
        assertEquals(401, r.code)
    }
}
