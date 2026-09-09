# PhoneMood Single-File JSON Period Export 2.0

*Chinese version: [PERIOD_EXPORT_SCHEMA.zh-CN.md](PERIOD_EXPORT_SCHEMA.zh-CN.md)*

Shipped in Android 1.4.0. Structure version `2.0`, summary version `2.0`, analysis policy version
`1.0`. Each save or share emits exactly one UTF-8 JSON file — no ZIP, no companion CSV, no
external data dictionary.

[Machine schema](schemas/phonemood-period-v2.schema.json) ·
[Analysis policy](ANALYSIS_POLICY_V1.md) ·
[Full synthetic 7-day example](schemas/phonemood-period-v2.example.json)

## Where to start reading

An external AI should read `data_dictionary` and `measurement` first for the definitions, then
`summary` and `days` for the facts, and only then `statistical_analysis.findings`. To recompute
anything independently, use the cumulative session duration in `analysis_matrix.rows` together
with the variable-interval per-app duration in `transitions`, joined by each model's `sample_ids`.
**Do not stack several rolling exports on top of one another** — deduplicate on the stable
observation IDs first.

## File structure

| Field | Meaning |
|---|---|
| `export_metadata` | Snapshot time, data revision, app version, real vs. synthetic marker |
| `period` | Today / 7 / 30 calendar days, IANA time zone, UTC bounds, including today if unfinished |
| `measurement` | The 1–10 scale, the 30-minute pre-answer window, threshold-triggered sampling, default settings and relevant configuration history |
| `data_quality` | Time coverage, count of complete finished days, count of valid ratings, response rate computed against actual delivery |
| `summary` | Total recorded use, per-app totals, and mean / median / P25 / P75 plus mean rating over complete finished days |
| `days` | Exactly 1 / 7 / 30 entries, including days with no records and today; per-day coverage and facts |
| `apps` | A dictionary of stable package names and display names — never in-app content |
| `usage_segments` | Active foreground segments clipped to the period, retaining source ID, session and the observed time zone |
| `sessions` | Referenced sessions, their bounds, and cumulative use as of the snapshot and within the period |
| `mood_observations` | Checkpoints in the period, answered or first delivered, including unanswered prompts |
| `context` | Pre-period segments, coverage and prompts used only for boundary features and adjacent answers; excluded from period summaries |
| `analysis_matrix.rows` | One row per actual in-period answer: 30-minute behaviour, plus cumulative session behaviour as of the answer |
| `transitions` | Variable intervals between adjacent actual answers, including both usable and excluded pairs |
| `statistical_analysis` | The three model families, parameters, covariance, sample IDs, findings, deletion refits and a complete policy snapshot |
| `user_insights` | Fixed template keys and parameters used by the UI, referencing computed findings — not AI-generated text |
| `data_dictionary` | Embedded definitions, missingness rules, statistical interpretation and limitations |

## Time, missingness and scope

UTC times use ISO-8601, and intervals are `[start_utc, end_utc_exclusive)`. Every `*_ms` field is
milliseconds; "minutes" inside model term names means minutes. Calendar days are cut in the
reporting time zone, so a daylight-saving day can be 23 or 25 hours long.

Today runs only up to the moment of generation. The daily trend, daily averages and quantiles use
**only complete finished days**. A period total is recorded use; where coverage is incomplete it
does not imply that the true total is known.

Coverage distinguishes `VERIFIED`, `UNKNOWN` and `NOT_MONITORED`, summed separately into
verified / unknown / not_monitored milliseconds. The three always add up to the interval length.
Coverage evidence requires a successful query chain with state anchors, minus gaps. Records from
older versions that lack such evidence stay UNKNOWN.

When a day has no records **and** no coverage evidence, `recorded_active_ms` is null. Duration
fields on windows and pairs report recorded quantity; even a zero must be read together with
coverage. Only a zero inside a COMPLETE interval may be read as "genuinely no counted use". An
app absent from the sparse app array likewise equals zero only under complete coverage.
`non_active_ms` may include excluded apps and must not be read as rest.

