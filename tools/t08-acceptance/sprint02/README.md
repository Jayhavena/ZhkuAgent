# Sprint02 离线工程执行器

纯 Node 标准库，无安装步骤。`plan` 和 `synthetic` 不读取环境凭据、不导入网络库、不启动数据库或模型；`java-offline` 仅校验导入的脱敏 JSON，不能从 JSON 判断它是否真的由 Java 生成。新结果为 `T08_S02_RESULT`，不冒充旧 `T08_SAMPLE`；尤其 G3 计划结果不能作为旧工具要求的 ONLINE 证据。

## 命令

P2-C默认的 `java-offline` 已要求DSH普通离线回执，输出 `T08_RECEIPTED_RESULT`，其中的traceResult仍为既有评测结果。必须提供 `--receipts`、`--challenge`、`--surefire-report`，并将Java测试源码纳入冻结清单；`aggregate`须提供同一组证据重新校验。缺失/重复/错关联回执不会自动降级为纯轨迹通过。完整参数和2→3计数语义见 [回执契约](../../../../docs/preparation/t08/sprint02/execution-receipt-v1.md)。结果始终UNVERIFIED/SELF_REPORTED。

以下历史单纯导入示例需额外指定 `--receipt-policy trace-only`，结果receiptStatus为NOT_REQUESTED，不能作为回执闭环验收。

以下在项目根目录执行，`node` 需已安装。Windows 示例可在普通 PowerShell 5.1 中使用，路径参数可加引号；不依赖 `pwsh`、`rg` 或 npm。退出码：0=命令成功（plan 仍是未执行）；1=校验不通过或合成案例失败/缺证；2=参数、输入或输出错误。**CLI 退出 0 不等于模型质量通过。**

```powershell
node tools/t08-acceptance/sprint02/cli.mjs validate --dataset tools/t08-acceptance/sprint02/synthetic-dataset.json --rubric tools/t08-acceptance/sprint02/synthetic-rubric.json

# 输出目录必须事先存在，文件必须是新文件；请为每次执行使用不同文件名。
node tools/t08-acceptance/sprint02/cli.mjs create-manifest --root . --out sprint02-synthetic-freeze.json tools/t08-acceptance/sprint02/synthetic-dataset.json tools/t08-acceptance/sprint02/synthetic-rubric.json
# 上一步stdout返回 manifestSha256，把它作为本批次的外部固定值。
$pin = (Get-FileHash -Algorithm SHA256 -LiteralPath sprint02-synthetic-freeze.json).Hash.ToLowerInvariant()
node tools/t08-acceptance/sprint02/cli.mjs verify --root . --manifest sprint02-synthetic-freeze.json --expect-manifest-sha256 $pin
node tools/t08-acceptance/sprint02/cli.mjs run --root . --manifest sprint02-synthetic-freeze.json --expect-manifest-sha256 $pin --dataset tools/t08-acceptance/sprint02/synthetic-dataset.json --rubric tools/t08-acceptance/sprint02/synthetic-rubric.json --mode synthetic --candidate-id synthetic-engineering --source-sha synthetic-fixture-only --out sprint02-synthetic-result.json
node tools/t08-acceptance/sprint02/cli.mjs aggregate --result sprint02-synthetic-result.json

# 对任一已冻结真实数据集，把上方 --mode synthetic 换成 --mode plan。
# 每个case都记录 NOT_EXECUTED / NOT_EVALUABLE；仅输出标识、结构finding和计数，不输出问题/参考答案正文。
```

### Java Runner / TaskStore 真实轨迹导出（离线模拟，单 case）

验收终端在现有 JDK 21、Maven 离线缓存可用时执行。将 `$Mvn` 指向本机已有 Maven，不通过 wrapper 下载新工具：

```powershell
& $Mvn -o -pl knowledge -am "-Dtest=RuleAgentOfflineTraceExportTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dzhku.t08.export=<仓库内新文件绝对路径>" "-Dzhku.t08.dataset=<数据集文件绝对路径>" "-Dzhku.t08.caseId=<caseId>" "-Dzhku.t08.candidateId=<candidateId>" "-Dzhku.t08.sourceSha=<40或64位小写源码SHA>" test
```

