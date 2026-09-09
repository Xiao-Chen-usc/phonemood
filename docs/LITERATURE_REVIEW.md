# Literature Review: Emotional Distress, Smartphone Use, and Everyday Measurement

*Chinese version: [LITERATURE_REVIEW.zh-CN.md](LITERATURE_REVIEW.zh-CN.md)*

[Project overview](../README.md) · [中文项目介绍](../README.zh-CN.md) · [Analysis policy](ANALYSIS_POLICY_V1.md)

## Motivation and scope

PhoneMood grew out of a concern about the relationship between digital behavior and emotional distress, including depressive symptoms and stress. Its central methodological question is how to examine that relationship when remembered usage may differ from recorded behavior, some interactions are habitual, and broad survey measures do not describe the sequence of use and emotional experience within a person's day.

This selective narrative review follows that motivation from the evidence on emotional distress to measurement validity, everyday awareness, and the combination of behavioral records with momentary self-reports. It then considers two intended uses: supporting research on potentially adverse patterns and helping individuals recognize their own patterns. AI-assisted interpretation is a possible later extension.

The review draws on 13 research papers, including one coauthored preprint, plus supplementary accessibility guidance. Sources were checked in September 2026 through targeted searches on smartphone/social-media use and emotional distress, the validity of usage estimates and scales, habitual checking, ecological momentary assessment (EMA), and personal-data feedback. Publisher pages, institutional research records, indexed abstracts, and available author manuscripts were used. This is not a systematic review or evidence that the project introduces a previously unstudied method. Study findings and their application to PhoneMood are distinguished below.

## 1. Emotional distress and digital behavior: why measurement matters

Thomée, Härenstam, and Hagberg examined mobile-phone use, stress, sleep disturbance, and depressive symptoms in a prospective young-adult cohort. They reported associations between aspects of phone use and mental-health outcomes, including some at follow-up after excluding baseline symptoms. This supports investigating potentially adverse relationships, but the study's phone-use measures and historical context should not be equated with present-day app-level logs. [Thomée et al. (2011)](https://pubmed.ncbi.nlm.nih.gov/21281471/)

