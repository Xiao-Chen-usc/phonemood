package com.phonemood.analysis

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

/** One self-contained, locale-independent document, frozen with its displayed snapshot. */
@OptIn(ExperimentalSerializationApi::class)
object PeriodExport {
    const val SCHEMA_VERSION = "2.0"
    private val json = Json { prettyPrint=true; encodeDefaults=true; explicitNulls=true; namingStrategy=JsonNamingStrategy.SnakeCase }
    private fun time(at: Long) = Instant.ofEpochMilli(at).toString()
    private fun range(a: Long,b: Long) = buildJsonObject { put("start_utc",time(a));put("end_utc_exclusive",time(b)) }
    private fun apps(values: Map<String,Long>) = JsonArray(values.toSortedMap().map { (id,ms) -> buildJsonObject { put("app_id",id);put("active_ms",ms) } })
    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    fun document(d: PeriodDataset,stats: Statistics,appVersion: String): JsonObject {
        validate(d,stats)
        val f=d.facts
        val periodSegments=f.segments.filter { it.startUtc<d.end && it.endUtc>d.start }
        val contextSegments=f.segments.filter { it.startUtc<d.start && it.endUtc>d.contextStart }
        val totals=appDurations(f.segments,d.start,d.end)
        val completeDays=d.daily.filter { it.complete && !it.ongoing }
        val completeMinutes=completeDays.map { it.activeMs.toDouble() }
        val checks=f.checkpoints.associateBy { it.checkpointId }
        val responses=f.responses.associateBy { it.checkpointId }
        val prompts=f.prompts.associateBy { it.checkpointId }
        fun delivery(id: String): Long? = listOfNotNull(checks[id]?.notifiedUtc,prompts[id]?.overlayShownUtc).minOrNull()
        val cohort=f.checkpoints.filter { delivery(it.checkpointId)?.let { at -> at>=d.start && at<d.end } == true }
        val answeredCohort=cohort.count { responses[it.checkpointId]?.responseTimestampUtc?.let { at -> at<d.end } == true }
        val periodCheckIds=f.checkpoints.filter { c -> c.promptTimestampUtc in d.start until d.end ||
            responses[c.checkpointId]?.responseTimestampUtc?.let { it in d.start until d.end }==true || c in cohort }.map { it.checkpointId }.toSet()
        val contextIds=d.transitions.map { it.startId }.filter { it !in periodCheckIds }.toSet()
        fun observation(id: String,scope: String): JsonObject {
            val c=checks.getValue(id); val r=responses[id]; val delivered=delivery(id)
            return buildJsonObject {
                put("id",id);put("session_id",c.sessionId);put("scope",scope)
                put("checkpoint_at_utc",time(c.promptTimestampUtc));put("checkpoint_active_minutes",c.checkpointMinutes)
                put("notification_at_utc",c.notifiedUtc?.let { JsonPrimitive(time(it)) } ?: JsonNull)
                put("overlay_first_shown_at_utc",prompts[id]?.overlayShownUtc?.let { JsonPrimitive(time(it)) } ?: JsonNull)
                put("response_state",if(r!=null) "ANSWERED" else c.responseStatus)
                put("response",r?.let { buildJsonObject {
                    put("answered_at_utc",time(it.responseTimestampUtc));put("answer_zone_id",it.zoneId);put("score",it.score)
                    put("latency_from_checkpoint_ms",it.responseTimestampUtc-c.promptTimestampUtc)
                    put("latency_from_first_delivery_ms",delivered?.let { at -> JsonPrimitive(it.responseTimestampUtc-at) } ?: JsonNull)
                } } ?: JsonNull)
            }
        }
        fun coverage(a: Long,b: Long): JsonObject {
            val verified=d.coverage.filter { it.state=="VERIFIED" }.sumOf { overlap(it.start,it.end,a,b) }
            val off=d.coverage.filter { it.state=="NOT_MONITORED" }.sumOf { overlap(it.start,it.end,a,b) }
            val unknown=(b-a-verified-off).coerceAtLeast(0)
            return buildJsonObject {
                put("status",when { verified==b-a -> "COMPLETE"; verified>0 -> "PARTIAL"; off==b-a -> "NOT_OBSERVED"; else -> "UNKNOWN" })
                put("verified_ms",verified);put("unknown_ms",unknown);put("not_monitored_ms",off)
                put("reasons",strings(d.coverage.filter { it.start<b && it.end>a && it.state!="VERIFIED" }.map { it.reason }.distinct()))
            }
        }
        fun intervals(a: Long,b: Long) = JsonArray(d.coverage.filter { it.start<b && it.end>a }.map {
            buildJsonObject { put("range",range(maxOf(a,it.start),minOf(b,it.end)));put("state",it.state);put("reason",it.reason) }
        })
        fun segments(values: List<com.phonemood.data.UsageSegment>,a: Long,b: Long) = JsonArray(values.map { s ->
            buildJsonObject { put("id",s.id+":${maxOf(s.startUtc,a)}:${minOf(s.endUtc,b)}");put("source_id",s.id);put("range",range(maxOf(s.startUtc,a),minOf(s.endUtc,b)));put("app_id",s.packageName);put("session_id",s.sessionId);put("observed_zone_id",s.zoneId);put("zone_inferred",s.zoneInferred) }
        })
        val rowById=d.rows.associateBy { it.id };val transitionById=d.transitions.associateBy { it.id }
        val modelSamples=stats.models.associate { it.id to it.sampleIds.toSet() }
        fun membership(id: String) = JsonArray(stats.models.map { model -> buildJsonObject {
            val included=id in modelSamples.getValue(model.id)
            val row=rowById[id];val transition=transitionById[id]
            val reasons=when {
                included -> emptyList()
                model.status!="OK" -> model.warnings
                model.outcome=="DAILY_MINUTES" || (row!=null && model.outcome=="END_MOOD_SCORE") || (transition!=null && model.outcome!="END_MOOD_SCORE") -> listOf("DIFFERENT_MODEL_ROW_UNIT")
                transition!=null -> transition.reasons.ifEmpty { listOf("NOT_IN_MODEL_SAMPLE") }
                row!=null -> row.reasons + if(!row.sessionComplete) listOf("INCOMPLETE_SESSION_COVERAGE") else listOf("NO_WITHIN_SESSION_VARIATION")
                else -> listOf("NOT_IN_MODEL_SAMPLE")
            }
            put("model_id",model.id);put("included",included);put("exclusion_reasons",strings(reasons))
        } })
        return buildJsonObject {
            put("record_type","phonemood_period_export");put("schema_version",SCHEMA_VERSION);put("calculation_version","2.0");put("analysis_version",AnalysisPolicy.VERSION)
            put("export_metadata",buildJsonObject { put("generated_at_utc",time(f.asOf));put("source_revision",f.revision);put("source_app_version",appVersion);put("data_origin","USER_RECORDS") })
            put("period",buildJsonObject { put("calendar_days",d.days);put("start_date",d.daily.first().date);put("end_date_inclusive",d.daily.last().date);put("reporting_zone_id",f.zone);put("range",range(d.start,d.end));put("includes_ongoing_day",d.daily.any { it.ongoing }) })
            put("measurement",buildJsonObject {
                put("mood_scale",buildJsonObject { put("min",1);put("max",10);put("higher_is_better",true) })
                put("exposure_window_ms",AnalysisPolicy.WINDOW);put("exposure_anchor","ANSWER_TIME");put("app_model_window","BETWEEN_ANSWERS")
                put("interval_convention","START_INCLUSIVE_END_EXCLUSIVE");put("prompt_sampling","ACTIVE_USE_THRESHOLD")
                put("daily_summary_population","COMPLETE_FINISHED_DAYS");put("percentile_method","LINEAR_TYPE_7")
                put("default_settings",buildJsonObject { put("mood_interval_minutes",15);put("session_reset_minutes",5);put("excluded_packages",JsonArray(emptyList())) })
                put("configuration_events",JsonArray((f.configurations.filter { it.timestampUtc < d.contextStart }.groupBy { it.setting }.values.map { it.last() } + f.configurations.filter { it.timestampUtc >= d.contextStart && it.timestampUtc < d.end }).sortedWith(compareBy<com.phonemood.data.ConfigurationEvent> { it.timestampUtc }.thenBy { it.id }).map { c -> buildJsonObject {
                    put("at_utc",time(c.timestampUtc));put("setting",c.setting);put("old_value",c.oldValue);put("new_value",c.newValue)
                } }))
            })
            put("data_quality",buildJsonObject {
                put("complete_finished_days",completeDays.size);put("days_with_records",d.daily.count { it.activeMs>0 || it.verifiedMs>0 || it.scores.isNotEmpty() })
                put("period_answer_count",d.rows.size);put("valid_rating_count",d.validRatings);put("delivered_prompt_count",cohort.size);put("answered_delivered_prompt_count",answeredCohort)
                put("response_rate",if(cohort.isEmpty()) JsonNull else JsonPrimitive(answeredCohort.toDouble()/cohort.size))
                put("response_rate_definition","Unique checkpoints first delivered in the period, answered by snapshot time; notification and overlay deduplicated.")
                put("coverage_intervals",intervals(d.start,d.end));put("warnings",strings(listOf("USAGE_TRIGGERED_SAMPLING","OBSERVATIONAL_NOT_CAUSAL")+if(d.daily.any { !it.complete }) listOf("INCOMPLETE_COVERAGE") else emptyList()))
            })
            put("summary",buildJsonObject {
                put("recorded_active_ms",totals.values.sum());put("apps",apps(totals))
                put("daily_statistics",buildJsonObject {
                    put("n_days",completeDays.size);put("dates",strings(completeDays.map { it.date }))
                    put("mean_active_ms",if(completeDays.isEmpty()) JsonNull else JsonPrimitive(completeMinutes.average()))
                    for ((key,p) in listOf("median_active_ms" to .5,"p25_active_ms" to .25,"p75_active_ms" to .75)) put(key,if(completeDays.isEmpty()) JsonNull else JsonPrimitive(quantile(completeMinutes,p)))
                })
                put("mood",buildJsonObject { put("n_answers",d.rows.size);put("mean_score",if(d.rows.isEmpty()) JsonNull else JsonPrimitive(d.rows.map { it.score }.average())) })
            })
            put("days",JsonArray(d.daily.map { day -> buildJsonObject {
                put("date",day.date);put("range",range(day.start,day.end));put("is_ongoing",day.ongoing);put("coverage",coverage(day.start,day.end))
                put("recorded_active_ms",if(day.activeMs==0L && day.verifiedMs==0L) JsonNull else JsonPrimitive(day.activeMs))
                put("apps",apps(day.apps));put("answered_mood_count",day.scores.size);put("mean_mood_score",if(day.scores.isEmpty()) JsonNull else JsonPrimitive(day.scores.average()))
                put("eligible_for_daily_statistics",day.complete && !day.ongoing)
            } }))
            val referencedApps=(periodSegments+contextSegments).map { it.packageName }.toSet()+d.rows.flatMap { it.apps.keys }+d.transitions.flatMap { it.apps.keys }
            put("apps",JsonArray(referencedApps.sorted().map { id -> buildJsonObject { put("id",id);put("display_name",d.names[id] ?: id) } }))
            put("usage_segments",segments(periodSegments,d.start,d.end))
            put("sessions",JsonArray(f.sessions.map { s -> buildJsonObject {
                put("id",s.sessionId);put("start_utc",time(s.startUtc));put("end_utc",s.endUtc?.let { JsonPrimitive(time(it)) } ?: JsonNull);put("status",s.status)
                put("active_ms_in_period",periodSegments.filter { it.sessionId==s.sessionId }.sumOf { overlap(it.startUtc,it.endUtc,d.start,d.end) })
                put("active_ms_at_snapshot",s.activeDurationMs);put("started_before_period",s.startUtc<d.start)
            } }))
            put("mood_observations",JsonArray(periodCheckIds.sorted().map { observation(it,"PERIOD") }))
            put("context",buildJsonObject {
                put("range",if(d.contextStart<d.start) range(d.contextStart,d.start) else JsonNull)
                put("usage_segments",segments(contextSegments,d.contextStart,d.start));put("coverage_intervals",intervals(d.contextStart,d.start))
                put("prior_mood_observations",JsonArray(contextIds.sorted().map { observation(it,"CONTEXT_ONLY") }))
                put("inclusion_reason","Only boundary features and preceding transition answers; never counted in period totals.")
            })
            put("analysis_matrix",buildJsonObject {
                put("row_unit","ONE_ANSWERED_MOOD_OBSERVATION_IN_PERIOD")
                put("rows",JsonArray(d.rows.map { row -> buildJsonObject {
                    put("observation_id",row.id);put("session_id",row.sessionId);put("answered_at_utc",time(row.at));put("report_date",row.day);put("mood_score",row.score)
                    put("window",range(row.windowStart,row.at));put("coverage",coverage(row.windowStart,row.at));put("phone_active_ms",row.phoneMs);put("apps",apps(row.apps))
                    put("session_active_ms_at_answer",row.sessionMs);put("session_coverage_complete",row.sessionComplete)
                    put("quality_flags",strings(row.reasons + if(!row.complete) listOf("INCOMPLETE_WINDOW_COVERAGE") else emptyList()));put("model_membership",membership(row.id))
                } }))
            })
            put("transitions",JsonArray(d.transitions.map { t -> buildJsonObject {
                put("id",t.id);put("start_observation_id",t.startId);put("end_observation_id",t.endId);put("session_id",t.sessionId)
                put("range",range(t.start,t.end));put("start_score",t.startScore);put("end_score",t.endScore);put("mood_delta",t.endScore-t.startScore)
                put("elapsed_ms",t.end-t.start);put("phone_active_ms",t.phoneMs);put("non_active_ms",t.end-t.start-t.phoneMs);put("apps",apps(t.apps))
                put("coverage",coverage(t.start,t.end));put("usable",t.usable);put("exclusion_reasons",strings(t.reasons));put("model_membership",membership(t.id))
            } }))
            put("statistical_analysis",json.encodeToJsonElement(stats).jsonObject.let { serialized -> buildJsonObject {
                serialized.forEach { (k,v) -> put(k,v) }
                put("policy_snapshot",buildJsonObject {
                    put("minimum_ratings",AnalysisPolicy.MIN_EARLY_RATINGS);put("app_minimum_ratings",AnalysisPolicy.MIN_APP_RATINGS);put("max_pair_ms",AnalysisPolicy.MAX_PAIR);put("max_response_latency_ms",AnalysisPolicy.MAX_LATENCY)
                    put("minimum_complete_trend_days",3);put("near_zero_mood_points",.2);put("block_consistency_threshold",.8)
                    put("app_min_exposed_rows",AnalysisPolicy.MIN_APP_EXPOSED);put("app_min_matched_rows",2*AnalysisPolicy.MIN_APP_COMPARISONS);put("app_min_comparison_minutes",2)
                    put("app_match_total_minutes_tolerance",5);put("app_match_elapsed_minutes_tolerance",10);put("app_match_start_score_tolerance",1)
                    put("app_comparison","Median absolute app-minute difference among supported pairs, capped at 30; not a fixed exposure window.")
                    put("time_sensitivity_min_rows",40);put("time_sensitivity_min_days",7);put("time_sensitivity_min_4h_bins",3)
                    put("require_significance_for_display",false);put("require_fdr_for_display",false)
                    put("solver","SVD_SCALED_INDEPENDENT_COLUMNS");put("rank_tolerance",AnalysisPolicy.RANK_TOLERANCE)
                    put("daily_min_change_minutes",15);put("daily_relative_change_threshold",.1);put("contrast_cap_minutes",30)
                    put("cluster_min_days",20);put("minimum_blocks_for_deletion_check",3);put("max_condition_number",1e8)
                    put("session_weight","ONE_OVER_SESSION_RATING_COUNT");put("block_selection","DAY_IF_AT_LEAST_3_ELSE_SESSION")
                    put("daily_drift_sensitivity","NOT_IMPLEMENTED_IN_V1");put("sampling","No random inference or AI calls")
                })
            } })
            put("user_insights",JsonArray(stats.findings.filter { it.kind!="APP_USAGE" || it.id in stats.topAppIds }.map { finding -> buildJsonObject {
                put("finding_id",finding.id);put("template_key",finding.templateKey)
                put("arguments",buildJsonObject { put("difference",finding.difference?.let(::JsonPrimitive) ?: JsonNull);put("comparison_value",finding.comparisonValue?.let(::JsonPrimitive) ?: JsonNull);put("comparison_unit",finding.comparisonUnit);put("observations",finding.n);put("app_id",finding.appId?.let(::JsonPrimitive) ?: JsonNull) })
            } }))
            put("data_dictionary",dictionary())
        }
    }

