package com.phonemood.analysis

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import org.apache.commons.math3.distribution.TDistribution
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.SingularValueDecomposition
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.coroutineContext
import kotlin.math.*

@Serializable
data class ModelResult(val id: String, val outcome: String, val status: String, val sampleIds: List<String>,
    val terms: List<String> = emptyList(), val coefficients: List<Double> = emptyList(), val covariance: List<List<Double>> = emptyList(),
    val degreesOfFreedom: Double? = null, val standardErrorMethod: String? = null, val droppedControls: List<String> = emptyList(),
    val warnings: List<String> = emptyList())
@Serializable
data class BlockRefit(val excludedBlock: String, val difference: Double?, val agrees: Boolean, val status: String)
@Serializable
data class Finding(val id: String, val modelId: String, val kind: String, val appId: String? = null,
    val status: String, val difference: Double? = null, val comparisonValue: Double? = null,
    val n: Int = 0, val groups: Int = 0, val consistency: Double? = null, val ciLow: Double? = null, val ciHigh: Double? = null,
    val p: Double? = null, val q: Double? = null, val templateKey: String, val reasons: List<String> = emptyList(),
    val sensitivityModelId: String? = null, val blockRefits: List<BlockRefit> = emptyList(),
    val comparisonUnit: String = if (kind == "DAILY_USE_TREND") "DAYS" else "MINUTES",
    val contrastOutcome: String = when(kind) { "DAILY_USE_TREND" -> "DAILY_MINUTES_CHANGE"; "SESSION_LENGTH" -> "WITHIN_SESSION_MOOD_CHANGE"; else -> "EXTRA_MOOD_CHANGE" })
@Serializable
data class Statistics(val policyVersion: String = AnalysisPolicy.VERSION, val models: List<ModelResult>,
    val findings: List<Finding>, @kotlinx.serialization.SerialName("top_app_finding_ids") val topAppIds: List<String>)
data class Fit(val result: ModelResult, val focal: Int) {
    val beta get() = result.coefficients[focal]
    val se get() = result.covariance.takeIf { it.isNotEmpty() }?.get(focal)?.get(focal)?.coerceAtLeast(0.0)?.let(::sqrt)
}

