# 首批10题真实评测（里程碑二）

状态：M2首批真实报告已交付。10题/11run实际执行，42次调用，无自动重试；1题PASS、9题FAIL，全部适用维度可评估5/10。报告与证据见 [M2质量报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/quality-report.md)。M2当时因通用进程辅助函数250秒参数上限，使用等价1350秒监督器调用原获批jar；M3随后修复并实际验收标准Execute，不能倒推M2当时标准命令通过。[M3全量报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m3/live-5e4325f591f441b4a33d08add4151382/quality-report.md)已交付：20题/21run/79次调用，1 PASS、19 FAIL，全维可评估3/20，额度关闭。

固定名单沿用冻结包 `smoke-subset.json`：001、002、003、004、005、006、007、013、018、020。不替换失败题，不从分母中剔除未知项。问题文本取冻结v1.0原题，题集SHA固定为 `36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280`。

RFE-013 固定两个计划轮次：ASK，然后仅在实际追问的 conditionId=courseType 且允许PUBLIC时，在同一task回复 `PUBLIC`；续答为RESOLVE_PUBLIC，不执行专业选修课分支。缺少正确追问时保存跳过原因，不擅自补条件或创建替代任务。

## 执行与输入

`RuleFactBatchPlan` 固定10 task、最多11 run、55次调用（9题各5次、013共10次）；每run仍遵循既有4次决策+1次独立核验、1024输出tokens/次、20秒/次、110秒/run。宿主上限1350秒，无自动重试。`RuleFactBatchHost` 每题独立factory/store，013的两个run共享同一个factory额度，不重置预算。

宿主复用里程碑一的 OnlineFactory、TaskStore、Runner、审核目录、真实检索/证据工具、capture、摘要绑定标注以及既有scorer和Trace Grader。题卡预期只留在本地包中，模型仅收到实际用户问题及既有资料/协议。没有为批次修改模型提示词或修补M1已发现的历史限定缺项，以保留基线。

每个题目/轮次保存开始、终态、capture、待核验标注或typed错误码。TaskStore错误保留EXPIRED/CAPACITY等具体码；store产生终态但缺Runner Outcome时记录RUN_OUTCOME_MISSING并保留发布快照/Trace，不伪装成空回答。已开始但中断、尚未开始、跳过澄清、采集/标注错误均留在报告中。

普通单题失败继续其他固定题。MODEL_AUTH_FAILURE、MODEL_CONNECTION_FAILURE、MODEL_UNAVAILABLE、批次截止或资源无法关闭时停批，未执行项归于批次/宿主原因，不能推断模型回答错误。批次只预留一次，未用额度关闭，禁止续跑或复用。

## 复核与报告

每轮的 `annotation-reviewed.json` 必须绑定同轮captureSha256和answerSha256，独立核对事实、引用对主张的支持、对象/历史版本适用性、澄清/缺口、未授权操作声明。人工/独立Agent核验身份要如实记录，不将机器标注冒充用户人工确认。缺标注保留不可判定。

**RFE-013必须核验ASK与RESOLVE_PUBLIC两份标注**；ASK的片段是实际澄清问题/缺口说明，而不是续答文本。无必答事实且实际未发表主张的轮次，在独立核验确认无无支持主张之后，事实维度才可不适用。不能为省略标注而自动判不适用。

续答不重新检索时，仅在父capture摘要、相同task、不同run、version+2、相同题集/配置、courseType追问及PUBLIC回复链路都匹配后，继承父轮已判定的检索观察；不把引用当作检索，不无条件借用其他run。父轮失败或未知也会继承。

`RuleFactBatchReport` 只聚合现有判定，不创建平行评分体系。JSON和Markdown保留每题六维理由、每轮身份、capture/annotation路径和SHA；sources还记录provider审计、题目错误、closeout和批次中断证据。

指标口径：

- 分母固定10题；RFE-013按一个题目计，计划轮次分别展示。其余冻结10题不在本批通过率分母中。
- 通过率=PASS题数/10；FAIL、UNJUDGEABLE、ERROR分别列出。
- 确定整题结论比例=(PASS+FAIL)/10；ERROR不作为质量结论。
- 全部适用维度可评估比例=计划轮次齐全且所有适用维度有明确PASS/FAIL的题数/10。不适用项不要求通过。已确定FAIL的题仍可能有其他维度未知，因此该比例与确定结论比例独立。
- 逐维按题汇总PASS/FAIL/UNJUDGEABLE/ERROR/NOT_APPLICABLE；任何失败优先保留，未知不得变成PASS。

报告生成失败只记report-error.json，不删除执行证据；可离线重新运行Report入口，无需再次调用模型。停止或报告异常后的恢复只处理证据，不自动恢复调用额度。

## 入口

