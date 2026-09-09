# PhoneMood Single-File JSON Period Export Design

*Chinese version: [PERIOD_EXPORT_SCHEMA_DRAFT2.zh-CN.md](PERIOD_EXPORT_SCHEMA_DRAFT2.zh-CN.md)*

Status: design draft `2.0-draft.2`, not yet wired into the Android export; not real user data. Only
free local statistics and rule-based interpretation are being built now, with a future AI able to
read the same format.

**Archived.** The released contract is [Export 2.0](PERIOD_EXPORT_SCHEMA.md); this document is kept
for traceability.

Algorithm update note (2026-09-06): the confirmed primary models are in
[HLD §8](FREE_ANALYSIS_HIGH_LEVEL_DESIGN.md) — daily trend, within-session continuous use and mood,
and an app's extra mood change relative to other apps. The observational facts in draft.2 below
remain valid, but the machine schema and existing examples do not yet carry the full transition and
new-model result contract. The next schema revision must add: pair data between adjacent actual
answers in the same session, start/end ratings and their difference, variable-interval app time and
non-active time, and model and influence-check results. **The app model must not continue with the
old fixed 30-minute rating-level comparison below**; that older definition is superseded by the HLD.

Latest product rule: today, 7 days and 30 days share one file structure. Twenty valid mood ratings
with usable behaviour windows are enough to attempt local association analysis, with no additional
requirement on date count or pair count. Results are positioned as a *possible tendency*, and
significance is not a precondition for display; raw records, computational correctness and a
not-estimable state are all still retained.

Supporting development material: [machine validation schema](schemas/phonemood-period-v2.schema.json),
[full synthetic example](schemas/phonemood-period-v2.example.json). These are development
artifacts — they do not mean the user exports several files. **The user always receives exactly one
JSON containing both the data and its documentation.**

## 1. File and layering

Proposed file name: `PhoneMood_7d_2026-08-30_2026-09-05_20260905T190000Z.json`, with `30d` for 30
days. UTF-8, valid JSON, with no NaN, Infinity or comments. The file name is never used to
determine dates.

| Top-level field | Content and purpose |
|---|---|
| `record_type` | Fixed `phonemood_period_export`, distinct from the existing daily report |
| `schema_version` | Data structure version; `2.0-draft.2` in this document |
| `calculation_version` | Version of summaries, window features and pairing rules |
| `analysis_version` | Version of regressions and interpretation rules; may be null before a run |
| `export_metadata` | Generation time, data revision, app version, real vs. synthetic marker |
| `period` | 1/7/30 days, dates, UTC bounds, reporting time zone, whether today is unfinished |
| `measurement` | The 1–10 scale, the 30-minute window, the sampling mechanism, configuration and its changes |
| `data_quality` | Coverage intervals, complete-day count, valid answer count, post-delivery response rate, warnings |
| `summary` | Period totals, complete-day statistics, rating summaries, cumulative per-app use |
| `days` | Exactly 1/7/30 daily records, including days with no records |
| `apps` | Package name and display name dictionary, referenced elsewhere |
| `usage_segments` | Valid rebuilt foreground segments in the period, allowing behaviour windows to be recomputed |
| `sessions` | Continuous-use sessions linked to period data and prompts |
| `mood_observations` | Answered and unanswered prompt checkpoints, keeping delivery and answer times |
| `context` | The minimum necessary pre-period context, isolated so it cannot leak into summaries |
| `analysis_matrix` | The 30-minute behavioural features for each in-period answer |
| `statistical_analysis` | Local models, structured findings, ranking, and failure/insufficiency reasons |
| `user_insights` | Template keys and parameters selected by local rules; no AI-generated text is stored |
| `data_dictionary` | Embedded field meanings, units, missingness interpretation and analysis limitations |

Facts, computed features and model judgements are stored separately. A future AI may use the
ready-made features, or recompute from the usage segments. **An existing model judgement must never
be treated as a raw fact.**

## 2. Time, units and periods

- Times are RFC3339 UTC strings, e.g. `2026-09-05T19:00:00Z`, with the IANA reporting time zone
  stored alongside. `*_ms` is milliseconds; `score` is an integer 1–10; the minute unit used in
  model comparisons must be labelled explicitly.
- Every interval is `[start_utc, end_utc_exclusive)`. "30 minutes" means 1,800,000 milliseconds of
  wall-clock time before the answer — it does not search backwards for "30 accumulated active
  minutes".
