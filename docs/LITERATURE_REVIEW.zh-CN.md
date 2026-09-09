# 文献综述：情绪困扰、智能手机使用与日常测量

*English version: [LITERATURE_REVIEW.md](LITERATURE_REVIEW.md)*

[项目总览](../README.zh-CN.md) · [English overview](../README.md) · [统计策略](ANALYSIS_POLICY_V1.zh-CN.md)

## 动机与范围

PhoneMood 源于对数字行为与情绪困扰（包括抑郁症状与压力）之间关系的关注。它的核心方法论问题是：当**回忆中的使用可能不同于实际记录的行为**、部分互动是习惯性的、而宽泛的问卷测量又无法描述一个人一天之内使用与情绪体验的先后顺序时，应当如何考察这一关系。

这篇选择性的叙述综述沿着这一动机展开：从情绪困扰的证据，到测量效度、日常觉察，再到行为记录与即时自我报告的结合。随后讨论两种预期用途：支持对潜在不良模式的研究，以及帮助个人识别自身的模式。AI 辅助的解释是可能的后续扩展。

综述引用 13 篇研究论文（其中包括一篇作者参与合著的预印本），以及补充性的无障碍指南。文献于 2026 年 9 月通过针对性检索核对，检索主题包括智能手机／社交媒体使用与情绪困扰、使用量估计与量表的效度、习惯性查看、生态瞬时评估（EMA），以及个人数据反馈。使用了出版商页面、机构研究记录、被索引的摘要，以及可获取的作者手稿。**这不是系统综述，也不构成本项目提出了一种前人未曾研究过的方法的证据。** 下文对研究发现与其在 PhoneMood 中的应用作了区分。

## 1. 情绪困扰与数字行为：为什么测量重要

