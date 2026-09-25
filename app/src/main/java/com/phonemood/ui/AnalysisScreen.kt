package com.phonemood.ui

import android.app.Application
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonemood.R
import com.phonemood.analysis.*
import com.phonemood.phoneMood
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

data class AnalysisContent(val data: PeriodDataset, val stats: Statistics, val longTermData: PeriodDataset = data, val longTermStats: Statistics = stats,
    /** The long-term half was carried over from an earlier moment, so it is not export-coherent. */
    val longTermReused: Boolean = false)
data class AnalysisUiState(val days: Int = 7, val endDate: LocalDate? = null, val content: AnalysisContent? = null, val loading: Boolean = false, val error: Boolean = false)

class AnalysisViewModel(application: Application): AndroidViewModel(application) {
    private val app = application.phoneMood
    private val mutable = MutableStateFlow(AnalysisUiState())
    var reportingZone: ZoneId = ZoneId.systemDefault()
        private set
    fun today(): LocalDate = LocalDate.now(reportingZone)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation=0L
    
    private var exportPayload: String? = null
    /** Set by the signals that actually move the cumulative dataset, cleared once it is rebuilt. */
    private var longTermDirty = false
    fun select(days: Int, endDate: LocalDate) {
        require(days in listOf(1,3,7,30) && endDate <= today())
        mutable.value=AnalysisUiState(days=days,endDate=endDate)
        refresh()
    }
    fun refresh(longTerm: Boolean = true) {
        job?.cancel(); val token=++generation; val days=mutable.value.days
        val requestedEnd=mutable.value.endDate
        val reusable=mutable.value.content?.takeIf { !longTerm }
        mutable.value=mutable.value.copy(loading=true,error=false)
        job=viewModelScope.launch {
            try {
                val facts=withContext(Dispatchers.IO) {
                    val state=app.repository.dao.state()
                    if(state?.monitoringEnabled==true && System.currentTimeMillis()-state.lastHeartbeatUtc>15_000) app.repository.poll()
                    reportingZone=ZoneId.of(app.repository.dao.firstStart()?.zoneId ?: ZoneId.systemDefault().id)
                    app.repository.analysisFacts(days,cumulative=true)
                }
                val result=withContext(Dispatchers.Default) {
                    val data=PeriodDatasetBuilder.build(facts,days,requestedEnd ?: Instant.ofEpochMilli(facts.asOf).atZone(ZoneId.of(facts.zone)).toLocalDate().minusDays(1))
                    // The cumulative dataset spans the whole record, and its leave-one-day-out
                    // refits grow with its length. It moves only when an answer or a setting
                    // changes, so the minute tick keeps today's figures current and reuses it.
                    if(reusable!=null) AnalysisContent(data,StatisticalEngine.analyze(data),reusable.longTermData,reusable.longTermStats,longTermReused=true)
                    else { val longTerm = PeriodDatasetBuilder.cumulative(facts)
                        AnalysisContent(data,StatisticalEngine.analyze(data),longTerm,StatisticalEngine.analyze(longTerm)) }
                }
                if(token==generation) {
                    mutable.value=AnalysisUiState(days=days,endDate=LocalDate.parse(result.data.daily.last().date),content=result)
                }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { if(token==generation) mutable.value=mutable.value.copy(loading=false,error=true) }
        }
    }
    fun cancelRefresh() { generation++;job?.cancel();mutable.value=mutable.value.copy(loading=false) }
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun observeWhileVisible() {
        merge(flow { while(currentCoroutineContext().isActive) { emit(false);delay(60_000) } },
            app.repository.dao.watchAnswerCount().distinctUntilChanged().drop(1).map { true },
            app.repository.dao.watchConfigurationTime().distinctUntilChanged().drop(1).map { true })
            // Debounce keeps only the last signal, so a data change arriving next to a tick would
            // otherwise be forgotten. The flag carries it across the window instead.
            .onEach { if(it) longTermDirty=true }
            .debounce(300).collect { refresh(longTerm=longTermDirty.also { longTermDirty=false }) }
    }
    suspend fun prepareExport(content: AnalysisContent): String = withContext(Dispatchers.Default) {
        // On screen a carried-over long-term half is harmless, but the document states when it was
        // observed, and two halves taken at different moments would misreport that. Exporting is a
        // deliberate, rare action, so it pays for the recomputation and both halves share one
        // `facts` again, exactly as they did before the minute tick stopped rebuilding it.
        val longTermData=if(content.longTermReused) PeriodDatasetBuilder.cumulative(content.data.facts) else content.longTermData
        val longTermStats=if(content.longTermReused) StatisticalEngine.analyze(longTermData) else content.longTermStats
        exportPayload=PeriodExport.encodeScreen(content.data,content.stats,longTermData,longTermStats,app.packageManager.getPackageInfo(app.packageName,0).versionName.orEmpty())
        "PhoneMood_${content.data.days}d_${content.data.daily.first().date}_${content.data.daily.last().date}_${content.data.end}.json"
    }
    suspend fun save(uri: Uri) = withContext(Dispatchers.IO) {
        val payload=checkNotNull(exportPayload)
        try { checkNotNull(getApplication<Application>().contentResolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use { it.write(payload) } }
        catch(e: Exception) { runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver,uri) };throw e }
        finally { exportPayload=null }
    }
    fun clearExport() { exportPayload=null }
    suspend fun share(content: AnalysisContent): Uri {
        val name=prepareExport(content)
        return withContext(Dispatchers.IO) {
            val folder=File(getApplication<Application>().cacheDir,"analysis-exports").apply { mkdirs() }
            folder.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>24*60*60_000L }?.forEach { it.delete() }
            val file=File(folder,name)
            try { file.writeText(checkNotNull(exportPayload),Charsets.UTF_8);FileProvider.getUriForFile(getApplication(),"${getApplication<Application>().packageName}.files",file) }
            finally { exportPayload=null }
        }
    }
}

