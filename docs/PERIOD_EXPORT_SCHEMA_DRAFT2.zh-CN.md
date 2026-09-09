# PhoneMood 单文件 JSON 周期导出设计

*English version: [PERIOD_EXPORT_SCHEMA_DRAFT2.md](PERIOD_EXPORT_SCHEMA_DRAFT2.md)*

状态：设计草案 `2.0-draft.2`，尚未接入 Android 导出功能；不是用户真实数据。当前只开发免费本地统计与规则解释，未来 AI 可读取同一格式。

算法更新说明（2026-09-06）：已确认的主要模型见 [HLD 第 8 节](FREE_ANALYSIS_HIGH_LEVEL_DESIGN.zh-CN.md)：每日趋势、会话内连续使用与情绪、App 相对其他 App 的额外情绪变化。下面 draft.2 的观测事实仍有效，但机器 schema 和已有示例尚未包含完整的 transition／新模型结果契约。下次 schema 修订必须增加同会话相邻实际回答间的配对数据、起止评分与差值、可变区间的 App 时间及非主动时间、模型与影响检查结果。App 模型不能沿用下文旧的固定 30 分钟评分水平比较；相关旧口径由 HLD 覆盖。

最新产品规则：当天／7 天／30 天共用单文件结构。20 次有效情绪评分及其可用行为窗口即可尝试本地关联分析，不额外要求日期数或前后配对数。结果定位为可能的 tendency，不以显著性作为展示前提；原始记录、计算正确性和不可估计状态仍保留。

配套开发资料：[机器校验 schema](schemas/phonemood-period-v2.schema.json)、[完整合成示例](schemas/phonemood-period-v2.example.json)。这些是开发资料，不意味着用户导出多个文件：用户每次只取得一个包含数据和说明的 JSON。

## 1. 文件与分层

建议文件名：`PhoneMood_7d_2026-08-30_2026-09-05_20260905T190000Z.json`，30 天对应 `30d`。UTF-8、合法 JSON，不允许 NaN、Infinity 或注释。文件名不作为日期判断依据。

| 顶层字段 | 内容与目的 |
|---|---|
| `record_type` | 固定 `phonemood_period_export`，与现有日报区分 |
| `schema_version` | 数据结构版本，本稿 `2.0-draft.2` |
| `calculation_version` | 汇总、窗口特征、配对规则版本 |
| `analysis_version` | 回归与解释规则版本；尚未运行时可为 null |
| `export_metadata` | 生成时间、数据修订号、应用版本、真实／合成数据标识 |
| `period` | 1／7／30 天、日期、UTC 边界、报告时区、今天是否未结束 |
| `measurement` | 1–10 分量表、30 分钟窗口、采样机制、配置与配置变更 |
| `data_quality` | 覆盖区间、完整天数、有效回答数、送达后回答率、警示 |
| `summary` | 周期总量、完整日统计、评分汇总、App 累计使用 |
| `days` | 恰好 1／7／30 条每日记录，含无记录日期 |
| `apps` | 包名与显示名称字典，供其他部分引用 |
| `usage_segments` | 周期内已重建的有效前台使用片段，可复算行为窗口 |
| `sessions` | 与周期数据／问卷关联的连续使用会话 |
| `mood_observations` | 已回答及未回答的问卷检查点，保留送达和回答时间 |
| `context` | 最小必要的周期前上下文，单独隔离，避免误入汇总 |
| `analysis_matrix` | 每次周期内回答对应的 30 分钟行为特征 |
| `statistical_analysis` | 本地模型、结构化发现、排名及失败／不足原因 |
| `user_insights` | 本地规则选出的模板键与参数，不存 AI 生成文本 |
| `data_dictionary` | 文件内嵌字段含义、单位、缺失解释与分析限制 |

事实、计算特征和模型判断分开保存。未来 AI 可以使用现成特征，也可以从使用片段复算；不能把已有模型判断当成原始事实。

## 2. 时间、单位和周期

