package com.phonemood.monitoring

import org.junit.Assert.*
import org.junit.Test

class SessionEngineTest {
    private val engine = SessionEngine()
    private fun min(n: Int) = n * 60_000L
    private fun event(n: Int, type: String, pkg: String = "app", excluded: Boolean = false) = Event(min(n), type, pkg, excluded = excluded)
    private val start = Event(0, "START")
    @Test fun `short locked break does not count toward active time`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME"), event(20, "LOCK"), event(22, "RESUME")), min(32))
        assertEquals(1, result.sessions.size)
        assertEquals(min(30), result.sessions.single().active)
        assertEquals(min(32), result.checkpoints.single().at)
    }
    @Test fun `threshold-length interruption closes session at interruption start`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME"), event(20, "LOCK"), event(25, "RESUME")), min(35))
        assertEquals(2, result.sessions.size)
        assertEquals(min(20), result.sessions.first().end)
        assertTrue(result.checkpoints.isEmpty())
    }
    @Test fun `app switches preserve session and create separate segments`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME", "reddit"), event(12, "RESUME", "chrome"), event(21, "RESUME", "youtube")), min(30))
        assertEquals(1, result.sessions.size); assertEquals(3, result.segments.size)
        assertEquals("youtube", result.checkpoints.single().pkg)
    }
    @Test fun `overlap duplicates and shuffled inputs reconstruct identically`() {
        val events = listOf(start, event(0, "RESUME"), event(12, "RESUME", "other"), event(36, "LOCK"))
        val first = engine.rebuild(events, min(40))
        assertEquals(first, engine.rebuild((events + events).reversed(), min(40)))
    }
    @Test fun `delayed poll catches every boundary at its exact active threshold`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME")), min(94))
        assertEquals(listOf(30, 60, 90), result.checkpoints.map { it.minutes })
        assertEquals(listOf(min(30), min(60), min(90)), result.checkpoints.map { it.at })
    }
    @Test fun `excluded app time does not count`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME"), event(15, "RESUME", "launcher", true), event(17, "RESUME")), min(32))
        assertEquals(min(30), result.sessions.single().active)
        assertEquals(2, result.segments.size)
    }
    @Test fun `pause prevents background events from restarting monitoring`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME"), event(10, "STOP"), event(20, "RESUME")), min(90))
        assertEquals(min(10), result.sessions.single().active); assertTrue(result.checkpoints.isEmpty())
    }
    @Test fun `configuration changes apply prospectively`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME"), Event(min(40), "CONFIG", interval = 60), event(40, "RESUME")), min(61))
        assertEquals(listOf(30, 60), result.checkpoints.map { it.minutes })
    }
    @Test fun `old package pause after new resume does not interrupt new app`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME", "a"), event(10, "RESUME", "b"), Event(min(10) + 1, "PAUSE", "a")), min(30))
        assertEquals(min(30), result.sessions.single().active)
    }
    @Test fun `checkpoint identities survive subsequent polls`() {
        val input = listOf(start, event(0, "RESUME"))
        assertEquals(engine.rebuild(input, min(31)).checkpoints.single(), engine.rebuild(input, min(61)).checkpoints.first())
    }
    @Test fun `shutdown stops active usage`() {
        val result = engine.rebuild(listOf(start, event(0, "RESUME"), event(10, "SHUTDOWN")), min(100))
        assertEquals(min(10), result.sessions.single().active)
    }
    @Test fun `exact threshold produces one checkpoint`() {
        assertEquals(1, engine.rebuild(listOf(start, event(0, "RESUME")), min(30)).checkpoints.size)
    }
}