/** SVD on independently selected, scaled columns. No inversion of X'X. */
object LinearFit {
    fun fit(id: String, outcome: String, ids: List<String>, x: List<DoubleArray>, y: DoubleArray,
        names: List<String>, focalName: String, weights: DoubleArray = DoubleArray(y.size) { 1.0 },
        clusters: List<String> = ids, absorbedGroups: Int = 0, leverageOffset: DoubleArray = DoubleArray(y.size), inference: Boolean = true): Fit? {
        if (x.isEmpty() || x.size != y.size || y.any { !it.isFinite() } || x.any { row -> row.size != names.size || row.any { !it.isFinite() } }) return null
        require(weights.size == y.size && weights.all { it > 0 && it.isFinite() })
        val n = y.size
        val basis = mutableListOf<DoubleArray>(); val keep = mutableListOf<Int>(); val scales = mutableListOf<Double>()
        for (j in names.indices) {
            val column = DoubleArray(n) { x[it][j] * sqrt(weights[it]) }
            val scale = sqrt(column.sumOf { it * it } / n).takeIf { it > 0 } ?: 1.0
            val v = DoubleArray(n) { column[it] / scale }
            repeat(2) { for (q in basis) { val dot = v.indices.sumOf { v[it] * q[it] }; for (i in v.indices) v[i] -= dot * q[i] } }
            val norm = sqrt(v.sumOf { it * it })
            if (norm > AnalysisPolicy.RANK_TOLERANCE * sqrt(n.toDouble())) {
                basis += DoubleArray(n) { v[it] / norm }; keep += j; scales += scale
            } else if (names[j] == focalName) return null
        }
        val focal = keep.indexOf(names.indexOf(focalName))
        val df = n - keep.size - absorbedGroups
        if (focal < 0 || df < 0 || (inference && df < 1)) return null
        val matrix = Array2DRowRealMatrix(Array(n) { i -> DoubleArray(keep.size) { j -> x[i][keep[j]] * sqrt(weights[i]) / scales[j] } })
        val svd = SingularValueDecomposition(matrix)
        if (svd.conditionNumber > 1e8 || svd.rank != keep.size) return null
        val inverse = svd.solver.inverse
        val params = svd.solver.solve(ArrayRealVector(DoubleArray(n) { y[it] * sqrt(weights[it]) })).toArray()
        val residual = DoubleArray(n) { i -> y[i] * sqrt(weights[i]) - (keep.indices.sumOf { j -> matrix.getEntry(i,j) * params[j] }) }
        val cov = Array(keep.size) { DoubleArray(keep.size) }
        val grouped = clusters.withIndex().groupBy({ it.value }, { it.index })
        val clustered = grouped.size >= 20
        if (inference) {
            if (clustered) {
                for (indices in grouped.values) {
                    val score = DoubleArray(keep.size) { j -> indices.sumOf { i -> inverse.getEntry(j,i) * residual[i] } / scales[j] }
                    for (a in keep.indices) for (b in keep.indices) cov[a][b] += score[a] * score[b]
                }
                val correction = grouped.size.toDouble() / (grouped.size - 1) * (n - 1).toDouble() / df
                for (a in keep.indices) for (b in keep.indices) cov[a][b] *= correction
            } else {
                for (i in 0 until n) {
                    val leverage = keep.indices.sumOf { j -> matrix.getEntry(i,j) * inverse.getEntry(j,i) } + leverageOffset[i]
                    if (leverage >= 1 - 1e-9) return Fit(ModelResult(id,outcome,"OK",ids,keep.map { names[it] },params.mapIndexed { j,v -> v / scales[j] },
                        degreesOfFreedom=df.toDouble(), droppedControls=names.filterIndexed { j,_ -> j !in keep }, warnings=listOf("UNCERTAINTY_UNAVAILABLE_HIGH_LEVERAGE")),focal)
                    val adjusted = residual[i] / (1 - leverage)
                    for (a in keep.indices) for (b in keep.indices) cov[a][b] += inverse.getEntry(a,i) * inverse.getEntry(b,i) * adjusted * adjusted / scales[a] / scales[b]
                }
            }
        }
        return Fit(ModelResult(id,outcome,"OK",ids,keep.map { names[it] },params.mapIndexed { j,v -> v / scales[j] },
            if (inference) cov.map { it.toList() } else emptyList(), if (clustered) (grouped.size - 1).toDouble() else df.toDouble(),
            if (!inference) null else if (clustered) "DAY_CLUSTER_CR1" else "HC3", names.filterIndexed { j,_ -> j !in keep },
            if (inference && !clustered) listOf("HC3_DOES_NOT_ACCOUNT_FOR_SERIAL_DEPENDENCE") else emptyList()),focal)
    }
}

fun quantile(values: List<Double>, p: Double): Double {
    if (values.isEmpty()) return 0.0
    val s = values.sorted(); val at = (s.size - 1) * p; val lo = floor(at).toInt(); val hi = ceil(at).toInt()
    return s[lo] + (s[hi] - s[lo]) * (at - lo)
}