测试以本地确定性工具/决策端口驱动真实 `RuleAgentRunner` 和 `RuleAgentTaskStore`，从 `traceHistory()` 导出；不触碰数据库或供应商。当前只支持 `input.supplements` 为空的单轮 case，多轮输入会明确失败而不输出部分轨迹。导出使用本地 `task-1` / `turn-1` / `run-1` / `action-1` / `evidence-1` / `claim-1` 标签，且省略 source/chunk UUID、原问题、原文与凭证字段。输出路径须为仓库内新文件，且不得加入既有冻结清单。

随后把普通 `run` 命令的 `--mode synthetic` 换成 `--mode java-offline`，并补上 `--java-export tools/t08-acceptance/sprint02/java-export.json --case-id <caseId>`。该入口要求导出与 CLI 的 case、candidate、source SHA、冻结数据集 SHA 一致，只接受一个 G2 development case，且只接受单轮。报告标记 `UNVERIFIED_IMPORTED_JAVA_TRACE`、`javaExecutionVerified:false`、`summary.verifiedJavaCandidate:false`、`providerCalls:0`、`qualityStatus:NOT_EVALUABLE`，Rubric 保持 `PROPOSED_NOT_APPROVED`。

JSON 的 `sourceLayer:ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED` 是自报字段，candidate/source SHA 相等也只证明字符串一致。手写 JSON 可以通过结构校验；工具绝不因此授予 `ACTUAL_JAVA_RUNNER_VERIFIED`，也不把它聚合为已验证真实 Java 候选。独立可信的外部执行证明需要另外绑定源码树/文件摘要、执行命令和导出摘要；本实现没有这种认证入口或可信回执验证器。即使另有人工执行记录，Node 报告仍保持来源未验证。

Java 导出连续执行两次真实 Runner/TaskStore，比较按键排序的脱敏 JSON 字节。随机关联 ID 全部转换为局部标签，关系引用同步改写；factId 仅允许 `sha256:` 加 64 位小写 hex，revision 只接受已审查固定值 `2025.9.2`，其他值拒绝。导出全文检查隐藏 UUID 子串。导入同样使用严格字段和值白名单，逐 action 检查连续 step、非递减 modelAttempt、唯一 ID 及一对一 observation；控制事件必须显式 `NOT_OBSERVED/null`。COMPLETE 必须有非空 items/claims、evidence 和 CLAIM 关系，ID 唯一且双向引用完整。缺失数组不能自动当成观察到的空数组。

单轮仅允许 `task-1/turn-1/run-1` 与首次发布 `version:2`；尚未实现跨轮身份唯一性与版本递增验证，因此多 case、多轮直接拒绝。当前结果投影只支持 CLAIM 关系，其他角色失败关闭。截断或异常轨迹不能工程 PASS；无效导入和缺失结果保存脱敏 FAIL 报告，CLI 返回 1。

`create-manifest` 使用显式文件清单与精确字节 SHA-256，拒绝重复路径、绝对路径、`..`、反斜杠、缺失文件、软链接/Windows junction（含父目录）和自冻结。manifest 独立存放；`verify` 和 `run` 必须传固定 manifest SHA，运行解析的同一份输入字节还会与冻结条目复核。**可信 pin 必须在冻结时另存到可信记录/Git；若事后重新对被篡改 manifest 计算 pin，不能证明历史未改。**工具不写回输入，输出用独占创建，拒绝覆盖历史。

一个执行报告只允许一个 group、split、candidate/source 与 dataset/rubric SHA 组合。数据文件可含多组；对混组文件运行时必须显式传 `--group G2`、`--group G3` 或 `--group G4`，不传则拒绝混组。结果记录 `datasetTotalCaseIds`（全部ID）、`expectedCaseIds`（所选组ID）、`excludedCaseIds`（明确排除的其他组ID）及排除计数，聚合检查三者对账，不能静默删掉失败。聚合还校验重复案例、逐条身份、费用未知、状态与轨迹，不跨报告合并通过率。

