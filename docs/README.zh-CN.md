# PhoneMood 文档

*English version: [README.md](README.md)*

每份文档都有中英两个版本。**英文占主文件名，中文版加 `.zh-CN` 后缀。** 两者保持对应——修改其中一份时，另一份也应同步。

建议从[项目总览](../README.zh-CN.md)和[文献综述](LITERATURE_REVIEW.zh-CN.md)开始。

## 研究与测量

| 文档 | 中文 | English |
|---|---|---|
| 文献综述——情绪困扰、智能手机使用与日常测量 | [中文](LITERATURE_REVIEW.zh-CN.md) | [EN](LITERATURE_REVIEW.md) |
| 历史验证记录（1.0.0） | [中文](VALIDATION.zh-CN.md) | [EN](VALIDATION.md) |
| 统计策略 1.0——已冻结的计算参数 | [中文](ANALYSIS_POLICY_V1.zh-CN.md) | [EN](ANALYSIS_POLICY_V1.md) |

## 设计

| 文档 | 中文 | English |
|---|---|---|
| 总体设计——记录、会话、问卷与日报 | [中文](HIGH_LEVEL_DESIGN.zh-CN.md) | [EN](HIGH_LEVEL_DESIGN.md) |
| 免费本地分析——总体设计 | [中文](FREE_ANALYSIS_HIGH_LEVEL_DESIGN.zh-CN.md) | [EN](FREE_ANALYSIS_HIGH_LEVEL_DESIGN.md) |
| 免费版 User Stories 与验收条件 | [中文](FREE_TIER_USER_STORIES.zh-CN.md) | [EN](FREE_TIER_USER_STORIES.md) |
| 回顾与导出流程 | [中文](REVIEW_WORKFLOW.zh-CN.md) | [EN](REVIEW_WORKFLOW.md) |
| 认知无障碍界面说明 | [中文](ADHD_FRIENDLY_UI.zh-CN.md) | [EN](ADHD_FRIENDLY_UI.md) |

## 数据格式

| 文档 | 中文 | English |
|---|---|---|
| 周期导出 schema 2.0（正式） | [中文](PERIOD_EXPORT_SCHEMA.zh-CN.md) | [EN](PERIOD_EXPORT_SCHEMA.md) |
| 周期导出设计草案 2（已归档） | [中文](PERIOD_EXPORT_SCHEMA_DRAFT2.zh-CN.md) | [EN](PERIOD_EXPORT_SCHEMA_DRAFT2.md) |
| 由实际引擎生成的导出示例 | [中文](examples/implemented-exports/README.zh-CN.md) | [EN](examples/implemented-exports/README.md) |

## 平台行为与版本

| 文档 | 中文 | English |
|---|---|---|
| 自适应电池策略 | [中文](BATTERY_STRATEGY.zh-CN.md) | [EN](BATTERY_STRATEGY.md) |
| 悬浮心情提醒（1.1） | [中文](OVERLAY_UPDATE.zh-CN.md) | [EN](OVERLAY_UPDATE.md) |
| 安静统计（1.3.1） | [中文](QUIET_NOTIFICATIONS.zh-CN.md) | [EN](QUIET_NOTIFICATIONS.md) |
| 自动开始与快捷权限配置（1.3） | [中文](AUTOMATIC_SETUP.zh-CN.md) | [EN](AUTOMATIC_SETUP.md) |

## 测试

| 文档 | 中文 | English |
|---|---|---|
| 真机验证清单 | [中文](DEVICE_TESTING.zh-CN.md) | [EN](DEVICE_TESTING.md) |
| 在电脑上测试（模拟器） | [中文](COMPUTER_TESTING.zh-CN.md) | [EN](COMPUTER_TESTING.md) |
| README 截图及其来源说明 | [中文](screenshots/README.zh-CN.md) | [EN](screenshots/README.md) |
| 工程交接——一个尚未解决的通知 bug | [中文](../HANDOFF.zh-CN.md) | [EN](../HANDOFF.md) |

## 关于结论的说明

这些文档区分了「已经构建并测试过的」与「尚未验证的」。**工程验证和合成示例不能确立可用性、测量效度，或心理／教育层面的有效性。** 已知的局限会写在文档里，而不是略去。