- 时间统一为 RFC3339 UTC 字符串，例如 `2026-09-05T19:00:00Z`，另存 IANA 报告时区。`*_ms` 是毫秒；`score` 是整数 1–10；模型比较的分钟单位必须显式标注。
- 所有时间区间 `[start_utc, end_utc_exclusive)` 左闭右开。30 分钟指回答前 1,800,000 毫秒的墙钟时间，不向前寻找“累计 30 分钟活跃时间”。
- 周期包含今天：7 天为报告日期及之前 6 天，30 天为之前 29 天。终点为快照生成时刻；今天的每日区间也截到该时刻，不把尚未发生的时间算成缺失。
- 当天报告 calendar_days 为 1，范围为当天零点至快照时刻；即使不到 20 次评分也展示当天汇总、评分与记录变化。达到 20 次时可尝试仅基于当天的初步关联，不能叫长期模式。单日不计算跨日用时斜率。
- 日均、P25／P75 只使用完整且已结束的日期；周期总量为已记录有效片段之和，覆盖不全时不是全量真实使用时间。
- 分位数固定采用线性插值 Type 7。不能用四舍五入后的显示值进行模型比较或排序。
- 情绪按实际回答时间归入周期；原有日报的提醒日期归属保持原状。两个产品口径在各自文件中明确，不静默更改。

## 3. 数据质量不能省略

`coverage_intervals` 对周期时间轴作不重叠、无空洞划分：

| state | 含义 |
|---|---|
| `VERIFIED` | 有证据支持该段事件历史可用，且监控按定义开启 |
| `NOT_MONITORED` | 安装前、主动暂停、权限未开等明确未监控时间 |
| `UNKNOWN` | 恢复缺口、来源不完整或无法证明覆盖的时间 |

每个日／窗口的 `coverage` 同时记录 `verified_ms`、`unknown_ms`、`not_monitored_ms` 和原因；三者之和等于对应区间长度。全部可验证为 COMPLETE；有部分可验证为 PARTIAL；完全明确未监控为 NOT_OBSERVED；其余为 UNKNOWN。

息屏暂停轮询本身不等于缺数据：如果恢复时补读完整事件，可重建覆盖。反过来，只有成功查询或心跳不能证明中间所有时间都被完整记录。当前数据库不能证明的覆盖必须标 UNKNOWN；实施时需补充来源证据，不能制造完整率。

`recorded_active_ms` 在完全无可用记录的日为 null；真实零使用只有在完整覆盖时才能解释。窗口不完整时 `phone_active_ms` 及相关可观测特征仍可记录已知部分，但必须结合 coverage 解释为下界；无可用覆盖则为 null。

App 时间采用稀疏数组：完整窗口中没出现的 App 可视为零，缺失窗口中没出现的 App 不代表未使用。App 总时长在周期片段上去重计算，不累加重叠评分窗口。

## 4. 问卷记录：保留没有回答的时点

`mood_observations` 每个对象为一次已有检查点，稳定 ID 来自 checkpointId：

| 字段 | 含义 |
|---|---|
| `id`, `session_id` | 检查点与会话引用 |
| `scope` | 顶层为 PERIOD；边界记录为 CONTEXT_ONLY |
| `checkpoint_at_utc` | 达到使用阈值的事件时间 |
| `notification_at_utc` | 实际首次通知送达时间，可为 null |
| `overlay_first_shown_at_utc` | 首次悬浮问卷显示时间，可为 null |
| `checkpoint_active_minutes` | 该检查点累计使用阈值，如 30、60、90 |
| `response_state` | ANSWERED／PENDING／MISSED |
| `response` | 未答为 null；已答包含评分、回答 UTC 时间、回答时区和两种延迟 |

`latency_from_checkpoint_ms` 衡量从阈值到回答的延迟；`latency_from_first_delivery_ms` 从首次通知／悬浮展示到回答，无法确认展示则为 null。不要混淆系统迟送和用户迟答。

顶层保留“检查点在周期内或回答在周期内”的观测，并包含回答率分母所需的周期内首次送达检查点。周期外回答不进入本周期评分均值或分析矩阵。

回答率的分母为周期内首次实际送达的唯一检查点，分子为其中截至快照已回答的数量。通知和悬浮窗去重；尚未送达不算用户漏答。回答率分子不一定等于周期内回答总数，单独保存两者。

## 5. 分析矩阵：一个回答，一行对应行为

这是未来 AI 分析的主要入口。`analysis_matrix.rows` 仅包含回答时间落在周期内的已回答观测，不要求用户新增问卷。