Thomée、Härenstam 与 Hagberg 在一个前瞻性青年队列中考察了手机使用、压力、睡眠障碍与抑郁症状。他们报告了手机使用的若干方面与心理健康结果之间的关联，其中一些在排除基线症状后于随访时仍然存在。这支持对潜在不良关系展开研究，但**该研究的手机使用测量方式与其历史背景，不应等同于当今应用级别的日志**。[Thomée 等（2011）](https://pubmed.ncbi.nlm.nih.gov/21281471/)

社交媒体使用是一种更具体的暴露。在一项针对 1,787 名美国青年的调查中，Lin 等人发现自我报告的社交媒体使用与用 PROMIS 工具测得的抑郁症状之间存在关联。作者将方向与机制列为有待进一步研究的问题。**这一关联并不能确立社交媒体导致了这些症状。** [Lin 等（2016）](https://pubmed.ncbi.nlm.nih.gov/26783723/)

这些发现与「平均关联很小」的证据并存。Orben 与 Przybylski 对大型青少年数据集的分析发现，数字技术使用与幸福感之间存在负相关，但至多只能解释 0.4% 的变异。该结果针对的是所研究的测量方式与人群，并不排除对某一个体而言具有实际意义的模式；但它确实挑战了「手机使用与情绪困扰总是强相关」这一笼统主张。[Orben 与 Przybylski（2019）](https://doi.org/10.1038/s41562-018-0506-1)

**对 PhoneMood 的意义：** 动机是考察不利模式**在何时、对何人**出现，而不是把所有手机使用都判定为有害。暴露测量、结果指标、时间尺度与个体之间的差异，共同界定了研究问题。抑郁症状、感知压力、情绪控制和即时心情必须保持区分；**PhoneMood 当前的心情评分不能替代对这四者的测量。**

## 2. 整体手机使用与具体应用：两个相关但不同的分析层次

一台设备可以承载社交互动、娱乐、工作与学习。因此，手机总时长与某个应用内的时长描述的是使用的相关但不同的方面。应用时长包含在总时长之内。若把二者解释为两种独立可加的危害，就会重复计算相互重叠的暴露，并使比较变得含糊。

对 PhoneMood 而言，有两个问题是有用的：心情如何随一次使用会话的推进而变化，以及心情如何与一个使用区间内**时间在各应用之间的分配**相关。这是两种不同的统计比较。在总使用量相近的条件下得到的应用关联，更接近于对「这段时间是如何分配的」的比较，而不是对「单纯增加应用使用量」之效应的估计。当前的[统计策略](ANALYSIS_POLICY_V1.zh-CN.md)与[导出规范](PERIOD_EXPORT_SCHEMA.zh-CN.md)记录了预期的比较方式。

Beyens 等人对 63 名青少年进行为期一周、每日六次的采样，发现社交媒体使用与即时幸福感之间的关联**在个体之间差异很大**。这为关注个体模式提供了动机；但它并不能确立：为某一个人估计出的关联是因果的，或随时间稳定。[Beyens 等（2020）](https://www.nature.com/articles/s41598-020-67727-7)

**对 PhoneMood 的意义：** 保留应用身份与时间信息，同时承认日志遗漏了什么。日志不揭示所浏览的内容、使用目的、社交情境，也不揭示一次互动是支持性的还是令人不快的。心情反过来影响后续使用的可能性同样需要考虑。**两个暴露层次不应与已被证实的双向因果过程相混淆。**

## 3. 测量效度：行为、主观体验与时机

### 报告的使用与记录的使用未必一致

Andrews 等人将 23 名参与者的估计与两周的实际手机使用记录作比较。对时长的估计具有一定价值，但**对每日使用次数的估计与记录到的频率并不相关**。这是支持对特定使用量测量方式进行效度检验的证据，而不是否定所有自我报告。[Andrews 等（2015）](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0139004)

Ellis 等人将手机使用与「成瘾」问卷同 Apple 的屏幕使用时间记录作比较。心理测量量表与记录到的行为之间的关联总体较差；单一估计以及围绕习惯性使用设计的测量表现相对更好。这些结果质疑把此类量表当作行为数量的代理指标，但**也不能使时长本身成为判定功能损害或问题性使用的充分标准**。[Ellis 等（2019）](https://research.lancaster-university.uk/en/publications/do-smartphone-usage-scales-predict-behavior/)

Tkaczyk 等人将数字痕迹与 137 名捷克青少年的单次报告和每日睡前报告作比较。两种报告形式都显示出相当大的偏差与较低的个体间聚合效度。每日报告与逐日记录变化之间的个体内聚合度同样偏低。因此，在该样本中，**更频繁的回顾式报告并没有自动解决测量问题**。[Tkaczyk 等（2024）](https://www.muni.cz/en/research/publications/2400299)

综合来看，这些研究支持一个具体的关切：一个大致能区分重度与轻度用户的测量方式，仍可能很差地代表某一个人在时刻之间或日与日之间的行为。它们同时表明，将自我报告与数字痕迹作比较是一条已有的研究路径。**PhoneMood 建立在这一方向之上；它并不是第一个自动记录手机使用的项目。**

### 三个测量问题应当保持分离

| 问题 | PhoneMood 目前提供什么 | 仍需评估什么 |
|---|---|---|
| 行为是否被准确记录？ | 前台使用片段、应用标识、时间戳与覆盖元数据。 | 与独立观察到的活动的一致性、遗漏事件、过滤规则，以及设备特定行为。 |
| 测量到的是什么心理体验？ | 一个 1–10 的自我报告心情评分。 | 该题目及其端点的理解方式；是否适合测量个体内变化；若要研究压力、抑郁症状或情绪控制，需要各自独立的工具。 |
| 时机是否支持预期的比较？ | 由使用量触发的检查点、实际回答时间，以及基于区间的分析输入。 | 采样偏差、回答延迟、未被观测到的中间事件，以及在持续使用时段之外缺乏可比评分。 |

自动日志减少了对回忆行为的依赖，但**心情仍然是一种主观报告**。用经过效度检验的问卷测症状、用日志测使用量，衡量的是互补的两件事。无论统计模型拟合得多好，也无论增加多少观测，都无法找回一个从未被测量过的构念。

同样，软件可靠性与心理测量可靠性是两回事。正确地持久化一个分数，并不能确立这个分数作为一种测量的质量。一项关于心情波动的研究需要把有意义的个体内变化与测量误差区分开，而不是假定重复评分应当保持恒定。**PhoneMood 尚未为其心情题目建立这方面的证据。**

### 与作者既往研究的联系

Xiao Chen 合著了《Digital Self-Regulation Before Sleep and Emotional Control Among Adolescents》，这是一项基于 PISA 2022、覆盖加拿大与香港 996 所学校 19,779 名学生的研究。多层次分析在学生层面与学校层面均报告了正向关联。**该论文是预印本，未经同行评审。** [Chen 等（2026），第 1 版](https://www.preprints.org/manuscript/202609.0400/v1)

测量方式使这一联系变得具体。第 3.2 节描述了一个源自 OECD、基于十个自我报告题目的情绪控制分数，以及一个关于就寝时是否关闭社交网络与应用通知的单一数字自我调节题目。这些捕捉的是**被报告的调节行为**，而非记录到的应用暴露。第 5.7 节将自我报告、单题测量与横断面设计列为局限，并明确提出使用手机日志等行为指标。[全文 §3.2 与 §5.7](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub)

PhoneMood 沿着这一测量方向推进：记录行为，并在更接近使用的时刻采样主观体验。它转向日常的、个体内的观察，结果变量也不同——是即时心情而非情绪控制。**建立其心情题目与使用记录的效度仍然是必要的；该应用并未复现或验证 PISA 的研究发现。**

该论文的作者贡献声明将方法学、软件、形式化分析与调查归于 Xiao，为既往分析工作与当前项目之间提供了有记录的联系。[作者贡献，印刷版第 16 页](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub#page=17)

## 4. 习惯性查看与觉察的界限

Oulasvirta 等人描述了「查看习惯」：对随手可得内容的短暂、重复的查看。在三项研究中，他们发现这些查看可能引向设备上的进一步活动。这支持对自动化的、几乎未经审慎考虑的互动展开研究，而不是假定每一次使用都始于一个完整表述的目标。**它并不能确立每一次互动都是无意识的或有害的。** [Oulasvirta 等（2012）](https://doi.org/10.1007/s00779-011-0412-2)

**对 PhoneMood 的意义：** 自动记录可能使被忽视的使用模式变得可供反思。**人们是否真的因此变得更有觉察，必须加以评估**——例如比较他们的预期与其记录，并询问哪些模式令他们意外。仅凭日志无法识别意图、觉察程度，或一次查看的心理意义。

这也暴露了当前设计的一个局限。使用时长阈值面向的是累计使用，因此**未达到阈值的短暂查看片段可能没有邻近的心情报告**。不应把该应用描述为已经在测量所有习惯性查看的情绪后果。未来的研究可以在控制提醒负担的前提下，比较时长触发、随机定时与短查看相关三种采样方式。

在界面与代码中，**主动手机使用（active phone use）**是对被计入的前台使用的一个操作性标签，**不是对使用是否出于意图的判断**。同样，自动采集日志与个人的自动化行为是两回事。

## 5. 把行为记录与即时报告结合起来

Dunton、Dzubur 与 Intille 在一项青少年身体活动研究中，将传感器驱动、情境敏感的 EMA 与随机提醒结合。不同提醒类型捕捉到不同的活动情境。该研究为将自我报告与观察到的行为绑定提供了先例，同时表明**触发方式本身就是采样设计的一部分**。它并不能验证 PhoneMood 的使用阈值或心情题目。[Dunton 等（2016）](https://www.jmir.org/2016/6/e106/)

PhoneMood 结合三个组成部分：记录到的使用、由使用触发的心情报告邀请，以及回答的实际时间。这可以支持比「对典型一天的笼统估计」更具时间特异性的问题。它是**对主观体验的近实时采样，而不是对情绪的连续测量**。[数据集构造器](../app/src/main/java/com/phonemood/analysis/PeriodDataset.kt)与[导出格式](PERIOD_EXPORT_SCHEMA.zh-CN.md)保留了回答时机、不可用配对与覆盖信息，以供解释。

分析问题与采集问题仍然是分开的。来自同一个人的重复观测是相互依赖的；缺失的回答可能是选择性的；先前的心情与时段可能同时影响使用与随后的心情。当前引擎包含会话内模型与条件性应用模型，并附有明确的局限说明。**它并不能消除全部混杂，不能识别「没有手机」的反事实，也无法找回未被观测到的睡眠、压力与线下事件。**

加入随机定时的提醒可以拓宽被采样的时刻，包括持续使用之外的时段。但**它不会随机化手机暴露**。一个关于「改变使用会怎样」的因果问题，除了改进测量之外，还需要合适的干预或其他站得住脚的识别策略。

## 6. 研究价值与个人觉察

研究目标是让行为—情绪关联更可检视：哪些使用被计入、报告了哪种体验、报告发生在什么时候、以及缺失了什么。这可以支持对潜在不良模式、人与应用之间的差异，以及回忆使用与记录使用之差异的研究。**当前的小规模用户测试尚未就这些问题产生有记录的发现。**

个人层面的目标是帮助一个人识别他们本可能忽视的模式。Li、Dey 与 Forlizzi 的个人信息学模型把采集、整合、反思与行动区分为一个更广的追踪过程中的不同阶段。这为**把「觉察」与「应用是否成功采集了数据」分开评估**提供了充分理由。[Li 等（2010）](https://personalinformatics.ianli.com/lab/model)

对 PhoneMood 而言，一个有用的解释也许是：在某些被记录到的使用模式期间，较低的评分反复出现——随后是关于当时还发生了什么、以及是否值得尝试改变的追问。应用同样应当允许出现有利的或不确定的模式。**为一个预设的「有害」叙事寻找印证，会同时损害研究与个人理解。**

一个教育方向的扩展可以把反思与学习者自定的任务或目标联系起来。这需要额外的情境与结果指标：**仅凭时长无法说明手机使用是打断了学习，还是促成了学习。** 同样，本项目的[认知无障碍说明](ADHD_FRIENDLY_UI.zh-CN.md)描述的是基于 W3C 指南的界面假设，**不是 ADHD 有效性的证据**。[W3C, Help Users Focus](https://www.w3.org/WAI/WCAG2/supplemental/objectives/o5-user-focus/)

### AI 作为后续扩展

Chopra 等人观察并访谈了 19 位围绕自我追踪健康数据使用生成式 AI 的人。他们的质性发现描述了问题的迭代细化、未被满足的需求，以及在追踪各阶段提供支持的机会。这些提示了帮助用户向自己的数据提问的可能性，**但并未确立持久的改善**。[Chopra 等（2025）](https://doi.org/10.1145/3749503)

Buçinca、Malaya 与 Gajos 发现，要求更审慎参与的设计能够在一项 AI 辅助决策任务中减少过度依赖，同时在主观体验上有所权衡。对 PhoneMood 而言，这促使我们在易用性之外，评估辅助是否帮助用户进行**准确的**推理。**这并不是「增加交互负担会有益于 ADHD 用户」的证据。** [Buçinca 等（2021）](https://www.eecs.harvard.edu/~kgajos/papers/2021/bucinca2021trust.shtml)

未来的 AI 层可以帮助形成问题、指出缺失的情境，并解释已经采集到的记录。它的价值将取决于事实依据与用户理解。PhoneMood 目前使用确定性统计与固定文本；**一个 AI 模型不可能仅靠生成流畅的解释，就修补缺失的观测。**

## 拟议的研究方向

以下是当前小规模用户测试之外的候选评估方案，**不是已完成的研究，也不是预注册方案**。

| 目标 | 候选比较 | 结果指标与边界 |
|---|---|---|
| 验证行为测量 | 将日志与独立观察到的活动作比较；将参与者的估计与由此得到的日志作比较。 | 时长／频率一致性、误差模式、缺失情况与设备覆盖。日志本身同样需要效度检验。 |
| 评估心理测量与时机 | 研究心情题目的理解方式；在相近的提醒预算下比较不同采样计划。 | 题目含义、回答延迟、完成率、负担，以及对短暂查看和非使用时段的覆盖。其他构念需要各自独立的工具。 |
| 考察个体内关联 | 用预先设定的比较分析重复的手机／应用使用与心情观测。 | 需处理先前心情、时间依赖性、时段与缺失。**关联仍不同于因果效应。** |
| 评估个人觉察 | 用相同记录，比较基础的记录展示与结构化摘要，采用解释任务与访谈。 | 对使用模式的识别、准确性、不确定性，以及对关联与因果的区分。 |
| 评估 AI 扩展 | 用完全相同的记录比较固定反馈与 AI 辅助反馈，随后进行一次独立的解释任务。 | 事实依据、无支持的因果陈述、用户理解与负担。**仅有满意度是不够的。** |

受控的解释任务可以从内容可核对、且明确标注的合成记录开始。后续涉及个人记录的工作可以考察其与参与者自身处境的相关性。研究人群、结果指标、样本量、缺失数据处理与知情同意／隐私安排都需要与各自的设计相匹配。**应用中的展示阈值不是样本量的论证依据。本文不主张已获得任何正式的研究伦理批准。**

## 参考文献

1. Thomée, S., Härenstam, A., & Hagberg, M. (2011). Mobile phone use and stress, sleep disturbances, and symptoms of depression among young adults—a prospective cohort study. *BMC Public Health, 11*, 66. [DOI: 10.1186/1471-2458-11-66](https://doi.org/10.1186/1471-2458-11-66).

2. Lin, L. Y., Sidani, J. E., Shensa, A., Radovic, A., Miller, E., Colditz, J. B., Hoffman, B. L., Giles, L. M., & Primack, B. A. (2016). Association between social media use and depression among U.S. young adults. *Depression and Anxiety, 33*(4), 323–331. [DOI: 10.1002/da.22466](https://doi.org/10.1002/da.22466).

3. Orben, A., & Przybylski, A. K. (2019). The association between adolescent well-being and digital technology use. *Nature Human Behaviour, 3*, 173–182. [DOI: 10.1038/s41562-018-0506-1](https://doi.org/10.1038/s41562-018-0506-1).

4. Beyens, I., Pouwels, J. L., van Driel, I. I., Keijsers, L., & Valkenburg, P. M. (2020). The effect of social media on well-being differs from adolescent to adolescent. *Scientific Reports, 10*, 10763. [DOI: 10.1038/s41598-020-67727-7](https://doi.org/10.1038/s41598-020-67727-7).

5. Andrews, S., Ellis, D. A., Shaw, H., & Piwek, L. (2015). Beyond self-report: Tools to compare estimated and real-world smartphone use. *PLOS ONE, 10*(10), e0139004. [DOI: 10.1371/journal.pone.0139004](https://doi.org/10.1371/journal.pone.0139004).

6. Ellis, D. A., Davidson, B. I., Shaw, H., & Geyer, K. (2019). Do smartphone usage scales predict behavior? *International Journal of Human-Computer Studies, 130*, 86–92. [DOI: 10.1016/j.ijhcs.2019.05.004](https://doi.org/10.1016/j.ijhcs.2019.05.004).

7. Tkaczyk, M., Tancoš, M., Šmahel, D., Elavsky, S., & Plhák, J. (2024). (In)accuracy and convergent validity of daily end-of-day and single-time self-reported estimations of smartphone use among adolescents. *Computers in Human Behavior, 158*, 108281. [DOI: 10.1016/j.chb.2024.108281](https://doi.org/10.1016/j.chb.2024.108281).

8. Chen, A., Qin, L., Chen, X., Wu, R., & Sonnert, G. (2026). Digital self-regulation before sleep and emotional control among adolescents: A multilevel cross-national study of Canada and Hong Kong, using PISA 2022. *Preprints.org*, version 1, posted September 4, 2026. [DOI: 10.20944/preprints202609.0400.v1](https://doi.org/10.20944/preprints202609.0400.v1) · [全文](https://www.preprints.org/frontend/manuscript/d6ec2b35543ba2f04de356b22a522e61/download_pub)。未经同行评审。

9. Oulasvirta, A., Rattenbury, T., Ma, L., & Raita, E. (2012). Habits make smartphone use more pervasive. *Personal and Ubiquitous Computing, 16*, 105–114. [DOI: 10.1007/s00779-011-0412-2](https://doi.org/10.1007/s00779-011-0412-2). 2011 年首次在线发表。

10. Dunton, G. F., Dzubur, E., & Intille, S. (2016). Feasibility and performance test of a real-time sensor-informed context-sensitive ecological momentary assessment to capture physical activity. *Journal of Medical Internet Research, 18*(6), e106. [DOI: 10.2196/jmir.5398](https://doi.org/10.2196/jmir.5398).

11. Li, I., Dey, A., & Forlizzi, J. (2010). A stage-based model of personal informatics systems. *Proceedings of the SIGCHI Conference on Human Factors in Computing Systems*, 557–566. [DOI: 10.1145/1753326.1753409](https://doi.org/10.1145/1753326.1753409).

12. Chopra, S., Juarez, K., Fogarty, J., & Munson, S. A. (2025). Engagements with generative AI and personal health informatics: Opportunities for planning, tracking, reflecting, and acting around personal health data. *Proceedings of the ACM on Interactive, Mobile, Wearable and Ubiquitous Technologies, 9*(3). [DOI: 10.1145/3749503](https://doi.org/10.1145/3749503).

13. Buçinca, Z., Malaya, M. B., & Gajos, K. Z. (2021). To trust or to think: Cognitive forcing functions can reduce overreliance on AI in AI-assisted decision-making. *Proceedings of the ACM on Human-Computer Interaction, 5*(CSCW1), Article 188. [DOI: 10.1145/3449287](https://doi.org/10.1145/3449287).

补充指南：W3C Web Accessibility Initiative. *Help Users Focus: Cognitive Accessibility Objective.* [W3C 指南](https://www.w3.org/WAI/WCAG2/supplemental/objectives/o5-user-focus/)。

*本文在 AI 辅助下，依据项目作者陈述的动机与所引文献撰写。其中的研究问题、设计意涵与拟议评估，是为 PhoneMood 所作的综合。*