Social-media use is a more specific exposure. In a survey of 1,787 US young adults, Lin and colleagues found associations between self-reported social-media use and depressive symptoms measured with a PROMIS instrument. The authors identified direction and mechanism as questions for further work. This association does not establish that social media caused the symptoms. [Lin et al. (2016)](https://pubmed.ncbi.nlm.nih.gov/26783723/)

These findings coexist with evidence of small average associations. Orben and Przybylski's analysis of large adolescent datasets found a negative association between digital-technology use and well-being that explained at most 0.4% of variation. That result concerns the measures and populations studied, rather than ruling out every consequential pattern for an individual. It does challenge a blanket claim that phone use and emotional distress are always strongly related. [Orben and Przybylski (2019)](https://doi.org/10.1038/s41562-018-0506-1)

**Implication for PhoneMood:** the motivation is to investigate when and for whom unfavorable patterns appear. It is not to classify all phone use as harmful. Differences among exposure measures, outcomes, timescales, and people help define the research problem. Depressive symptoms, perceived stress, emotional control, and momentary mood must remain distinct; PhoneMood's current mood rating does not substitute for measurements of all four.

## 2. Overall phone use and particular apps: related levels of analysis

A device can host social interaction, entertainment, work, and learning. Total phone time and time in a particular app therefore describe related but different aspects of use. App time is contained within total time. Interpreting them as two independent, additive harms would count overlapping exposure and leave the comparison unclear.

For PhoneMood, two questions are useful: how mood varies alongside the progression of a usage session, and how mood relates to the allocation of time among apps within a usage interval. These are different statistical comparisons. A conditional app association at similar total usage is closer to a comparison of how that time was allocated than an estimate of the effect of simply adding more app use. The current [analysis policy](ANALYSIS_POLICY_V1.md) and [export specification](PERIOD_EXPORT_SCHEMA.md) document the intended comparisons.

Beyens and colleagues sampled 63 adolescents six times daily for one week and found substantial differences among individuals in associations between social-media use and momentary well-being. This motivates attention to individual patterns; it does not establish that an association estimated for one person is causal or stable over time. [Beyens et al. (2020)](https://www.nature.com/articles/s41598-020-67727-7)

**Implication for PhoneMood:** preserve app identity and timing while acknowledging what logs omit. They do not reveal the content viewed, purpose of use, social context, or whether an interaction was supportive or upsetting. The possibility that mood influences subsequent use also needs consideration. Two levels of exposure should not be confused with a demonstrated two-way causal process.

## 3. Measurement validity: behavior, subjective experience, and timing

### Reported use and recorded use do not necessarily agree

Andrews and colleagues compared 23 participants' estimates with two weeks of recorded smartphone use. Estimated duration had some value, but estimates of the number of daily uses did not correlate with recorded frequency. This is evidence for validating particular usage measures, rather than rejecting all self-report. [Andrews et al. (2015)](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0139004)

Ellis and colleagues compared smartphone-use and “addiction” questionnaires with Apple's Screen Time records. Associations between psychometric scales and recorded behavior were generally poor; single estimates and measures framed around habitual use performed more favorably. These results question using such scales as proxies for behavioral quantity. They do not make duration alone a sufficient criterion for impairment or problematic use. [Ellis et al. (2019)](https://research.lancaster-university.uk/en/publications/do-smartphone-usage-scales-predict-behavior/)

Tkaczyk and colleagues compared digital traces with single-time and daily end-of-day reports from 137 Czech adolescents. Both report formats showed considerable discrepancies and low between-person convergent validity. Daily reports also showed low within-person convergence with day-to-day recorded changes. More frequent retrospective reporting therefore did not automatically solve the measurement problem in that sample. [Tkaczyk et al. (2024)](https://www.muni.cz/en/research/publications/2400299)

Together, these studies support a specific concern: a measure that roughly distinguishes heavier from lighter users may still poorly represent one person's moment-to-moment or day-to-day behavior. They also show that comparing self-report with digital traces is an established research approach. PhoneMood builds on that direction; it is not the first project to record phone use automatically.

### Three measurement questions should remain separate

| Question | What PhoneMood currently supplies | What still needs evaluation |
|---|---|---|
| Was behavior recorded accurately? | Foreground-use segments, app identifiers, timestamps, and coverage metadata. | Agreement with independently observed activity, missing events, filtering, and device-specific behavior. |
| What psychological experience was measured? | A single 1–10 self-reported mood rating. | Interpretation of the item and endpoints; suitability for within-person change; separate instruments if studying stress, depressive symptoms, or emotional control. |
| Does the timing support the intended comparison? | Usage-triggered checkpoints, actual response times, and interval-based analysis inputs. | Sampling bias, response delay, unobserved intervening events, and the absence of comparable ratings outside sustained-use periods. |

Automatic logs reduce dependence on remembered behavior, but mood remains a subjective report. Using a validated questionnaire for symptoms and a log for usage would measure complementary things. Neither a well-fitting statistical model nor additional observations can recover a construct that was never measured.

Likewise, software reliability and psychometric reliability are different. Persisting a score correctly does not establish the quality of that score as a measure. A study of fluctuating mood would need to distinguish meaningful within-person change from measurement error, rather than assume that repeated ratings should stay constant. PhoneMood has not yet established that evidence for its mood item.

### Connection to the author's prior research

Xiao Chen coauthored *Digital Self-Regulation Before Sleep and Emotional Control Among Adolescents*, a PISA 2022 study of 19,779 students in 996 schools in Canada and Hong Kong. The multilevel analyses report positive associations at student and school levels. The paper is a preprint that has not been peer reviewed. [Chen et al. (2026), version 1](https://www.preprints.org/manuscript/202609.0400/v1)

The measures make the connection concrete. Section 3.2 describes an OECD-derived emotional-control score based on ten self-report items and a single digital self-regulation item about disabling social-network and app notifications at bedtime. These capture reported regulation, rather than recorded app exposure. Section 5.7 identifies self-report, single-item measurement, and the cross-sectional design as limitations, and explicitly proposes behavioral indicators such as smartphone logs. [Full text, §§3.2 and 5.7](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub)

PhoneMood develops that measurement direction by recording behavior and sampling subjective experience closer to use. It shifts to everyday, within-person observation, with a different outcome: momentary mood rather than emotional control. Establishing the validity of its mood item and usage records remains necessary; the app does not replicate or validate the PISA findings.

The paper's author-contribution statement credits Xiao with methodology, software, formal analysis, and investigation, providing a documented connection between prior analytical work and the present project. [Author contributions, printed p. 16](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub#page=17)

## 4. Habitual checking and the limits of awareness

Oulasvirta and colleagues described checking habits: brief, repeated inspections of readily available content. Across three studies, they found that these checks could lead to further activity on the device. This supports examining automatic or minimally deliberated interactions, rather than assuming every episode begins with a fully articulated goal. It does not establish that every interaction is unconscious or harmful. [Oulasvirta et al. (2012)](https://doi.org/10.1007/s00779-011-0412-2)

**Implication for PhoneMood:** automatic records may make overlooked usage patterns available for reflection. Whether people actually become more aware must be evaluated, for example by comparing their expectations with their records and asking which patterns surprise them. Logs alone cannot identify intention, awareness, or the psychological meaning of a check.

This also exposes a limitation of the current design. Usage-duration thresholds are oriented toward accumulated use, so brief checking episodes that do not reach a threshold may have no nearby mood report. The app should not be described as already measuring the emotional consequences of all habitual checks. A future study could compare duration-triggered, randomly timed, and brief-check-related sampling while controlling the prompt burden.

In the interface and code, **active phone use** is an operational label for counted foreground usage. It is not a judgment that use was intentional. Similarly, automatic collection of a log is different from automatic behavior by the person.

## 5. Bringing behavioral records and momentary reports together

Dunton, Dzubur, and Intille combined sensor-informed, context-sensitive EMA with random prompts in an adolescent physical-activity study. Prompt types captured different activity contexts. The study offers a precedent for tying self-report to observed behavior, while showing that the trigger is part of the sampling design. It does not validate PhoneMood's usage thresholds or mood item. [Dunton et al. (2016)](https://www.jmir.org/2016/6/e106/)

PhoneMood combines three components: recorded usage, a usage-triggered invitation to report mood, and the actual time of the response. This can support more temporally specific questions than a general estimate of a typical day. It is near-time sampling of subjective experience, not continuous measurement of emotion. The [dataset builder](../app/src/main/java/com/phonemood/analysis/PeriodDataset.kt) and [export format](PERIOD_EXPORT_SCHEMA.md) retain response timing, unusable pairs, and coverage information for interpretation.

The analysis problem remains separate from collection. Repeated observations from one person are dependent; missing responses can be selective; prior mood and time of day may influence both use and subsequent mood. The current engine includes within-session and conditional app models with explicit limitations. It does not remove all confounding, identify a no-phone counterfactual, or recover unobserved sleep, stress, and offline events.

Adding randomly timed prompts could broaden the moments sampled, including periods outside sustained use. It would not randomize phone exposure. A causal question about changing use would require a suitable intervention or other defensible identification strategy in addition to improved measurement.

## 6. Research value and personal awareness

The research aim is to make behavior–emotion associations more inspectable: which usage was counted, which experience was reported, when the report occurred, and what is missing. This could support studying potentially adverse patterns, variation among people and apps, and differences between remembered and recorded use. The current small-scale user testing has not yet produced documented findings on those questions.

The personal aim is to help someone recognize patterns they may otherwise overlook. Li, Dey, and Forlizzi's personal-informatics model separates collection, integration, reflection, and action within a broader tracking process. This provides a useful reason to evaluate awareness separately from whether the app successfully collects data. [Li et al. (2010)](https://personalinformatics.ianli.com/lab/model)

For PhoneMood, a useful interpretation might be that lower ratings recur during certain recorded usage patterns, followed by questions about what else was happening and whether a change is worth exploring. The app should allow favorable or inconclusive patterns as well. Confirming a predetermined story of harm would undermine both research and personal understanding.

An educational extension could connect reflection to a learner-defined task or goal. That would require additional context and outcomes: duration alone cannot show whether phone use interrupted study or enabled it. Likewise, the project's [cognitive accessibility notes](ADHD_FRIENDLY_UI.md) describe interface hypotheses drawing on W3C guidance; they are not evidence of ADHD efficacy. [W3C, Help Users Focus](https://www.w3.org/WAI/WCAG2/supplemental/objectives/o5-user-focus/)

### AI as a later extension

Chopra and colleagues observed and interviewed 19 people using generative AI around self-tracked health data. Their qualitative findings describe query refinement, unmet needs, and opportunities for support across tracking stages. They suggest possibilities for helping users ask questions of their data, but do not establish lasting improvement. [Chopra et al. (2025)](https://doi.org/10.1145/3749503)

Buçinca, Malaya, and Gajos found that designs requiring more deliberate engagement could reduce overreliance in an AI-assisted decision task, with tradeoffs in subjective experience. For PhoneMood, this motivates evaluating whether assistance helps users reason accurately, alongside ease of use. It is not evidence that adding interaction burden would benefit ADHD users. [Buçinca et al. (2021)](https://www.eecs.harvard.edu/~kgajos/papers/2021/bucinca2021trust.shtml)

A future AI layer could help formulate questions, identify missing context, and explain the records already collected. Its value would depend on factual grounding and user understanding. PhoneMood currently uses deterministic statistics and fixed text; an AI model cannot repair absent observations merely by producing a fluent explanation.

## Proposed study directions

These are candidate evaluations beyond the current small-scale user testing, not completed studies or preregistered protocols.

| Aim | Candidate comparison | Outcomes and boundaries |
|---|---|---|
| Validate behavior measurement | Compare logging with independently observed activity; compare participant estimates with the resulting logs. | Duration/frequency agreement, error patterns, missingness, and device coverage. Logs also need validation. |
| Evaluate psychological measurement and timing | Study interpretation of the mood item; compare sampling schedules under a similar prompt budget. | Item meaning, response delay, completion, burden, and coverage of brief checks and non-use periods. Separate instruments are needed for other constructs. |
| Investigate within-person associations | Analyze repeated phone/app-use and mood observations with prespecified comparisons. | Account for prior mood, temporal dependence, time of day, and missingness. Associations remain distinct from causal effects. |
| Evaluate personal awareness | Compare a basic record display with structured summaries of the same records, using interpretation tasks and interviews. | Recognition of usage patterns, accuracy, uncertainty, and distinction between association and causation. |
| Evaluate an AI extension | Compare fixed and AI-assisted feedback using identical records, followed by an independent interpretation task. | Factual grounding, unsupported causal statements, user understanding, and burden. Satisfaction alone is insufficient. |

Controlled interpretation tasks could begin with clearly labeled synthetic records whose contents can be checked. Later work with personal records could examine relevance to participants' own circumstances. Study population, outcomes, sample size, missing-data handling, and consent/privacy arrangements would need to match each design. App display thresholds are not sample-size justifications. No formal study approval is claimed here.

## References

1. Thomée, S., Härenstam, A., & Hagberg, M. (2011). Mobile phone use and stress, sleep disturbances, and symptoms of depression among young adults—a prospective cohort study. *BMC Public Health, 11*, 66. [DOI: 10.1186/1471-2458-11-66](https://doi.org/10.1186/1471-2458-11-66).

2. Lin, L. Y., Sidani, J. E., Shensa, A., Radovic, A., Miller, E., Colditz, J. B., Hoffman, B. L., Giles, L. M., & Primack, B. A. (2016). Association between social media use and depression among U.S. young adults. *Depression and Anxiety, 33*(4), 323–331. [DOI: 10.1002/da.22466](https://doi.org/10.1002/da.22466).

3. Orben, A., & Przybylski, A. K. (2019). The association between adolescent well-being and digital technology use. *Nature Human Behaviour, 3*, 173–182. [DOI: 10.1038/s41562-018-0506-1](https://doi.org/10.1038/s41562-018-0506-1).

4. Beyens, I., Pouwels, J. L., van Driel, I. I., Keijsers, L., & Valkenburg, P. M. (2020). The effect of social media on well-being differs from adolescent to adolescent. *Scientific Reports, 10*, 10763. [DOI: 10.1038/s41598-020-67727-7](https://doi.org/10.1038/s41598-020-67727-7).

5. Andrews, S., Ellis, D. A., Shaw, H., & Piwek, L. (2015). Beyond self-report: Tools to compare estimated and real-world smartphone use. *PLOS ONE, 10*(10), e0139004. [DOI: 10.1371/journal.pone.0139004](https://doi.org/10.1371/journal.pone.0139004).

6. Ellis, D. A., Davidson, B. I., Shaw, H., & Geyer, K. (2019). Do smartphone usage scales predict behavior? *International Journal of Human-Computer Studies, 130*, 86–92. [DOI: 10.1016/j.ijhcs.2019.05.004](https://doi.org/10.1016/j.ijhcs.2019.05.004).

7. Tkaczyk, M., Tancoš, M., Šmahel, D., Elavsky, S., & Plhák, J. (2024). (In)accuracy and convergent validity of daily end-of-day and single-time self-reported estimations of smartphone use among adolescents. *Computers in Human Behavior, 158*, 108281. [DOI: 10.1016/j.chb.2024.108281](https://doi.org/10.1016/j.chb.2024.108281).

8. Chen, A., Qin, L., Chen, X., Wu, R., & Sonnert, G. (2026). Digital self-regulation before sleep and emotional control among adolescents: A multilevel cross-national study of Canada and Hong Kong, using PISA 2022. *Preprints.org*, version 1, posted September 4, 2026. [DOI: 10.20944/preprints202609.0400.v1](https://doi.org/10.20944/preprints202609.0400.v1) · [Full text](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub). Not peer reviewed.

9. Oulasvirta, A., Rattenbury, T., Ma, L., & Raita, E. (2012). Habits make smartphone use more pervasive. *Personal and Ubiquitous Computing, 16*, 105–114. [DOI: 10.1007/s00779-011-0412-2](https://doi.org/10.1007/s00779-011-0412-2). First published online in 2011.

10. Dunton, G. F., Dzubur, E., & Intille, S. (2016). Feasibility and performance test of a real-time sensor-informed context-sensitive ecological momentary assessment to capture physical activity. *Journal of Medical Internet Research, 18*(6), e106. [DOI: 10.2196/jmir.5398](https://doi.org/10.2196/jmir.5398).

11. Li, I., Dey, A., & Forlizzi, J. (2010). A stage-based model of personal informatics systems. *Proceedings of the SIGCHI Conference on Human Factors in Computing Systems*, 557–566. [DOI: 10.1145/1753326.1753409](https://doi.org/10.1145/1753326.1753409).

12. Chopra, S., Juarez, K., Fogarty, J., & Munson, S. A. (2025). Engagements with generative AI and personal health informatics: Opportunities for planning, tracking, reflecting, and acting around personal health data. *Proceedings of the ACM on Interactive, Mobile, Wearable and Ubiquitous Technologies, 9*(3). [DOI: 10.1145/3749503](https://doi.org/10.1145/3749503).

13. Buçinca, Z., Malaya, M. B., & Gajos, K. Z. (2021). To trust or to think: Cognitive forcing functions can reduce overreliance on AI in AI-assisted decision-making. *Proceedings of the ACM on Human-Computer Interaction, 5*(CSCW1), Article 188. [DOI: 10.1145/3449287](https://doi.org/10.1145/3449287).

Supplementary guidance: W3C Web Accessibility Initiative. *Help Users Focus: Cognitive Accessibility Objective.* [W3C guidance](https://www.w3.org/WAI/WCAG2/supplemental/objectives/o5-user-focus/).

*Prepared with AI assistance from the project author's stated motivation and the cited sources. The research questions, design implications, and proposed evaluations are the synthesis presented for PhoneMood.*