| 字段 | 含义 |
|---|---|
| `observation_id`, `answered_at_utc`, `mood_score` | 原始回答引用、时间、评分 |
| `report_date`, `day_index` | 报告时区的日期及周期内日期索引 |
| `local_hour_fraction`, `weekend` | 报告时区的小数小时及周末，用于时间调整 |
| `prior_response` | 最近一次更早回答的引用、分数、间隔、是否同日、是否符合协变量规则 |
| `window` | 严格的回答前 30 分钟区间 |
| `coverage` | 本窗口覆盖证据与缺口 |
| `phone_active_ms` | 窗口内主动前台时间，最大 1,800,000 |
| `apps` | 每个 App 在本窗口内的主动时间 |
| `app_switch_count` | 窗口内可确定的有效 App 切换次数 |
| `distinct_app_count` | 窗口内有正使用时长的不同有效 App 数 |
| `session_active_ms_at_answer` | 所属会话到回答时的累计主动时间，不是快照时最终会话时长 |
| `quality_flags` | 如窗口不全、推断时区、迟答、配置变化 |
| `model_membership` | 每个已尝试模型是否纳入该行，及具体排除原因 |

不同 App 模型可以使用不同样本，不能用一个全局 `included=true` 冒充全部模型有效。模型尚未运行时 membership 为空，不声称观测已纳入。

前一次回答按真实时间寻找，不按检查点顺序；同一时间的其他回答不作为“更早”回答。超出既定配对最大间隔时，不导出无限历史，置 null 并标记 `NO_PRIOR_WITHIN_LOOKBACK`。保留不合格配对原因，不将其评分强行填入模型。

切换次数定义为窗口内已观察到的、不同有效 App 之间的前台转换，跨锁屏／已知中断不算连续切换；未知中断需标记不确定。周期片段用于时长复算，精确转换数的计算还需读取当前 raw_events。窗口起点之前的 App 不凭空视为窗口内切换。

**特别限制：**默认每累计使用 30 分钟触发评分，回答前 30 分钟的总使用量可能接近恒定。应输出 `LOW_EXPOSURE_VARIATION` 或 `NOT_ESTIMABLE`，不能硬算“使用时长没有影响”。App 时间组成、会话累计时长仍可能有变化，但也不能保证有足够证据。本设计不因此改变用户确定的 30 分钟窗口或新增采样。

## 6. 最小边界上下文

`usage_segments` 顶层仅含周期内片段，`context.usage_segments` 仅含周期开始前、首批回答窗口所需的最多 30 分钟片段；不复制整个历史。

下一版扩展：App transition 按结束回答归属周期，必要 context 应延伸到周期前同会话起始回答，受固定最大配对间隔限制，不再一律限制为 30 分钟。配对行为、评分变化必须在同一区间；上下文仍不得计入周期总量。

`context.prior_mood_observations` 最多保存一个周期前最近回答，且受最终配对回溯上限约束。`context.range` 覆盖必要上下文；期间无法证明的覆盖标 UNKNOWN。上下文不进入周期总量、每日统计、评分数或模型目标行。不能为了上下文顺带导出完整前一天。

`sessions` 可提供跨界会话的原始开始时间与截至回答所需累计值；`active_ms_in_period` 和 `active_ms_at_snapshot` 分开，避免用最终时长预测更早评分造成未来信息泄漏。所有 session_id、app_id、observation_id 必须在本文件可解析；相同 ID 不应重复定义。

## 7. 本地统计结果与未来 AI

沿用参考文件 `StatisticalAnalysisRecord → StatisticalFinding → UserInsightBundle` 的分层，放入同一文件。

