# 实际引擎生成的导出示例

这三个 JSON 由 `AnalysisTest` 使用同一套 Android `PeriodDatasetBuilder`、`StatisticalEngine` 和 `PeriodExport` 生成，然后只把 `data_origin` 改成 `SYNTHETIC_TEST_FIXTURE`。它们用于阅读和 schema／业务校验，不是真实用户数据。

- `PhoneMood_1d_SYNTHETIC.json`：当天快照，包含今天可能未结束的日期。
- `PhoneMood_7d_SYNTHETIC.json`：滚动七个自然日。
- `PhoneMood_30d_SYNTHETIC.json`：滚动三十个自然日。

样本中的 App 额外变化、连续使用倾向和每日趋势都是由固定合成输入计算出来的，方便未来 AI 读取 `statistical_analysis` 并回到原始 `analysis_matrix`／`transitions` 复核。