object StatisticalEngine {
    suspend fun analyze(data: PeriodDataset): Statistics {
        val models = mutableListOf<ModelResult>(); val findings = mutableListOf<Finding>()
        fun insufficient(id: String, kind: String, reason: String, n: Int, app: String? = null) {
            models += ModelResult(id,if(kind=="DAILY_USE_TREND") "DAILY_MINUTES" else if(kind=="SESSION_LENGTH") "MOOD_SCORE" else "END_MOOD_SCORE","NOT_ESTIMABLE",emptyList(),warnings=listOf(reason))
            findings += Finding(id,id,kind,app,"INSUFFICIENT_DATA",n=n,templateKey="INSUFFICIENT_DATA",reasons=listOf(reason))
        }
        val daily = data.daily.filter { it.complete && !it.ongoing }
        if (daily.size >= 3) {
            fun fit(ds: List<DayData>, inference: Boolean = true) = LinearFit.fit("daily_trend","DAILY_MINUTES",ds.map { it.date },
                ds.map { doubleArrayOf(1.0,(java.time.LocalDate.parse(it.date).toEpochDay() - java.time.LocalDate.parse(data.daily.first().date).toEpochDay()).toDouble()) },
                ds.map { it.activeMs / 60000.0 }.toDoubleArray(),listOf("intercept","day_index"),"day_index",inference=inference)
            val fit = fit(daily)
            if (fit != null) {
                models += fit.result
                val h = (java.time.LocalDate.parse(daily.last().date).toEpochDay() - java.time.LocalDate.parse(daily.first().date).toEpochDay()).toDouble()
                val threshold = max(15.0,.1 * quantile(daily.map { it.activeMs / 60000.0 },.5))
                val refits = daily.map { day -> blockResult(day.date,fit(daily.filter { it.date != day.date },false),h,fit.beta*h,threshold) }
                val consistency = refits.count { it.agrees }.toDouble()/daily.size
                findings += finding(fit,"DAILY_USE_TREND",null,h,daily.size,consistency,threshold).copy(blockRefits=refits)
            } else insufficient("daily_trend","DAILY_USE_TREND","LOW_EXPOSURE_VARIATION",daily.size)
        } else insufficient("daily_trend","DAILY_USE_TREND","NEED_COMPLETE_DAYS",daily.size)
        coroutineContext.ensureActive()

        val sessionRows = data.rows.filter { it.reasons.isEmpty() && it.sessionComplete }
        if (data.validRatings >= AnalysisPolicy.MIN_RATINGS) {
            val fit = sessionFit(sessionRows)
            if (fit != null) {
                models += fit.result
                val spans = sessionRows.groupBy { it.sessionId }.values.filter { it.size >= 2 }.map { group -> (group.maxOf { it.sessionMs } - group.minOf { it.sessionMs }) / 60000.0 }.filter { it > 0 }
                val h = min(30.0,quantile(spans,.5))
                val contributing = sessionRows.filter { it.id in fit.result.sampleIds }
                val blocks = blocks(contributing.map { it.day },contributing.map { it.sessionId })
                val refits = if (blocks.distinct().size >= 3) blocks.distinct().map { block ->
                    coroutineContext.ensureActive()
                    blockResult(block,sessionFit(contributing.filterIndexed { i,_ -> blocks[i] != block },false),h,fit.beta*h,AnalysisPolicy.NEAR_ZERO)
                } else emptyList()
                val consistency = refits.takeIf { it.isNotEmpty() }?.let { it.count { r -> r.agrees }.toDouble()/it.size }
                findings += finding(fit,"SESSION_LENGTH",null,h,contributing.map { it.sessionId }.distinct().size,consistency,AnalysisPolicy.NEAR_ZERO).copy(blockRefits=refits)
            } else insufficient("session_mood","SESSION_LENGTH","NEED_WITHIN_SESSION_VARIATION",sessionRows.size)
        } else insufficient("session_mood","SESSION_LENGTH","NEED_20_RATINGS",data.validRatings)

        val transitions = data.transitions.filter { it.usable }
        if (data.validRatings < AnalysisPolicy.MIN_RATINGS) insufficient("app_mood","APP_USAGE","NEED_20_RATINGS",data.validRatings)
        else {
            for (app in transitions.flatMap { it.apps.keys }.distinct().sorted()) {
                coroutineContext.ensureActive()
                val exposure = transitions.count { (it.apps[app] ?: 0) >= 60_000 }
                if (exposure < AnalysisPolicy.MIN_APP_EXPOSED) { insufficient("app:$app","APP_USAGE","RARE_APP",transitions.size,app); continue }
                val h = supportedComparison(transitions,app)
                if (h < 2) { insufficient("app:$app","APP_USAGE","NO_SUPPORTED_COMPARISON",transitions.size,app); continue }
                val fit = appFit(transitions,app)
                if (fit == null) { insufficient("app:$app","APP_USAGE","LOW_RESIDUAL_APP_VARIATION",transitions.size,app); continue }
                models += fit.result
                val blocks = blocks(transitions.map { it.day },transitions.map { it.sessionId })
                val refits = if (blocks.distinct().size >= 3) blocks.distinct().map { block ->
                    coroutineContext.ensureActive()
                    blockResult(block,appFit(transitions.filterIndexed { i,_ -> blocks[i] != block },app,inference=false),h,fit.beta*h,AnalysisPolicy.NEAR_ZERO)
                } else emptyList()
                val consistency = refits.takeIf { it.isNotEmpty() }?.let { it.count { r -> r.agrees }.toDouble()/it.size }
                var f = finding(fit,"APP_USAGE",app,h,transitions.map { it.sessionId }.distinct().size,consistency,AnalysisPolicy.NEAR_ZERO).copy(blockRefits=refits)
                // Eligibility is determined solely by input, never by significance.
                if (transitions.size >= 40 && transitions.map { it.day }.distinct().size >= 7 && transitions.map { localHour(it.end,data.facts.zone).toInt()/4 }.distinct().size >= 3) {
                    val sensitive = appFit(transitions,app,zone=data.facts.zone,sensitivity=true)
                    if (sensitive != null) {
                        models += sensitive.result
                        f = f.copy(sensitivityModelId=sensitive.result.id)
                        if (abs(sensitive.beta*h) >= AnalysisPolicy.NEAR_ZERO && sensitive.beta*fit.beta < 0) f = f.copy(status="MIXED_TENDENCY",templateKey="TIME_ADJUSTMENT_SENSITIVE",reasons=f.reasons+"TIME_ADJUSTMENT_SENSITIVE")
                    } else f = f.copy(reasons=f.reasons+"TIME_ADJUSTMENT_NOT_ESTIMABLE")
                }
                findings += f
            }
            if (findings.none { it.kind == "APP_USAGE" }) insufficient("app_mood","APP_USAGE","NEED_COMPARABLE_PAIRS",transitions.size)
        }
        val withQ = applyFdr(findings)
        val top = withQ.filter { it.kind=="APP_USAGE" && it.status in setOf("EARLY_HIGHER","EARLY_LOWER") }
            .sortedWith(compareByDescending<Finding> { it.consistency != null }.thenByDescending { abs(it.difference ?: 0.0) }.thenByDescending { it.n }.thenBy { it.appId }).take(3).map { it.id }
        return Statistics(models=models,findings=withQ,topAppIds=top)
    }

