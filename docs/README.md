# docs/ 索引

仓库的文档、设计、计划与历史证据都在这一个目录下。**先看这张表，再决定点进哪个子目录**——不然后面 5400 个 evidence 文件会淹没你。

## 当前有效的

| 目录 | 内容 |
| --- | --- |
| `zhkuagent/` | 项目状态、范围、里程碑与产品交付说明。**找“现在到哪一步了”看这里**，入口是 `2026-10-18-project-status.md` |
| `design/` | 架构设计与接口契约，如 `rule-agent-contract-v3.md`、主流程设计 |
| `decisions/` | ADR：为什么这么设计。12 篇，按日期命名 |
| `plans/` | 阶段性计划与迁移清单 |
| `superpowers/` | 计划（`plans/`）与规格（`specs/`），当前集成收口计划在这里 |
| `reviews/` | 评审结论，如 obs 接口冻结评审、日志策略校验 |
| `manual/` | 怎么跑起来：本机 Nginx 预览、受控校园网页演示 |
| `releases/` | 版本说明与工程冻结：`v0.1.0-rc.1-engineering-freeze.md` |
| `product/` | 产品语义与规则评审材料（rule-check 各批次） |
| `collaboration/` | 跨会话协作记录：分工、并行竞态与排查过程，如 `2026-11-08-agent-eval-chain-ci-failure-triage.md` |
| `preparation/` | 各批次验收准备：验收矩阵、评分规则、证据格式与校验工具 |
| `design-references/` | 设计参考素材与 home 改版方案 |
| `retrospectives/` | 复盘：如 Agent 运行时生命周期问题 |

## 历史归档（体积大，按需查）

| 目录 | 内容 |
| --- | --- |
| `evidence/` | **5400 个文件 / 78.5 MB**。按里程碑与日期归档的执行证据：json 报告 2366、xml 1504、log 587、md 337。查证某个历史结论时才需要进来 |
| `agent/` | 迁移前的 README，历史留档 |
| `project/` | 交接记录（handoffs） |

## 约定

- 文件名以 `YYYY-MM-DD-` 开头，按时间排；中文名与英文名混用是历史原因，新文档优先英文短横线。
- 一句话结论写进 `zhkuagent/` 的状态文档，长篇论证放 `design/`，执行结果产物放 `evidence/`。
- 根目录的 `README.md` 只放项目定位、模块划分和构建方式，不再堆进度细节。
- 提交规范见 [`git-commit-workflow.md`](git-commit-workflow.md)。
