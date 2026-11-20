# 里程碑一：修正版包与临时库验收

最新状态：**里程碑一接线本地验收 PASS；RFE-001 回答质量 FAIL**。用户已批准修正版并执行一次，消耗4次调用，无重试，额度已关闭、临时容器已清理。真实记录、六维依据、负例和评分版本边界见 [acceptance.md](live-6d5e96400fc24fd7bab0b38d64ae35cc/acceptance.md)。其余19题未执行，不能宣称全题集或产品验收通过。下文保留修正版授权前的准备记录。

仓库/Worktree：`D:\alldesk\Projects\ZhkuAgent\main`；分支 `feat/zhkuagent-v3-frontend`；HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`；无提交、推送或发布。保留已有修改。

## 首次执行及根因

用户批准原包 `ca4e42571adbcca8b237cafe0cb934248d2031e0f99080c84876269f935ee0e7` 后，启动器的包核对通过、专用临时容器就绪，Java 宿主的再次核对却拒绝。排查确认生产源码、jar 和启动器摘要未变化，差异仅在 `/evaluationTurn/factIds` 的数组顺序：Java 无序 Set 的迭代顺序跨进程变化。此时尚未执行授权预留、数据库迁移或模型构造；没有供应商调用，临时容器已清理。

同时发现宿主随机数据库名称不符合既有 Reviewed2025ControlledCatalogue 的临时库白名单。没有绕过白名单：改为沿用 `zhkuagent_rulecheck_e2e` 固定临时库名，容器仍随机命名并仅绑定专用回环端口55519，拒绝共享库端口54329。原授权与关闭回执均保留在 [首包目录](../2026-11-17-frozen-m1/README.md)，未转移额度或重试旧包。

## 修正范围

- Turn 构造时将 factIds 复制为不可变 SortedSet，按稳定顺序序列化。新增跨3个独立 JVM 的 JSON 一致性测试，不再仅测试同一进程。
- 宿主与启动器使用既有安装器的临时库白名单；补充拒绝共享端口、远端、无效端口和不兼容库名的测试。
- 新增 Assembly 零模型调用路径，复用真实迁移、审核目录、检索器、证据工具和来源 sidecar。该路径不创建 OnlineFactory/Provider，不读取 DashScope 凭证，不消费模型额度。
- Assembly 与 Execute 均通过既有 Docker 就绪入口；每次数据库相关子操作重新按 Process→User→Machine 解析数据库凭证，只传给子进程，不写入参数或文件。

## 实际验证

```powershell
mvn -pl knowledge -am "-Dtest=RuleFactOnlineHostTest,RuleFactBoundEvaluationTest,RuleFactExecutionEvaluationTest,RuleFactFrozenDatasetTest,RuleAgentEvidenceToolsTest,AgentEvalChainRegressionTest" "-DskipTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Operation Prepare -CaseId RFE-001 -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m1-r2/request-package.json
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Operation Assembly -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m1-r2/request-package.json -ApprovedPackageSha256 f4a2c6d6b4090ad8e7b7e4f67f30fab50eeef5ba7a10feaeef8606bf141127e7
```

以上命令均退出0。相关测试32/32通过（2+2+7+2+13+6），0失败/错误/跳过，报告副本在 `tests/`。新增跨进程测试首次暴露了测试辅助进程的 Windows stdout 编码问题，已改为明确写出UTF-8 JSON字节；最终执行的报告确认通过，未将早期失败报告计为PASS。

修正版 Prepare 构建通过；三个独立 pwsh/Java 进程的 Check 均通过，原始输出为 `preflight-1.txt` 至 `preflight-3.txt`。

Assembly：专用随机命名容器，回环端口55519；空临时库成功应用19个迁移至v23。RFE-001 原题检索返回10个通过核验的候选，source/chunk/revision/checksum 已绑定；命中 `handbook-2025-article-4`，读取返回4个审核事实（其中2个是冻结必答事实）、3份已核验证据。原始宿主观察见 [assembly.json](assembly-fbcb0bc64ac94cc5ac8fefeb027ed3c4/assembly.json)。调用数为0，后续只读 Docker 检查确认无 `zhku-rulefact-eval-*` 容器残留；未操作共享演示库。

## 新执行包

`request-package.json` SHA-256：

`f4a2c6d6b4090ad8e7b7e4f67f30fab50eeef5ba7a10feaeef8606bf141127e7`

范围不变：RFE-001 冻结原题、1 task/1 run，DashScope `qwen3.8-flash`，最多5次供应商调用，每次最多1024输出tokens/20秒，run最长110秒，无自动重试或续跑，专用临时数据库。拟授权有效期为用户确认后24小时；调用额度不等于固定货币费用承诺。

AGENTS.md 第26行要求核对已批准包身份/SHA、额度、预算、有效期。修复改变生产源码、构建与启动器身份，因而不能凭原 SHA 的授权调用新构建。已向用户请求这个新 SHA 的明确确认。在确认前只做本地测试和零模型装配；不生成新 APPROVED 回执、不运行 Execute。

## 剩余验收

取得新包具体授权后，执行一次真实样本；核对实际调用、资源清理、capture/annotation 摘要绑定和逐维判定，保留完整20题分母。真实执行失败也按事实报告，不自动重跑。当前不能宣称真实 Agent 回答已完成或里程碑PASS。