`context` extends backwards only as far as needed — the 30 minutes before an answer, or the
adjacent pre-period answer, the latter looking back at most 120 minutes. For a session that
crosses the period boundary, cumulative use is preserved separately as
`session_active_ms_at_answer`; it may include earlier use, and it cannot be recomputed from the
context segments alone. **The final session duration must never be substituted for the cumulative
value at the time of the answer.**

Configuration history contains, for each setting, the last change before the context start plus
every change after it. `default_settings` covers settings with no change on record.
System-filtered launchers, system UI and PhoneMood itself do not count as active use.

## Two analysis inputs

`analysis_matrix.rows` carries `observation_id`, `session_id`, `answered_at_utc`, `report_date`,
`mood_score`, a strict 30-minute `window`, coverage, per-app active time,
`session_active_ms_at_answer`, `session_coverage_complete`, quality flags, and per-model inclusion
status with reasons. Hour and date indices can be recomputed from the timestamp and reporting time
zone; no "natural mood" field is fabricated.

`transitions` carries start and end observation IDs, start and end ratings and `mood_delta`, the
actual interval, total active time, per-app time, non-counted active time, coverage, usability
status and exclusion reason. **The app model uses this whole interval** — it never passes a
30-minute behaviour row off as a pair. A pair belongs to the period containing its ending answer.
The first answer generates no pair when it has no preceding observation. Cross-session pairs,
over-long gaps and late answers are all retained as unusable pairs.

The gap around a rating is not necessarily 30 minutes. Prompt delay is stored as a signed integer;
a negative delay is flagged as an anomaly and excluded rather than clamped to zero. No answer is
simply `response = null`.

## Models, contrasts and interpretation

Every model exports `outcome`, `status`, `sample_ids`, retained `terms`, `coefficients` in
original units, the covariance matrix, degrees of freedom, the standard-error method, dropped
controls and warnings. **A model that cannot be estimated does not get zero-filled coefficients.**
The daily model references dates, the session model references answer IDs, and the app model
references transition IDs.

Findings are explicitly typed:

| kind | contrast_outcome | comparison_unit | difference |
|---|---|---|---|
| DAILY_USE_TREND | DAILY_MINUTES_CHANGE | DAYS | Fitted change in daily use (minutes) across an *h*-day span |
| SESSION_LENGTH | WITHIN_SESSION_MOOD_CHANGE | MINUTES | Change in rating for *h* more cumulative minutes within the same session |
| APP_USAGE | EXTRA_MOOD_CHANGE | MINUTES | Extra change from substituting *h* minutes of other apps with the target app, holding total active use, gap and starting rating equal |

*h* is stored as `comparison_value`; it is not a fixed window. The app primary model's
`outcome = END_MOOD_SCORE` is expressed separately from the finding's `EXTRA_MOOD_CHANGE`.

`block_refits` retains the difference after each day/session deletion, its estimability and its
directional agreement. `consistency` is the proportion of refits that agree — **not a probability
of being correct**. `ci_low` / `ci_high` / `p` / `q` may be null; neither p nor FDR is a display
threshold. The time-of-day sensitivity model has its own ID and never automatically replaces the
primary model. Highlighted app finding IDs appear in `top_app_finding_ids`, at most three.

All of this is exploratory association. No claim is made about causation, diagnosis, a true
phone-free mood baseline, or in-app content.

## Validation and compatibility

```bash
python3 scripts/generate_period_schema.py
python3 scripts/validate_period_export.py your-export.json
```

The validator needs `jsonschema>=4.17`. It checks the formal structure, the period entry count, ID
references, window and pair durations, segment recomputation, model sample and covariance
dimensions, the coverage timeline, and the highlighted findings. Android also checks the core
business invariants before saving.

The production example lives in
[examples/implemented-exports](examples/implemented-exports/README.md), generated by synthetic
tests running the same Kotlin engine and clearly marked `SYNTHETIC_TEST_FIXTURE`. The development
machine schema is not a second file that users need to carry alongside their export.

The historical `2.0-draft.2` design is preserved in
[the old draft](PERIOD_EXPORT_SCHEMA_DRAFT2.md); the old machine schema and example remain as
`*-draft2.*`. The validation script can accept the old example by version, but Android only ever
produces the released 2.0. The older daily report's `record_type` and its export entry point
remain independent.