@Composable
fun AnalysisScreen(vm: AnalysisViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var moreDates by rememberSaveable { mutableStateOf(false) }
    var parentEnd by rememberSaveable { mutableStateOf<String?>(null) }
    var parentDays by rememberSaveable { mutableIntStateOf(7) }
    var showMethodPage by rememberSaveable { mutableStateOf(false) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri==null) { vm.clearExport();exporting=false }
        else scope.launch {
            try { vm.save(uri);message=context.getString(R.string.analysis_export_saved) }
            catch(_: Exception) { message=context.getString(R.string.export_failed) }
            finally { exporting=false }
        }
    }
    LaunchedEffect(lifecycle,vm) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { vm.observeWhileVisible() }
            finally { vm.cancelRefresh() }
        }
    }
    LazyColumn(Modifier.widthIn(max=720.dp).fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(context.getString(R.string.analysis_title),style=MaterialTheme.typography.headlineLarge)
                IconButton(onClick={vm.refresh()},enabled=!state.loading) { Icon(Icons.Outlined.Refresh,context.getString(R.string.analysis_refresh)) }
            }
            Text(context.getString(R.string.analysis_intro),color=Muted)
            Spacer(Modifier.height(20.dp))
            Text(context.getString(R.string.analysis_recent),style=MaterialTheme.typography.titleLarge)
            val today=vm.today()
            val end=state.endDate ?: today.minusDays(1)
            val lastSunday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1)
            Column {
                TextButton(onClick={moreDates=!moreDates}) { Text(context.getString(R.string.trends_more_dates)) }
                if(moreDates) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Sage,selectedLabelColor=Forest),selected=state.days==1 && end==today.minusDays(1),onClick={vm.select(1,today.minusDays(1))},label={Text(context.getString(R.string.review_yesterday))})
                    FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Sage,selectedLabelColor=Forest),selected=state.days==7 && end==lastSunday,onClick={vm.select(7,lastSunday)},label={Text(context.getString(R.string.review_last_week))})
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(3,7,30).forEach { days -> FilterChip(colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Sage,selectedLabelColor=Forest),selected=state.days==days && end==today.minusDays(1),onClick={vm.select(days,today.minusDays(1))},label={Text(context.getString(R.string.analysis_period_days,days))}) }
                }
                if(moreDates) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={
                        DatePickerDialog(context,{_,year,month,day -> vm.select(1,LocalDate.of(year,month+1,day))},end.year,end.monthValue-1,end.dayOfMonth).apply {
                            datePicker.maxDate=today.atTime(23,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }.show()
                    }) { Text(context.getString(R.string.review_choose_day)) }
                    TextButton(onClick={vm.select(1,today)}) { Text(context.getString(R.string.review_today)) }
                }
                if(moreDates) Text(context.getString(R.string.review_range_help),style=MaterialTheme.typography.bodySmall,color=Muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={vm.select(state.days,end.minusDays(state.days.toLong()))}) { Text(context.getString(R.string.review_previous)) }
                    TextButton(enabled=end.plusDays(state.days.toLong())<today,onClick={vm.select(state.days,end.plusDays(state.days.toLong()))}) { Text(context.getString(R.string.review_next)) }
                }
            }
        }
        if(parentEnd!=null && state.days==1) item {
            TextButton(onClick={ val date=LocalDate.parse(parentEnd);parentEnd=null;vm.select(parentDays,date) }) { Text(context.getString(R.string.review_back_period)) }
        }
        if(state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth());Text(context.getString(R.string.analysis_updating)) }
        if(state.error) item { Text(context.getString(R.string.analysis_error),color=MaterialTheme.colorScheme.error) }
        message?.let { text -> item { Text(text) } }
        state.content?.let { content ->
            val data=content.data
            item {
                Text(if(data.days==1) data.daily.first().date else "${data.daily.first().date} – ${data.daily.last().date}",style=MaterialTheme.typography.titleMedium)
            }
            item { PeriodMoodSummary(data) }
            val usageDays=data.daily.filter { it.activeMs>0 || it.verifiedMs>0 }
            item {
                SoftCard(Sage) {
                    Text(context.getString(R.string.analysis_daily),style=MaterialTheme.typography.titleLarge)
                    Text(context.getString(R.string.analysis_total,if(usageDays.isEmpty()) "—" else formatAnalysisDuration(usageDays.sumOf { it.activeMs })),style=MaterialTheme.typography.titleMedium)
                    if(usageDays.size>1) {
                        Text(context.getString(R.string.analysis_daily_mean,formatAnalysisDuration(usageDays.map { it.activeMs }.average().toLong())))
                        Text(context.getString(R.string.analysis_typical,formatAnalysisDuration(quantile(usageDays.map { it.activeMs.toDouble() },.25).toLong()),formatAnalysisDuration(quantile(usageDays.map { it.activeMs.toDouble() },.75).toLong())))
                    }
                    val color=Forest
                    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                        val max=usageDays.maxOfOrNull { it.activeMs }?.coerceAtLeast(1) ?: 1L
                        val step=size.width/data.days
                        data.daily.forEachIndexed { i,d -> if(d in usageDays) drawLine(color,
                            Offset(step*(i+.5f),size.height),Offset(step*(i+.5f),size.height*(1-d.activeMs.toFloat()/max)),strokeWidth=(step*.65f).coerceAtMost(28f)) }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(data.daily.first().date,style=MaterialTheme.typography.bodySmall)
                        if(data.days>1) Text(data.daily.last().date,style=MaterialTheme.typography.bodySmall)
                    }
                    var expanded by remember(data.start, data.days) { mutableStateOf(false) }
                    TextButton(onClick={expanded=!expanded}) { Text(context.getString(if(expanded) R.string.analysis_hide_data else R.string.analysis_view_data)) }
                    if(expanded) usageDays.forEach { day ->
                        val hasData=day.activeMs>0 || day.verifiedMs>0
                        TextButton(onClick={ if(state.days>1) { parentEnd=state.endDate.toString();parentDays=state.days };vm.select(1,LocalDate.parse(day.date))}, modifier=Modifier.fillMaxWidth()) {
                            Text(context.getString(R.string.analysis_day_row,day.date,if(hasData) formatAnalysisDuration(day.activeMs) else "—",context.getString(if(day.ongoing) R.string.review_in_progress else if(!hasData) R.string.review_no_records else if(day.complete) R.string.analysis_known else R.string.analysis_partial)))
                        }
                    }
                }
            }
            item {
                val apps = data.daily.flatMap { it.apps.entries }.groupBy({ it.key }, { it.value })
                    .mapValues { (_, durations) -> durations.sum() }
                SoftCard { UsageDots(apps, data.names, data.days) }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(context.getString(R.string.analysis_long_term),style=MaterialTheme.typography.headlineSmall)
                Text(context.getString(R.string.analysis_long_term_basis),style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            val longStats=content.longTermStats
            item {
                val history = content.longTermData
                val apps = history.daily.flatMap { it.apps.entries }.groupBy({ it.key }, { it.value })
                    .mapValues { (_, durations) -> durations.sum() }
                SoftCard {
                    Text(context.getString(R.string.analysis_total, formatAnalysisDuration(history.daily.sumOf { it.activeMs })),
                        style = MaterialTheme.typography.titleMedium)
                    Text(context.getString(R.string.usage_dots_record_span, history.daily.first().date, history.daily.last().date, history.days),
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    UsageDots(apps, history.names, history.days)
                }
            }
            val phoneFinding=longStats.findings.firstOrNull { it.kind=="PHONE_USAGE" && it.status!="INSUFFICIENT_DATA" }
            val clearApps=clearAppFindings(longStats,content.longTermData)
            val preview=if(phoneFinding==null) 3 else 2
            if(phoneFinding==null && clearApps.isEmpty()) item {
                SoftCard(Sage) {
                    Text(context.getString(R.string.analysis_collecting_title),style=MaterialTheme.typography.titleLarge)
                    Text(context.getString(R.string.analysis_collecting_body),color=Muted)
                }
            }
            phoneFinding?.let { finding -> item { LongTermCard(finding) } }
            item {
                var showAllApps by rememberSaveable { mutableStateOf(false) }
                Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    (if(showAllApps) clearApps else clearApps.take(preview)).forEach { finding ->
                        // n counts every transition the model saw, not the ones this app appeared in.
                        val exposure=finding.appId?.let { id -> content.longTermData.transitions.count { it.usable && (it.apps[id] ?: 0) >= 60_000 } } ?: 0
                        LongTermCard(finding,appLabel(content.longTermData.names[finding.appId],finding.appId),exposure)
                    }
                    if(clearApps.size>preview) TextButton(onClick={showAllApps=!showAllApps}, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(if(showAllApps) context.getString(R.string.analysis_hide_all_apps) else context.getString(R.string.analysis_show_all_apps,clearApps.size))
                    }
                }
            }
            item {
                TextButton(onClick={showMethodPage=true}, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(context.getString(R.string.analysis_method_link), modifier=Modifier.weight(1f))
                    Text("→")
                }
            }
            item {
                var showLogs by remember(data.start,data.days) { mutableStateOf(false) }
                SoftCard {
                    Text(context.getString(R.string.analysis_logs),style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={showLogs=!showLogs}, modifier=Modifier.fillMaxWidth()) { Text(context.getString(if(showLogs) R.string.analysis_hide_logs else R.string.analysis_view_logs)) }
                    if(showLogs) {
                        Text(context.getString(R.string.review_mood),style=MaterialTheme.typography.titleMedium)
                        data.rows.takeLast(5).forEach {
                            Text("${it.day} ${Instant.ofEpochMilli(it.at).atZone(ZoneId.of(data.facts.zone)).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}  ${it.score}/10")
                        }
                        Text(context.getString(R.string.where_your_time_went),style=MaterialTheme.typography.titleMedium)
                        val apps=data.daily.flatMap { it.apps.entries }.groupBy({it.key},{it.value}).mapValues { it.value.sum() }
                        apps.entries.sortedByDescending { it.value }.take(5).forEach { (id,ms) -> Text("${appLabel(data.names[id],id)} · ${formatAnalysisDuration(ms)}") }
                    }
                }
            }
            item {
                Text(context.getString(R.string.analysis_export_help), style=MaterialTheme.typography.bodySmall, color=Muted)
                Column {
                    Button(enabled=!exporting && !state.loading && !state.error,onClick={scope.launch { exporting=true;try { save.launch(vm.prepareExport(content)) } catch(_: Exception) { exporting=false;message=context.getString(R.string.export_failed) } }}) { Text(context.getString(R.string.analysis_export)) }
                    OutlinedButton(enabled=!exporting && !state.loading && !state.error,onClick={scope.launch {
                        exporting=true
                        try { val uri=vm.share(content);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),context.getString(R.string.analysis_share))) }
                        catch(_: Exception) { message=context.getString(R.string.export_failed) }
                        finally { exporting=false }
                    }}) { Text(context.getString(R.string.analysis_share)) }
                }
            }
        }
    }
    if (showMethodPage) AnalysisMethodPage(onClose={showMethodPage=false})
}