    fun sessionFit(rows: List<MoodRow>, inference: Boolean = true): Fit? {
        val groups = rows.groupBy { it.sessionId }.toSortedMap().values.filter { it.size >= 2 && it.maxOf { r -> r.sessionMs } > it.minOf { r -> r.sessionMs } }
        if (groups.isEmpty()) return null
        val x = mutableListOf<DoubleArray>(); val y = mutableListOf<Double>(); val weights=mutableListOf<Double>(); val leverage=mutableListOf<Double>(); val ids=mutableListOf<String>(); val clusters=mutableListOf<String>()
        for (g in groups) {
            val mx = g.map { it.sessionMs/60000.0 }.average(); val my=g.map { it.score }.average()
            for (r in g.sortedWith(compareBy<MoodRow>{it.at}.thenBy { it.id })) {
                x += doubleArrayOf(r.sessionMs/60000.0-mx); y += r.score-my; weights += 1.0/g.size; leverage += 1.0/g.size; ids+=r.id; clusters+=r.day
            }
        }
        return LinearFit.fit("session_mood","MOOD_SCORE_WITHIN_SESSION",ids,x,y.toDoubleArray(),listOf("session_minutes_within"),"session_minutes_within",weights.toDoubleArray(),clusters,groups.size,leverage.toDoubleArray(),inference)
    }

