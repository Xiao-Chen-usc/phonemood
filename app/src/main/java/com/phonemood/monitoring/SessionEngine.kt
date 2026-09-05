package com.phonemood.monitoring

/** Pure, deterministic replay. Inputs and outputs are UTC milliseconds. */
data class Event(
    val at: Long, val type: String, val pkg: String = "", val app: String = pkg,
    val zone: String = "UTC", val interval: Int = 30, val reset: Int = 5,
    val excluded: Boolean = false, val zoneInferred: Boolean = false,
) { val key: String get() = "$at|$type|$pkg" }
data class Segment(val id: String, val start: Long, val end: Long, val pkg: String, val app: String, val sessionId: String, val zone: String, val zoneInferred: Boolean)
data class Session(val id: String, val start: Long, var end: Long?, var active: Long = 0, var status: String = "ACTIVE", var next: Int = 30)
data class Checkpoint(val id: String, val sessionId: String, val minutes: Int, val at: Long, val pkg: String, val zone: String)
data class Reconstruction(val segments: List<Segment>, val sessions: List<Session>, val checkpoints: List<Checkpoint>)

class SessionEngine {
    fun rebuild(input: List<Event>, now: Long): Reconstruction {
        val segments = mutableListOf<Segment>()
        val sessions = mutableListOf<Session>()
        val checkpoints = linkedMapOf<String, Checkpoint>()
        var session: Session? = null
        var foreground: Event? = null
        var interruption: Long? = null
        var cursor = 0L
        var interval = 30
        var reset = 5
        var enabled = false
        fun close(at: Long) {
            session?.apply { end = at; status = "CLOSED" }
            session = null; foreground = null; interruption = null
        }
        fun advance(to: Long) {
            val s = session
            val f = foreground
            if (s != null && f != null && to > cursor) {
                val before = s.active
                val duration = to - cursor
                while (s.next * 60_000L <= before + duration) {
                    val at = cursor + (s.next * 60_000L - before).coerceAtLeast(0)
                    val id = "${s.id}:${s.next}"
                    checkpoints[id] = Checkpoint(id, s.id, s.next, at, f.pkg, f.zone)
                    s.next += interval
                }
                val last = segments.lastOrNull()
                if (last != null && last.end == cursor && last.pkg == f.pkg && last.sessionId == s.id && last.zone == f.zone && last.zoneInferred == f.zoneInferred) {
                    segments[segments.lastIndex] = last.copy(end = to)
                } else segments += Segment("${s.id}:$cursor:${f.pkg}", cursor, to, f.pkg, f.app, s.id, f.zone, f.zoneInferred)
                s.active += duration
            }
            val stopped = interruption
            if (stopped != null && to - stopped >= reset * 60_000L) close(stopped)
            cursor = to
        }
        fun interrupt(at: Long) {
            foreground = null
            if (session != null && interruption == null) {
                interruption = at
                session?.status = "INTERRUPTED"
            }
        }
        val order = mapOf("STOP" to 0, "CONFIG" to 1, "START" to 2, "PAUSE" to 3, "RESUME" to 4, "UNLOCK" to 5, "LOCK" to 6, "SHUTDOWN" to 7)
        input.distinctBy { it.key }.filter { it.at <= now }.sortedWith(compareBy<Event> { it.at }.thenBy { order[it.type] ?: 5 }.thenBy { it.pkg }).forEach { e ->
            advance(e.at)
            when (e.type) {
                "START" -> { enabled = true; interval = e.interval.coerceAtLeast(1); reset = e.reset.coerceAtLeast(1) }
                "STOP" -> { close(e.at); enabled = false }
                "CONFIG" -> {
                    interval = e.interval.coerceAtLeast(1); reset = e.reset.coerceAtLeast(1)
                    session?.let { it.next = ((it.active / 60_000 / interval).toInt() + 1) * interval }
                    // Changing exclusions never credits the previously foreground app indefinitely.
                    interrupt(e.at)
                }
                "RESUME" -> if (enabled) {
                    if (e.excluded) interrupt(e.at)
                    else {
                        if (session == null) {
                            session = Session("${e.at}:${e.pkg}", e.at, null, next = interval)
                            sessions += session!!
                        }
                        session?.status = "ACTIVE"; interruption = null; foreground = e
                    }
                }
                "PAUSE" -> if (foreground?.pkg == e.pkg) interrupt(e.at)
                "LOCK", "SHUTDOWN" -> interrupt(e.at)
            }
        }
        advance(now)
        return Reconstruction(segments, sessions, checkpoints.values.toList())
    }
}
