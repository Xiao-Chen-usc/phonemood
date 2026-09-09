package com.phonemood.analysis

import com.phonemood.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class AnalysisTest {
    @Test fun cumulativeHistoryDoesNotFollowRecentDateSelection() = runBlocking {
        val facts=fixture()
        val history=PeriodDatasetBuilder.cumulative(facts)
        val ids=history.rows.map { it.id }
        val stats=StatisticalEngine.analyze(history)
        for(days in listOf(1,3,7,30)) {
            val recent=PeriodDatasetBuilder.build(facts,days,java.time.LocalDate.parse("2026-09-04"))
            assertEquals(ids,PeriodDatasetBuilder.cumulative(facts).rows.map { it.id })
            assertEquals(facts.asOf,history.end)
            val export=Json.parseToJsonElement(PeriodExport.encodeScreen(recent,StatisticalEngine.analyze(recent),history,stats,"test")).jsonObject
            assertEquals(PeriodExport.document(history,stats,"test"),export["long_term"])
        }
        assertTrue(history.start<=history.rows.minOf { it.at })
        assertTrue(history.rows.size>PeriodDatasetBuilder.build(facts,3,java.time.LocalDate.parse("2026-09-04")).rows.size)
    }

    @Test fun phoneRegressionRecoversEffectAfterPreviousMoodAndTimeAdjustment() {
        val rows=(0 until 60).map { i ->
            val minutes=(i%3+1)*5L
            val at=(i*193L+40)*60_000
            val previous=MoodRow("p$i","s$i",at-(20+i%7)*60_000,"day${i/4}",7+i%2,0,0,emptyMap(),true,0,true,emptyList())
            val current=previous.copy(id="c$i",at=at,phoneMs=minutes*60_000,score=previous.score-(minutes/5).toInt())
            PhoneSample(previous,current)
        }
        val fit=StatisticalEngine.phoneFit(rows,"UTC")!!
        assertEquals(-.2,fit.beta,1e-10)
        assertTrue(fit.result.terms.containsAll(listOf("previous_mood","hour_sin","hour_cos")))
        assertNull(StatisticalEngine.phoneFit(rows.map { it.copy(current=it.current.copy(phoneMs=10*60_000)) },"UTC"))
    }

    private val minute = 60_000L
    private fun transition(i: Int, start: Int, end: Int, p: Int, g: Int, a: Int) =
        Transition("t$i", "a$i", "b$i", "s${i/3}", i*200*minute, (i*200+g)*minute,
            start,end,"2026-09-${1+i/6}",p*minute,mapOf("x" to a*minute,"other" to (p-a)*minute),emptyList())

    @Test fun appContrastRecoversKnownEffectDespiteConstantTotalTime() {
        val rows=(0 until 36).map { i -> val start=5+i%4;val a=if(i%3==0) 10 else 0
            transition(i,start,start-a/5,30,30,a) }
        val fit=StatisticalEngine.appFit(rows,"x")!!
        assertEquals(-.2,fit.beta,1e-10)
        assertEquals(listOf("phone_minutes","elapsed_minutes"),fit.result.droppedControls)
        assertEquals(10.0,StatisticalEngine.supportedComparison(rows,"x"),1e-10)
    }

    @Test fun targetExplainedEntirelyByTotalTimeIsNotIdentifiable() {
        val rows=(0 until 36).map { i -> transition(i,5+i%4,4+i%4,10+i%5*5,40+i%2*5,10+i%5*5) }
        assertNull(StatisticalEngine.appFit(rows,"x"))
    }

    @Test fun outcomeAndTimeReparameterizationsPreserveAppContrast() {
        val rows=(0 until 48).map { i -> val a=i%3*5;val p=15+i%4*5;val g=p+5+i%5
            transition(i,5+i%4,5+i%4-a/5,p,g,a) }
        val original=StatisticalEngine.appFit(rows,"x")!!
        val fit=LinearFit.fit("equivalent","DELTA",rows.map { it.id },rows.map {
            doubleArrayOf(1.0,it.startScore.toDouble(),it.phoneMs/60000.0,it.elapsedMinutes-it.phoneMs/60000.0,it.apps.getValue("x")/60000.0)
        },rows.map { (it.endScore-it.startScore).toDouble() }.toDoubleArray(),listOf("intercept","start","phone","non_active","app"),"app")!!
        assertEquals(-.2,original.beta,1e-10)
        assertEquals(original.beta,fit.beta,1e-10)
    }

    @Test fun differentSessionMeanMoodDoesNotBecomeWithinSessionSlope() {
        val rows=(0 until 8).flatMap { s -> (0 until 3).map { j ->
            MoodRow("$s:$j","s$s",(s*200+j*10)*minute,"day$s",2+s,0,0,emptyMap(),true,
                (s*20+j*10)*minute,true,emptyList())
        } }
        assertEquals(0.0,StatisticalEngine.sessionFit(rows)!!.beta,1e-12)
        val singleton=rows+MoodRow("single","single",0,"day",10,0,0,emptyMap(),true,900*minute,true,emptyList())
        assertEquals(24,StatisticalEngine.sessionFit(singleton)!!.result.sampleIds.size)
    }

    @Test fun hc3MatchesIndependentInterceptMeanCalculation() {
        val ys=doubleArrayOf(1.0,2.0,4.0,5.0)
        val fit=LinearFit.fit("mean","y",List(4){"$it"},List(4){doubleArrayOf(1.0)},ys,listOf("intercept"),"intercept")!!
        assertEquals(3.0,fit.beta,1e-12)
        assertEquals(10.0/9.0,fit.result.covariance[0][0],1e-12)
    }

    @Test fun dayClusterMeanVarianceMatchesTwentyIndependentDayMeans() {
        val ys=DoubleArray(40) { 1.0+it/2 }
        val fit=LinearFit.fit("mean","y",List(40){"$it"},List(40){doubleArrayOf(1.0)},ys,
            listOf("intercept"),"intercept",clusters=List(40){"day${it/2}"})!!
        assertEquals(10.5,fit.beta,1e-12)
        assertEquals(1.75,fit.result.covariance[0][0],1e-12)
        assertEquals("DAY_CLUSTER_CR1",fit.result.standardErrorMethod)
        assertEquals(19.0,fit.result.degreesOfFreedom!!,0.0)
    }

    @Test fun twentyRatingsCanProduceTodayInsightAndExportsAreDeterministic() = runBlocking {
        val facts=fixture()
        for(days in listOf(1,7,30)) {
            val data=PeriodDatasetBuilder.build(facts,days)
            val stats=StatisticalEngine.analyze(data)
            assertEquals(days,data.daily.size)
            assertTrue(data.validRatings>=20)
            assertTrue(stats.models.any { it.id=="app:x:time" && it.status=="OK" })
            assertTrue(stats.findings.any { it.appId=="x" && it.status=="EARLY_LOWER" })
            val encoded=PeriodExport.encode(data,stats,"1.4.0")
            assertEquals(encoded,PeriodExport.encode(data,StatisticalEngine.analyze(data),"1.4.0"))
            val doc=Json.parseToJsonElement(encoded).jsonObject
            val synthetic=JsonObject(doc+mapOf("export_metadata" to JsonObject(doc.getValue("export_metadata").jsonObject+mapOf("data_origin" to JsonPrimitive("SYNTHETIC_TEST_FIXTURE")))))
            File("build/analysis-fixtures").mkdirs()
            File("build/analysis-fixtures/PhoneMood_${days}d_SYNTHETIC.json").writeText(Json { prettyPrint=true }.encodeToString(JsonObject.serializer(),synthetic))
        }
    }

    @Test fun unknownCoverageDoesNotBecomeZeroUseOrModelEvidence() = runBlocking {
        val data=PeriodDatasetBuilder.build(fixture().copy(evidence=emptyList()),7)
        assertFalse(data.daily.any { it.complete })
        assertFalse(data.rows.any { it.sessionComplete })
        assertFalse(data.transitions.any { it.usable })
        assertTrue(StatisticalEngine.analyze(data).models.all { it.status=="NOT_ESTIMABLE" })
    }

    @Test fun actualAnswerAnchorsThirtyMinutesAndTransitionsUseWholeInterval() {
        val f=fixture();val check=f.checkpoints[1];val answer=f.responses[1]
        val moved=answer.copy(responseTimestampUtc=answer.responseTimestampUtc+4*minute)
        val data=PeriodDatasetBuilder.build(f.copy(responses=f.responses.map { if(it.checkpointId==answer.checkpointId) moved else it }),30)
        val row=data.rows.first { it.id==answer.checkpointId }
        assertEquals(appDurations(f.segments,row.windowStart,row.at),row.apps)
        assertEquals(moved.responseTimestampUtc-30*minute,row.windowStart)
        val transition=data.transitions.first { it.endId==check.checkpointId }
        assertEquals(34.0,transition.elapsedMinutes,0.0)
        assertTrue(transition.usable)
        val late=PeriodDatasetBuilder.build(f.copy(responses=f.responses.map { if(it.checkpointId==answer.checkpointId) answer.copy(responseTimestampUtc=answer.responseTimestampUtc+6*minute) else it }),30)
        assertTrue(late.rows.first { it.id==answer.checkpointId }.reasons.contains("LATE_RESPONSE"))
        assertTrue(data.transitions.any { "DIFFERENT_SESSION" in it.reasons })
    }

    @Test fun daylightSavingDayUsesActualTwentyThreeHours() {
        val f=fixture().copy(asOf=Instant.parse("2026-03-09T19:00:00Z").toEpochMilli(),zone="America/Los_Angeles")
        val data=PeriodDatasetBuilder.build(f,7)
        val spring=data.daily.first { it.date=="2026-03-08" }
        assertEquals(23*60*minute,spring.end-spring.start)
    }

    @Test fun noPriorAnswerDoesNotCreateBaseline() {
        val f=fixture(); val data=PeriodDatasetBuilder.build(f.copy(responses=f.responses.take(1)),30)
        assertEquals(1,data.rows.size)
        assertTrue(data.transitions.isEmpty())
    }

    @Test fun threeCompleteDaysCanBeCheckedWithoutRequiringResidualDfInDeletedFit() = runBlocking {
        val d=PeriodDatasetBuilder.build(fixture(),7)
        val daily=d.daily.take(3).mapIndexed { i,it -> it.copy(activeMs=(60+i*30)*minute,complete=true,ongoing=false) }
        val stats=StatisticalEngine.analyze(d.copy(daily=daily,rows=emptyList(),transitions=emptyList()))
        val f=stats.findings.first { it.kind=="DAILY_USE_TREND" }
        assertEquals("EARLY_HIGHER",f.status)
        assertEquals(60.0,f.difference!!,1e-10)
        assertEquals("DAYS",f.comparisonUnit)
    }

    @Test fun historicalDayExcludesLaterAnswersAndEndsAtMidnight() = runBlocking {
        val facts=fixture()
        val last=java.time.LocalDate.parse("2026-09-04")
        val data=PeriodDatasetBuilder.build(facts,1,last)
        val midnight=Instant.parse("2026-09-05T00:00:00Z").toEpochMilli()
        assertEquals(midnight,data.end)
        assertEquals("2026-09-04",data.daily.single().date)
        assertFalse(data.daily.single().ongoing)
        assertTrue(data.rows.all { it.at>=data.start && it.at<midnight })
        assertTrue(data.transitions.all { it.end<midnight })
        val doc=PeriodExport.document(data,StatisticalEngine.analyze(data),"test")
        assertFalse(doc.getValue("period").jsonObject.getValue("includes_ongoing_day").jsonPrimitive.boolean)
        assertEquals(Instant.ofEpochMilli(facts.asOf).toString(),doc.getValue("export_metadata").jsonObject.getValue("generated_at_utc").jsonPrimitive.content)
    }

    @Test fun historicalWeekHasSevenFinishedDatesAndNoToday() {
        val data=PeriodDatasetBuilder.build(fixture(),7,java.time.LocalDate.parse("2026-08-30"))
        assertEquals("2026-08-24",data.daily.first().date)
        assertEquals("2026-08-30",data.daily.last().date)
        assertEquals(7,data.daily.size)
        assertTrue(data.daily.none { it.ongoing })
        assertEquals(data.daily.sumOf { it.activeMs },appDurations(data.facts.segments,data.start,data.end).values.sum())
    }

    @Test fun historicalDstDayStillUsesTwentyThreeHours() {
        val f=fixture().copy(asOf=Instant.parse("2026-03-10T19:00:00Z").toEpochMilli(),zone="America/Los_Angeles")
        val data=PeriodDatasetBuilder.build(f,1,java.time.LocalDate.parse("2026-03-08"))
        assertEquals(23*60*minute,data.end-data.start)
        assertFalse(data.daily.single().ongoing)
    }

    @Test(expected=IllegalArgumentException::class) fun futureDateIsRejected() {
        PeriodDatasetBuilder.build(fixture(),1,java.time.LocalDate.parse("2027-01-01"))
    }

    private fun fixture(): Facts {
        val start=Instant.parse("2026-08-08T00:00:00Z").toEpochMilli()
        val end=start+29*24*60*minute+23*60*minute
        val segments=mutableListOf<UsageSegment>();val sessions=mutableListOf<PhoneSession>()
        val checks=mutableListOf<MoodCheckpoint>();val answers=mutableListOf<MoodResponse>()
        for(day in 0 until 30) for(s in 0 until 4) {
            val id="$day:$s";val at=start+(day*24+s*6)*60*minute
            sessions+=PhoneSession(id,at,at+180*minute,180*minute,"CLOSED",210)
            var mood=8
            for(j in 0 until 6) {
                val a=at+j*30*minute;val b=a+30*minute
                val x=if(j>0 && (day+s+j)%3==0) 15*minute else 0
                if(x>0) { segments+=UsageSegment("$id:$j:x",a,a+x,"x","Example X",id,"UTC",false);mood-- }
                segments+=UsageSegment("$id:$j:o",a+x,b,"other","Other app",id,"UTC",false)
                checks+=MoodCheckpoint("$id:$j",id,(j+1)*30,b,"other","UTC","ANSWERED",b)
                answers+=MoodResponse("$id:$j",b,mood,"UTC")
            }
        }
        return Facts(end,"UTC",42,start,segments,sessions,checks,answers,emptyList(),emptyList(),emptyList(),listOf(UsageCoverageEvidence(start,end,end)))
    }
}
