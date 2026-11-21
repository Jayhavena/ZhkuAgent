# Qwen4-Max 发布核验兼容性排查

排查对象为 main Worktree、`feat/zhkuagent-v3-frontend`、HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`，冻结包 SHA-256 `f3dae14844718c502c6111ee20e0837a6fccc965bc42e4dd38df5becdb72e198`。从包内摘要独立核对 Provider、JSON 解码器、发布协议、发布合同、Gate 和批次宿主六份源码均匹配。

冻结包包含工作区未提交的发布协议改动。用 `git show HEAD:knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationProtocol.java` 核对，HEAD 将 JSON 解析和 record 映射失败都归为 `REVIEW_JSON_INVALID`。本批使用的冻结源码已拆分两个码，因此历史记录里的旧码只能按当时的合同解读。E13 的归档样例记录旧码，当前离线重放得到 Schema 拒绝码，两者对应不同版本的诊断分类。

## 实际失败边界

- Qwen4-Max 的 58 次决策调用通过 Provider 端响应校验。19 次发布核验均为 HTTP 200，4 次完成本地解码，15 次标为 `REVIEW_JSON / REVIEW_SCHEMA_INVALID`。15 次输出为 33–422 token，均在 1024 上限内。RFE-020 未触发发布核验。[批次审计](live-2e14820612c34848a14a36db890ee063/audit-verification.json) · [RFE-003 Provider 审计](live-2e14820612c34848a14a36db890ee063/RFE-003/provider-traces.jsonl)
- `RuleAgentProvider.reviewContent` 已验证 `finish_reason=stop` 和文本 `message.content`，`RulePublicationProtocol.decode` 已成功读取整段 JSON。失败发生在 `RuleAgentJson.bindReview`，其中严格字段映射和 record 构造约束共用一个拒绝码。15 题的发布诊断都没有成功解码的语义核验记录，也没有 Gate 事实相关性拒绝记录。[RFE-003 发布诊断](live-2e14820612c34848a14a36db890ee063/RFE-003/ANSWER/publication-diagnostics.json)
- 决策请求附带 `response_format=json_schema` 和 `strict=true`。发布核验请求使用 `response_format=json_object`，`reviewContract` 作为消息正文描述字段和枚举。模型必须自行满足本地合同。两个 M3 包的发布核验提示、合同和决策 Schema 摘要分别一致。旧 Flash 批次的 18 次发布核验均通过 Provider 解码，其中部分随后在 Gate 被拒。由于两批实际决策轨迹与核验输入有差异，这只能定位兼容性变化，无法将失败单独归因于模型替换。[旧批次说明](../2026-11-18-m3-full-r3/README.md)

百炼将 `qwen4-max` 列为支持 JSON Schema 的模型，并说明 JSON Object 模式只约束 JSON 语法，字段名及类型仍可能变化。严格模式是可验证的请求侧候选，现有记录无法证明完整发布核验 Schema 在该模型上的实测结果。[百炼结构化输出文档](https://help.aliyun.com/zh/model-studio/qwen-structured-output)

## 离线复核

使用 Maven 运行 `RuleAgentProviderTest`、`RuleAgentReviewDiagnosticsTest`、`RuleAgentE13ArchivedReviewRegressionTest`、`RuleReviewConditionPromptTest`、`RuleAgentSchemaParityTest`、`RulePublicationGateTest`，112 项通过。已有样例确认未知字段、枚举值错误及历史 E13 的 `IDENTIFIED + UNKNOWN` 都会产生同一 Schema 拒绝码。合法形状中的指纹不匹配、跨度不匹配、未知输入引用有各自的 `REVIEW_BINDING` 拒绝码。

本批归档仅保存响应摘要、元数据及解码后的诊断，无法由这些证据确定 Qwen4-Max 失败响应中的字段或构造约束。E13 是历史复现样例，其错误不能归因给本批。排查过程中真实模型调用为 0。

OpenCode 独立核查已完成，请求模型为 `opencode-go/deepseek-v4.1-flash`，编排器未返回单独的实际执行模型字段。它核对了冻结源码、HEAD 的错误码差异，以及本地校验和留存边界。Codex 复核上述差异，并将 `RuleClarificationContractTest` 与 `RulePublicationSpanExcerptTest` 合并执行，新增 15 项离线测试通过，连同上一轮共 127 项。额外样例覆盖容量上限和跨度拒绝的分类边界。[独立核查 Qwen4 发布核验 Schema 兼容性](t3-thread://v2/eb819992-5cf0-4415-84b9-e8ffd8aa9d9c/thread:delegated-task:command%3Amcp%3Aef3016f6-b9e0-4d5a-b1a5-9e09449ffde8%3Adelegate-task%3Ad4e8dc05-dafe-4af4-bebb-67fd86edc54c)

## 缩小下一次验证范围

先用 RFE-003 和 RFE-004 作为同条款对照，分别代表本批曾触发 Schema 拒绝和成功解码的题。最小诊断探针可沿用当前 JSON Object 请求，只在受控本地记录脱敏的核验响应，或在现有拒绝码下记录固定字段路径和失败类别。若获得实际失败样例，将它冻结为回归夹具后再比较严格 JSON Schema，其他请求参数和输入保持一致。结构化输出约束传输形状，本地仍应核对跨度、指纹、事实引用、条件及发布权限。

新的真实诊断执行须另立包，绑定 `qwen4-max`、题目、证据保留方式和额度并获得批准。

## 已实施的统一合同修复（2026-11-18）

按“决策与核验共用同一份模型无关合同、供应商差异收进适配层、换模型先过小套合同验收”的方向完成实现；本节改动真实模型调用 0。

- **核验严格 Schema 与合同同源**：新增 `RulePublicationProtocol.schema()`，record 字段、枚举（含 Dimension/Subject/Recognition/BindingRole/UnresolvedReason 全部取值）、容量上限全部取自 `RulePublicationProtocol.description()` 同一来源，不维护第二份限制；span 声明为 excerpt 回显形态，历史坐标形态仅保留给归档证据解码。
- **请求侧按模型能力适配**：`RuleAgentProvider` 新增模型注册表 `REVIEW_OUTPUT_MODES`。`qwen3.8-flash`、`qwen4-max` 的核验请求改用 `response_format=json_schema`（Schema 名 `zhku_rule_publication_review`，strict=true）；`deepseek-v3` 登记为 JSON Object 兜底并依赖本地合同校验；未登记模型在 `Settings` 配置层直接拒绝，不再等到运行期供应商报错。决策请求保持既有严格 Schema 不变。
- **本地校验仍是最终守卫并带字段路径**：核验解码先做形状走查，拒绝时在 `validation.field` 记录脱敏字段路径（如 `requirements[0].dimension`、`bindings[0].role`），不保存模型原值；record 构造约束（指纹格式、唯一性、容量、IDENTIFIED+UNKNOWN 不变式）做有限归因。供应商是否强制 Schema 不由本改动证明，跨度定位、指纹、事实引用与发布权限仍由本地解码与 Gate 独立复核。
- **模型切换合同验收套件**：新增 `RuleModelSwitchContractTest`，覆盖 Schema 与 description 逐字段同源、枚举/容量同源、请求模式跟随注册表、未登记模型配置层拒绝、形状走查字段路径且不回显模型原值、`IDENTIFIED+UNKNOWN` 约束归因；既有 E13 归档回放与 T05/013 核验回放全部保持通过。

当前相关检查：模型切换聚焦 114 项通过；扩大回归 `RuleAgent*Test,RulePublication*Test,RuleFact*Test,RuleAnswer*Test,RuleReview*Test,RuleModel*Test,RuleClarification*Test,AgentEvalChainRegressionTest` 共 545 项，其中仅既有 2 项 Windows 启动器环境失败与 4 项未提供专用数据库的测试错误，其余全部通过。核验请求字节数仍在 `maxRequestBytes` 预算内（由 `RuleReviewConditionPromptTest.actualPayloadFitsExistingBudgetWithoutAnyProviderAllowance` 复验）。

**尚未验证的边界**：严格核验 Schema 在 `qwen4-max` 与 `qwen3.8-flash` 上的真实行为须另立包实测（Qwen4-Max 上一批 15 次 `REVIEW_SCHEMA_INVALID` 是本次修复对象）；`deepseek-v3` 的模型标识与端点能力须在接入时按控制台核对；GPT 类模型当前端点仅允许 DashScope 兼容入口，接入需先扩展端点适配。新增任何模型先过 `RuleModelSwitchContractTest` 合同验收，再做 RFE-003/RFE-004 同条款小题实测，最后才进入冻结 20 题全量。

若采用单题评测入口，须同时调整其包定义、Provider 模型和采集身份。当前 `RuleFactOnlineHost` 启动的是默认 Flash 配置，且 capture 显式填写 `RuleAgentProvider.MODEL`。全量批次已经使用包中的模型构建 Provider，并以 `provider.model()` 记录身份。单题探针应沿用同一绑定，确保探针调用和后续报告归属于同一个模型。
