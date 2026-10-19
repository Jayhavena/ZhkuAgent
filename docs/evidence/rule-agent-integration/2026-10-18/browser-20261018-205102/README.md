# 浏览器批次 20261018-205102：FAIL，保留部分验证

本批源码为 `ba0e80f3de34cf59d40f435b9cb7d98c120e7b49`。学生实际构建经本机同源测试代理访问真实临时库身份与已审核 2025.9.2 资料；执行器为 `SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS`，供应商调用和模型尝试均为 0。这是测试静态同源代理，不是仓库 nginx 验收，也不证明在线模型可用。

**整体结果为 FAIL。** 主会话使用 CUA 亲测了以下五项，但最后的资料资格撤销触发测试宿主 SQL 约束错误，未能完成整批验收。后续修复必须使用新源码、新租约另行验证，不能把本批改记为通过。

| 部分验证项 | 主会话实际 UI 观察 | 原始传输佐证 |
| --- | --- | --- |
| 登录 | 打开真实学生登录页并完成测试身份登录 | `auth/me` 401 → `auth/login` 200 → `auth/me` 200 |
| 异步澄清、续答与原文 | “需要澄清”进入处理中和澄清；选择 PUBLIC 续答，得到结果，并查看真实相关段落与原文 | 任务 `476dc86e-f2c6-451b-9aac-8420509ff0ae`：202 → ACTIVE v1 → WAITING_USER v2 → 续答 202 → ACTIVE v3 → RESOLVED/COMPLETE v4；原文 200 |
| 跨页退出撤回 | B 标签页退出后，A 标签页的结果与原文撤回 | 20:56:27（北京时间）退出 204 |
| 迟到响应不得复活 | “直接查证”的终态响应被暂扣；另一页退出后释放，A 页结果与原文未复活 | 任务 `319a9134-9835-4927-8a6f-e953d9be384b` 的终态被暂扣；退出 204 后尝试交付，记录 `clientDisconnected=true` |
| PARTIAL 展示 | “部分覆盖”展示部分覆盖结果，并在撤资格前打开对应原文 | 任务 `9d390df6-9088-4100-8c34-025a13f8e420`：ACTIVE → RESOLVED/PARTIAL v2，带资料依据；原文 200 |

UI 断言由 root 主会话完成；本记录由协作代理依据 root 的亲测报告整理。该协作代理执行回合的 CUA inventory 为空，未独立观察页面，详见 [连接边界](subagent-browser-connection-boundary.json)。真实 UI 快照与截图保留在 root 的 CUA 对话记录；本目录未导出截图或原始 DOM 快照文件。[代理往返记录](browser-proxy-exchanges.json)只能佐证真实 HTTP 时序与任务合同，不能替代上述 UI 断言。结构化结果见 [browser-observations.json](browser-observations.json)。

## 失败与未验证范围

root 写入 `REVOKE_REVIEW` 后，宿主在本源码的 `RuleAgentReviewedBrowserLeaseTest.java:59` 执行 `sharing_rights_confirmed=false`，却保留 `visibility_scope=STUDENT_VISIBLE`，违反 `ck_knowledge_source_student_visibility`。更新未成功，宿主随后异常结束。因此“撤销资料资格后，重新访问旧原文必须失效”**未验证**；后续后端不可用不能解释成 reader 已正确拒绝资格失效的原文。

原始 [Maven 日志](maven.log)与 [Surefire XML](TEST-com.zhku.knowledge.ruleagent.web.RuleAgentReviewedBrowserLeaseTest.xml)均记录 `DataIntegrityViolationException`，测试统计为 1 tests / 0 failures / 1 errors / 0 skipped，`BUILD FAILURE`。[上游错误](browser-proxy-upstream-error.json)记录 `ECONNREFUSED`，只表明后端已不可用。目录没有 `review-revoked.json`，不能声称资格变更完成。

## 资源回收

[宿主退出记录](owned-process-exit.json)显示宿主进程已退出、专用临时数据库容器已回收，宿主退出码为 1。[STOP](STOP)为随后显式停止代理的控制标记；[代理退出记录](browser-proxy-exit.json)确认显式 STOP、前端监听退出、暂扣响应数为 0。该 STOP 不代表宿主正常完成验收。

[独立回收探测](subagent-resource-cleanup.json)于 21:01:34（北京时间）确认代理会话退出码 0、PID 47060 不存在，前端 18825 与后端 11111 均返回 `ConnectionRefused`。root 已关闭自己创建的两个标签页。协作代理未创建标签页、未改源码、未重跑 Maven。

批次身份及启动信息：[lease-manifest.json](lease-manifest.json)、[browser-ready.json](browser-ready.json)、[browser-proxy-ready.json](browser-proxy-ready.json)。迟到响应场景保留了 [late-response-held.json](late-response-held.json)、`HOLD_NEXT_TERMINAL`、`RELEASE_LATE` 原始控制与响应记录。
