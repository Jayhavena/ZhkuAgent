# ZhkuAgent E13 实际失败样本：针对性离线修复与回归（2026-10-27）

状态：OFFLINE_TARGETED_TESTS_PASS；NO_PAID_MODEL_CALLS；NO_REAL_ONLINE_RERUN。

## 原始证据（不可回写）

- 2026-10-27 Live 执行：E02 / E04-PUBLIC 为 RESOLVED+COMPLETE；E13 最终 FAILED。
- 真实 E13 第 4 次 PUBLICATION_REVIEW 收到 HTTP 200、finish_reason=stop、可解析的 JSON，但返回 REVIEW_JSON_INVALID。
- 在原始 JSON 中确实存在 dimension=UNKNOWN 与 recognition=IDENTIFIED 同时出现；Java Requirement 合同禁止该组合。
- 上轮隔离三题 Host 将 Runner 的失败继续上抛，使 TaskStore 输出 TASK_EXECUTION_UNAVAILABLE；诊断类别被遮蔽。
- 冻结包：d4702146ec98832518401ca08f2f421c61e02fc3ff0fa95ec2e1840b70cd7681，未修改。
- 原 Live 回执 SHA-256：b0702d34403a7fba0611bfb6b6b848e8b11eb0b33a6fae10d8589b28052a2728。
- 原 Provider exchanges SHA-256：5598bab233a33d303395301aee56d960e2f03e7995655d77bef06b816ad51fdc。

## 修复范围

1. RulePublicationProtocol.description：补充 IDENTIFIED/UNKNOWN 互斥的明确协议要求；未支持实时事项须在 unresolvedDemands 中说明，不绑定伪事实。
2. RuleAgentProvider.REVIEW_SYSTEM：与上述不变量保持一致。仅改提示词/合同提示，不自动篡改供应商响应，也不放宽严格解码或发布 Gate。
3. RuleAgentThreeQuestionHostTest：遇到 Runner 原生 FAILED/STOPPED 时，关闭当前批次并保留其真实 outcome，避免测试宿主抛出新异常导致 TaskStore 将原始失败类别包装成 TASK_EXECUTION_UNAVAILABLE。没有添加重试，也没有放开请求序列或执行预算。
4. RuleResultPublisher.issue：INVALID_MODEL_RESPONSE 失败时更明确表示协议/语义核验未通过，不把处理失败当成资料不存在或正常资料不足终态。

## 离线回归

- 新资源：knowledge/src/test/resources/three-question-regression/e13-review-20261027.json。仅包含从已经保存的本地审计记录提取的 SemanticInput 和模型核验正文，不复制会话标识、用户凭证或供应商密钥。文件 SHA-256：3c3528a832edb1960893332fb85687e6eb6c8f9f45678e5cd3a1c160e1892da7。
- 新测试：RuleAgentE13ArchivedReviewRegressionTest.java：原响应严格解码拒绝；提示词和合同显式说明；支持的安全 unresolved-only JSON 能正常解码；失败仍归为 PROCESSING_FAILURE，不伪称 DATA_GAP；真实 Provider 解析链通过本机 127.0.0.1 假服务复现 REVIEW_JSON_INVALID（无外部 API）。
- 更新现有 RuleAgentThreeQuestionScopeTest.java：合成 Runner FAILED 时，隔离 Host 关闭批次且保留原 FAILED reason，不继续调用。
- 2026-10-27 09:49 (UTC+08): 离线扩展回归共 126 tests，0 failures，0 errors，BUILD SUCCESS。覆盖 E13 样本、scope、review diagnostics、review prompt、online factory、publication eligibility/gate/excerpt。此时新 loopback 用例尚未加入。
- 2026-10-27 09:51 (UTC+08): 新增 loopback 测试后，单独运行 RuleAgentE13ArchivedReviewRegressionTest，共 4 tests，0 failures，0 errors，BUILD SUCCESS。
- 2026-10-27 10:04 (UTC+08): 将新增 loopback 用例并入全部定向回归后统一重跑，共 127 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。所有测试均为 Maven offline 模式。
- 第一次离线尝试有测试参数问题（原 RuleReviewConditionPromptTest 要求 zhku.rulecheck.evidenceDir），补齐隔离 TEMP 证据路径后通过；不是业务逻辑回归失败。
- 全部 Maven 调用使用 -o 离线依赖模式；测试进程中移除 DASHSCOPE_API_KEY 和 ZHKU_DB_PASSWORD；没有读取供应商密钥，没有收费模型或 Embedding API 请求。

## 结论与严格限制

这次修复的是【供应商模型出现不合规核验 JSON 时的提示词约束、故障分类和隔离宿主失败封闭】。真实 E13 旧响应依然必须被拒绝；不能自动改写 UNKNOWN+IDENTIFIED 为合法完整答案。旧 3 题冻结包因生产源码发生变化，不得再以原 SHA 的授权身份执行新 Live；原授权已消费。

本轮没有再次真实运行 E13，也没有证明 E13 现在能发布理想的 DATA_GAP / SCOPE_LIMIT 答复。当前事实仍是：E13 原 Live 失败，修复后的本地回归通过。新的真实产品验收将需要新的冻结包与明确的在线授权。

不进行 18 题扩容、不修改检索评测基线、不提交/推送 Git。