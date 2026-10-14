# T05 放宽核验时间预算（方案 A）与请求包身份（t05-30）

本轮 2026-10-14 15:41 开始，承接 `723937b157acb87af53a2147c4a2a90a828a1029`。
**真实供应商 0 次、数据库 0 次**；只做预算变更、本地验收与零调用包核对。

## 一、依据与授权

- 用户在本会话明确选择**方案 A**，即选项表中已列明的数值：**单次调用上限 10 秒 → 20 秒**、**整 run 上限 45 秒 → 90 秒**。
- 依据测量结论：核验的截止时间 = `min(单次上限, 整 run 剩余)`；整 run 里建库 + 检索 + 3 次决策已用掉约 34 秒，
  核验只拿到约 10.4 秒并以 10020 ms 撞满**单次上限**。**只放宽单次无效**，必须同时放宽两者。
- 这是**有界验证预算**，不是 SLA，也不构成外发许可。

## 二、改动

| 文件 | 改动 | 为什么 |
| --- | --- | --- |
| `RuleAgentContract` | `BUDGET_REVISION`→`T05_REVIEW_BUDGET_03`；`DEFAULT_BUDGET` 的 `maxModelMillis` 10000→**20000**、`maxRunMillis` 45000→**90000**；注释改写为记录用户授权与理由 | 预算身份必须随数值一起变，否则旧包会被误当成同一批次 |
| `RuleAgentOnlineProbeHost` | 包路径 `t05-30`、批次号 `P01_BUDGET_06`、批次说明改写 | 预算变了 → 旧包 SHA 与旧批次号一律不得复用 |
| 测试 | `RuleAgentContractTest`/`RuleAgentOnlineFactoryTest` 把预算与批次字面量改为引用常量；`RuleAgentRunnerTest` 的迟到回复用例改用 `DEFAULT_BUDGET.maxModelMillis()` 断言 | 避免再出现"字面量与常量脱钩"的假绿/假红 |

**未改**：模型 `qwen3.8-flash`、问题 P01、协议 `REVISION` 05、每 run 最多 4 次调用、无自动重试、
输出 token 1024、核验语义与发布守卫、1—3 句柄上限、学生页面与资料治理。

## 三、本地验收

- `com.zhku.knowledge.ruleagent.*Test` 8 个类 **146 项通过，0 失败 0 错误**（报告 15:48:05—15:48:45）。
  （首次运行有 4 项失败，全部是**预算/批次字面量未同步**，不是逻辑回归：1 处 `budgetRevision` 仍写旧值、
  2 处批次号仍写 `P01_STRUCTURED_05`、1 处断言 `Duration.ofSeconds(10)`；已全部改为引用常量并复跑通过。）
- 零调用 `--CheckPackage` 通过：`providerCalls=0`、`databaseOperations=0`、`protocolRevision=RULE_AGENT_ACTION_CONTRACT_05`。
- 零调用 `--CheckEnvironment` 回执：`timeoutMillis=20000`、`runTimeoutMillis=90000`、`budgetRevision=T05_REVIEW_BUDGET_03`、
  `model=qwen3.8-flash`，`providerCalls=0`。

## 四、未验证与风险

1. 放宽预算后核验是否真能在 20 秒内返回——**未验证**；若仍超时，说明瓶颈不在时间预算。
2. 核验超时的**成因**仍未确定（核验任务生成本身较长，或该次为偶发慢响应），属假设。
3. 整 run 放宽到 90 秒意味着**最坏情况一次运行的等待时间翻倍**；这是本次授权的已知代价。
4. 放开时间不改变任何权限、次数、候选范围或语义约束。

## 五、待授权（本文件不构成外发许可）

请求包身份：

- 路径：`docs/evidence/rule-agent-provider/2026-10-14/t05-30/request-package.json`
- SHA256：`f11c3af0ce3ceaafddd5508a242587684a9d801f55a93d5c91fbaad9f6c2450d`
- 批次号：`P01_BUDGET_06`；模型 `qwen3.8-flash`；预算标识 `T05_REVIEW_BUDGET_03`；次数上限 4、无自动重试。
