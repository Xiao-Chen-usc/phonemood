package com.phonemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.phonemood.R
import com.phonemood.analysis.*

@Composable
fun PeriodMoodSummary(data: PeriodDataset) {
    val context=LocalContext.current
    val overview=moodOverview(data.daily)
    val ratedDays=data.daily.filter { it.scores.isNotEmpty() }
    SoftCard(Peach) {
        Text(context.getString(R.string.period_mood_title),style=MaterialTheme.typography.titleLarge)
        if(overview.dailyMean==null) Text(context.getString(R.string.period_mood_empty),color=Muted)
        else {
            Text(context.getString(R.string.period_mood_average,overview.dailyMean),style=MaterialTheme.typography.titleMedium)
            Text(context.getString(R.string.period_mood_support,overview.ratedDays,overview.ratings),style=MaterialTheme.typography.bodySmall,color=Muted)
            val dailyMeans=ratedDays.map { it.scores.average() }
            val description=ratedDays.zip(dailyMeans).joinToString("; ") { (day,mean) -> "${day.date}: $mean" }
            Text(context.getString(R.string.mood_scale_label),style=MaterialTheme.typography.bodySmall,color=Muted)
            Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription=description }) {
                listOf(0f,.5f,1f).forEach { y -> drawLine(Line,Offset(0f,y*size.height),Offset(size.width,y*size.height),1.dp.toPx()) }
                val coords=dailyMeans.mapIndexed { i,mean -> mean?.let { Offset(if(dailyMeans.size==1) size.width/2 else 6.dp.toPx()+i.toFloat()/(dailyMeans.size-1)*(size.width-12.dp.toPx()),size.height-6.dp.toPx()-(it.toFloat()-1)/9*(size.height-12.dp.toPx())) } }
                coords.zipWithNext().forEach { (a,b) -> if(a!=null && b!=null) drawLine(Forest,a,b,2.dp.toPx()) }
                coords.filterNotNull().forEach { drawCircle(Forest,4.dp.toPx(),it) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(ratedDays.first().date,style=MaterialTheme.typography.bodySmall);if(ratedDays.size>1) Text(ratedDays.last().date,style=MaterialTheme.typography.bodySmall) }
            if(data.days>1) {
                if(overview.firstHalf!=null && overview.secondHalf!=null) Text(context.getString(R.string.period_mood_early,overview.firstHalf,overview.secondHalf))
            }
        }
    }
}