@Composable
private fun AnalysisMethodPage(onClose: () -> Unit) {
    val context=LocalContext.current
    Surface(color=Cream, modifier=Modifier.fillMaxSize()) {
        LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=16.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick=onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,context.getString(R.string.analysis_method_back)) }
                    Text(context.getString(R.string.analysis_method_title),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,context.getString(R.string.analysis_method_close)) }
                }
            }
            item { Text(context.getString(R.string.analysis_method_intro),style=MaterialTheme.typography.bodyLarge,color=Muted) }
            item { MethodSection(context.getString(R.string.analysis_method_data_title),context.getString(R.string.analysis_method_data_body)) }
            item { MethodSection(context.getString(R.string.analysis_method_daily_title),context.getString(R.string.analysis_method_daily_body)) }
            item { MethodSection(context.getString(R.string.analysis_method_session_title),context.getString(R.string.analysis_method_session_body)) }
            item { MethodSection(context.getString(R.string.analysis_method_app_title),context.getString(R.string.analysis_method_app_body)) }
            item { MethodSection(context.getString(R.string.analysis_method_threshold_title),context.getString(R.string.analysis_method_threshold_body)) }
            item { MethodSection(context.getString(R.string.analysis_method_change_title),context.getString(R.string.analysis_method_change_body)) }
        }
    }
}