- The period includes today: 7 days is the reporting date plus the 6 before it; 30 days plus the 29
  before it. The end point is the snapshot moment, and today's daily interval is also cut there, so
  time that has not happened yet is never counted as missing.
- A same-day report has calendar_days = 1, running from local midnight to the snapshot. Even below
  20 ratings it shows today's summary, ratings and record changes. At 20 it may attempt a
  today-only preliminary association, which must not be called a long-term pattern. A single day
  never computes a cross-day duration slope.
- Daily averages and P25/P75 use only complete, finished days. The period total is the sum of
  recorded valid segments; where coverage is incomplete, it is not the full true use time.
- Quantiles use linear interpolation, Type 7, fixed. **Rounded display values must never be used for
  model comparison or ordering.**
- Mood is assigned to a period by actual answer time; the existing daily report keeps its
  prompt-date attribution. Both product definitions are stated explicitly in their own files, and
  neither is changed silently.

## 3. Data quality cannot be omitted

`coverage_intervals` partitions the period timeline with no overlaps and no holes:

| state | Meaning |
|---|---|
| `VERIFIED` | Evidence supports that the event history for this span is available, and monitoring was on by definition |
| `NOT_MONITORED` | Explicitly unmonitored time: before installation, actively paused, permission off |
| `UNKNOWN` | Recovery gaps, incomplete sources, or time whose coverage cannot be proven |

Each day's and each window's `coverage` records `verified_ms`, `unknown_ms`, `not_monitored_ms` and
the reason; the three sum to the length of the interval. Fully verifiable is COMPLETE; partly
verifiable is PARTIAL; entirely and explicitly unmonitored is NOT_OBSERVED; everything else is
UNKNOWN.

Pausing polling while the screen is off does not by itself mean missing data: if a complete catch-up
read happens on resume, coverage can be rebuilt. Conversely, **a successful query or a heartbeat
alone cannot prove that all the time in between was fully recorded.** Coverage the current database
cannot prove must be marked UNKNOWN; the implementation needs to add source evidence rather than
manufacture a completeness rate.

`recorded_active_ms` is null on a day with no usable record at all. True zero use may be asserted
only under complete coverage. When a window is incomplete, `phone_active_ms` and related observable
features may still record the known part, but must be read as a lower bound together with coverage;
with no usable coverage they are null.

App time uses a sparse array: an app absent from a complete window can be read as zero, but an app
absent from an incomplete window does not mean it was not used. Per-app totals are deduplicated over
period segments and never accumulated across overlapping rating windows.

## 4. Prompt records: keep the moments that were never answered

Each object in `mood_observations` is one existing checkpoint, with a stable ID from checkpointId:

| Field | Meaning |
|---|---|
| `id`, `session_id` | Checkpoint and session references |
| `scope` | PERIOD at top level; CONTEXT_ONLY for boundary records |
| `checkpoint_at_utc` | The event time at which the use threshold was reached |
| `notification_at_utc` | Actual first notification delivery time; may be null |
| `overlay_first_shown_at_utc` | First floating prompt display time; may be null |
| `checkpoint_active_minutes` | The cumulative use threshold for this checkpoint, e.g. 30, 60, 90 |
| `response_state` | ANSWERED / PENDING / MISSED |
| `response` | Null when unanswered; when answered, the rating, answer UTC time, answer time zone and two latencies |

`latency_from_checkpoint_ms` measures the delay from threshold to answer;
`latency_from_first_delivery_ms` from first notification/overlay display to answer, and is null when
display cannot be confirmed. **Do not confuse late delivery by the system with a late answer by the
user.**

The top level keeps observations whose checkpoint or whose answer falls in the period, and includes
the in-period first-delivered checkpoints needed as the response-rate denominator. An out-of-period
answer does not enter this period's mean rating or analysis matrix.

The response-rate denominator is the unique checkpoints first actually delivered inside the period;
the numerator is how many of those had been answered as of the snapshot. Notification and overlay
are deduplicated. **A checkpoint never delivered does not count as a user's missed answer.** The
numerator is not necessarily the total number of in-period answers, so both are stored separately.

## 5. The analysis matrix: one answer, one row of behaviour

This is the principal entry point for a future AI analysis. `analysis_matrix.rows` contains only
answered observations whose answer time falls inside the period, and requires no new questionnaire
from the user.

