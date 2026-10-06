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

    @Test fun playReadyParses() = runBlocking {
        reply(200, """{"status":"ready","video":{"video_id":"abc12345678","title":"T","duration":120},"stream":"/api/stream/abc12345678","subtitles":[{"lang":"en","label":"English","url":"/api/subs/abc12345678/en"}],"resume_seconds":42,"remaining_sec":600}""")
        val p = (api.play(base, "tok", "abc12345678") as ApiResult.Ok).value
        assertEquals("ready", p.status)
        assertEquals(42, p.resumeSeconds)
        assertEquals("en", p.subtitles.single().lang)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/videos/abc12345678/play", req.url.encodedPath)
        assertEquals("Bearer tok", req.headers["Authorization"])
    }

    @Test fun playDownloadingIsOk() = runBlocking {
        reply(202, """{"status":"downloading"}""")
        assertEquals("downloading", (api.play(base, "t", "abc12345678") as ApiResult.Ok).value.status)
    }

    @Test fun playBlockedBodyIsOk() = runBlocking {
        reply(403, """{"error":"time_up","category":"fun","next_start":"8:00","available":[{"category":"edu","remaining_min":12.5}]}""")
        val p = (api.play(base, "t", "abc12345678") as ApiResult.Ok).value
        assertEquals("time_up", p.error)
        assertEquals("8:00", p.nextStart)
        assertEquals("edu", p.available.single().category)
    }

    @Test fun playExpiredTokenStays401() = runBlocking {
        reply(401, """{"error":"invalid_token"}""")
        assertEquals(401, (api.play(base, "t", "abc12345678") as ApiResult.HttpError).code)
    }

    @Test fun heartbeatSendsBody() = runBlocking {
        reply(200, """{"remaining":0,"time_up":true}""")
        val h = (api.heartbeat(base, "t", HeartbeatRequest("abc12345678", 30, 75)) as ApiResult.Ok).value
        assertTrue(h.timeUp)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"position_seconds\":75"))
        assertTrue(body.contains("\"seconds\":30"))
    }

    @Test fun heartbeatOutsideSchedule() = runBlocking {
        reply(403, """{"error":"outside_schedule"}""")
        assertEquals("outside_schedule", (api.heartbeat(base, "t", HeartbeatRequest("abc12345678", 30, 0)) as ApiResult.Ok).value.error)
    }

    @Test fun heartbeatNotWatchingIsHttpError() = runBlocking {
        reply(409, """{"error":"not_watching"}""")
        val r = api.heartbeat(base, "t", HeartbeatRequest("abc12345678", 30, 0)) as ApiResult.HttpError
        assertEquals("not_watching", r.error)
    }

    @Test fun expiredTokenIs401() = runBlocking {
        reply(401, """{"error":"unauthorized"}""")
        val r = api.home(base, "old") as ApiResult.HttpError
        assertEquals(401, r.code)
    }
    @Test fun searchEncodesQueryAndParsesStatus() = runBlocking {
        reply(200, """{"videos":[{"video_id":"abc12345678","title":"Cats","status":"pending"}],"error":""}""")
        val r = (api.search(base, "tok", "chats & chiens") as ApiResult.Ok).value
        assertEquals("pending", r.videos.single().status)
        assertEquals("", r.error)
        val req = server.takeRequest()
        assertEquals("/api/v1/search", req.url.encodedPath)
        assertEquals("chats & chiens", req.url.queryParameter("q"))
        assertEquals("Bearer tok", req.headers["Authorization"])
    }

    @Test fun requestVideoSendsIdAndParses() = runBlocking {
        reply(200, """{"status":"approved","video":{"video_id":"abc12345678","title":"Cats"}}""")
        val r = (api.requestVideo(base, "tok", "abc12345678") as ApiResult.Ok).value
        assertEquals("approved", r.status)
        assertEquals("abc12345678", r.video?.videoId)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/requests", req.url.encodedPath)
        assertTrue(req.body!!.utf8().contains("\"video_id\":\"abc12345678\""))
    }

    @Test fun requestFetchFailedIsHttpError() = runBlocking {
        reply(502, """{"error":"fetch_failed"}""")
        val r = api.requestVideo(base, "tok", "abc12345678") as ApiResult.HttpError
        assertEquals(502, r.code)
        assertEquals("fetch_failed", r.error)
    }

    @Test fun requestsParse() = runBlocking {
        reply(200, """{"requests":[{"video_id":"abc12345678","status":"denied","requested_at":"2026-10-06 12:00:00"}]}""")
        val r = (api.requests(base, "tok") as ApiResult.Ok).value.requests.single()
        assertEquals("denied", r.status)
        assertEquals("2026-10-06 12:00:00", r.requestedAt)
    }
}