@Composable
private fun MethodSection(title: String, body: String) {
    SoftCard(Sage) { Text(title,style=MaterialTheme.typography.titleLarge);Text(body,style=MaterialTheme.typography.bodyMedium,color=Muted) }
}

@Composable
private fun formatAnalysisDuration(ms: Long): String = LocalContext.current.getString(R.string.analysis_duration,ms/3_600_000,(ms/60_000)%60)

/** Falls back to a readable last segment when the launcher label could not be resolved. */
internal fun appLabel(name: String?,pkg: String?): String {
    val id=pkg.orEmpty()
    val label=name?.takeIf { it.isNotBlank() } ?: id
    if(label!=id || !id.contains('.')) return label
    val part=id.split('.').lastOrNull { it.length>1 } ?: return label
    return part.replaceFirstChar { it.uppercase() }
}

/** Apps with a clear direction, most-used first over the same history the findings came from. */
internal fun clearAppFindings(stats: Statistics,data: PeriodDataset): List<Finding> {
    val usage=data.daily.flatMap { it.apps.entries }.groupBy({ it.key },{ it.value }).mapValues { it.value.sum() }
    return stats.findings.filter { it.kind=="APP_USAGE" && it.status in setOf("EARLY_HIGHER","EARLY_LOWER") }
        .sortedWith(compareByDescending<Finding> { usage[it.appId] ?: 0L }.thenBy { it.appId })
}