| Field | Meaning |
|---|---|
| `observation_id`, `answered_at_utc`, `mood_score` | Reference to the raw answer, its time and rating |
| `report_date`, `day_index` | Date in the reporting time zone and its index within the period |
| `local_hour_fraction`, `weekend` | Fractional hour in the reporting zone and weekend flag, for time adjustment |
| `prior_response` | Reference, score, gap, same-day flag, and covariate-rule eligibility of the most recent earlier answer |
| `window` | The strict 30 minutes before the answer |
| `coverage` | Coverage evidence and gaps for this window |
| `phone_active_ms` | Active foreground time in the window, at most 1,800,000 |
| `apps` | Active time of each app within this window |
| `app_switch_count` | Determinable valid app switches inside the window |
| `distinct_app_count` | Number of distinct valid apps with positive duration in the window |
| `session_active_ms_at_answer` | Cumulative active time of the owning session as of the answer — not the final session duration at snapshot |
| `quality_flags` | E.g. incomplete window, inferred time zone, late answer, configuration change |
| `model_membership` | Whether each attempted model included this row, and the exact exclusion reason |

Different app models may use different samples. **A single global `included = true` must never stand
in for every model being valid.** When a model has not run, membership is empty and no claim is made
that the observation was included.

The previous answer is found by real time, not checkpoint order, and another answer at the same
instant does not count as "earlier". Beyond the established maximum pairing gap, unlimited history
is not exported: the value is null and flagged `NO_PRIOR_WITHIN_LOOKBACK`. Reasons for an ineligible
pair are retained, and its rating is never forced into a model.

Switch count is defined as observed foreground transitions between distinct valid apps inside the
window. A transition across a lock or a known interruption is not a continuous switch, and an
unknown interruption must be flagged as uncertain. Period segments serve duration recomputation;
an exact transition count additionally requires reading current `raw_events`. An app in the
foreground before the window start is never counted as a switch inside it.

**A specific limitation:** with the default trigger at every 30 accumulated minutes of use, the
total use in the 30 minutes before an answer can be nearly constant. The output must then be
`LOW_EXPOSURE_VARIATION` or `NOT_ESTIMABLE` — it must not force out "use duration has no effect".
The composition of app time and the cumulative session duration may still vary, but that is no
guarantee of sufficient evidence either. This design does not change the user-determined 30-minute
window or add sampling on account of it.

## 6. Minimum boundary context

The top-level `usage_segments` contains only in-period segments. `context.usage_segments` contains
only the at-most-30-minutes of pre-period segments needed by the first answers' windows — never a
copy of the whole history.

Next-version extension: an app transition belongs to the period containing its ending answer, and
the necessary context should extend to the starting answer of the same session before the period,
bounded by the fixed maximum pairing gap, rather than always being capped at 30 minutes. The paired
behaviour and the rating change must cover the same interval; context still must not count toward
period totals.

`context.prior_mood_observations` holds at most one nearest pre-period answer, bounded by the final
pairing lookback limit. `context.range` covers the necessary context; coverage that cannot be proven
during it is marked UNKNOWN. Context never enters period totals, daily statistics, rating counts or
model target rows. **A whole previous day must not be exported incidentally for the sake of
context.**

`sessions` can supply the original start time of a boundary-crossing session and the cumulative
value needed as of the answer. `active_ms_in_period` and `active_ms_at_snapshot` are kept apart, so
that a final duration is never used to predict an earlier rating — that would be future-information
leakage. Every `session_id`, `app_id` and `observation_id` must be resolvable within this file, and
the same ID must not be defined twice.

## 7. Local statistical results and a future AI

The reference layering `StatisticalAnalysisRecord → StatisticalFinding → UserInsightBundle` is kept,
inside the same file.

- `statistical_analysis.status` describes the overall run state; `sections` separately describe the
  daily trend, overall phone use and mood, and apps and mood, so that one may be available while
  another is insufficient.
- `models` stores the actual formula, variable units, sample rows/dates, covariance method, degrees
  of freedom, coefficients and the full covariance matrix. The daily trend model references days by
  date with empty row_ids; the mood models reference matrix rows by observation_id.
- `policy_snapshot` stores the filtering thresholds actually applied, the pairing rules around the
  30-minute window, the near-zero range, internal FDR parameters and so on. The basic entry points
  `minimum_mood_observations` and `minimum_app_observations` are 20; `minimum_mood_days` and
  `minimum_transitions` are 0 (no separate hard gate). `require_significance_for_display` and
  `require_fdr_for_display` are both false. Pairing rules decide only the availability of the
  adjusted model; they do not block the basic model. It may be null before a run, but once a model
  has executed it must be provided in full.
