# 动态 Agent 轨迹说明（模拟／真实局部／完整在线成功，分开标记）

本文件只描述**已经存在**的轨迹，不新增调用、不生成未来题面、不改旧失败记录。
三类身份严格分开：**SIMULATED**（本地假服务）、**REAL_PARTIAL**（真实供应商局部）、**REAL_COMPLETE**（真实全链路成功）。

---

## 一、REAL_PARTIAL：t05-34 唯一真实尝试（可追溯）

| 项 | 值 |
| --- | --- |
| 批次／包 | `P01_CLOSEOUT_07` / `8a3a4548fc72814d76f732d39692ccc9bd41b87a48502caddce248d7e866731a` |
| 执行模式 | `ONLINE`（北京 DashScope，`qwen3.8-flash`） |
| 源码身份 | 执行前 HEAD `f91ae15`，实现提交 `99da32c`，17 个源码漂移 0 |
| run 身份 | `771462d0-2064-4fe4-b330-cb22eda14d70`（证据产物 `…-outcome.json` 文件名即 runId；taskId 未写入脱敏摘要，原始 trace 留本机） |
| 证据入口 | `docs/evidence/rule-agent-provider/2026-10-14/t05-34-p01-closeout/actual-outcome-sanitized.json` |

问题：**专业选修课和公共选修课在修读安排上有什么不同？**（P01，属已曝光开发任务）

| # | 模型动作 | 工具观察 | 状态变化 | 终止原因 |
| --- | --- | --- | --- | --- |
| 1 | `DECISION` 第 1 次：请求已构造并派发（4557 B，派发标记 14 ms） | 无（工具步 0） | 任务 ACTIVE → 运行结束 | **`MODEL_TIMEOUT`**：20031 ms 内无 HTTP 状态、无响应头、无响应体、无 requestId、无 usage |
| — | 无第 2 次动作 | — | `taskStatus=FAILED`、`runState=FAILED`、`terminated=true`、`coverage=NONE`、`answerSource=NONE`、`issues=MODEL_TIMEOUT` | 额度消耗 1、剩余 4 关闭、无自动重试 |
| — | SEARCH／READ／FINISH／独立核验／发布 | 全部 `NOT_EXECUTED` | — | 未到达 |

**该轨迹的缺失**：独立核验、发布、任何工具观察均不存在。**不得**由本条轨迹推断“检索/读取可用”或“故障已消失”。

---

## 二、SIMULATED：本地假服务上的完整轨迹（仅证明宿主侧机制）

来源：`RuleAgentOnlineFactoryTest`（入口为该测试文件，运行身份见 `README.md` 的指纹表；task/run 为运行时随机 UUID，不是稳定身份）。

| 场景 | 模型动作序列 | 工具观察 | 状态变化 | 终止原因 |
| --- | --- | --- | --- | --- |
| P01 全链路（`legalCountsPublishBothReviewedStudyFactsWithoutTruncatingCandidates`） | 4 次：`SEARCH_REVIEWED_RULES` → `READ_VERIFIED_RULE` → `FINISH_CHECK` → `PUBLICATION_REVIEW` | SEARCH 候选 10 条、READ 命中 `VERIFIED_FACT` | ACTIVE → `RESOLVED`；发布 2 条 `T02/ARTICLE4` 已核验事实 | 正常结束（`FINISH_CHECK` + 同 run 核验通过） |
| 澄清续答（`realTaskStoreTokenContinuesThroughOnlineFactoryAndSharesBatchQuota`） | 首 run 追问 → `WAITING_USER`；携 continuation token 新建 run → 继续至 `FINISH_CHECK` + `PUBLICATION_REVIEW` | 同假服务 | `WAITING_USER` → `RESOLVED`；两 run 共享同一批次额度 | 正常结束 |
| 同 run 核验强制（`p01ProbeRequiresSameRunReviewAndPublicationRatherThanTwoFactIds`） | 缺少 `PUBLICATION_REVIEW` 时判定不通过 | — | 不发布 | 核验缺失即不通过 |

**这些是模拟轨迹**：供应商是本地 HTTP 假服务（`LOCAL_TEST_ONLY`），只证明**宿主侧**协议、状态机、预算、核验与发布门禁；
**不能**作为在线行为证据。

---

## 三、REAL_COMPLETE：完整在线成功

**缺失，计数 0。** 依据：`T05-CLOSEOUT.md` 第 10.1 节——5 个链路段中 SEARCH／READ／FINISH 曾真机执行，
**独立核验“仅尝试过”（t05-28 第 4 次调用 `MODEL_TIMEOUT`）、发布“从未执行”**；
`t05-34` 更在首个决策即超时。因此**不存在**任何“真实全链路 + 核验通过 + 发布成功”的轨迹。

**在线澄清续答**同样为 **0 次**（只在模拟中出现）。

**禁止措辞**：不得写“澄清后成功发布”“线上全链路跑通”“T05 已完成”。缺失必须写成缺失。
