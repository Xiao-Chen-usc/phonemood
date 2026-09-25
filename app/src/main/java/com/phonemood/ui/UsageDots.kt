package com.phonemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonemood.R
import kotlin.math.floor
import kotlin.math.roundToInt

private const val DOT_COUNT = 64
private const val DOTS_PER_SIDE = 8
private const val DAY_MS = 24 * 60 * 60_000L

internal data class UsageDotGroup(val id: String, val durationMs: Long, val dots: Int, val paletteIndex: Int)
internal data class UsageDotModel(val groups: List<UsageDotGroup>, val unusedDots: Int, val totalMs: Long)

/** The fixed square represents 24 hours per day, regardless of the selected date range. */
internal fun usageDotModel(apps: Map<String, Long>, days: Int): UsageDotModel {
    require(days > 0)
    val sorted = apps.filterValues { it > 0 }.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    val top = sorted.take(5).map { it.key to it.value }
    val other = sorted.drop(5).sumOf { it.value }
    val items = (top + if (other > 0) listOf("__other__" to other) else emptyList())
        .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
    val total = sorted.sumOf { it.value }
    val denominator = DAY_MS.toDouble() * days
    val usedDots = (total / denominator * DOT_COUNT).roundToInt().coerceIn(0, DOT_COUNT)
    val shares = items.map { (_, ms) -> ms / maxOf(denominator, total.toDouble()) * DOT_COUNT }
    val counts = shares.map { floor(it).toInt() }.toMutableList()
    // Distribute rounding leftovers by largest fractional share, with stable tie-breaking.
    (0 until (usedDots - counts.sum()).coerceAtLeast(0)).forEach { offset ->
        val order = shares.indices.sortedWith(compareByDescending<Int> { shares[it] - floor(shares[it]) }.thenBy { it })
        if (order.isNotEmpty()) counts[order[offset % order.size]]++
    }
    // Resolve palette collisions in a stable ID order. Distinct visible apps must never
    // look like two separated blocks of the same app when their dots are packed by time.
    val usedColors = mutableSetOf<Int>()
    val paletteById = items.map { it.first }.filter { it != "__other__" }.sorted().associateWith { id ->
        var index = (id.hashCode().toLong() and 0x7fffffffL).rem(8).toInt()
        while (index in usedColors) index = (index + 1) % 8
        usedColors += index
        index
    }
    return UsageDotModel(items.mapIndexed { index, (id, ms) -> UsageDotGroup(id, ms, counts[index], paletteById[id] ?: -1) },
        DOT_COUNT - counts.sum(), total)
}

private val dotColors = listOf(
    Color(0xFF416F57), Color(0xFFBB714F), Color(0xFF526EA0), Color(0xFF9A6693),
    Color(0xFF897332), Color(0xFF4A8990), Color(0xFFB05E69), Color(0xFF6C7950)
)

private fun appDotColor(group: UsageDotGroup): Color = if (group.id == "__other__") Color(0xFF86998A)
else dotColors[group.paletteIndex]

@Composable
internal fun UsageDots(apps: Map<String, Long>, names: Map<String, String>, days: Int, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val model = usageDotModel(apps, days)
    val description = buildString {
        append(context.getString(R.string.usage_dots_accessibility, duration(context, model.totalMs), days))
        model.groups.forEach { group ->
            append("; ")
            append(if (group.id == "__other__") context.getString(R.string.usage_dots_other) else appLabel(names[group.id], group.id))
            append(" ")
            append(duration(context, group.durationMs))
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(context.getString(if (days == 1) R.string.usage_dots_day_title else R.string.usage_dots_average_title),
            style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val square = minOf(112.dp, maxWidth * .40f)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Canvas(Modifier.size(square).semantics { contentDescription = description }) {
                    val step = size.width / DOTS_PER_SIDE
                    val colors = model.groups.flatMap { group -> List(group.dots) { appDotColor(group) } } +
                        List(model.unusedDots) { Line }
                    colors.forEachIndexed { index, color ->
                        drawCircle(color, radius = step * .32f,
                            center = Offset((index % DOTS_PER_SIDE + .5f) * step, (index / DOTS_PER_SIDE + .5f) * step))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    model.groups.forEach { group ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).background(appDotColor(group), CircleShape))
                            Text(buildString {
                                append(if (group.id == "__other__") context.getString(R.string.usage_dots_other) else appLabel(names[group.id], group.id))
                                append(" · ")
                                append(duration(context, group.durationMs))
                            }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Text(context.getString(R.string.usage_dots_note), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
