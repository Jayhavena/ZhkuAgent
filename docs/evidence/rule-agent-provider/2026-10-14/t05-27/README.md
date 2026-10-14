# T05 决策侧改为 JSON Schema 严格结构化输出 + 模型切至 qwen3.8-flash（t05-27）

> **本目录的 `request-package.json` 已被取代，禁止用于任何外发申请。**
> 原因：该包按 T05_READ_BUDGET_02（单次 10 秒 / 整 run 45 秒）冻结；用户随后授权方案 A 放宽为
> **单次 20 秒 / 整 run 90 秒**，预算标识升为 `T05_REVIEW_BUDGET_03`，最终身份为
> `docs/evidence/rule-agent-provider/2026-10-14/t05-30/request-package.json`。
> 本目录的源码改动、测试结论与"结构化输出被真机接受、READ 缺口已解决"的结论仍然有效；仅包身份与 SHA 作废。

本轮 2026-10-14 15:16 开始，承接 `210ad3d8e0788c7e39ba0b5f2c44889686d2197c`。
**真实供应商 0 次、数据库 0 次**；只做本地实现、本地验收、零调用包核对。

## 一、依据与授权

- 用户授权：由「改用支持 JSON Schema 严格模式的模型」→ 明确指定 **`qwen3.8-flash`**。
- 官方依据：结构化输出文档的 JSON Schema 支持列表含「Qwen3.8-Flash 系列」；北京地域模型清单含 `qwen3.8-flash`；
  数组关键字支持 `minItems`/`maxItems`/`items`，**`uniqueItems` 不支持（提交即 400）**。
- 方案：**决策侧由工具调用改为 `response_format={"type":"json_schema","json_schema":{name,strict:true,schema}}`**，
  目的即让 1—3 个句柄上限由供应商承担；**独立核验侧保持 JSON 文本协议不变**。

## 二、改动

| 文件 | 改动 | 为什么 |
| --- | --- | --- |
| `RuleAgentProtocol` | `REVISION`→`RULE_AGENT_ACTION_CONTRACT_05`；`tools()` 换成 `decisionSchema(readReferences)`：根对象 + `oneOf` 四个动作分支，分支内 `type`为`const`、`additionalProperties:false`、`required` 取自 record 组件，`handles` 带 `minItems=MIN_READ_HANDLES`/`maxItems=maxReadHandles` 与当次候选 `enum` | 动作定义仍与权威合同同源（同一组 `JsonSubTypes`、同一批常量与枚举），只换承载形式 |
| `RuleAgentProvider` | 新增 `MODEL="qwen3.8-flash"` 并以此固定校验；`SYSTEM` 决策行改为"只输出一个严格符合响应 JSON Schema 的动作对象"；`envelope` 决策侧下发 `response_format.json_schema`；`decisionAction` 改为校验 `finish_reason=stop` + 文本 `content` + 必须为**单个对象**（数组/标量/包装对象一律拒绝） | 线协议与提示词必须一致；不回退旧协议、不自动取第一个 |
| `RuleAgentOnlineProbeHost` | 包路径 `t05-27`、批次号 `P01_STRUCTURED_05`、`actionTools`→`actionSchema`、模型校验改用 `RuleAgentProvider.MODEL` | 换模型＋换线协议后旧包 SHA 与旧批次号一律不得复用 |
| 测试 | 两个测试类的模拟响应改回结构化输出的文本 JSON；新增/保留反例 | 旧（工具调用）响应必须被拒绝 |

**未改**：动作字段与必填、`MIN_READ_HANDLES`/`maxReadHandles` 数值、编号映射、运行循环、Broker、发布守卫、
证据工具、时间与次数预算、核验侧协议、学生页面与资料治理。

## 三、责任划分

- **供应商承担（`strict=true`，均为未验证）**：输出合法 JSON 并严格符合 Schema；`type` 取自分支 `const`；
  `handles` 的 `minItems/maxItems` 与候选 `enum`；`additionalProperties=false`。
- **宿主全额保留**：动作字段闭合（record 组件精确匹配）、句柄唯一性（供应商 `uniqueItems` 不支持）、
  编号格式与 `candidateSetId` 绑定、跨任务/过期集合、子问题归属、预算、证据与发布守卫。
- **明确不承诺**：上述供应商约束是否被**真正强制**，未经真实验证前不得称为保证。

## 四、本地验收

- `com.zhku.knowledge.ruleagent.*Test` 7 个类 **145 项通过，0 失败 0 错误**（报告 15:22—15:25）。
- 请求体预算实测：带结构化输出 Schema、10 个当次候选与审核原文的一轮决策请求 **4457 字节 / 上限 65536**（余量 61079），
  且 `toolsAbsent=true`。
- 零调用 `--CheckPackage` 通过：`providerCalls=0`、`databaseOperations=0`、`protocolRevision=RULE_AGENT_ACTION_CONTRACT_05`。
- 反例覆盖：空句柄、4 个唯一编号、非数组、重复、未知、跨任务/过期集合、未知动作、缺字段、额外字段、
  参数类型错误、重复键、`finish_reason=length`、**数组内容**、包装对象。
- **流程偏差（如实记录）**：本轮是整条线协议的替换，测试夹具与实现同批修改，未单独制造一次"先失败"运行；
  反例基线是上一提交（`210ad3d`）中编码工具调用协议的测试断言，可由 git 历史核验。

## 五、未验证项（不构成保证）

1. `qwen3.8-flash` 是否接受 `response_format=json_schema`（官方按系列列出）；
2. 是否接受 `enable_thinking=false` 与 `max_completion_tokens=1024` 共存（官方提示结构化输出不要设 `max_tokens`，
   本方案保留既定输出预算，截断将按非法响应拒绝）；
3. `finish_reason` 实际取值；
4. `minItems/maxItems`/`enum` 是否被真正强制；
5. Qwen3.8-Flash 的推理强度低于 Plus，是否影响 P01 的选择质量。

## 六、待授权（本文件不构成外发许可）

换模型即换一个未验证的供应商能力面，**必须重新取得 P01 单独批次的具体外发许可**。请求包身份：

- 路径：`docs/evidence/rule-agent-provider/2026-10-14/t05-27/request-package.json`
- SHA256：`8686b37598a23818aa521a565997a146cd819c5ddc2cb69070a0b86fb6105a86`
- 批次号：`P01_STRUCTURED_05`；模型 `qwen3.8-flash`；次数上限 4、无自动重试。
