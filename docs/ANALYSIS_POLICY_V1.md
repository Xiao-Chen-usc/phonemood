# Free-Tier Statistical Analysis Policy 1.0

*Chinese version: [ANALYSIS_POLICY_V1.zh-CN.md](ANALYSIS_POLICY_V1.zh-CN.md)*

Implemented in PhoneMood 1.4.0. Every computation runs on the device; the same snapshot, time
zone and version produce the same result. Rules select from fixed English and Chinese templates
and never call an AI. **The numbers below are product parameters for surfacing an insight, not
clinical thresholds.**

## Periods, samples and missingness

- Today, a rolling 7 days and a rolling 30 days all include today. Calendar days are cut using
  the IANA time zone in force when recording first began. A daylight-saving day is counted at its
  true length.
- A mood rating belongs to the period containing the time it was actually answered. The
  behavioural features of the 30 minutes before the answer are kept separately.
- A mood model is attempted from **20 valid ratings** upward. A rating must fall in 1–10, and the
  answer delay relative to its checkpoint must be 0–5 minutes. A negative delay is preserved and
  flagged as a clock anomaly.
- The continuous-use model additionally requires complete session coverage up to the answer. The
  per-app model uses only intervals that lie within one session, whose adjacent actual answers are
  (0, 120] minutes apart, whose coverage is complete, and which contain no relevant configuration
  change.
- No baseline is constructed when there is no preceding answer, and two different sessions are
  never stitched into a usable pair. Unanswered checkpoints and unusable pairs are still exported.
- `VERIFIED` coverage comes from a successful catch-up read chain anchored by
  foreground/pause/lock/shutdown state, minus known gaps and pauses. Older data cannot be promoted
  to complete coverage on the strength of a heartbeat alone. The limitation that Android may drop
  events is stated explicitly and never absorbed.

## The three primary models

| Topic | Model and contrast | Display rule |
|---|---|---|
| Daily use trend | Over complete finished days, `minutes ~ intercept + actual_day_index`; *h* is the true span between the first and last valid date | ≥3 complete finished days; the change must reach `max(15 min, 0.1 × median daily use)` |
| Continuous use and mood | Mood and cumulative active minutes centred within each session; WLS with weight `1/n_session` | A session with a single rating, or with no variation in duration, contributes no slope; *h* is the median within-session duration span, capped at 30 minutes |
| Per-app extra change | `end_score ~ intercept + start_score + phone_minutes + elapsed_minutes + app_minutes` | Coefficient × *h* expresses the extra change in rating from substituting *h* minutes of other apps with the target app; it is **not** a fixed 30-minute window |

The session intercept is an absorbed session-level mean, **not** a measured "natural mood for the
day". An app coefficient is a conditional association against a pooled reference group of other
apps; nothing is inferred about what the user was watching from an app's name.

## Comparable support for an app

The target app must have been used for ≥1 minute in at least 5 usable intervals. Two intervals
count as comparable when total active duration differs by ≤5 minutes, the actual gap differs by
≤10 minutes, the starting rating differs by ≤1 point, and target-app duration differs by ≥2
minutes. At least 10 distinct intervals must take part in such a match. *h* is the median of all
matched differences, capped at 30 minutes.

Matching only establishes input support and the contrast quantity for display; the primary model
is fitted on **all** valid intervals. Neither the candidate nor *h* is chosen by looking at end
ratings, p-values or the strongest result. App duration that is fully explained by the control
variables is unidentifiable and yields no coefficient. The actual sample IDs, retained terms and
dropped controls are all exported.

## Solving, uncertainty and stability

Columns are processed in fixed order: RMS scaling of the weighted columns, two passes of
orthogonalisation, removal of redundant controls at a relative threshold of `1e-9`, then a solve
by SVD. `X'X` is never inverted. A model is not estimable if the target term is redundant, if the
condition number exceeds `1e8`, or if the primary model lacks residual degrees of freedom.
Parameters and covariance are returned in original units.

Below 20 dates, HC3 is used, with an explicit note that it does not address within-day
correlation. Leverage in the session model includes the absorbed session intercept `1/n_session`,
and residual degrees of freedom are reduced by the number of sessions. From 20 dates upward,
day-clustered CR1 is used with the correction `G/(G−1) × (n−1)/(n−p−absorbed_groups)`, and the
*t* degrees of freedom are G−1. HC3 uses residual degrees of freedom. When leverage approaches 1,
the coefficient is kept rather than an interval being invented.

With ≥3 dates, blocks are deleted day by day; otherwise, with ≥3 sessions, session by session.
The daily trend is always deleted day by day. *h* is held fixed across refits. Every deleted
block, its difference, its estimability, and whether it agrees in sign and is non-trivial are all
exported. A refit that fails still counts in the denominator. A deletion refit only requires the
coefficient to be identifiable — not extra residual degrees of freedom.

A mood difference below 0.2 points in absolute value is labelled "no clear tendency yet". A
non-trivial difference whose deletion agreement is below 0.8 is labelled "mixed tendency".
Otherwise it is labelled a preliminary increase or decrease. A preliminary result may still be
given when there are not enough deletion blocks, marked as having limited support. Neither a CI
crossing zero nor a p/BH-FDR value failing to clear a threshold blocks display.

BH-FDR is computed only across app primary models that have a valid p-value, and is exported as
a diagnostic. A near-zero standard error does not manufacture a spuriously precise p-value.

## Predefined time-of-day check and ranking

When an app has ≥40 valid intervals, ≥7 dates, and end times spanning ≥3 distinct four-hour
blocks, a second model is fitted adding `sin(2πhour/24)` and `cos(2πhour/24)`, on the same sample
and the same *h*. If the two disagree in direction and the sensitivity difference reaches 0.2
points, the result is flagged as sensitive to time-of-day adjustment. **The stronger result is
not substituted for the primary model.**

The first version adds no centred-day drift term. This is exported explicitly as
`NOT_IMPLEMENTED_IN_V1`, and the copy does not claim that slow trends have been controlled for.

Only apps with a preliminary increase or decrease reach the highlighted card. They are ordered by
whether a deletion check was available, then by absolute contrast difference, then by sample count
descending, then by app ID ascending — at most three. If fewer than three qualify, fewer are
shown. Each app's *h* must be displayed alongside its result. **This ordering is not a leaderboard
of causal impact.**

## Numerical and engineering verification

`AnalysisTest.kt` checks against known results for: constant total duration, perfect
collinearity, equivalent parameterisations, session baseline absorption, HC3, missing coverage,
actual answer anchoring, daylight saving, 20 same-day ratings, reproducible export, and a
three-day trend. `MigrationTest.kt` verifies that upgrading a version 1 or 2 database preserves
facts and fabricates no coverage. The production JSON is checked twice: against the machine schema
and against cross-field invariants.