```powershell
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Batch -Operation Check -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m2/request-package.json -ApprovedPackageSha256 18143180bc208127846495b726e5f218a7f6140d9b4ed3e2b30f61c4cdbcff5c
```

`Prepare`负责新构建和新包，拒绝覆盖；`Check`零调用核对；`Assembly`仅在专用临时库检查本模式全部题目检索/读取端口，不构造Provider；`Execute`必须有用户对本包身份、模式对应额度、预算和有效期的明确批准及回执（M2为55次，M3为105次）。操作不会读取其他Worktree的凭证，不使用共享演示库。Batch模式必须明确PackageRelativePath。

批次执行后，逐轮完成标注，再调用 `RuleFactBatchReport.main(root, batchDirectory, outputJson, outputMarkdown)`。输出文件必须是新路径。受控回答/原文只在本地保存30天，不自动加入Git、CI或公开上传。

## M3 全量基线扩展（已交付）

M3 继续使用本页相同的执行、采集、标注和六维评分契约；`-FullDataset` 选择冻结 v1.0 全部20题。20 task、最多21 run/105次调用；每题一次，013仍只有预声明PUBLIC续答。宿主2550秒（42.5分钟），单次/单run预算不变。重复试验不在本包授权内，待首轮结果评估后另包决定。新请求包及实际状态见 [M3准备记录](../../../evidence/rule-agent-provider/2026-11-17-frozen-m3/README.md)。

评测专用 `Invoke-ZhkuEvaluationProcess` 支持最长3600秒，只回收自己启动的进程树；通用辅助函数原有250秒上限不变。零调用进程测试覆盖2550秒参数和实际超时回收；M3获批后标准Execute实际执行并完成清理，原始记录见M3目录。

`baselineIdentity` 留存题集、模型/端点、决策/核验提示词、动作/发布协议及schema、预算、资料版本、工具/Provider/评分实现与jar身份。包保存实际提示词、协议和原始问题。M2运行副本已在既有eval/baselines分区保存，M2历史报告保持不变。比较优先展示共同10题的逐题变化与全部20题成绩，不能直接把1/10与全量通过率视为改进；单次试验不能证明统计显著性。

失败分类是报告附加层，不能改写六维结论。`OBSERVED_LAYER_V1` 采用可重叠标签，按题去重统计缺陷题数：

| 类别 | 所需依据及边界 |
| --- | --- |
| RETRIEVAL_FAILURE | 完整检索观察下，既有retrievalRecall判FAIL；缺观察是未知，不是漏召回 |
| CONDITION_JUDGMENT_ERROR | 独立审阅证明回答选择了错误对象/分支/必要条件；仅漏历史限定或分支解释须归回答遗漏 |
| INSUFFICIENT_EVIDENCE | 绑定回答的独立审阅，区分本就缺个人/当期/范围外资料的预期边界（defect=false）与取得必要材料后仍不恰当断言缺证据的缺陷 |
| CITATION_ERROR | 既有引用FAIL只能自动说明引用要求失败；独立审阅再细分缺引用、错误版本/定位或不支持主张，不自动宣称伪造 |
| EXECUTION_EXCEPTION | FAILED/STOPPED或typed宿主错误，保留终态/issue；未启动项单列未知，不能归咎模型回答 |
| ANSWER_INCOMPLETE | 独立审阅绑定回答遗漏，不能将其当作错误条件判断或已证明检索失败 |
| ATTRIBUTION_UNKNOWN | 已有FAIL/未知但无充分分类依据；保留未知而不猜测根因 |

语义分类可写 `failure-reviews.json` 数组，元素含caseId、capturePath、captureSha256、category、subtype、defect、reviewer、reason、answerExcerpt。Report入口核对同题同轮capture摘要、已成功评价的记录及实际回答片段；过期或跨题标注拒绝出报告。缺分类审阅不影响既有评分，但不能把未证明的原因计为缺陷。原始Provider报文在本批禁用，只保存调用元数据/动作/观察/发布问题/结果；无法由这些证据支持的根因明确未知。

首轮报告需基于分类证据列修复优先级，兼顾影响题数与严重程度；预期数据边界、尚未执行和归因未知不能混入已证实缺陷排行。

评审后补充：宿主/审阅错误与自动未细分引用FAIL只列观察，不直接计Agent缺陷；分类表同时显示已证实缺陷与非缺陷/待归因数。FAILED/STOPPED表示实际执行失败，不证明模型根因。版本比较明确包含选择范围、批次源文件与jar差异。实际运行复制批准/预留回执并摘要绑定；旧记录无本地副本时显示未验证，不能伪造历史。courseType是生产澄清协议约束，宿主据此允许PUBLIC，非冻结题卡原字段。逐维适用分母=全题-已证实NA，ERROR/未知仍在分母中，保留评价覆盖率的保守含义。
