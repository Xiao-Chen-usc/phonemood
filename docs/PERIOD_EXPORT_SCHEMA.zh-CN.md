# PhoneMood 单文件 JSON 周期导出 2.0

*English version: [PERIOD_EXPORT_SCHEMA.md](PERIOD_EXPORT_SCHEMA.md)*

已在 Android 1.4.0 接入。结构版本 `2.0`、汇总版本 `2.0`、统计策略版本 `1.0`。每次保存／分享只输出一个 UTF-8 JSON 文件；不需要 ZIP、附属 CSV 或外部数据字典。

[机器 schema](schemas/phonemood-period-v2.schema.json) · [统计策略](ANALYSIS_POLICY_V1.zh-CN.md) · [完整7天合成示例](schemas/phonemood-period-v2.example.json)

## 先从哪里看

外部 AI 可以先看 `data_dictionary` 和 `measurement` 了解定义，再看 `summary`／`days` 获取事实，最后阅读 `statistical_analysis.findings`。若要自行重算，使用 `analysis_matrix.rows` 的会话累计用时及 `transitions` 的可变区间 App 用时，结合模型的 `sample_ids`；不要把多个滚动导出直接叠加，必须按稳定观测 ID 去重。

## 文件结构

| 字段 | 含义 |
|---|---|
| `export_metadata` | 快照时间、数据修订号、App版本、真实／合成数据标识 |
| `period` | 当天／7／30个自然日、IANA时区、UTC起止，包含尚未结束的今天 |
| `measurement` | 1–10分量表、回答前30分钟窗口、阈值触发采样、默认设置和相关配置历史 |
| `data_quality` | 时间覆盖、完整结束日数、有效评分数、按实际送达计算的回答率 |
| `summary` | 已记录总用时、各App汇总、完整结束日的均值／中位数／P25／P75和评分均值 |
| `days` | 恰好1／7／30条，包括没记录的日期和今天；逐日覆盖与事实 |
| `apps` | App稳定包名与显示名词典，不包含App内内容 |
| `usage_segments` | 裁切到周期的主动前台使用片段，保留来源ID、会话和观测时区 |
| `sessions` | 被引用会话、起止、截至快照和周期内累计用时 |
| `mood_observations` | 周期内检查点、回答或首次送达的问卷，包含未回答问卷 |
| `context` | 仅用于边界特征和相邻回答的周期前片段、覆盖、问卷；不进入周期汇总 |
| `analysis_matrix.rows` | 每次周期内实际回答一行，30分钟行为和截至回答的会话累计行为 |
| `transitions` | 相邻实际回答的可变区间，含有效及被排除的配对 |
| `statistical_analysis` | 三类模型、参数、协方差、样本ID、发现、删除重算及完整策略快照 |
| `user_insights` | 页面固定模板键和参数，引用已计算发现，不是AI生成文本 |
| `data_dictionary` | 内嵌定义、缺失规则、统计解释和限制 |

## 时间、缺失和范围

UTC时间用ISO-8601，区间为 `[start_utc,end_utc_exclusive)`；所有 `*_ms` 是毫秒，模型项名字中的 minutes 是分钟。按报告时区分自然日，因此夏令时日可以是23／25小时。

今天只截至生成时刻。每日趋势、日均及分位数只使用覆盖完整的结束日；周期总量是已记录用时，不意味着覆盖不全时仍知道完整真实用时。

覆盖明确区分 `VERIFIED`、`UNKNOWN` 和 `NOT_MONITORED`，分别汇总为 verified／unknown／not_monitored毫秒。三者之和等于区间长度。成功查询链有状态锚点并扣除缺口后才能提供覆盖证据；旧版本没有这种证据的记录保持UNKNOWN。

日级完全没有记录也没有覆盖证据时 `recorded_active_ms=null`。窗口／配对的时长字段表示已记录量，即使为0也必须结合覆盖判断；只有COMPLETE区间中的0才能解释为确实没有计入的使用。稀疏App数组中没有某App，同样只在完整覆盖时等于零。`non_active_ms`可能包含排除App，不能解释为休息。