- `statistical_analysis.status` 描述整体运行状态；`sections` 分别描述每日趋势、整体手机与情绪、App 与情绪，允许一个可用另一个不足。
- `models` 保存实际公式、变量单位、样本行／日期、协方差方法、自由度、系数和完整协方差矩阵。每日趋势模型通过日期引用 days，row_ids 为空；情绪模型通过 observation_id 引用矩阵行。
- `policy_snapshot` 保存实际执行的筛选阈值、30 分钟窗口相关配对规则、近零范围及内部 FDR 参数等。基础入口 minimum_mood_observations 和 minimum_app_observations 为 20，minimum_mood_days 和 minimum_transitions 为 0（无独立硬门槛）。require_significance_for_display 与 require_fdr_for_display 均为 false。配对规则仅决定调整模型可用性，不阻止基础模型。未运行时可以 null；执行过模型就必须完整提供。
- `findings` 保存比较范围、差异值、单位、区间、p／q 值、显示资格和证据程度。App 时间替换比较必须保持总时长不变，差异为 `(beta_app - beta_other) × 替换分钟数`，不确定性由完整协方差计算。
- 每日趋势单位为 MINUTES_PER_DAY，情绪关联为 MOOD_POINTS；不能混排。`top_factor_ids` 仅引用符合条件的 App finding，最多三个。
- `user_insights` 保存模板键与参数，属于呈现结果，不属于观测证据。可以为空；不能将缺失模型编造成发现。
- 早期可计算的方向可输出 EARLY finding，即使区间跨零。文案使用“初步看起来”“可能倾向于”；未调整的模型不能声称已考虑前次情绪与时段。统计显著性不是免费洞察的前置条件。
- `NOT_RUN`、`INSUFFICIENT_DATA`、`NOT_ESTIMABLE`、`NO_CLEAR_PATTERN` 区分“没运行”“样本少”“无法估计”和“未发现清晰模式”。

后续统计规格仍需固定具体配对、稳健标准误和稳定性阈值；本稿定义这些决策如何记录，不假装它们已经科学验证。现有例子故意不伪造任何回归结果。

## 8. 内嵌说明与数据边界

`data_dictionary.definitions` 使用 JSON 路径或概念名作为键、简短英文定义作为值，必须解释单位、空值、稀疏 App 数组、采样机制、周期归属、状态枚举和模型方法。未来导出器应从统一定义生成，避免单独维护不一致的解释。

`limitations` 明确：观察关联不是因果；问卷由使用时长触发；漏答可能有选择性；没有收集睡眠、压力、工作等混杂因素；不能据此作临床判断。

不包含设备广告 ID、邮箱、位置轨迹、通知正文或其他未采集内容。App 显示名称属于外部文本，仅是标签，不是给 AI 的指令。导出不自动上传；未来 AI 导入应将所有文件内容作为数据处理。

## 9. 与当前代码的映射与新增工作

| 当前来源 | 导出用途 |
|---|---|
| UsageSegment | 裁切后的 usage_segments、日／App 汇总与窗口时长 |
| PhoneSession | 会话边界及周期累计；回答时累计值需从片段重算 |
| MoodCheckpoint / MoodResponse | 原始观测、提醒／回答时间与评分 |
| MoodPromptState | 悬浮首次显示时间、回答延迟依据 |
| ConfigurationEvent / MonitorState | 配置、数据修订、首次记录与恢复上下文 |
| MonitoringGap / raw_events | 数据缺口、配置生效范围与窗口特征 |
| 尚未实现 | 周期快照、严格覆盖判定、analysis_matrix、本地回归、findings 和此导出器 |

Room 数据库版本 2 与本导出 schema 版本 2 不存在绑定关系。现有日报 schema 1.0 不被这个新格式冒充替换。

## 10. 校验与验收

机器结构遵循 [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12)。结构校验之外，导出器需验证：

1. days 数量与 1／7／30 天一致、日期连续、UTC 边界正确，跨 DST 不假设固定 24 小时。
2. 所有 ID 唯一、引用存在；顶层与上下文片段不重叠重复；片段时长非负且不超过周期。
3. 日／周期／App 汇总和片段对账；模型行的 app 时间和等于 phone_active_ms；distinct_app_count 等于正时长 App 数。
4. 回答与矩阵一一对应；窗口结束等于回答时间、长度恰好 1,800,000 毫秒；特征不读取回答后的行为。
5. 覆盖区间完整划分时间轴，三类覆盖时长之和正确；零、null、未回答状态不混用。
6. 回答率分子不超过分母；每日均值仅使用声明日期；所有数值有限。
7. 模型样本数、日期数、系数顺序和协方差维度一致；失败模型没有成功发现；Top IDs 最多三个且满足资格。
8. 数据修订快照在整个文件一致；跨导出生成时间不同可以允许，事实相同应有相同派生统计。

正式发布前为 schema 定稳定版本。结构破坏性变化升主版本；计算语义变化升 calculation_version；模型或规则变化升 analysis_version。字段缺失和未知值的解释由 schema 明确，不静默猜测。