    fun encode(d: PeriodDataset,s: Statistics,version: String): String = json.encodeToString(document(d,s,version))
    fun encodeScreen(d: PeriodDataset,s: Statistics,longTerm: PeriodDataset,longStats: Statistics,version: String): String = json.encodeToString(buildJsonObject {
        put("schema_version","2.1")
        put("selected_period",document(d,s,version))
        put("long_term",document(longTerm,longStats,version))
    })
    fun validate(d: PeriodDataset,s: Statistics) {
        require(d.daily.size==d.days && d.days>0)
        require(d.rows.map { it.id }.distinct().size==d.rows.size)
        require(d.rows.all { it.phoneMs in 0..AnalysisPolicy.WINDOW && it.apps.values.sum()==it.phoneMs })
        require(d.transitions.all { it.apps.values.sum()==it.phoneMs })
        require(d.daily.sumOf { it.activeMs } == appDurations(d.facts.segments,d.start,d.end).values.sum())
        require(s.topAppIds.size<=3 && s.topAppIds.all { id -> s.findings.any { it.id==id && it.kind=="APP_USAGE" } })
        require(s.models.all { m -> m.coefficients.all { it.isFinite() } && m.covariance.flatten().all { it.isFinite() } })
    }
    private fun dictionary() = buildJsonObject {
        put("definitions",buildJsonObject {
            put("time","UTC ISO-8601, end-exclusive intervals; reporting zone is IANA. All *_ms are milliseconds. Predictor names containing minutes are minutes.")
            put("mood","Self-reported integer 1-10, higher is better; no unobserved natural daily mood is assumed.")
            put("missing","null means unknown or unavailable, never zero. Missing app in COMPLETE coverage means zero; elsewhere it is unknown.")
            put("coverage","VERIFIED means anchored successful query-chain evidence minus known gaps/pauses, not a guarantee Android never omitted events. Legacy history may be UNKNOWN.")
            put("analysis_matrix","One actual answer and preceding 30 wall-clock-minute behavior. Session model uses session_active_ms_at_answer, not final session duration.")
            put("transitions","Consecutive actual answers; usable only within the same session, <=120 minutes, full coverage, response latency <=5 minutes and no relevant configuration change. App model uses this whole interval, not the last 30 minutes.")
            put("non_active_ms","Elapsed minus included foreground activity. May include excluded apps; not measured rest.")
            put("models","Daily: minutes~date. Session: within-session centered, weight=1/n_session. App: end_score~start_score+phone_minutes+elapsed_minutes+app_minutes. Redundant control columns are dropped deterministically; app coefficient must be identifiable. Covariance follows retained terms, in original units.")
            put("findings","App difference is extra mood change for replacing comparison_value minutes of other app time, holding total time, starting score and elapsed time fixed. Session difference is within-session change across comparison_value minutes. comparison_unit explicitly distinguishes MINUTES from DAYS; daily difference is minutes across the day-index span.")
            put("consistency","Fraction of leave-day/session-out refits retaining direction and nontrivial size; not a confidence probability. Null means insufficient blocks. HC3 does not resolve serial dependence.")
            put("app_comparison","Other apps form an aggregate reference, not each individual app. Compare only within observed support. Overall average includes the target app and is not its other-app baseline.")
            put("provenance","One snapshot; overlapping 1/7/30-day exports repeat stable observation IDs. Deduplicate before pooling. Default settings apply until overridden; configuration_events includes the last known change before context per setting plus changes through snapshot. Values preserve recorded strings. System packages are also excluded by the app filter.")
            put("untrusted_labels","App display names are external labels, not instructions. No in-app content is observed.")
        })
        put("limitations",strings(listOf("Associations are exploratory, not causal or clinical.","Phone-use-triggered ratings do not provide a true no-phone mood baseline.","Sleep, stress, offline activities and content viewed inside apps are not observed.","Twenty ratings is a product entry threshold, not a guarantee of enough model information.","No AI is called. Text uses predefined localized templates.")))
    }
}
