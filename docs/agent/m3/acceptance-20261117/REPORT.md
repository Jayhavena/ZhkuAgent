# M3 核心业务技术验收收敛记录

2026-11-17。**M3 最小技术链验收通过，可进入 M4 集成准备；实际账单仍待核验，不能宣称整体 Agent 可靠性或正式发布通过。** 本次汇合离线业务边界、真实数据库及 Spring HTTP、已授权真实模型复测三种证据，只新增验收记录，没有修改生产代码、评分合同或历史报告，没有新增真实模型调用。

## 版本和运行身份

- 本次回归及 HTTP 源：`8fd53b4832b4b458b0ced34a69dc040fb0826270`，分支 `fix/m3-runtime-boundaries-20261114`。
- 真实复测源：`be762644854724754efa2ca658ec815f7139e2dd`；`git diff --exit-code be762644 HEAD -- knowledge/src/main agent-runtime/src/main` 返回 0。后续 8fd53b48 只保存回执，生产源码一致。两次构建的用途和摘要分别保留，不能混用 jar 身份。
- 本次重新构建 persistent jar，摘要见 [build.json](build.json)。Windows / Docker PostgreSQL / Spring HTTP；实例 `m3-close-20261117`，后端 `127.0.0.1:58200`，身份库端口 55550、临时规则库端口 55551，SIMULATED，在线关闭。
- 资产根 `D:/alldesk/Projects/ZhkuAgent/main`，外置手册 `D:/alldesk/Projects/ZhkuAgent/main/docs/student-handoff/2025版学生手册2025.9.2.pdf`。外置资产不入库。操作前重新检查本机凭据，专用实例使用独立随机数据库凭据；未写共享 54329 演示库。

## 固定范围及结果

| 证据组 | 实际结果 | 能支持的判断 |
| --- | --- | --- |
| 当前核心回归 | 14 类、255 项，0 失败/错误/跳过；21 条矩阵要求全部命中已执行方法 | 澄清、条件作用域、适用性、Claim/when、漏答/能力缺口、原文完整性、Schema、失败采集、超时、取消和幂等的固定回归通过 |
| 当前构建首次启动 HTTP | 14 项通过，0 模型调用 | 真实数据库注册登录、CSRF、Task API、幂等、澄清续答、结果、原文权限、取消与注销可工作 |
| 同构建重启后 HTTP | 14 项通过，0 模型调用 | 持久账号仍可登录；旧任务不可跨重启/会话访问；新任务链路仍通过 |
| 已完成的新授权真实两轮复测 | 8 次实际调用，Grader 2 PASS；补充检查 19 项通过 | 一个问题确实执行检索/读取、必要澄清、续答及两次发布核验，发布匹配 PUBLIC 条件的规则 Claim |
| 本次停止回收 | Stop=0、宿主退出=0，三个端口无监听，规则容器移除，身份容器停止、卷保留 | 本次正常关停完成，身份数据按设计保留 |

[matrix.json](matrix.json) 固定方法与源文件摘要；[regression.json](regression.json) 保存执行计数、每类 XML 摘要和方法匹配。21 条是选定技术要求，不是 21 个真实模型任务，不计算任务成功率。原始输出在本机 `knowledge/target/m3-acceptance-20261117.log`，其摘要已记录；target 不提交。

HTTP 脱敏回执：[首启](http-initial.json)、[重启](http-restart.json)、[停止回收](shutdown.json)。两次取消均实际得到 202/STOPPED。注册、登录、任务、续答、结果和原文经后端 HTTP 完成。本次没有重新执行浏览器操作，没有验证持久学生 HTTP 的 ONLINE 全链路。

真实复测和未改写的原生报告见 [真实复测记录](../../eval/m3-retest-20261117/RESULT.md)。新 Schema 本批被供应商接受，不证明供应商必然拒绝所有非法配对，宿主校验仍保留。旧失败及其 2 NOT_EVALUABLE 报告保持原样，未倒填快照或改分。

## 复验入口

仓库根执行；数据库操作前先运行 `knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly`，重新核对 Process/User/Machine 凭据，不把密钥写进参数或报告。

```powershell
$pdf='D:/alldesk/Projects/ZhkuAgent/main/docs/student-handoff/2025版学生手册2025.9.2.pdf'
& .\mvnw.cmd -o -pl knowledge -am '-Dtest=RuleAgentQuestionConditionsTest,RuleAgentKnownConditionActionTest,RuleAgentGapAttributionTest,RuleCheckApplicabilityTest,RuleCheckOriginalEvidenceServiceTest,RulePublicationGateTest,RuleAgentRunnerTest,RuleAgentProviderTest,RuleAgentTaskStoreTest,RuleAgentOnlineFactoryTest,RuleAgentIssueSchemaTest,RuleAgentProbeFailureCaptureTest,AgentEvalChainRegressionTest,EvalChainFailureReportTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dzhku.rulecheck.evidenceDir=$PWD/knowledge/target/m3-evidence" "-Dzhku.t06.handbookPdf=$pdf" test
```

HTTP 使用 `start-persistent-rule-check.cmd -Action Start`，显式传入独立 `-InstanceName`、三个端口、`-ExecutionMode SIMULATED`、`-AssetsRoot`、`-HandbookPdf`。首次构建后，以同参数 `-Action Restart -SkipBuild` 重启。设置 `ZHKU_STUDENT_TEST_BASE_URL`、`ZHKU_STUDENT_TEST_OUTPUT`，分别运行 `node agent/frontend/tests/manual/persistent-student-http.mjs initial` / `restart`。initial 创建固定合成账号，须使用新专用实例，不对既有实例盲目重跑注册。最后 Stop 并检查退出码、进程、容器及端口。保留身份卷；不读取或提交含凭据的 state.json。

## 开放项目与 M4 交接

1. **实际账单待核验**：真实调用输入 14852、输出 1074 tokens，目录价估算 0.0147814 元；实际账单及实际成功任务成本仍 null/UNVERIFIED。后续按模型、Key 标识及北京时间 2026-11-17 08:24:15—08:24:32 关联百炼费用明细。估价不代替账单，历史批次费用待办保留。
2. **真实质量覆盖有限**：只有一个问题两轮。显式首问声明、能力缺口码和各异常场景未逐一真实复测；提取器仍是有限启发式；无事实读取的缺口码也包含多块事实未读齐边界。旧 20 题总体分数不因单题通过而重算。
3. **尚未正式集成**：本地分支含 M2/M3 代码、测试和回执；本轮没有 push、PR、远程 CI 或合入 main。M4 先界定相对官方主线的必要提交，再做审查、CI 和候选；合入后重新构建并复验 HTTP，不能把本地结果当作 main 已通过。
4. **上线仍 NO_GO**：持久 HTTP ONLINE、长期稳定性、在线宿主、HTTPS/安全 Cookie、邀请范围、隐私和账单等仍需各自准入。

技术验收证据已收敛，财务补证单列保留。后续转向 M4 持续回归与版本整合，不沿用已消耗的调用票据。
