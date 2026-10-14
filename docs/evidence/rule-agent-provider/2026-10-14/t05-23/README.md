# T05 受约束动作输出能力查证（第一步：确定唯一方案）

本轮 2026-10-14 14:40 开始，主动执行上限 20 分钟。基线 `a8f99120f296eef28d218bb7f7a52b273694c9ee`。
仅做官方资料查证与方案判定；**真实供应商 0 次、数据库 0 次、不改运行源码、不改题目/预算/提示词、不运行探针或 P02**。

## 一、核对结论

| 项目 | 结论 |
| --- | --- |
| 接口与模型 | 北京地域 OpenAI 兼容 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` + `qwen-plus`，`enable_thinking=false`（本仓库现状，未改） |
| 当前动作生成机制 | 仅 `response_format={"type":"json_object"}`，动作合同写在模型消息里 → 供应商只保证"是合法 JSON"，不保证任何字段/结构 |
| 本轮要判定 | 该模型能否提供**供应商承担约束**的结构化动作输出 |

**判定**：对固定模型 `qwen-plus`，官方**明确承诺支持工具调用（Function Calling）**，但**未承诺支持 JSON Schema 严格结构化输出**。因此唯一可实施方案是**工具调用**，并且它只改善"动作名称与参数结构"，**供应商不承担参数值约束（含数组 1—3 上限与唯一性）**，宿主校验必须全额保留。

## 二、官方依据（2026-10-14 查阅）

1. 结构化输出（北京地域）：<https://help.aliyun.com/zh/model-studio/qwen-structured-output>
   - JSON Object 与 JSON Schema 两种模式；`response_format={"type":"json_schema","json_schema":{"name":…,"strict":true,"schema":{…}}}`。
   - **支持的模型**：`JSON Object` 标签页含「千问 Plus（非思考模式）：Qwen3.6-Plus 系列、Qwen3.5-Plus 系列、**Qwen-Plus 系列**」；
     `JSON Schema` 标签页只列 **「Qwen3.7-Plus 系列、Qwen3.8-Flash 系列、Qwen3.7-Flash 系列、Qwen3.7-Max 系列、Qwen3.8-Max 系列模型」**。
     → **`qwen-plus` 不在 JSON Schema 的官方承诺范围**。
   - 关键字支持：数组支持 `items / prefixItems / minItems / maxItems / unevaluatedItems`；
     **数组 `uniqueItems`（含 `contains`/`minContains`/`maxContains`/`additionalItems`）不支持，提交即 400**。
   - 生产建议：**开启结构化输出时不要设置 `max_tokens`**（可能截断 JSON）。
   - 多模态输入会静默降级为 `json_object`（本任务为纯文本，不适用）。
2. 工具调用（Function Calling）：<https://help.aliyun.com/zh/model-studio/qwen-function-calling>
   - **支持的模型**含「千问 Plus：Qwen3.7-Plus 系列、Qwen3.6-Plus 系列、Qwen3.5-Plus 系列、**Qwen-Plus 系列**」→ `qwen-plus` 在列。
   - 请求：`tools:[{type:"function",function:{name,description,parameters}}]`；`parameters` 被文档定义为**对入参的描述**，全文无 `strict` 语义 → **属"Schema 被用作模型提示"，不是强制校验**。
   - 响应：`choices[0].message.tool_calls[0].{id,function.name,function.arguments(JSON 字符串)}`；模型不调用工具时走 `content`。
3. DashScope API 参考：<https://help.aliyun.com/zh/model-studio/qwen-api-via-dashscope>
   - `tool_choice`：`auto` / `none` / `required` / `{"type":"function","function":{"name":…}}`；
     **Qwen 系列不支持 `required`**；**思考模式不支持强制指定某个工具**（本项目为非思考模式）。
   - `parallel_tool_calls`：默认 `false`。
   - `response_format.type` 支持 `json_schema`，但该页面对 `json_schema` 的说明按"获取指定格式的输出"文档执行，模型范围同上。

> 第三方聚合博客曾称 DashScope 不支持 `json_schema`（主题无关、与官方页面冲突）；以官方页面为准，第三方结论不作为依据。

## 三、责任划分（供应商 vs 宿主）

| 约束 | 承担方 | 依据 |
| --- | --- | --- |
| 输出是**合法 JSON** | 供应商（工具调用下为 `function.arguments` 字符串） | 官方工具调用响应结构 |
| **动作名称**只能是宿主登记的 4 个之一 | 供应商（结构性：函数名来自 `tools`） | 工具调用机制 |
| **参数是 JSON 对象**且键名结构由宿主定义 | 供应商（描述级）+ 宿主复核 | 文档无 `strict` |
| 句柄**数量 1—3** | **宿主**（供应商未承诺任何数量约束） | JSON Schema 模型范围不含 qwen-plus |
| 句柄**唯一性** | **宿主**（`uniqueItems` 供应商直接不支持，400） | 官方关键字表 |
| 编号格式 `c[1-9][0-9]*`、集合代号绑定、跨任务/过期集合 | **宿主**（不变） | 现有 `RuleCandidateReferences` |
| 字段闭合、必填、子问题归属、发布守卫 | **宿主**（不变） | 现有 `RuleAgentProtocol`/`RuleResultPublisher` |

## 四、拟定请求与响应合同（第二步实施依据）

- 请求：保留现有 `model/stream/enable_thinking/max_completion_tokens/messages`；把 `response_format` 替换为
  `tools`（4 个动作由现有权威合同 `RuleAgentProtocol.description()` + `NextAction` record 组件生成，不另写一套字段）
  + `tool_choice:"auto"` + `parallel_tool_calls:false`。
  READ 的 `handles.items.enum` 取当次完整候选编号（`c1..cN`），实际 UUID 仍只留宿主。
- 响应：改读 `choices[0].message.tool_calls`；**`finish_reason` 预期由 `stop` 变为 `tool_calls`，需适配，不能把新合法响应判为旧协议错误**；
  `tool_calls` 数量必须恰为 1，>1 或为 0 一律拒绝且不执行任何动作；已知 usage 先落审计再校验动作。
- 决策与独立核验分别处理；核验侧保持独立上下文。

## 五、未验证项（不得当作已承诺能力）

1. `qwen-plus` 在**本项目实际端点**上接受 `tools`/`tool_choice`/`parallel_tool_calls` 的行为（官方承诺支持，但本项目未实测）；
2. `finish_reason` 实际取值与 `usage` 字段位置；
3. `json_schema` 在 `qwen-plus` 上究竟返回 400 还是静默降级为 `json_object`（**只记录，不作为实现依据**）；
4. 工具调用与 `max_completion_tokens` 能否共存（官方对结构化输出建议不设 `max_tokens`）；
5. **工具调用能否真正改善"选择数量"** —— 文档未承诺任何数量强制，本轮**不作该项承诺**。

## 六、替代选择（若用户不接受"改善但不强制"）

- **A. 维持现状**（`json_object` + 宿主拒绝）：零改动，但 P01 的 `READ_HANDLE_COUNT` 仍可能复现。
- **B. 采用工具调用**（本轮推荐）：动作名与参数结构转为供应商结构，**参数值约束仍全在宿主**。
- **C. 改用 JSON Schema 严格模式**：需要把模型切到 `Qwen3.7-Plus` 等系列 → **超出本任务授权（不得自行切模型）**，需用户决定。
- **D. 停止实施并收口**："工程成果已冻结，真实在线验收未通过"。

## 七、本轮范围与边界

- 只读查证 + 证据落盘；未改任何 `knowledge/src` 文件，未调用供应商，未写数据库。
- 未放宽 READ 的 1—3 上限，未默认选择 `c1`，未固定 P01 答案，未增加白名单，未静默回退旧协议。
- 原始第三方页面副本仅存于本机 `tmp/t05-23/`，不入库；本文件只保留结论、链接与责任划分。
