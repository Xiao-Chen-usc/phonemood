# Review and Export Flow

*Chinese version: [REVIEW_WORKFLOW.zh-CN.md](REVIEW_WORKFLOW.zh-CN.md)*

## The core path

Today (immediate awareness) → Trends (last 7 days by default) → pick a date or period → read the
records and their completeness → export or share the selected records.

The former "daily export", the raw "usage records" screen and "analysis" were consolidated into
Trends, so that a user no longer has to generate JSON before they can understand their own
records. The bottom navigation keeps three destinations only — Today, Trends, Settings. Raw logs
are no longer a primary user-visible screen.

## Date selection

- Yesterday is the default. One step back reaches the day before; a date picker reaches any
  single day in history.
- "Last week" means the previous complete calendar week, Monday through Sunday.
- "Last 7 days" and "last 30 days" end at yesterday, excluding today, which has not finished.
- Earlier/later paging moves by the current period length — 1, 7 or 30 days — and never lands on
  today automatically.
- Tapping a day inside a period opens that single day's review, with a way back to the period.
- Today is labelled separately as "Today's preview". Its results and exports are marked as
  containing an in-progress day.
- Dates follow the time zone recorded in the database, and the page displays that zone. Daylight
  saving is handled at the actual day boundary.

## Results and data state

**The end of a day does not mean the record is complete.** The page distinguishes four states:
in progress, record complete, record incomplete, no records. Missing records are shown as a dash
with an explanation — never interpreted as zero use.

A single day shows use time, ratings and per-app time. Multiple days add a daily average,
day-by-day records and correlational analysis. Mood ratings show the five most recent by default
and expand on demand; the 30-day breakdown expands on demand. Raw records can be read and
exported even when the analysis threshold has not been met.

## Export

Export and share use the same snapshot as the results currently on screen, and the file name
carries the start and end dates. History queries and calculations both use the selected time
range, dropping ratings after the end boundary; the generation time and the period end time are
recorded separately. The JSON field `includes_ongoing_day` is computed from the actual dates, and
the schema permits both true and false. Export is temporarily disabled while a refresh is running
or after it fails, so that a stale result is never mistaken for the latest one.

The existing automatic daily background save is retained and its files are not deleted. The
foreground no longer treats the list of auto-saved files as the entry point to history; reviews
are generated directly from the records on the device.

## Verification

Added tests for single-day history boundaries, a complete historical week, a daylight-saving day,
and rejection of future dates. The debug build, unit tests and lint were run, and the review entry
points were checked on the existing emulator.