`context` 最多按必要的回答前30分钟或周期前相邻回答扩展，后者最大回看120分钟。跨周期会话的累计用时另保留为 `session_active_ms_at_answer`，可能含更早使用，不能仅靠context片段重算这个累计量。不得使用最终会话时长代替回答时的累计量。

配置历史只包含context起点前每个设置的最后变更及此后变更；`default_settings` 用于尚无覆盖变更的设置。系统过滤的桌面、系统UI及本App也不计入主动用时。

## 两种分析输入

`analysis_matrix.rows` 包含 `observation_id`、`session_id`、`answered_at_utc`、`report_date`、`mood_score`、严格30分钟 `window`、覆盖、逐App主动时间、`session_active_ms_at_answer`、`session_coverage_complete`、质量标志和逐模型纳入状态／原因。小时和日期索引可由时间与报告时区重算，不伪造自然心情字段。

`transitions` 包含起止观测ID、起止评分和 `mood_delta`、实际区间、总主动时间、各App时间、非计入主动时间、覆盖、可用状态和排除原因。App模型使用这整个区间，不把30分钟行为行冒充配对。按结束回答归属周期。首个回答没有前次观测时不生成配对；不同会话、过长间隔和迟答等保留为不可用配对。

评分前后间隔不一定等于30分钟。问卷延迟保留有符号整数，负延迟标记异常并排除，不钳成零。没有回答就是 `response=null`。

## 模型、对比量与解释

每个模型导出 `outcome`、`status`、`sample_ids`、保留 `terms`、原单位 `coefficients`、协方差矩阵、自由度、标准误方法、删除控制项和警示。模型无法估计时不填零系数。每日模型引用日期，会话模型引用回答ID，App模型引用transition ID。

发现明确区分：

| kind | contrast_outcome | comparison_unit | difference |
|---|---|---|---|
| DAILY_USE_TREND | DAILY_MINUTES_CHANGE | DAYS | h天跨度内拟合日用时变化（分钟） |
| SESSION_LENGTH | WITHIN_SESSION_MOOD_CHANGE | MINUTES | 同会话累计使用多h分钟对应的评分变化 |
| APP_USAGE | EXTRA_MOOD_CHANGE | MINUTES | 总主动用时、间隔、起始评分相同时，以目标App替换其他App h分钟对应的额外变化 |

h存为 `comparison_value`，不是固定窗口。App主模型的 `outcome=END_MOOD_SCORE` 与发现的 `EXTRA_MOOD_CHANGE` 分开表达。

`block_refits` 保留逐日／会话删除后的差值、可估计状态及方向一致性；`consistency` 是重算一致比例，不是正确概率。`ci_low/ci_high/p/q` 可为空，p／FDR不是显示门槛。时段敏感性模型另有ID，不自动替换主模型。重点App发现ID在 `top_app_finding_ids` 中，最多3项。

所有这些都是探索性关联；不会宣称因果、诊断、真实未使用手机心情基线或App内内容。

## 校验与兼容

```bash
python3 scripts/generate_period_schema.py
python3 scripts/validate_period_export.py your-export.json
```

校验工具需要 `jsonschema>=4.17`。它检查正式结构、周期条数、ID引用、窗口／配对时长、片段重算、模型样本与协方差维度、覆盖时间轴和重点发现。Android保存前也检查核心业务不变量。

正式示例在 [examples/implemented-exports](examples/implemented-exports/README.zh-CN.md)，由同一Kotlin引擎的合成测试生成，明确标记 `SYNTHETIC_TEST_FIXTURE`。开发机器schema不是用户需要附带的第二个文件。

历史 `2.0-draft.2` 设计保存在 [旧草案](PERIOD_EXPORT_SCHEMA_DRAFT2.zh-CN.md)；旧机器schema和示例保留为 `*-draft2.*`。校验脚本可按版本兼容旧示例，但Android只生成正式2.0。旧每日日报的record_type与导出入口保持独立。
