# T05 工具调用协议对齐与请求包身份（t05-25）

本轮 2026-10-14 15:02 开始，承接 `67b9850b0a88ea65f2714432b37dc6bc9c11435a`。
**真实供应商 0 次、数据库 0 次**；只做协议一致性修正、请求体预算实测与包身份更新。

## 一、为什么 t05-24 的包作废

`67b9850` 把决策侧动作生成改成了工具调用，但系统提示词仍在要求模型「**只输出一个下一动作 JSON**」，
并指向 `actionContract.actions`。这是**请求合同内部自相矛盾**：新协议要求工具调用，提示词却仍在描述旧文本协议，
模型有可能把动作写进 `content` 而不是发起工具调用，从而被宿主以 `ACTION_CALLS_INVALID` 拒绝。
因此提示词必须与工具协议对齐，包身份随之更新为 **t05-25**。旧包不删除、不改写，仅标记被取代。

## 二、本轮改动

| 文件 | 改动 | 为什么 |
| --- | --- | --- |
| `RuleAgentProvider.SYSTEM` | 决策相关行改为工具语义：每次只调用一个已登记动作工具、必须调用 `tools` 中登记的函数、参数只填该函数声明的字段、字段与限制以工具参数定义为准、不得调用 `tools` 以外的动作 | 提示词与线协议必须一致；策略约束（仅解释 2025.9.2、不判断个人资格、不将执行故障当资料缺失、operation 为 null 等）**逐条保留未放宽** |
| `RuleAgentOnlineProbeHost` | 包路径改为 `t05-25/request-package.json` | 提示词变更导致包内容变化，旧 SHA 授权不得复用 |
| `RuleAgentOnlineFactoryTest` | 记录真实请求体字节数，在合法 1/2/3 用例中断言其小于 `maxRequestBytes`，并落盘实测值 | 新增 `tools` 会增大请求体，必须证明不触发宿主侧 `BUDGET_EXCEEDED` |

**未改**：动作字段/必填/枚举/句柄 1—3 上限、编号映射、运行循环、Broker、发布守卫、证据工具、时间与次数预算、
核验侧协议、学生页面与资料治理。

## 三、请求体预算实测（工具调用后）

```json
{"maximumRequestBytes":65536,"measuredRequestBytes":4453,"headroomBytes":61083,
 "selectedCount":3,"candidateCopies":10,"toolsIncluded":true,"responseFormatAbsent":true}
```

即"带 4 个工具定义 + 10 个当次候选 + 审核原文"的一轮决策请求只占上限的 6.8%，余量充裕。

## 四、本地验收

- `com.zhku.knowledge.ruleagent.*Test` 7 个类 **144 项通过，0 失败 0 错误**（Provider 45、Factory 32、Runner 21、
  PublicationGate 15、TaskStore 14、EvidenceTools 10、Contract 7），报告时间 15:05:44—15:06:05。
- 零调用 `--CheckPackage` 通过：`providerCalls=0`、`databaseOperations=0`、
  `protocolRevision=RULE_AGENT_ACTION_CONTRACT_04`。
- 详细计数、请求体实测与源码 SHA 见 `local-verification.json`。

## 五、未验证项（不构成保证）

1. `qwen-plus` 实际端点是否接受 `tools`/`tool_choice`/`parallel_tool_calls`；
2. `finish_reason` 实际取值（本地同时接受 `tool_calls` 与 `stop`）；
3. 工具参数 Schema 是否被供应商强制（官方无 `strict` 语义，本方案按"不强制"设计）；
4. 与 `max_completion_tokens=1024` 的共存性；
5. 工具调用能否改善真实 READ 选择数量。

## 六、待授权（本文件不构成外发许可）

对外发布前需用户给出 **P01 单独批次**的具体外发许可。请求包身份：

- 路径：`docs/evidence/rule-agent-provider/2026-10-14/t05-25/request-package.json`
- SHA256：`78909e8a3f3a37a4db8ea6934e4d86a46cf6869f2fcab243359a51e962a432fe`
- 探针批次号：`P01_TOOL_CALL_04`（旧批次号与旧 SHA 一律拒绝）
