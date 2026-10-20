# Sprint02 · T08 离线准备

状态：进行中，真实模型/Judge均未授权、未执行。

| 任务 | 负责人/文件边界 | 状态 |
| --- | --- | --- |
| T08-A/C：20例与政策账本 | s02_policy_cases；cases/**、policy-ledger.json | 已集成68733ee；主控自检20例/12证据/7反例通过，独立复核可作历史限定试点参考 |
| T08-B：共同契约和Rubric | 主控；contract.md、rubric.json | v1已冻结，阈值待批准；9维覆盖7项要求，未计算模型成绩 |
| T08-D：Node计划/合成/冻结工具 | s02_eval_engine；tools/t08-acceptance/sprint02/**、test/sprint02* | 已集成98abd62/2e270d0；含主控输入投影后54项实际通过 |
| T08-D：任意冻结输入适配器 | s02_online_readiness；ruleagent/eval/**及专用脚本/测试 | 修复原在线入口只支持固定题的交付缺口，不调用供应商 |
| 集成、冻结、独立review和准入 | 主控 | 待三条线返回 |

依据：[共同契约](contract.md)、[Rubric](rubric.json)、[调用预算提案](run-plan.json)。原T08文档与校验器来自`578a9eb`，其历史“未执行”结论保持；T09仅查准入，不运行。

本轮基线：主目录`f500ce2`四份既有文档改动保留；Sprint01工作树`828c555`已跟踪文件干净，代码及证据已读取核对。当前独立集成分支`codex/sprint02-t08-evaluation`从828c555开始，不修改历史树。

保留集分工隔离的限制、正式评分规则和G2/G3/G4统计边界见contract。官方2025手册发布页已于2026-10-20核对（https://xsc.zhku.edu.cn/info/1154/15962.htm，发布2025-09-03）；附件与本地PDF字节一致性和现行适用性尚未确认。当前价格用[阿里云官方模型页](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)核对，仅浏览公开文档，不调用推理API。

## 本轮已取得证据

- 数据/Rubric冻结：[freeze.json](freeze.json)，外部固定SHA256 `87e2df031b667570ecfcc316acfb64e5b8cd1e7dd407d761958b85b2517d0300`。9个显式文件；后续不能重算pin冒充本冻结。
- 四组plan覆盖20例：development/G2=6，regression/G2=3、G4=3，holdout/G3=8；全部NOT_EXECUTED，质量全部NOT_EVALUABLE。G组与split不合并质量率。
- 合成成功链路PASS=1；另一个显式注入批次工程PASS=1/FAIL=1/INSUFFICIENT_EVIDENCE=1，按预期非零退出并保存全部三条，质量仍不可评估。这些只是验证评测程序。
- 原校验器22项 + 新引擎及故障回执26项 + 输入投影6项，共54项Node回归通过。最初主控误以为内置单例会触发故障，实际单例成功；该结果保留，另造明确合成故障输入验证非零退出，没有改真实题集或放宽断言。
- 独立政策复核：12/12原文来源摘要、审核摘要和区间一致；20例中15个HISTORICAL_SOURCE_CHECKED、5个SAFETY_EXPECTATION_ONLY。无本轮人工逐题签字。发现G4未触达注入点可能假通过，已由2e270d0补回执/动作关联门禁与红绿回归。
- 输入投影把8题变为四个双题文件，只保留pilotId/batch/id/question/supplements；不包含reference/expected/Rubric。主控不读取正文，具体输入与包摘要供最终授权绑定。

证据目录：`docs/evidence/sprint02/2026-10-20/`。真实调用次数、Judge、数据库操作均为0；实际模型质量与费用尚未取得。下一任务仍为适配器集成、实际Prepare/Check与准入报告。
