T05 最终工程验收：T05_ENGINEERING_ACCEPTED（2026-10-16）。

本轮只做零外发验收收口，未修改生产源码、原计划门槛、jar或任何69原始证据；没有模型/数据库/Docker操作，没有复用额度，没有进入T06实现。生产候选源码bc5cdf4，真实在线证据提交26a412e；本轮开始HEAD26a412e。

原定验收要求见docs/superpowers/plans/2026-10-13-rule-check-agent-plan-review.md第184–198行：本地fake超时、坏JSON、401/429/5xx、断连、未知动作分类准确且无自动重试；获准小批真实决策包含读观察后的继续/结束及一次有用澄清续答；记录身份、请求指纹、响应、次数、失败及未知费用。原文件SHA与开始时一致。

本轮先审查具体测试方法及夹具边界，再移除真实凭证并离线运行13类213项：0失败、0错误、0跳过，资源退出确认。Maven使用-o、显式测试类名单与预建的本轮test-evidence输出目录。使用本机127.0.0.1假HTTP服务，没有对真实模型或数据库操作。本轮没有新的生产行为，所以没有新增或修改测试来“凑通过”。原始XML见regression-reports，命令/退出见regression-process.json，方法/源码行/实际XML testcase见acceptance-matrix.json。

| 项 | 原计划门槛投影 | 结论 | 具体证据 |
| --- | --- | --- | --- |
| A1 | 本地fake服务覆盖超时、坏JSON、401/429/5xx、断连并分类 | PASS | RuleAgentProviderTest.timeoutIncludesWholeResponseAndConsumesAllowance；RuleAgentProviderTest.badJsonIsProcessingFailureWithoutRetry；RuleAgentProviderTest.authorizationAndServerErrorsDoNotBecomeMissingEvidence；RuleAgentProviderTest.rateLimitIsClassifiedWithoutRetry；RuleAgentProviderTest.serverFailureIsClassified；RuleAgentProviderTest.disconnectedEndpointIsFailureWithoutRetry；RuleAgentProviderTest.reviewHttpFailuresKeepPurposeAndDoNotRetry |
| A2 | 合法动作可执行、非法动作不执行；独立发布核验不绕过 | PASS | RuleAgentProviderTest.unknownActionCannotBecomeATool；RuleAgentProviderTest.failuresAreSafeClassifiedAndDoNotExecuteTools；RuleAgentProviderTest.wrongFingerprintUnknownFieldsAndInvalidEnumAreRejected；RuleAgentOnlineFactoryTest.p02SameTaskResumeIsIdempotentAndRequiresBothLiveReviews |
| A3 | ONLINE拒绝缺许可/缺密钥及旧回放，固定目的地/模型 | PASS | RuleAgentProviderTest.onlineEnvironmentRejectsReplayAndMissingAuthorization；RuleAgentProviderTest.onlineEnvironmentPinsDestinationAndModel；RuleAgentProviderTest.missingKeyMakesNoHttpCall；RuleAgentOnlineFactoryTest.probeStartupRequiresSpecificPackageApproval |
| A4 | 预算和调用次数不因故障/续答重置；无自动重试 | PASS | RuleAgentProviderTest.exhaustedAllowanceMakesNoHttpCall；RuleAgentProviderTest.reviewHttpFailuresKeepPurposeAndDoNotRetry；RuleAgentOnlineFactoryTest.quotaIsSharedAcrossNewRunsAndNeverReset |
| A5 | 最小化请求说明、准确外发范围、独立可执行装配和启动检查 | PASS | RuleAgentRequestPathTest.launcherUsesTheSameFreshDirectoryForPackageLiveAndLogs；RuleAgentRequestPathTest.hostRejectsExistingTargetBeforeReadingAnyAssets；RuleAgentRequestPathTest.launcherRejectsExistingNewTargetBeforeInvokingBuilder；RuleAgentOnlineFactoryTest.buildIdentityPinsArtifactAndSourceAndRejectsMissingJar |
| A6 | 获准真实决策包含读取观察后的继续和结束 | PASS | 69同任务原始轨迹与已冻结身份记录 |
| A7 | 一次有用澄清续答，归属/版本及原文依据完整 | PASS | 69同任务原始轨迹与已冻结身份记录 |
| A8 | 记录task/run、请求指纹、响应、实际次数，失败成本不伪造 | PASS | RuleAgentProviderTest.invalidEnvelopeKeepsKnownUsageAndMissingUsageStaysUnknown；RuleAgentReviewDiagnosticsTest.responseArchiveFailurePreventsAcceptedReply；RuleAgentReviewDiagnosticsTest.requestArchiveFailurePreventsDispatch |
| A9 | 原文/用途/权限/适用范围保留，旧授权关闭、资源退出 | PASS | 69同任务原始轨迹与已冻结身份记录 |

九行是对原门槛的可审映射，不是新增九个产品门槛。5xx用503代表；断连夹具具体为停止本地端点后的连接失败，不谎称全TLS/半包EOF变体覆盖。401/429/503等假HTTP故障只收到一次请求，应用层不自动补发；不对HTTP/TCP所有内部实现作超出证据的承诺。

当前工程候选的在线退出条件由69自身满足：task8c687d69-833f-405f-aaeb-80f0ec5cb386、run9ad28cae→1fb4624b、version2→4，两轮各SEARCH/READ/结束提议/独立核验，先WAITING_USER再RESOLVED COMPLETE；8次HTTP200，任务仓发布与outcome除turnId一致。该链路已完整包含读取后继续/结束及有用澄清，不靠跨批次阶段拼接，不以213项测试替代在线。当前固定P01题未另跑，不伪称全套探针在当前包重测；44包P01的历史完整通过另列，未拿来替代当前链路。

严格保留：原文/权限及2025.9.2历史适用范围、全部当前候选、原始Fact身份、必要条件、独立发布核验及拒绝标准。69首轮仍有两条同名条件记录并正确输出裸键；不合并、不放宽、不默认c1、不自动纠正响应。核验仍是同模型上下文/职责分离，不能称跨模型独立。

边界：这是T05“真实模型适配与独立在线装配”的工程验收收口，不是T06学生HTTP/代理链、T07页面、T08未预置能力/重复可靠性、T09学生收益或T10总体产品交付。原plan明确这些后续任务，不把它们塞回T05作为继续返工条件。一次工程成功不证明彻底根治或稳定概率；历史在线超时原因、实际账单、已删除临时库表清理计数仍未知，不降标准、不填0、不改历史失败。

权威状态记录：[T05-CLOSEOUT.md](../../2026-10-14/T05-CLOSEOUT.md)。本轮只在旧记录前添加当前结论与校正，旧正文尾部字节原样保留，基线SHA见identity-before.json。69的result-summary/overallT05Acceptance=NOT_DECLARED是上一批当时的范围声明，保留原字节；本轮基于原退出标准另作最终工程判定，不追改69回执。

工作树codex/t05-online-evidence尚未合入主仓库；主仓库ec1e362及四份既有脏文档保持。本轮不push/PR/标签/部署，不开放学生入口。源码、jar、运行资产、原始轨迹及所有旧授权关闭状态都需按各自证据核对，不能从主仓库旧jar继承此候选通过身份。

下一项T06：实际HTTP提问→ASK→续答→结果→原文，覆盖身份/CSRF/跨账号/跨task/旧version、幂等及代理实际响应；真实供应商外发须新的具体授权，本轮没有启动该阶段。

恢复：独立逆向提交撤回本轮状态与验收材料；保留所有69真实原文证据/历史失败与冻结产物，不恢复额度。源码/构建/资产变更须重新核对相应身份及验收。
