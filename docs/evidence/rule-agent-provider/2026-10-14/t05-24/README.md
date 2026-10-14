# T05 决策侧动作生成改为工具调用（第二步：最小适配）

> **本目录的 `request-package.json` 已被取代，禁止用于任何外发申请。**
> 原因：本包生成时系统提示词仍要求模型「只输出一个下一动作 JSON」，与工具调用协议自相矛盾；
> 提示词已在 t05-25 对齐，包身份改为 `docs/evidence/rule-agent-provider/2026-10-14/t05-25/request-package.json`。
> 本目录的源码改动与测试结论仍然有效；仅包身份与 SHA 作废。本文件保留原样不改写。

本轮 2026-10-14 14:46 开始。基线 `1783fd8959db5e46130a41593fbfe911f8e4352b`（第一步能力查证的提交）。
**真实供应商 0 次、数据库 0 次**；只做本地最小适配、本地模拟验收与零调用包核对。

## 一、方案与实现范围

依据第一步查证结论：对固定模型 `qwen-plus`，官方承诺工具调用、未承诺 JSON Schema 严格结构化输出。
因此把**决策侧**动作生成从「`response_format=json_object` + 消息内文本合同」改为**工具调用**；
**独立核验侧保持 JSON 文本协议不变**（官方未承诺结构化输出与工具调用在同一次请求内的共存性，不混用）。

| 文件 | 改动 | 为什么 |
| --- | --- | --- |
| `RuleAgentProtocol` | 新增 `tools(readReferences)`：由 `JsonSubTypes` 动作名、record 组件（字段/必填）、`MIN_READ_HANDLES`/`maxReadHandles`、`IDENTIFIER_PATTERN`、领域枚举派生 JSON Schema；新增 `actionJson(name,arguments)` 把供应商动作还原为原领域动作 | 机器可读动作定义必须与权威合同同源，不能另写一套字段与限制 |
| `RuleAgentContract` | 新增 `IDENTIFIER_PATTERN`/`MAX_FINISH_PROPOSALS`/`MAX_FACT_IDS`/`MAX_SUPPORTING_FACT_IDS`/`MAX_PROPOSED_ISSUES`，值校验与动作定义共用同一常量 | 同一限制只定义一次，避免线协议与宿主校验漂移 |
| `RuleCandidateReferences` | 新增 `references()`：暴露当前 `c1..cN` 编号界面，UUID 仍留宿主 | 候选枚举与 `currentCandidates` 来自同一次刷新 |
| `RuleAgentProvider` | `payload` 同一次刷新内取候选枚举并生成 `tools`；`envelope` 改为下发 `tools`+`tool_choice:"auto"`+`parallel_tool_calls:false`（决策侧不再下发 `response_format`）；响应按用途分流 | 只接受恰好一个工具调用 |
| `RuleAgentOnlineProbeHost` | `REVISION`→`RULE_AGENT_ACTION_CONTRACT_04`、新包路径 `t05-24`、`actionTools` 写入并纳入校验、探针批次号 `P01_TOOL_CALL_04` | 旧批次授权不得复用；包内容必须描述实际下发的请求 |
| 测试 | 两个测试类的模拟服务改为工具调用响应形态；新增枚举/多调用/无调用反例 | 旧文本协议响应必须被拒绝，不能静默回退 |

## 二、责任划分（供应商 vs 宿主）

- **供应商承担**：合法 JSON；动作名称来自宿主登记的 4 个函数名；`tool_calls` 结构。
- **宿主全额保留**：句柄 1—3、唯一性、编号格式与集合代号绑定、跨任务/过期集合、字段闭合、子问题归属、预算、
  证据与发布守卫、`tool_calls` 必须恰为 1。
- **明确不承诺**：工具参数 Schema 只是"入参描述"（官方无 `strict` 语义），**不构成数量/唯一性的强制保证**。

## 三、问题—改动—验证

| 问题 | 最小改动 | 验证 |
| --- | --- | --- |
| 动作名可被自由文本伪造 | 决策侧只读 `message.tool_calls[0].function.name` | 未知函数名→`ACTION_TYPE_UNKNOWN`；参数里再出现 `type`→`ACTION_FIELDS_INVALID` |
| 参数结构不受供应商约束 | 参数按 record 组件生成 Schema（含 `additionalProperties:false`、必填、`handles` 的 `minItems/maxItems/enum`） | 请求体断言 4 个工具名、READ 枚举等于当次集合（`c1`），且不含任何 UUID |
| 无动作/多动作/半截参数 | `tool_calls` 必须恰为 1；`finish_reason` 只接受 `tool_calls`/`stop` | 纯 content→`ACTION_CALLS_INVALID`；2 个调用→`ACTION_CALLS_INVALID`；`length`→`FINISH_REASON_INVALID` |
| 非法动作丢失已知成本 | usage 在动作校验前独立落审计 | 4 类非法响应均保留 usage 25/12；缺失 usage 仍为未知 |
| 包内容与实际请求不符 | `actionTools` 写入请求包并纳入校验，`protocolRevision` 升为 04 | 零调用 `--CheckPackage` 通过，`providerCalls=0` |

## 四、本地验收

- **RED**：先改测试夹具为工具调用响应 → 旧实现 4 项失败（`INVALID_MODEL_RESPONSE`，把新合法响应判为非法），随后实现。
- **GREEN**：`com.zhku.knowledge.ruleagent.*Test` 7 个类 **144 项通过**（Provider 45、Factory 32、Runner 21、
  PublicationGate 15、TaskStore 14、EvidenceTools 10、Contract 7），0 失败 0 错误；报告时间 15:00:0x—15:00:32。
- **零调用包核对**：`--CheckPackage` 通过，`protocolRevision=RULE_AGENT_ACTION_CONTRACT_04`，`providerCalls=0`。
- **未声明**：未运行前端套件、未运行需要数据库的用例；未做真实外发。

## 五、未验证项（不得当作已承诺能力）

1. `qwen-plus` 在实际端点接受 `tools`/`tool_choice`/`parallel_tool_calls` 的行为（官方承诺支持，本项目未实测）；
2. `finish_reason` 实际取值（本地同时接受 `tool_calls` 与 `stop`，真实值未观察）；
3. 参数 Schema 是否被供应商强制（官方无 `strict` 语义，本方案按"不强制"设计）；
4. 工具调用与 `max_completion_tokens=1024` 共存性（官方仅对结构化输出建议不设 `max_tokens`）；
5. **工具调用能否改善真实 READ 选择数量** —— 本方案不承诺该项，也不放宽 1—3 上限。

## 六、边界与未做

未放宽 READ 的 1—3 上限；未默认选择 `c1`、未自动截取前三个候选；未固定 P01 答案或增加白名单；
未根据非法响应补齐业务参数；未删除原文/范围/发布核验；未静默回退旧协议、回放或换模型；
未改学生页面、资料治理、业务规则；未进入 T06。真实模型历史调用计数仍为 **20**，旧外发额度全部关闭。

## 七、下一步（需单独授权）

申请 **P01 单独批次**的具体外发许可后方可执行；本任务书不构成外发许可。请求包身份：
`docs/evidence/rule-agent-provider/2026-10-14/t05-24/request-package.json`，
SHA256 `e00d57278447082d5bb7d16a8c620b86d55b17a8d3feb50814377eef7d392bf5`。
