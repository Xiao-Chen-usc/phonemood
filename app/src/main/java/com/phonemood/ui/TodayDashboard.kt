package com.phonemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonemood.R
import com.phonemood.data.MoodResponse
import com.phonemood.data.UsageSegment
import com.phonemood.report.DayWindow
import java.util.Date

@Composable
fun TodayMetrics(active: Long, ratings: List<MoodResponse>, reminders: Int, started: Boolean) {
    val context=LocalContext.current
    val configuration=LocalConfiguration.current
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricTile(context.getString(R.string.metric_mood),if(ratings.isEmpty()) "—" else String.format(configuration.locales[0],"%.1f / 10",ratings.map { it.score }.average()),Sage,Modifier.weight(1f))
            MetricTile(context.getString(R.string.metric_phone),if(started) duration(context,active) else "—",Peach,Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricTile(context.getString(R.string.metric_recorded),context.getString(R.string.metric_count,ratings.size),Sage,Modifier.weight(1f))
            MetricTile(context.getString(R.string.metric_reminded),context.getString(R.string.metric_count,reminders),Peach,Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String,value: String,color: Color,modifier: Modifier) {
    Surface(modifier=modifier,shape=RoundedCornerShape(20.dp),color=color) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label,color=Muted,style=MaterialTheme.typography.bodyMedium)
            Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable
fun DailyObservation(ratings: List<MoodResponse>, segments: List<UsageSegment>, day: LongRange) {
    val context=LocalContext.current
    val sorted=ratings.sortedBy { it.responseTimestampUtc }
    SoftCard {
        Text(context.getString(R.string.daily_observation),style=MaterialTheme.typography.titleLarge)
        when {
            sorted.isEmpty() -> Text(context.getString(R.string.observation_no_mood),color=Muted)
            sorted.size==1 -> Text(context.getString(R.string.observation_one_mood),color=Muted)
            else -> {
                val first=sorted.first();val last=sorted.last();val delta=last.score-first.score
                Text(context.getString(if(delta==0) R.string.observation_same else if(delta>0) R.string.observation_up else R.string.observation_down,
                    android.text.format.DateFormat.getTimeFormat(context).format(Date(first.responseTimestampUtc)),
                    android.text.format.DateFormat.getTimeFormat(context).format(Date(last.responseTimestampUtc)),kotlin.math.abs(delta)))
                Text(context.getString(R.string.observation_limit),style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
        val top=segments.groupBy { it.packageName }.values.maxByOrNull { group -> group.sumOf { DayWindow.overlap(it.startUtc,it.endUtc,day) } }
        if(top!=null) Text(context.getString(R.string.observation_top_app,top.first().appName.ifBlank { top.first().packageName },duration(context,top.sumOf { DayWindow.overlap(it.startUtc,it.endUtc,day) })),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MoodHistoryChart(ratings: List<MoodResponse>) {
    val context=LocalContext.current
    val points=ratings.sortedBy { it.responseTimestampUtc }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(context.getString(R.string.how_you_ve_been_feeling),style=MaterialTheme.typography.titleLarge)
        if(points.isEmpty()) Text(context.getString(R.string.your_mood_check_ins_will_show_up_here_there_s_no_right_way_to_feel),color=Muted)
        else SoftCard {
            val description=points.joinToString("; ") { "${android.text.format.DateFormat.getTimeFormat(context).format(Date(it.responseTimestampUtc))}: ${it.score}/10" }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("10",style=MaterialTheme.typography.bodySmall,color=Muted);Text(context.getString(R.string.mood_scale_label),style=MaterialTheme.typography.bodySmall,color=Muted) }
            Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription=description }) {
                listOf(0f,.5f,1f).forEach { y -> drawLine(Line,Offset(0f,y*size.height),Offset(size.width,y*size.height),1.dp.toPx()) }
                val start=points.first().responseTimestampUtc;val span=(points.last().responseTimestampUtc-start).coerceAtLeast(1)
                val coords=points.map { Offset(if(points.size==1) size.width/2 else 8.dp.toPx()+(it.responseTimestampUtc-start).toFloat()/span*(size.width-16.dp.toPx()),size.height-6.dp.toPx()-(it.score-1)/9f*(size.height-12.dp.toPx())) }
                coords.zipWithNext().forEach { (a,b) -> drawLine(Forest,a,b,2.dp.toPx(),StrokeCap.Round) }
                coords.forEach { drawCircle(Forest,4.dp.toPx(),it) }
            }
            Text("1",style=MaterialTheme.typography.bodySmall,color=Muted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(android.text.format.DateFormat.getTimeFormat(context).format(Date(points.first().responseTimestampUtc)),style=MaterialTheme.typography.bodySmall,color=Muted)
                if(points.size>1) Text(android.text.format.DateFormat.getTimeFormat(context).format(Date(points.last().responseTimestampUtc)),style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
    }
}
