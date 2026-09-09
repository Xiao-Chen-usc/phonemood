# PhoneMood Free Tier User Stories: Rolling Statistics, Data Export and Local Analysis

*Chinese version: [FREE_TIER_USER_STORIES.zh-CN.md](FREE_TIER_USER_STORIES.zh-CN.md)*

Status: free local analysis shipped in Android 1.4.0 and available for acceptance.
Corresponding implementation design: [Free Local Analysis High-Level Design](FREE_ANALYSIS_HIGH_LEVEL_DESIGN.md).
Date: 2026-09-05
Scope: only the free tier ships now; a future paid AI analysis has its boundary reserved, nothing more.
Product positioning: a data-insight experience in the spirit of Google Health. The free tier is
rule-based analysis; a future membership tier would offer AI-based analysis. **Only the free tier
is being built here.**
Free analysis approach: local descriptive statistics and basic regression, plus fixed
interpretation rules, plus localized English and Chinese templates.

## 1. Product goals and tier boundary

Product principle (as most recently adjusted): the free tier surfaces a *possibly useful
tendency*; research-grade significance is not a precondition for display. Twenty valid mood
ratings inside the selected period are enough to attempt local association analysis — there is no
additional requirement of seven valid dates, thirty ratings, or twenty before/after pairs. Twenty
is a product start threshold, **not a guarantee of accuracy**. The same-day report always gives
that day's records and changes; at 7 and 30 days, an "it preliminarily looks like…" reading is
offered whenever the data can be computed.

From the phone-use records and mood ratings it has already collected, PhoneMood completes its
statistics locally and answers three questions for the user:

1. How long has the phone been used lately — daily average, typical range, and which way the
   trend runs.
2. Within a single session, how mood ratings tend to move as continuous use lengthens.
3. Whether a particular app corresponds to an extra mood change relative to other apps, and how
   large that difference is.

The user is the *recipient* of the analysis. They do not have to answer these three questions,
fill in a new questionnaire, re-enter historical data, or pose their own query to get results.
The analysis uses the usage events, sessions, mood ratings and data-quality records the app
already holds; the existing day-to-day mood collection flow is unchanged. Opening the page, or
switching between 7 and 30 days, is enough. When data is insufficient, the app says so — it does
not ask the user to answer more on the spot to make up the sample.

"Impact" is kept as everyday phrasing in the user's question, but in statistical fields and result
copy it is expressed uniformly as **association**. Observational regression cannot establish
causation.

| Capability | This free tier | Future paid tier |
|---|---|---|
| Rolling 7- and 30-day data and summaries | Fully provided | Retained |
| Export of raw analysis data and statistical results | Fully provided; the user may hand it to an external AI | Retained |
| Local analysis of use trend, use-duration vs. mood, app vs. mood | Fully provided, no network, no AI call | Retained |
| Fixed-rule English and Chinese explanations | Fully provided | Retained |
| In-app AI deep explanation and personalized conversation | Not implemented; no subscription required | Designed separately in future |

A free export does **not** mean the app sends data to an external AI on its own. The user decides
whether to share the exported file. A future charge for in-app AI will not restrict a free user's
ability to obtain their own data.

### Reference material and the boundary on generated results