/** Every card quotes the same span of minutes so the effects can be read against each other. */
private const val COMPARISON_MINUTES = 15.0
/** Findings with numbers speak for themselves; the rest need their subject named first. */
@Composable
private fun LongTermCard(f: Finding,name: String = "",exposure: Int = 0) {
    val context=LocalContext.current
    val phone=f.kind=="PHONE_USAGE"
    val early=f.status=="EARLY_HIGHER" || f.status=="EARLY_LOWER"
    SoftCard(Sage) {
        if(!early) {
            val headline=when(f.status) {
                "NO_NOTICEABLE_TENDENCY" -> if(phone) R.string.analysis_headline_phone_flat else R.string.analysis_headline_app_flat
                "MIXED_TENDENCY" -> if(phone) R.string.analysis_headline_phone_mixed else R.string.analysis_headline_app_mixed
                else -> if(phone) R.string.analysis_headline_phone_waiting else R.string.analysis_headline_app_waiting
            }
            Text(if(phone) context.getString(headline) else context.getString(headline,name),style=MaterialTheme.typography.titleLarge,color=Ink)
        }
        FindingBody(f,name)
        Text(when {
            !early -> context.getString(R.string.analysis_observation_pending)
            phone -> context.getString(R.string.analysis_early_observation,f.n)
            else -> context.getString(R.string.analysis_early_observation_app,exposure,name)
        },style=MaterialTheme.typography.labelSmall,color=Muted)
    }
}

@Composable
private fun FindingBody(f: Finding,name: String = "") {
    val context=LocalContext.current
    val text=when(f.status) {
        "EARLY_HIGHER","EARLY_LOWER" -> {
            val direction=context.getString(if(f.status=="EARLY_HIGHER") R.string.analysis_higher else R.string.analysis_lower)
            // beta is points per minute, so the effect rescales linearly with the span quoted.
            val support=f.comparisonValue ?: 0.0
            val points=if(support>0) abs(f.difference ?: 0.0)*COMPARISON_MINUTES/support else abs(f.difference ?: 0.0)
            if(f.kind=="PHONE_USAGE") context.getString(R.string.analysis_phone_finding,COMPARISON_MINUTES,direction,points)
            else context.getString(R.string.analysis_app_finding,name,COMPARISON_MINUTES,direction,points)
        }
        "NO_NOTICEABLE_TENDENCY" -> context.getString(R.string.analysis_no_tendency)
        "MIXED_TENDENCY" -> context.getString(if(f.templateKey=="TIME_ADJUSTMENT_SENSITIVE") R.string.analysis_time_sensitive else R.string.analysis_mixed)
        else -> context.getString(when(f.reasons.firstOrNull()) {
            "NEED_APP_RATINGS" -> R.string.analysis_need_ratings
            "RARE_APP","NO_SUPPORTED_COMPARISON" -> R.string.analysis_need_app_support
            else -> R.string.analysis_not_estimable
        })
    }
    Text(text)
}
