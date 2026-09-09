# PhoneMood

**Smartphone Use, Mood, and Everyday Self-Reflection**

[中文介绍](README.zh-CN.md) · [All documents](docs/README.md) · [Literature review](docs/LITERATURE_REVIEW.md) · [Download Android prototype](https://github.com/Xiao-Chen-usc/phonemood/raw/main/PhoneMood-1.4.0-debug.apk)

## Contents

| Section | What it covers |
|---|---|
| [Why I built PhoneMood](#why-i-built-phonemood) | The motivation, and the measurement problem behind it |
| [Research questions](#research-questions) | The three questions the prototype is built to examine |
| [Literature and measurement background](#literature-and-measurement-background) | How the design follows from published findings — full [literature review](docs/LITERATURE_REVIEW.md) |
| [What the prototype does](#what-the-prototype-does) | Recording, check-ins, local analysis, export |
| [Project contributions](#project-contributions) | Design decisions, each linked to the code that implements it |
| [Evidence and limitations](#evidence-and-limitations) | What has been tested, and what is **not** yet established |
| [Next research steps](#next-research-steps) | Proposed evaluations, not completed studies |
| [Author and contributions](#author-and-contributions) | My role, and the coauthored PISA 2022 preprint |
| [Try, build, and inspect](#try-build-and-inspect) | Install the APK, build from source, read the data format |

**Design documents** ([index, EN and 中文](docs/README.md)) —
[Literature review](docs/LITERATURE_REVIEW.md) ·
[High-level design](docs/HIGH_LEVEL_DESIGN.md) ·
[Local analysis design](docs/FREE_ANALYSIS_HIGH_LEVEL_DESIGN.md) ·
[Statistical policy](docs/ANALYSIS_POLICY_V1.md) ·
[Export schema](docs/PERIOD_EXPORT_SCHEMA.md) ·
[Validation record](docs/VALIDATION.md)

PhoneMood is an Android prototype that combines automatically recorded smartphone and app use with brief mood check-ins. It grew out of a concern about emotional distress in everyday digital life and a measurement question: how can we study the relationship between phone use and emotional experience when people may not accurately recall their own usage?

The project has two aims: to support finer-grained research on within-person patterns, including potentially adverse associations, and to help people recognize patterns in their own phone and app use.

**Current stage:** working prototype undergoing small-scale user testing. It provides local statistical analysis and predefined summaries. AI-assisted interpretation is a future extension; formal participant findings and evidence of improved mental health or learning are not yet reported.

| 1. Review today's records | 2. Check in with your mood | 3. Explore changes over time |
| :---: | :---: | :---: |
| <a href="docs/screenshots/demo-today-1.4.png"><img src="docs/screenshots/demo-today-1.4.png" alt="Today screen showing phone-use duration, mood ratings, and answered versus issued reminders with synthetic demonstration data" width="240"></a> | <a href="docs/screenshots/demo-mood-check-in-1.4.png"><img src="docs/screenshots/demo-mood-check-in-1.4.png" alt="Mood check-in asking for a 1–10 rating after 30 minutes of phone use in a synthetic demonstration session" width="240"></a> | <a href="docs/screenshots/demo-trends-1.4.png"><img src="docs/screenshots/demo-trends-1.4.png" alt="Trends screen showing a seven-day mood chart from synthetic demonstration data" width="240"></a> |
| See usage, mood, and check-in completion together. | Record how you feel at that moment. | Review daily mood patterns across a selected period. |

*Android prototype screenshots using synthetic demonstration data, not participant findings. Click an image to enlarge. [Screenshot details](docs/screenshots/README.md).*

## Why I built PhoneMood

My motivation begins with concerns about the relationship between smartphone use, social media, and emotional distress, including depressive symptoms and stress. Studies have reported associations, but their magnitude and interpretation vary across measures and populations. This makes the question of **what we measure, and when we measure it**, central to the project. [Thomée et al., 2011](https://pubmed.ncbi.nlm.nih.gov/21281471/) · [Lin et al., 2016](https://pubmed.ncbi.nlm.nih.gov/26783723/) · [Orben & Przybylski, 2019](https://doi.org/10.1038/s41562-018-0506-1)

I coauthored a [PISA 2022 study of digital self-regulation before sleep and emotional control among adolescents in Canada and Hong Kong](https://www.preprints.org/manuscript/202609.0400/v1), currently a preprint that has not been peer reviewed. In its limitations, we identified reliance on self-report and a single digital self-regulation item, and proposed adding behavioral measures such as smartphone logs. PhoneMood develops this measurement direction through automatic usage records and nearby mood reports. Its focus is everyday, within-person observation; emotional control and momentary mood remain different constructs.

Retrospective estimates can miss aspects of actual use, particularly brief, repeated checking. Some interactions may be habitual and receive little deliberate attention, making it difficult to reconstruct them afterward. Research comparing reports with device records supports treating usage estimates as measurements to validate. [Andrews et al., 2015](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0139004) · [Oulasvirta et al., 2012](https://doi.org/10.1007/s00779-011-0412-2)

I built PhoneMood to bring recorded behavior and self-reported experience closer together in time. For research, that means examining patterns that a broad usage estimate may obscure. For the person using it, it means making their own behavior more visible, including app-use patterns that may accompany worse mood. The goal is to investigate and support awareness; the records cannot by themselves establish that an app caused harm.

## Research questions

1. **Measurement:** How closely do recorded phone and app use agree with what people remember, and which experiences do usage-triggered mood prompts miss?
2. **Behavior and emotion:** Within a person's records, how do overall usage patterns and time spent in particular apps relate to mood, once timing, prior ratings, and data coverage are considered?
3. **Personal awareness:** Can reviewing these records help someone recognize habitual patterns, notice potentially unfavorable associations, and identify questions or changes worth exploring?

Overall phone use and use of a particular app are related levels of analysis: app time is part of total time, not an independent exposure that can simply be added to it. The [literature review](docs/LITERATURE_REVIEW.md) explains this distinction and the possibility that emotional state also influences subsequent use.

## Literature and measurement background

The [full review](docs/LITERATURE_REVIEW.md) follows the project's motivation through six topics: emotional distress and digital behavior; overall versus app-specific use; measurement validity; habitual checking and awareness; combining behavioral records with momentary reports; and the research and personal value of feedback. It includes the coauthored preprint and a separate discussion of AI as a future extension.

The central methodological choice is **automatic recording of behavior alongside self-report of subjective experience**. A device log can reduce reliance on remembered usage, but it cannot directly measure depression, stress, intention, or the meaning of an interaction. PhoneMood currently asks a single mood question; it does not measure all of these constructs.

## What the prototype does

- Records active phone-use segments and usage by app.
- Requests a 1–10 mood rating after configurable amounts of active use.
- Offers notification and optional floating-card check-ins, snoozing, dismissal, and pause controls.
- Supports daily and period review, exploratory associations, and JSON export.
- Includes reconciliation logic for interrupted monitoring and late responses.
- Runs locally, with English and Chinese interfaces and no account or server.

Here, **active phone use** means foreground usage counted by the monitoring rules. It does not mean conscious or intentional use, and the app does not classify an interaction as unconscious.

## Project contributions

The inspectable contributions in this repository are a working collection interface, explicit measurement rules, and an analysis/export pipeline that retains the evidence behind its summaries.

| Design choice | Why it matters for interpretation | Implementation or documentation |
|---|---|---|
| Separate active usage from elapsed session time | A locked-screen interruption should not count as active use. | [Session engine](app/src/main/java/com/phonemood/monitoring/SessionEngine.kt) |
| Retain unanswered mood check-ins, actual response times, and gaps in usage monitoring | Missing observations should remain visible when interpreting the data. | [Dataset construction](app/src/main/java/com/phonemood/analysis/PeriodDataset.kt) |
| Examine within-session changes and conditional app associations | These answer narrower questions than comparing raw mood averages. | [Statistical engine](app/src/main/java/com/phonemood/analysis/StatisticalEngine.kt) · [Analysis policy](docs/ANALYSIS_POLICY_V1.md) |
| Export observations, model inputs, and quality metadata together | A reader can inspect the records and assumptions behind a finding. | [Export specification](docs/PERIOD_EXPORT_SCHEMA.md) · [Synthetic examples](docs/examples/implemented-exports/README.md) |

## Evidence and limitations

| Evidence category | Documented status |
|---|---|
| Initial engineering validation | The [September 5 report](docs/VALIDATION.md) records build, unit, device, lint, and emulator checks for **1.0.0**. These results do not certify later builds. |
| Later analysis implementation | The repository contains [analysis tests](app/src/test/java/com/phonemood/analysis/AnalysisTest.kt), schema checks, and clearly labeled synthetic exports. These support checking software behavior, not participant outcomes. |
| Early use | Small-scale user testing is underway, as reported by the project author. Participant counts, procedures, and findings are not yet documented here. |
| Field reliability and outcomes | Extended device validation remains open. No formal usability findings, psychometric validation of the mood item, or educational/clinical efficacy results are reported. |

The single mood item does not establish attention, executive function, ADHD symptoms, or learning. Usage-triggered ratings lack a true no-phone mood baseline; unanswered prompts may also be selective. Sleep, stress, offline activities, and in-app content are unobserved. Associations remain exploratory, and small samples or dependent observations can limit uncertainty estimates. Product display thresholds are not clinical cutoffs or guarantees of sufficient statistical information.

The [cognitive accessibility design notes](docs/ADHD_FRIENDLY_UI.md) describe interface hypotheses. Their effectiveness for ADHD users has not been established. PhoneMood is a self-observation prototype, with no diagnostic or treatment claims.

## Next research steps

1. **Evaluate measurement:** compare usage estimates with recorded behavior; validate device coverage, the mood item, and prompt timing. Consider additional sampling to capture brief checks and periods without sustained use.
2. **Study within-person patterns:** examine associations between phone/app use and mood, with explicit handling of prior mood, missingness, time of day, and repeated observations.
3. **Evaluate personal awareness:** test whether reviewing records helps people recognize their own patterns and distinguish associations from causes. A later study could compare fixed summaries with AI-assisted interpretation using identical inputs.

These are proposed studies beyond the current user testing. The [review's study directions](docs/LITERATURE_REVIEW.md#proposed-study-directions) explain comparisons and outcomes; no formal study approval or completed evaluation is implied.

## Author and contributions

[**Xiao Chen**](https://github.com/Xiao-Chen-usc) is responsible for the project concept, research framing, product and interaction design, statistical analysis design, and software implementation. PhoneMood is being developed as part of an emerging research interest at the intersection of education, digital health, psychology, and AI.

In the coauthored PISA study, the [author-contribution statement](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub#page=17) credits Xiao with methodology, software, formal analysis, and investigation.

This README and the accompanying literature review were prepared with AI assistance, with citations checked against linked publications or author-hosted sources. Assistance in preparing documentation is separate from the app's runtime behavior, which does not call an AI model.

## Try, build, and inspect

[Download PhoneMood 1.4.0 debug APK](https://github.com/Xiao-Chen-usc/phonemood/raw/main/PhoneMood-1.4.0-debug.apk). Requires Android 10/API 29 or newer. Enable Usage Access for tracking, notifications for notification prompts, and Display over other apps if using floating cards. The downloadable APK is a packaged snapshot; current source changes may differ.

To build from source, use JDK 17+, Android SDK 35, and an Android 10+ emulator or device:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Device scenarios are documented in the [testing checklist](docs/DEVICE_TESTING.md). For the committed 1.4.0 release source, see [`45d9037`](https://github.com/Xiao-Chen-usc/phonemood/commit/45d9037); record the actual commit and local changes when reporting new checks.

**Data and privacy:** The app does not request `INTERNET` permission. Records are stored in a local Room database. Exports under `Downloads/PhoneMoodHealth/` may include app identifiers, timestamps, usage durations, and mood ratings. Exporting or sharing a file puts that copy under the recipient's or selected service's data practices; no external AI upload happens automatically.

**Code map:** [UI](app/src/main/java/com/phonemood/ui/) · [Monitoring](app/src/main/java/com/phonemood/monitoring/) · [Check-ins](app/src/main/java/com/phonemood/mood/) · [Storage](app/src/main/java/com/phonemood/data/) · [Analysis](app/src/main/java/com/phonemood/analysis/) · [Tests](app/src/test/) · [Device tests](app/src/androidTest/) · [Design documents](docs/)