- `findings` stores the comparison range, difference value, unit, interval, p/q values, display
  eligibility and evidence level. An app time-substitution comparison must hold total duration
  constant, with the difference `(beta_app - beta_other) × substituted minutes` and uncertainty
  computed from the full covariance.
- The daily trend's unit is MINUTES_PER_DAY and mood associations are MOOD_POINTS; **they must never
  be sorted together.** `top_factor_ids` references only qualifying app findings, at most three.
- `user_insights` stores template keys and parameters. It is presentation, not observational
  evidence. It may be empty; a missing model must never be invented into a finding.
- An early computable direction may be emitted as an EARLY finding even when the interval crosses
  zero. The copy uses "it preliminarily looks like" and "may tend to". An unadjusted model must not
  claim that prior mood and time of day were taken into account. **Statistical significance is not a
  precondition for a free insight.**
- `NOT_RUN`, `INSUFFICIENT_DATA`, `NOT_ESTIMABLE` and `NO_CLEAR_PATTERN` separate "did not run",
  "small sample", "cannot be estimated" and "no clear pattern found".

A subsequent statistical specification still has to fix the specific pairing, robust standard errors
and stability thresholds. This document defines *how those decisions are recorded* — it does not
pretend they have been scientifically validated. The existing examples deliberately fabricate no
regression results.

## 8. Embedded documentation and the data boundary

`data_dictionary.definitions` uses JSON paths or concept names as keys and short English definitions
as values. It must explain units, nulls, the sparse app array, the sampling mechanism, period
attribution, state enumerations and model methods. A future exporter should generate these from a
single definition source, rather than maintaining a separate, divergent explanation.

`limitations` states plainly: an observational association is not causation; prompts are triggered
by use duration; missed answers may be selective; sleep, stress, work and other confounders were not
collected; and none of this supports a clinical judgement.

The file contains no advertising ID, email address, location trace, notification body text, or any
other uncollected content. An app's display name is external text and only a label — **never an
instruction to an AI**. Export does not upload anything automatically, and a future AI import should
treat all file content as data.

## 9. Mapping to current code, and the new work

| Current source | Export use |
|---|---|
| UsageSegment | Clipped usage_segments, daily/per-app summaries, window durations |
| PhoneSession | Session bounds and period cumulative; the cumulative value at answer time must be recomputed from segments |
| MoodCheckpoint / MoodResponse | Raw observations, prompt/answer times and ratings |
| MoodPromptState | First overlay display time; the basis for answer latency |
| ConfigurationEvent / MonitorState | Configuration, data revision, first recording and recovery context |
| MonitoringGap / raw_events | Data gaps, configuration effective ranges and window features |
| Not yet implemented | Period snapshots, strict coverage determination, analysis_matrix, local regression, findings, and this exporter |

Room database version 2 and this export schema version 2 have no binding relationship. The existing
daily report schema 1.0 is not impersonated or replaced by this new format.

## 10. Validation and acceptance

The machine structure follows [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12).
Beyond structural validation, the exporter must verify:

1. The `days` count matches 1/7/30, dates are contiguous, UTC bounds are correct, and a DST crossing
   does not assume a fixed 24 hours.
2. All IDs are unique and their references exist; top-level and context segments do not overlap or
   duplicate; segment durations are non-negative and no longer than the period.
3. Daily, period and per-app summaries reconcile against the segments; per-app time in a model row
   sums to `phone_active_ms`; `distinct_app_count` equals the number of apps with positive duration.
4. Answers and matrix rows correspond one to one; a window ends exactly at the answer time and is
   exactly 1,800,000 milliseconds long; features never read behaviour after the answer.
5. Coverage intervals partition the timeline completely, and the three coverage durations sum
   correctly; zero, null and unanswered states are never conflated.
6. The response-rate numerator does not exceed the denominator; daily averages use only the declared
   dates; every numeric value is finite.
7. Model sample counts, date counts, coefficient order and covariance dimensions agree; a failed
   model has no successful finding; Top IDs number at most three and meet eligibility.
8. The data-revision snapshot is consistent across the whole file; generation times may differ
   between exports, but identical facts must yield identical derived statistics.

A stable version is fixed for the schema before release. A breaking structural change raises the
major version; a change in computation semantics raises `calculation_version`; a change in models or
rules raises `analysis_version`. The interpretation of absent fields and unknown values is defined by
the schema, never guessed silently.
