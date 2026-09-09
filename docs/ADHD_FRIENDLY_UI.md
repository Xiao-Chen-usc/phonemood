# PhoneMood: Reducing the Load on the Home Screen

*Chinese version: [ADHD_FRIENDLY_UI.zh-CN.md](ADHD_FRIENDLY_UI.zh-CN.md)*

The interaction proposals below were presented with a visualization skill; the shipping
implementation uses the project's existing Jetpack Compose code.

## What shipped

- The home screen leads with a plain "Today" heading in a sans-serif face, which raised the
  readability of both body and secondary text.
- An unanswered mood check-in now appears **before** the statistics card, and the current pace
  appears before the cumulative time for the day.
- Today shows active use time only by default. The mood average, completion ratio, chart and
  per-app breakdown moved behind "Expand today's detail".
- The selected tab and the expanded/collapsed state persist through `rememberSaveable`, so they
  survive Activity recreation.
- Pausing tracking uses an explicit text button. After a pause, the home screen keeps the
  original entry point for starting tracking again.
- The permissions overview moved to Settings; the home screen keeps only contextual prompts.
  The overlay and notification prompts appear in sequence rather than at once.
- The existing green brand color is unchanged. Secondary text darkened from #738075 to #526256,
  applied to both the English and Chinese strings.

## Design rationale

W3C COGA recommends a clear purpose, simple content, headings that help a reader re-orient, and
reduced distraction:

- https://www.w3.org/WAI/WCAG2/supplemental/objectives/o5-user-focus/
- https://www.w3.org/WAI/WCAG2/supplemental/objectives/o3-clear-content/

These are design hypotheses grounded in cognitive accessibility guidance. **They have not been
validated with ADHD users.** Target users should be asked whether they find a pending check-in
faster, whether they understand the state after pausing, and whether they can find the collapsed
detail again.

## Scope of verification

An Android debug build, the existing unit tests and lint. No on-device usability study was run,
and no TalkBack or 200%-font-size visual check was performed. The interaction previews in the
design conversation used clearly labelled sample data and were never connected to real records.

## Copy review, English and Chinese

- Standing slogans and repeated reassurances were removed. Home, usage records, the daily export
  and Settings all use functional headings.
- State is reported as one of: not started, paused, needs attention, checking, recording. A stale
  heartbeat reports only "checking" — it never infers a service failure on its own.
- A check-in is described as "after about X more minutes of use". The defer action is stated as
  "remind me in 1 minute".
- The mood question and the tap-to-save-and-close explanation are identical across both rating
  surfaces, and the scale endpoints are unchanged. A successful save gives a short Toast.
- Starting or pausing tracking, and saving the exclusion list, give Snackbar feedback. The pause
  message is scoped to "new mood check-ins" and never claims that existing notifications were
  removed.
- English can be selected independently of the system language; the version number is read from
  the installed application info.
- The explanation of how use time is measured, and the permission setup notes, expand on demand.
  The analysis data threshold and method moved into the calculation detail.
- The export button explains what the JSON file contains. An unspecified "health AI" use was
  removed.
- No change to the statistical model, the rating range, or the recorded data structures.

Verification: every English and Chinese resource key and format placeholder was checked
one by one; the debug build, existing unit tests and lint were run. No new emulator visual pass
or user testing was performed.