## 结果口径

- `engineeringStatus`：`PASS`=合成轨迹或来源未验证的导入轨迹满足结构及工程预期；`FAIL`=状态违约或适配器异常；`NOT_EXECUTED`=plan 或 synthetic 未提供适配器；`INSUFFICIENT_EVIDENCE`=身份/轨迹/工具/证据不足。`NOT_EVALUABLE` 为保留通用状态；本执行器质量维度始终使用它。
- `qualityStatus` 一律 `NOT_EVALUABLE`，`summary.modelQualityEvaluable=false`。Rubric 为 `PROPOSED_NOT_APPROVED`，不会用合成轨迹计算维度分数或通过阈值。
- 每条记录保留 caseId、各轮 taskId/turnId/runId、状态、动作/观察、证据标识/关系、脱敏错误码和实际宿主 `elapsedMillis`。`expected.taskStatus/coverage` 是逐轮有序列表，长度须等于 `1 + supplements.length`。
- `modelAttemptCount=0`、`providerCalls=0` 是本工具未调用供应商的确定事实；usage 三项与 `costCny` 均明确 `null`，unknown=true。未执行时 elapsedMillis=null，不伪填0。
- 单案例异常被捕获，错误内容不原样落盘，后续案例继续；CLI 保存完整失败报告后返回1。证据回执仅保留白名单标识，不保存问题/参考答案、任意参数、原始响应或异常message。
- 声明 `faultInjection` 的案例必须有适配器根级 `faultReceipt:{type,point,reached:true,applied:true}`，type/point与案例一致，且 SEARCH/READ 分别有 `SEARCH_REVIEWED_RULES` / `READ_VERIFIED_RULE` 动作与同actionId的观察。缺回执、未触达或未生效均为 `INSUFFICIENT_EVIDENCE`，不能把普通失败当注入通过；预期为 FAILED 且故障证据完整时可工程通过。plan 保持未执行，质量仍不可评价。回执不保存payload。

## 可插拔边界

`engine.mjs` 导出 `validateDataset`、`validateRubric`、`createManifest`、`verifyManifest`、`runDataset`、`aggregate`。库调用：

```javascript
const report = await runDataset(dataset, rubric, {
  mode: 'synthetic', candidateId, sourceSha, datasetSha256, rubricSha256,
  adapter: async ({caseId, group, input, synthetic}) => ({turns: [/* 合成快照 */]}),
});
```

合成适配器拿不到 reference/expected，可以返回单快照或 `{turns:[...]}`。含故障声明时另传入 `faultInjection` 供合成适配器注入并返回根级faultReceipt。CLI 仅使用随代码审查的固定 `synthetic-adapter.mjs`，不加载任意插件。库函数调用者提供的 JS 适配器属于可信代码，工具不是 JS 安全沙箱，也无法证明第三方适配器没有私自调用外部服务。`sourceSha` 是调用者提供的身份，工具不代替 Git 源码审查。

Java 库调用与 CLI 共用 `importJavaTraceExport` 边界：使用 `mode:'java-offline', javaCaseId, javaExport:document`，或让 adapter 返回完整导出 document。不能直接传 `{turns:[...]}` 绕过导入，`javaSourceVerified` 不具备任何信任效力。所有导入在进入报告前完成严格校验，聚合再次验证结构。

内置合成案例 `synthetic-throw` 会抛工程异常，`synthetic-missing-trajectory` 会产生缺轨迹；其他合成ID走固定成功轨迹。行为来自预先写死的工程夹具，不读取参考答案生成结果。只允许所有案例 `synthetic=true` 且 provenance.kind=`SYNTHETIC_FAULT` 的非holdout数据集进入该模式。

## 验证

```powershell
node --test tools/t08-acceptance/test/*.test.mjs
```

包括既有 `validate-evidence.mjs` 合成夹具兼容测试与 Sprint02 新测试；本实现没有修改旧校验器，也没有读取真实保留集问题或参考答案。未执行真实模型、Judge、数据库、真实宿主或产品质量验收。
