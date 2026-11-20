# 里程碑一：真实执行到六维判定的本地验收

**接线验收 PASS；RFE-001 回答质量 FAIL；全20题质量 NOT_EVALUABLE。**

一条获批真实执行已形成完整身份、回答、检索来源、引用、工具观察和终态，能逐维解释判定；缺回答、缺观察、缺终态的离线负例均未误报通过。这满足本里程碑的链路验收，不代表样本答对、全部20题通过或产品发布验收。

## 执行身份与授权

- Repository / Worktree：`D:\alldesk\Projects\ZhkuAgent\main`
- Branch / HEAD：`feat/zhkuagent-v3-frontend` / `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`
- caseId：`RFE-001`；turn kind：`ANSWER`
- taskId：`04c7f0ed-a312-4963-ae62-13e30fc024bf`
- runId：`9d4a15ea-0b6b-45c4-9e09-2d1049632132`
- turnId / version：`443b78aa-1d1f-40d8-8c22-392fcaabff29` / `2`
- Dataset：`v1.0`，SHA `36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280`
- Model：实际响应 `qwen3.8-flash`；configuration/package SHA：`f4a2c6d6b4090ad8e7b7e4f67f30fab50eeef5ba7a10feaeef8606bf141127e7`
- 用户明确批准的 SHA、1 task/1 run、最多5次、1024输出tokens/次、110秒run、无重试及24小时有效期记录在上级 `execution-approval.json`；执行预留为 `execution-reserved.json`。

执行命令：

```powershell
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Operation Execute -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m1-r2/request-package.json -ApprovedPackageSha256 f4a2c6d6b4090ad8e7b7e4f67f30fab50eeef5ba7a10feaeef8606bf141127e7
```

启动器退出0。19个迁移成功应用于专用临时数据库，随后使用既有审核目录/检索器/证据工具和 OnlineFactory/Runner 执行，不是回放。审计中4条 BEGIN 与4条 FINISHED一一对应，均 HTTP 200：3次决策、1次独立发布核验；输入6694 tokens、输出526 tokens，每次输出均不超过1024。终态为 `RESOLVED / SUCCEEDED / COMPLETE`。未自动重试、未创建第二个run。

临时容器已由启动器清理，后续只读 `docker ps -a --filter name=zhku-rulefact-eval` 无残留；共享演示库未使用。额度关闭记录在上级 `authorization-closeout.json`：消耗4次，剩余1次关闭，remainingAuthorizedCalls=0。

## 原始回答与六维判定

发布的实际回答为：

> 学校选修课分为专业选修课和公共选修课。
> 公共选修课包括校内公选课和网络公选课。

| 维度 | 判定 | 对应证据与理由 |
| --- | --- | --- |
| retrievalRecall | PASS | 实际10个候选的 handle/source/chunk/revision/checksum 与 observationId/run/step 绑定，命中全部必要定位 `handbook-2025-article-4` |
| factSupport | PASS | 两条发布主张逐字对应冻结的 `elective-category-distinction` 与 `public-course-subcategories`；未发现额外事实 |
| citationIntegrity | PASS | 一份第四条证据的版本为2025.9.2、span为0–78；quote的SHA与发布checksum、冻结requiredEvidence一致；CLAIM关系同时支持c0/c1 |
| scopeAndApplicability | FAIL | 题卡requiredResponseElements要求“标明依据为2025.9.2历史文本”；元数据referenceRevision正确，但实际回答正文未明确历史文本限定，不假定未执行的前端会补提示 |
| clarificationAndGap | PASS | 分类问题条件明确，直接回答两个层级符合ANSWER_WITH_EVIDENCE；无需澄清，未虚构当期或个人信息 |
| executionSafety | PASS | 复用现有Trace Grader；SEARCH与READ均有成功观察，FINISH与PUBLICATION_REVIEW及终态符合执行前契约，调用/动作预算内，无办理操作或办理声明 |

实际整题为 **FAIL**，不是执行失败；原因是历史适用性限定缺项。没有修改回答、题卡、契约或核验标注来制造通过，也没有再次调用供应商重测。

语义标注由 **Codex 对实际产物做离线证据复核**，独立于答题与发布核验模型；不冒充用户人工终审。`annotation-reviewed.json` 的每个维度保留 reviewer、reason 和实际回答片段，并绑定采集SHA和回答SHA。引用支持关系按原文核对，不以引用数量代替核验。

## 报告与负例

- 原始且未改写：`capture.json`、`provider-traces.jsonl`、`calls.json`、`annotation-pending.json`、`report-unreviewed.json`。
- 独立标注：`annotation-reviewed.json`。
- 初次离线报告：`report-reviewed.json`；最终报告：`report-reviewed-final.json`。
- 最终报告保留20题分母：0 PASS / 1 FAIL / 19 UNJUDGEABLE。其余题明确为EXECUTION_MISSING，未静默排除，也未外推样本质量。
- `negative-controls.json` 使用真实capture的离线派生副本，明确标记为变异测试，不是新增真实执行。修改测试副本的标注绑定以独立验证缺失输入门禁，不篡改原始capture。

负例结果：缺回答 → UNJUDGEABLE；缺终态 → UNJUDGEABLE；缺工具观察 → 检索及安全维度UNJUDGEABLE，整题仍因原样保留的适用性缺项为FAIL。缺观察的结论依据目标维度不可判定，而非仅凭整题原本失败来声称负例通过。

实际负例暴露了离线绑定入口对null回答/Trace会抛异常的问题，已补为结构化不可判定；已知FAILED/STOPPED仍独立保留失败。此修正发生在真实执行结束后，**仅用于离线评分**，未重建或替换获批运行jar、未修改原始capture、未产生新供应商调用。`grading-provenance.json` 分别记录执行包/jar、原始采集、最终标注/报告、当前评分源码和class摘要，避免混淆运行版本与评分版本。

采集SHA：`689a962e94d2d88ddb9a47f0b5d5cfef47e6ae65e4859c353b061dd508b011e6`

最终报告SHA：`3d326aa3b507e9077963f458168340e49ada8b9369ef3df94e313c90b27da23d`

## 回归、边界与下一步

最终回归命令：

```powershell
mvn -q -pl knowledge -am "-Dtest=RuleFactOnlineHostTest,RuleFactBoundEvaluationTest,RuleFactExecutionEvaluationTest,RuleFactFrozenDatasetTest,RuleAgentEvidenceToolsTest,AgentEvalChainRegressionTest" "-DskipTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果退出0，32项测试、0失败/错误/跳过；报告副本在 `verification-tests/`。新增缺失输入断言包含在现有绑定测试中，没有将早期失败报告当作PASS。三个实际capture变异控制均离线退出0且没有PASS。配置的数据库密码/API key在本目录产物中未检出；capture无originalToken/continuationToken字段。

本轮修改仅为绑定评分器与对应测试的缺失输入处理，以及授权、核验、报告和文档。已有用户修改保留，未暂存、提交、推送或发布。受控原文仍限本地30天保存，不加入CI或Git。

剩余范围：未执行其余19题，未测前端呈现，未验证其他题的多轮真实模型行为，语义标注非用户人工终审。下一质量修复是让回答正文显式表达历史来源/适用性，并在后续独立任务中验收；本次不扩展修改回答逻辑，也不使用已关闭额度重跑。