    fun appFit(rows: List<Transition>, app: String, zone: String = "UTC", sensitivity: Boolean = false, inference: Boolean = true): Fit? {
        if (rows.size < 6) return null
        val names = listOf("intercept","start_mood","phone_minutes","elapsed_minutes") + if(sensitivity) listOf("hour_sin","hour_cos") else emptyList()
        val x = rows.map { r ->
            val phase=2*PI*localHour(r.end,zone)/24
            (listOf(1.0,r.startScore.toDouble(),r.phoneMs/60000.0,r.elapsedMinutes) + if(sensitivity) listOf(sin(phase),cos(phase)) else emptyList()) .plus((r.apps[app] ?: 0)/60000.0).toDoubleArray()
        }
        return LinearFit.fit("app:$app"+if(sensitivity)":time" else "","END_MOOD_SCORE",rows.map { it.id },x,rows.map { it.endScore.toDouble() }.toDoubleArray(),names+"app_minutes","app_minutes",clusters=rows.map { it.day },inference=inference)
    }

    /** Matched input support only, independent of outcomes except matching start mood. */
    fun supportedComparison(rows: List<Transition>, app: String): Double {
        val supported = mutableSetOf<Int>(); val differences=mutableListOf<Double>()
        for (i in rows.indices) for (j in 0 until i) {
            val a=rows[i];val b=rows[j]
            if (abs(a.phoneMs-b.phoneMs)>5*60_000 || abs(a.end-a.start-(b.end-b.start))>10*60_000 || abs(a.startScore-b.startScore)>1) continue
            val delta=abs((a.apps[app] ?: 0)-(b.apps[app] ?: 0))/60000.0
            if (delta >= 2) { supported+=i;supported+=j;differences+=delta }
        }
        return if (supported.size < 2*AnalysisPolicy.MIN_APP_COMPARISONS) 0.0 else min(quantile(differences,.5),30.0)
    }
    private fun blocks(days: List<String>,sessions: List<String>) = if(days.distinct().size>=3) days else sessions
    private fun localHour(at: Long,zone: String): Double { val t=Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)); return t.hour+t.minute/60.0+t.second/3600.0 }
    private fun blockResult(block: String,fit: Fit?,h: Double,original: Double,threshold: Double): BlockRefit {
        val delta=fit?.beta?.times(h)
        return BlockRefit(block,delta,delta?.let { sameDirection(it,original,threshold) } ?: false,if(fit==null) "NOT_ESTIMABLE" else "OK")
    }
    private fun sameDirection(a: Double,b: Double,threshold: Double) = a*b>0 && abs(a)>=threshold
    private fun finding(fit: Fit,kind: String,app: String?,h: Double,groups: Int,consistency: Double?,threshold: Double): Finding {
        val delta=fit.beta*h
        val status=when { abs(delta)<threshold -> "NO_NOTICEABLE_TENDENCY"; consistency!=null && consistency<.8 -> "MIXED_TENDENCY"; delta>0 -> "EARLY_HIGHER"; else -> "EARLY_LOWER" }
        val se=fit.se?.times(abs(h)); val dist=fit.result.degreesOfFreedom?.let { TDistribution(it) }
        val radius=if(se!=null && dist!=null) se*dist.inverseCumulativeProbability(.975) else null
        val p=if(se!=null && dist!=null && se>1e-12) (2*dist.cumulativeProbability(-abs(delta/se))).coerceIn(0.0,1.0) else null
        return Finding(fit.result.id,fit.result.id,kind,app,status,delta,h,fit.result.sampleIds.size,groups,consistency,
            radius?.let { delta-it },radius?.let { delta+it },p,templateKey=kind+"_"+status,
            reasons=fit.result.warnings + if(consistency==null) listOf("LIMITED_BLOCK_SUPPORT") else emptyList())
    }
    private fun applyFdr(findings: List<Finding>): List<Finding> {
        val eligible=findings.filter { it.kind=="APP_USAGE" && it.p!=null }.sortedWith(compareBy<Finding>{it.p}.thenBy { it.id })
        val q=mutableMapOf<String,Double>();var previous=1.0
        for(i in eligible.indices.reversed()) { previous=min(previous,eligible[i].p!!*eligible.size/(i+1));q[eligible[i].id]=previous }
        return findings.map { it.copy(q=q[it.id]) }
    }
}
