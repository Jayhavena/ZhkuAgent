# 新请求包申请材料（`P01_DIAGNOSTIC_01`）——**须用户另行明确批准后才可外发**

本文件是**申请**，不是许可。**在收到用户对本包指纹与费用上限的明确批准之前，不得执行任何真实调用。**

## 一、包与构建身份

| 项 | 值 |
| --- | --- |
| 请求包路径 | `docs/evidence/rule-agent-provider/2026-10-14/t05-36-diag/request-package.json` |
| 包指纹 SHA256 | `4da0c6cb069948c5214c4226055918b0f13980e9833f1f1dc72f38e51385f2ee` |
| jar | `knowledge/target/knowledge-0.1.0-rc.1-controlled.jar`，SHA256 `94e450f3a0cde1d34cbdf203d15f33c6604fc1d1df5e319f999d9b8e7f57c237`（21:42 构建；其后仅测试源码变动，主代码身份不变） |
| 分支 / 起点 HEAD | `codex/t05-online-evidence` / `5fc77f3`（父 `58e1ef7` 冻结基线） |
| 批次号 | `P01_DIAGNOSTIC_01` |
| 执行策略修订 | `STATE_AWARE_DIAGNOSTIC_01` |
| 授权状态 | `NOT_AUTHORIZED`（包内 `authorizationStatus` 字段） |

改动源码指纹见 `README.md` 第一节。

## 二、外发参数（模型／协议／预算**与上批一致，不并存变更**）

| 项 | 值 |
| --- | --- |
| 端点 | 北京 DashScope `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| 模型 | `qwen3.8-flash`（决策与独立核验**同一模型**，见 `VERIFICATION-MECHANISM.md` 第三节） |
| 协议版本 | `RULE_AGENT_ACTION_CONTRACT_05`；发布核验 `RulePublicationContract.REVISION` |
| 问题（1 条，已曝光开发任务） | 专业选修课和公共选修课在修读安排上有什么不同？ |
| 单次超时 | 20 000 ms |
| run 超时 | 110 000 ms（外层自有进程 125 s） |
| 每 run 调用 | 4 次决策 + 1 次独立核验 = 5 次 |
| 输出上限 | 1024 token |
| 自动重试 | **0** |

**相对 `P01_CLOSEOUT_07` 的唯一差异**：宿主侧新增连接层观测（`connectedMillis`）。模型、端点、协议、预算、问题、schema 形状**均未改变**。

## 三、次数与费用上限（申请值，须用户确认）

| 项 | 申请值 | 依据 |
| --- | --- | --- |
| 探针启动 | 1 次 | 一次有界批次 |
| 真实调用上限 | **5 次** | 1 run ×（4 决策 + 1 核验） |
| 失败后追加 | **0** | 失败即停止本批、关闭剩余额度、不补测、不重试 |
| 费用上限 | **人民币 5 元** | 沿用上批保守口径：按 qwen3.8-flash 输入 0.8 元/百万 token、输出 2.7 元/百万 token，每次按 100 万输入 token 上界 + 1024 输出预留 = 0.8027648 元/次 × 5 = **4.013824 元** ≤ 5 元；不依赖折扣、免费额度或缓存 |

**实际费用可能在超时情形下仍未知**（`t05-34` 即如此）；届时如实记“未知”，不记 0。

## 四、本批可观测目标与判定

1. **首要**：首决策请求能否在 20 s 内取得**响应头**。
   - 若取得响应 → 记录 `headersMillis/status/requestId/usage`，继续观察工具步与核验。
   - 若仍超时 → 由 `connectedMillis` 区分：**未连通**（网络路径问题）还是**已连通但无响应头**（TLS 或供应商侧静默）。这正是本批新增诊断的目的。
2. 工具执行：SEARCH / READ 是否真实执行并命中已核验事实。
3. 独立核验与发布：是否在同 run 内完成（历史上**从未成功到达发布**）。
4. 无论结果如何：核对资源退出（Java 残留 0、临时容器 0、演示库仍停止），保存脱敏证据，**不改旧失败记录**。

**P01 通过判据不变**（见 `RuleAgentOnlineProbeHost.p01Passed`）：同 run 内 `SEARCH+READ+FINISH+PUBLICATION_REVIEW` 全部完成、
发布两条 `T02/ARTICLE4` 事实、`coverage=COMPLETE`、`answerSource=LIVE_AGENT`。**阶段到达不能代替整链成功。**

## 五、停止条件

- 5 次调用用尽、或出现失败/超时 → **立即停止本批**，关闭剩余额度，不追加、不重试、不自行改预算或换模型。
- 任何“试图提高上限/换模型/放宽核验”的动作都需**重新申请并重新授权**。

## 六、待批准事项（缺一不可）

1. 批准**本包指纹** `4da0c6cb…85f2ee` 对应的一次有界外发；
2. 确认**调用上限 5 次**、**费用上限 5 元**；
3. 确认运行终端具备 **`pwsh` 7**（本会话无，启动器 `#requires -Version 7.0` 无法运行；批准后需在此类终端执行启动器）。