The experience takes its cue from Google Health's approach of turning existing records into
readable insights, with an AI Coach for members. Reference:
[Google Health's official introduction](https://blog.google/products-and-platforms/products/google-health/google-health-app/).
What is borrowed is the data overview, trend reading and drill-down experience. PhoneMood's own
free/member boundary is defined by this document, and no assumption is made that Google's free
features are all rule-computed.

This document draws on two design files, taking their "statistical output → rule interpretation →
user reads the result" chain as the basis for free analysis:

- Statistical analysis design: local regression, sample eligibility, actual rating differences,
  fixed templates and localization.
- Statistical data structures: `StatisticalAnalysisRecord`, `StatisticalFinding`,
  `UserInsightBundle` and fixed ranking.

```text
Existing Room usage records and mood ratings
→ Rolling 7/30-day PeriodRecord / AnalysisMatrix
→ Local basic regression
→ StatisticalAnalysisRecord / StatisticalFinding
→ Fixed rules generate UserInsightBundle
→ Android English/Chinese templates present the answers
```

The page presents, in order: "Phone use time and trend", "Phone use and mood", "Apps and mood".
Daily duration, daily average and typical range are descriptive statistics; the trend and the mood
associations come from local regression; the explanation comes from rules selecting a fixed
template. The page does not present the three analysis topics as questions awaiting the user's
answer, and it does not put an AI input box in the path to free analysis.

The original files' default of 30 days is extended here into separate 7-day and 30-day views. What
the user sees is computed by the system; **export is an additional capability, not a prerequisite
for seeing the analysis.**

## 2. Unified time and data definitions

These are the default product rules proposed here, so that pages, exports and models never use
different data.

- The page offers "last 7 days" and "last 30 days", defaulting to 7 and remembering the last
  choice.
- The same-day report gives today's summary, ratings and changes up to the current moment, and can
  equally be exported as a single JSON file. If today also reaches 20 valid ratings, a preliminary
  association may be shown, explicitly labelled "based on today only". It is neither forbidden nor
  read as a long-term pattern.
- Rolling means rolling calendar days in the reporting time zone, including today up to the moment
  of generation. It is not a fixed calendar week or month, and not exactly the past 168 or 720
  hours.
- Viewed on 2026-09-05, for example: 7 days is 08-30 through 09-05, and 30 days is 08-07 through
  09-05. Both endpoints are inclusive; the underlying interval is half-open.
- The existing reporting-time-zone rule carries over: the zone is fixed when recording first
  begins. Each event and answer keeps its original time and zone. Calendar-day length is computed
  in that zone; no day is assumed to be 24 hours.
- A period always has exactly 7 or 30 daily entries, including days with no records. **"No record"
  is not the same as "confirmed zero use."**
- Today is marked as in progress. Pre-installation, paused, missing-permission and monitoring-gap
  states are marked explicitly. Missing data must never be filled with zero and fed into the daily
  average or the regression.
- The page shows the period length, the number of complete record days, the number of days with
  records, the number of valid ratings, and the update time. The actual sample size of each
  statistic is displayed or exported separately.
- Phone use duration follows the current definition of active foreground use, excluding lock,
  screen-off and excluded apps. Segments crossing a day or period boundary are clipped first.
- Daily average, typical range and daily trend use complete record days within the period by
  default. Today's recorded duration is displayed separately — half a day is never compared
  directly against a full one to judge an increase or decrease.
- The default prompt fires after 30 minutes of continuous active phone use. Every answer keeps the
  descriptive features of the preceding 30 minutes, along with the prompt time, the actual
  notification time and the answer delay. The data range of each primary model is defined
  separately, by within-session observation and by adjacent-answer interval.
- The 30-minute pre-answer features are retained for export, superseding the reference design's
  suggestion of 60 minutes. The current primary algorithms are, per the HLD: daily trend,
  within-session regression, and per-app extra change. **The app model uses the behaviour between
  two adjacent actual answers in the same session — not a fixed 30 minutes.**
- An answer inside the period belongs to that period's analysis sample. Context before the period
  start may be read in order to construct a window and a previous mood, but that context must not
  count toward period totals, daily summaries or the valid-answer count, and it is marked as such
  in the export.
- The existing daily-report rule of attributing an answer to its prompt date is not silently
  changed; the period analysis records its own answer-time definition separately.

## 3. User stories and acceptance criteria

### US-00 | An analysis overview that reads clearly on opening

As a user, I want to open PhoneMood and see the trends and associations the app has found in my
existing records — the way a health app's analysis overview works — so that I understand my own
use habits and mood patterns.

Acceptance criteria:

1. The top of the page offers a rolling 7/30-day switch and the record coverage. The body
   organizes insight cards under "Use time and trend", "Phone use and mood", and "Apps and mood".
2. Each card leads with one sentence of core finding, then the key numbers and a simple chart,
   then the strength of data support and a "see the basis" entry point. **The user must not have
   to read a statistics table to get an answer.**
3. "See the basis" shows the date range, the number of valid records, the behavioural range being
   compared, and a plain description of the method. The underlying model values are provided via
   export.
4. Card explanations come from fixed rules selecting a template and filling in locally computed
   values — for example, "daily use time is trending upward in this period", or "no clear
   association between phone use duration and mood has been found yet". A template may appear only
   when its rule is satisfied.
5. Rules choose direction, difference size, support level, ordering and template *from statistical
   evidence*. They never substitute uncomputed intuition for the regression, and never generate
   free text.
6. The page delivers both a readable conclusion and inspectable data. Delivering only a chart, a
   CSV or a regression table does not count as completing the analysis experience.
7. When there is no reliable finding, the card explains why and keeps the existing data. It does
   not manufacture a positive or negative conclusion to fill the page.
8. This page provides the free analysis in full, with no membership purchase, AI chat, or
   paywalled card. A future member AI may offer deeper explanation and conversation on the same
   data; that is designed separately.

### US-01 | Switch between rolling 7 and 30 days

As a user, I want to view the last 7 or 30 days so that I can see recent change and longer-run
habits separately.

Acceptance criteria:

1. After switching, the charts, summaries, analysis results and export all use the same period
   snapshot.
2. Date boundaries, the unfinished state of today, and the update time are visible.
3. After crossing midnight in the reporting time zone, the next open or refresh rolls forward by a
   day rather than continuing to show the old range.
4. When the app has been installed for less than 7 or 30 days, the selected period is still shown
   with the record-less days marked — never counted as zero use.
5. Time zone and daylight-saving boundary tests pass; no event is double-counted into an adjacent
   day.

### US-02 | See aggregate data for the whole period

As a user, I want PhoneMood to summarize my existing records automatically and show me aggregate
data for the whole 7 or 30 days, so I do not have to organize or compute anything myself.

Acceptance criteria:

1. Shows total recorded use in the period, the mean and median over complete days, the typical
   range P25–P75, and the daily time series.
2. Shows each app's cumulative active use, its share of total recorded use, its session count, and
   the count and mean of its mood ratings.
3. Summaries state their denominator explicitly: which dates the daily average is based on, and
   how many valid answers the mean rating is based on. With no ratings it shows "no ratings yet",
   never a score of 0.
4. Aggregates are computed from the underlying segments and answers — never by taking a simple
   average of daily averages.
5. Total duration equals the sum of valid clipped segments in the period, and the per-app
   summaries reconcile against the total.
6. Descriptive statistics remain viewable when data is insufficient; the whole page is not hidden
   because a regression could not run.

### US-03 | Free export of data an external AI can analyse

As a user, I want to export a complete period in one action so that I can hand it to an external
AI, statistical software, or a researcher.

Acceptance criteria:

1. Both 7 and 30 days export a complete period free, in one action: a single `.json` file per
   export, with no per-day exporting, stitching or unzipping.
2. Today, 7 days and 30 days share one self-contained JSON structure, distinguished by the number
   of days. The file must be understandable and analysable by an external AI or statistical tool
   with **no companion file**; the necessary data documentation is inside the same JSON. The file
   must be valid UTF-8 JSON.
3. When there is too little data for a local regression, the existing records still export, with
   the insufficiency stated — no fabricated data or conclusions.
4. Before exporting, the user can see the date range and the kinds of data included. The user saves
   it or shares it through Android; nothing is uploaded automatically, and no unrelated history
   from another period is included.
5. The export content uses the same data snapshot as the selected period and the on-screen
   statistics.

Schema status: the released single-file JSON 2.0 is in place. Fields, models, pairs and embedded
documentation are described in the [export design](PERIOD_EXPORT_SCHEMA.md); the fixed computation
parameters are in [analysis policy 1.0](ANALYSIS_POLICY_V1.md). The machine schema and a synthetic
example generated by the real engine are both used in validation.

### US-04 | See the tendency in recent phone use time

As a user, I want PhoneMood to tell me directly, from my existing records, how long I have been on
my phone each day, the typical range, and whether use time is trending up, down, or showing no
clear change.

Acceptance criteria:

1. 7 and 30 days are analysed over their own periods; a 30-day result is never passed off as a
   7-day one.
2. Over valid complete days, a basic linear regression is run with the date index as the
   independent variable and daily active minutes as the dependent variable.
3. Shows "use time trending up / down / no clear change" and a meaningful magnitude — for example,
   roughly how many minutes per day the trend adds. This describes movement *within* the period; it
   makes no claim relative to the previous period.
4. The daily-duration trend and the mood-rating threshold are kept separate: 3 complete days are
   enough to attempt a preliminary trend, and daily records are still shown below that. Today's use
   and today's rating change do not need to reach 20 to be displayed.
5. A preliminary increase or decrease may be shown once direction and magnitude reach a versioned
   minimum display threshold. **A confidence interval excluding zero is not a hard gate.** Near
   zero it shows "change not evident", so that small numerical error is never written up as a
   trend.
6. Today's data in this period is marked separately, so that the low value of an unfinished day
   never produces a "decreasing" conclusion.
7. The computed result carries the slope, actual dates, sample size, uncertainty and method
   version, and can be exported for checking.

### US-05 | See how mood changes within one continuous use

As a user, I want PhoneMood to tell me, from the existing ratings inside one session, how my
rating moves when I use the phone for longer — without filling in a new questionnaire or asking an
AI.

Acceptance criteria:

1. Grouped by session, a within-session centred regression is run on cumulative active use time as
   of the answer against the rating. Every session carries the same total loss weight. **Mood
   differences that existed between sessions must never be explained as change within one use.**
2. Both 7 and 30 days have their own entry point and result state. A 7-day result must not be
   permanently blocked for "not yet 14 days".
3. Twenty valid ratings in the selected period start the attempt. A session with only one rating
   contributes no within-session slope; the actual contributing rating count and session count are
   reported separately, with no additional requirement of 20 fixed pairs. When every session has
   only one rating, it is stated as not estimable.
4. When data is insufficient, the specific reason — missing days, missing samples — is shown, and
   the descriptive statistics and export are retained.
5. Shows the rating difference corresponding to a duration increase that the actual within-session
   span supports. A 30-minute comparison is used where supportable; **no extrapolation beyond the
   observed span.**
6. Fixed copy example: "Within one continuous use, when use time lengthens by about A minutes,
   your rating tends to be about B points lower." Sample size, session count and preliminary
   support level are noted.
7. A confidence interval crossing zero may still be shown as "a preliminary tendency, needs more
   records to confirm" rather than hidden. When exposure is nearly constant, the model is not
   estimable, or window data is unavailable, the inability to judge is stated plainly — no
   direction is invented. When computable but very small, it shows "no clear tendency for now".
8. Irregular rating intervals, overnight pairs, late answers and window gaps are filtered by one
   uniform rule set, with the exclusion reason recorded.
9. "No clear association at present" is never written up as "the phone does not affect mood", and
   no diagnostic or causal conclusion is emitted.

### US-06 | Find the apps most clearly associated with mood

As a user, I want PhoneMood to analyse my existing per-app records and mood ratings automatically
and show me which apps are clearly associated, in which direction, and by how much — without my
having to nominate an app or judge for myself.

Acceptance criteria:

1. For each qualifying app, fit "end rating minus start rating" on total active time in the
   interval, target-app time, start rating, and time not counted as active use. The exact formula
   is in HLD 8.4.
2. The comparison is stated as: holding start mood, total active time and non-active time roughly
   equal, the extra mood change from substituting *h* minutes of other apps with the target app.
   *h* is **not** fixed at 30 minutes and must be supported by actual data.
3. App analysis shares the 20-valid-rating entry point; 40 rows or a cumulative 60 minutes are no
   longer required. A candidate app must still have been used across multiple answer windows, with
   enough variation in usage and contrast that a single use cannot produce a tendency. The exact
   candidate rules are recorded as versioned product parameters.
4. App behaviour is taken strictly from the interval between two adjacent actual answers in the
   same session, over the same interval as the change itself. No pair is constructed without a
   baseline. A pair belongs to the period containing its ending answer, and boundary-crossing
   context does not enter period totals. Day-by-day and session-by-session deletion refits check
   whether the direction is driven by a handful of records.
5. At most three sufficiently supported apps are shown; one, two or none are all acceptable. Weak
   results are never displayed to fill the list.
6. Early tendencies are filtered by a computable, non-trivial rating difference and basic contrast
   support — **not** by statistical significance or FDR as a hard display gate. Uncertainty and
   multiple-app testing feed internal records and a "to be confirmed" note; a preliminary result is
   never called strong evidence.
7. Each finding states its substituted minutes and extra rating difference explicitly. Ordering is
   first by support group, then by absolute difference, actual model sample size and package name.
   The UI does not reorder on its own.
8. A 7-day result meeting the rules may be shown as an "early sign". A minimum of 14 days must not
   be required to enter the full app list, and reaching 30 days alone must not mark a result
   stable.
9. When no app qualifies, it shows "no app association with enough data yet" and explains that more
   ratings spread across different days are needed.
10. Positive and negative associations are both displayed. Apps are never sorted into a
    beneficial/harmful leaderboard, and overall phone duration is never mixed into the app list.

### US-07 | Understandable, reproducible results, offline

As a free user, I want analysis without a network, an account or a subscription — and I want the
same data to produce the same conclusion.

Acceptance criteria:

1. In airplane mode, period summaries, trends, regressions, rule explanations and export all
   complete.
2. No LLM call, no cloud inference, no prompt construction, no per-call AI cost.
3. Statistical results are first turned into a structured finding, then a fixed rule selects an
   English or Chinese template; language follows the Android system setting.
4. The ordinary interface shows no beta, p-value or matrix jargon; technical values stay in the
   export.
5. The same data snapshot, period and analysis version produce the same results and the same fixed
   ranking. Runtime metadata such as the generation time is not required to be byte-identical.
6. Computation happens on a background thread on demand, on first entry to the analysis page or
   when data changes; a cache hit displays immediately. Switching periods must never briefly label
   the old period's results as the new period's.
7. Statistical regression is not added to the 10/30-second monitoring loop and adds no screen-off
   timed queries. A new answer or a catch-up read invalidates the relevant cache.
8. Computing, no data, insufficient data, model not estimable, success, and stale-cache-awaiting-
   update all have explicit interface states. No crash and no indefinite wait.
9. Given existing records, opening the analysis page yields either the three topics' analysis or an
   explicit insufficiency state — with no new questions, questionnaires, manual data entry or AI
   conversation step.

### US-08 | Free capability survives the future introduction of paid AI

As a free user, I want to keep viewing, analysing and exporting my own 7/30-day records after a
paid AI is introduced.

Acceptance criteria:

1. Nothing in this document's free capability depends on AI service status, an API key, login, or
   subscription state.
2. Data and local statistical results use stable, versioned structures that a future AI can read
   without making free users re-collect data.
3. No payment, subscription, AI upload or chat is implemented now, and no "unlock by paying"
   placeholder stands in for a local result.
4. The upload scope and user authorization for a future cloud AI are designed separately. **A free
   export action is never treated as authorization to upload to the cloud.**

## 4. Statistical details that must be settled before development

These are explicit decision items for the subsequent statistical specification — not choices a
developer may make freely. The product scope of these user stories is settled; the values below
must be fixed and versioned before model development.

| Decision item | What must be made explicit |
|---|---|
| Export schema (released 2.0) | Emit single-file JSON per PERIOD_EXPORT_SCHEMA.md; coverage determination, model parameters and statistical rules are frozen and recomputable |
| Data coverage and complete days | How a complete day is determined from start-of-recording, pauses, gaps and permission state; the absence of a known gap does not automatically mean complete |
| Time pairing | Maximum gap to the previous rating, whether overnight pairs are usable, the late-answer limit, and the length of out-of-period context |
| Window validity | Coverage requirement for the 30 minutes before an answer, and consistent handling of excluded apps |
| Regression inference | QR/SVD solving, the specific robust standard-error algorithm, within-day correlation handling, degrees of freedom, and small-sample degradation rules |
| Stability and ranking | Minimum display magnitude, basic app contrast rules, preliminary-tendency labelling; intervals and FDR are not hard gates for early display |
| App substitution comparison | The substitution-quantile algorithm, the feasible support range at fixed total duration, and the full covariance computation for the coefficient difference |
| Numerical anomalies | Fixed handling of zero variance, collinearity, insufficient effective sample, infinities and missing values |

Using robust standard errors does not mean selection bias has been removed. Ratings are triggered
by a use threshold, so conclusions must be confined to the use contexts that actually have
ratings. **This sample must not be interpreted as the user's all-day psychological state.**

The reference draft's suggestions of "30 days only by default" and "Top Factors need at least 14
days" are superseded by this product requirement: the free tier offers 7 and 30 days together;
seven days may show an early result that meets the support requirements, or may explicitly show
insufficiency. Additional factors such as late-night use, session length and switching frequency
are out of scope for this delivery.

## 5. Implementation order and overall definition of done

1. Period snapshots, daily/per-app summaries, quality state and single-file JSON export (US-01 to
   US-03) are complete; the released schema, recomputation validation and local rule analysis
   shipped with Android 1.4.0.
2. Daily trend, local continuous-use vs. mood, per-app extra change regression and deterministic
   interpretation (US-04 to US-06) are complete.
3. The health-app-style insight overview (US-00), bilingual pages, caching, offline operation and
   end-to-end acceptance (US-07) are complete; the paid AI upload/analysis boundary is reserved for
   a future version (US-08).

The free version counts as done only when all of the following hold: 7 and 30 days both switch and
export; all three analysis questions have either a success or an explicit insufficiency state;
tests with real valid data and with synthetic data cover the models and the ranking; missing-date,
period-boundary, late-answer, collinearity and small-sample tests pass; English and Chinese carry
the same meaning; offline operation is complete; the analysis does not reintroduce screen-off
polling; and the APK build, unit tests and lint all pass. Synthetic samples are used only in
tests and never mixed into user records.
