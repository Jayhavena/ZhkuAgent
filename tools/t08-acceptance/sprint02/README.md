# Sprint02 离线工程执行器

纯 Node 标准库，无安装步骤。只支持 `plan` 和 `synthetic`，不读取环境凭据，不导入网络库，不启动数据库、Java 宿主或真实模型。新结果为 `T08_S02_RESULT`，不冒充旧 `T08_SAMPLE`；尤其 G3 计划结果不能作为旧工具要求的 ONLINE 证据。

## 命令

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

`create-manifest` 使用显式文件清单与精确字节 SHA-256，拒绝重复路径、绝对路径、`..`、反斜杠、缺失文件、软链接/Windows junction（含父目录）和自冻结。manifest 独立存放；`verify` 和 `run` 必须传固定 manifest SHA，运行解析的同一份输入字节还会与冻结条目复核。**可信 pin 必须在冻结时另存到可信记录/Git；若事后重新对被篡改 manifest 计算 pin，不能证明历史未改。**工具不写回输入，输出用独占创建，拒绝覆盖历史。

一个执行报告只允许一个 group、split、candidate/source 与 dataset/rubric SHA 组合。数据文件可含多组；对混组文件运行时必须显式传 `--group G2`、`--group G3` 或 `--group G4`，不传则拒绝混组。结果记录 `datasetTotalCaseIds`（全部ID）、`expectedCaseIds`（所选组ID）、`excludedCaseIds`（明确排除的其他组ID）及排除计数，聚合检查三者对账，不能静默删掉失败。聚合还校验重复案例、逐条身份、费用未知、状态与轨迹，不跨报告合并通过率。

## 结果口径

- `engineeringStatus`：`PASS`=合成轨迹满足工程预期；`FAIL`=状态违约或适配器异常；`NOT_EXECUTED`=plan 或未提供适配器；`INSUFFICIENT_EVIDENCE`=身份/轨迹/工具/证据不足。`NOT_EVALUABLE` 为保留通用状态；本执行器质量维度始终使用它。
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

适配器拿不到 reference/expected，可以返回单快照或 `{turns:[...]}`。含故障声明时另传入 `faultInjection` 供合成适配器注入并返回根级faultReceipt。CLI 仅使用随代码审查的固定 `synthetic-adapter.mjs`，不加载任意插件。库函数调用者提供的 JS 适配器属于可信代码，工具不是 JS 安全沙箱，也无法证明第三方适配器没有私自调用外部服务。`sourceSha` 是调用者提供的身份，工具不代替 Git 源码审查。

内置合成案例 `synthetic-throw` 会抛工程异常，`synthetic-missing-trajectory` 会产生缺轨迹；其他合成ID走固定成功轨迹。行为来自预先写死的工程夹具，不读取参考答案生成结果。只允许所有案例 `synthetic=true` 且 provenance.kind=`SYNTHETIC_FAULT` 的非holdout数据集进入该模式。

## 验证

```powershell
node --test tools/t08-acceptance/test/*.test.mjs
```

包括既有 `validate-evidence.mjs` 合成夹具兼容测试与 Sprint02 新测试；本实现没有修改旧校验器，也没有读取真实保留集问题或参考答案。未执行真实模型、Judge、数据库、真实宿主或产品质量验收。
