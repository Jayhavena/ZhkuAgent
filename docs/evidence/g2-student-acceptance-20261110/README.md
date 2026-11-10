# G2 学生试用候选最终验收（2026-11-10）

## 结论

在候选提交 `0a08ebffd7a5dfb0ca1642fb318ce3e712ad9210` 上，使用真实临时 PostgreSQL、真实 Spring HTTP、Nginx 学生页面和只读已审核资料，完成零模型验收：

- 浏览器租约宿主 1 项 JUnit 通过；浏览器 12 组检查覆盖注册登录、提交轮询、澄清续答、刷新恢复、取消、会话失效、重新登录边界、跨账号隔离、资格撤销、退出清理和构建资产一致性。
- 账号安全 HTTP 回归 11 项通过（4 个测试类，合成夹具）。
- 两次运行的 `sourceDrift` 均为 0；数据库连接数为 0；监听、工作线程和临时容器均已回收；`providerCalls` 为 0。

本结论是**受控工程验收**，不代表真实模型质量、生产部署、正式发布或实际费用结算通过。

## 与 PR #1 的关系

本次验收的 `0a08ebff` 属于本地 `trial/student-candidate-20261110`，**不在 PR #1 当前 head `ae017631` 中**。

- PR #1 当前 head 为 `42d4491d` 加 1 个归档边界提交，只处理 13 个文件的移除与说明；它不包含本候选的 G2 修复。
- 因此，本报告不能用来证明 PR #1 head 的学生页面整链通过。
- 是否把本候选接入 PR #1 或另开 PR，需要新的明确授权；本轮 G2 验收没有 push，也没有改动 PR #1 的 head（PR #1 的 head 已在先前批准的更新中设为 `ae017631`）。

## 环境与边界

| 项目 | 值 |
| --- | --- |
| 被测提交 | `0a08ebffd7a5dfb0ca1642fb318ce3e712ad9210` |
| 执行模式 | `SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS` |
| 模型调用 | 0；`modelAttemptCount=0`，`providerCalls=0` |
| 数据库 | 一次性临时 PostgreSQL，迁移 23；未挂载数据卷 |
| 浏览器主链数据 | `REVIEWED_2025_TEXT`，只读已审核资料；资料根路径不随本证据发布 |
| 账号安全矩阵数据 | `SYNTHETIC_FIXTURES` |
| 前端 | `0.1.0-rc.1`；加载的 4 个构建资产 SHA-256 与 `dist` 一致 |
| 后端 | `0.1.0-rc.1` |
| 资源回收 | 容器退出、数据库连接 0、后台工作线程退出、监听退出 |
| 实际人民币账单 | `UNVERIFIED`（本机宿主没有实时账单熔断） |

## 浏览器主链结果

| 检查 | 观察结果 |
| --- | --- |
| 注册与登录 | 注册成功并回到登录页；使用新账号登录学生页 |
| 提交与轮询 | 任务进入等待澄清；补答后变为 `RESOLVED`，`coverage=COMPLETE` |
| 澄清后续答 | 页面恢复等待状态，未自动重发 POST |
| 已完成任务刷新 | 恢复历史答案；历史数量 2；可恢复上一条结果 |
| 活动任务刷新与取消 | 恢复活动任务；点击停止后显示“本次查证已停止” |
| 会话失效 | 登录页出现；历史元数据保留；旧答案消失；服务端退出返回 204 |
| 重新登录边界 | 已登录；旧任务显示“过期或不可访问”；旧答案不重现 |
| 跨账号隔离 | 快照、原文、取消、消息四类请求均返回 404 `TASK_NOT_AVAILABLE`；无他人历史 |
| 资格撤销前正控 | 原文请求返回 200，答案可见 |
| 资格撤销后 | 快照和原文均返回 409 `TASK_EXPIRED`，响应不包含结果 |
| 撤销后刷新 | 旧结论和旧答案不出现；显示失败提示；自动 POST 数为 0 |
| 显式退出 | 登录页出现；任务元数据清空；旧答案消失 |
| 构建资产 | 4 个已加载资产的字节数和 SHA-256 与本地 `dist` 一致 |

## 账号安全矩阵

`RuleCheckRegisteredAccountHttpTest`（3）、`RuleCheckPersistentLoginSecurityHttpTest`（5）、`RuleCheckRememberMeHttpRestartTest`（1）、`RuleCheckSlidingLoginHttpTest`（2），合计 11 项通过，0 失败、0 错误、0 跳过。

该矩阵使用合成夹具，覆盖注册账号、持久登录安全、记住我重启和滑动登录；它与上面的已审核资料浏览器主链是两组独立证据，不能互相替代。

## 证据索引

| 文件 | 内容 |
| --- | --- |
| `acceptance.json` | 机器可读总记录：被测提交、前后端类/源码摘要、加载资产、宿主退出、原始证据摘要 |
| `registration.json` | 注册成功 |
| `waiting-reload.json` | 等待澄清时刷新恢复，未自动重发 |
| `resolved-history.json` | 已完成任务刷新与历史恢复 |
| `active-reload-cancel.json` | 活动任务刷新与取消 |
| `auth-expiry.json` | 会话失效与本地元数据边界 |
| `reauth-boundary.json` | 重新登录后的旧任务边界 |
| `account-isolation.json` | 跨账号四类操作拒绝 |
| `revoke-control.json` | 撤销前原文正控 |
| `revoked-endpoints.json` | 撤销后快照/原文拒绝 |
| `revoked-reload.json` | 撤销后刷新不重现旧结论 |
| `logout.json` | 显式退出清理 |
| `loaded-assets.json` | 实际加载资产摘要 |
| `account-security.json` | 账号安全矩阵的脱敏汇总 |

原始 `maven.log`、`command.json`、`results.json`、`database.json`、XML 报告和浏览器宿主退出记录保留在本地 `docs/evidence/student-trial-20261110/`，不随本目录提交；`acceptance.json` 记录了其中 5 个关键原始文件的 SHA-256，便于本地复核。

`g2-acceptance-01`、`g2-browser-01`、`g2-browser-final-01`、`g2-browser-green-01` 是验收过程中的中间尝试，存在源码漂移、未使用的旧租约或已复现缺陷；它们不作为通过证据，已在 `acceptance.json` 的 `intermediateAttempts` 中标注为 `NOT_ACCEPTED`。

## 复跑

在候选提交的干净检出中，先确保 Docker 就绪，并使用新的阶段名和专用端口：

```powershell
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Phase trial-browser-rerun-01 -Tests RuleAgentReviewedBrowserLeaseTest -Port 55524 -ReviewedBrowserLease -AssetsRoot '<已核对审核资料根>' -EvidenceRoot docs/evidence/student-trial-rerun
```

账号安全矩阵单独运行：

```powershell
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Phase trial-account-rerun-01 -Tests RuleCheckRegisteredAccountHttpTest,RuleCheckPersistentLoginSecurityHttpTest,RuleCheckRememberMeHttpRestartTest,RuleCheckSlidingLoginHttpTest -Port 55525 -EvidenceRoot docs/evidence/student-trial-rerun
```

浏览器租约必须显式单选，且必须提供已核对资料根；普通测试不得夹带租约参数。阶段名每轮更换，不能复用已迁移的临时库。

## 未验证与限制

- 真实模型决策、真实供应商调用、真实学生试用和模型质量未验证；本轮为工程回放。
- 实际人民币账单为 `UNVERIFIED`；预算上限不等于账单已核对。
- G1 签核和运行时资产摘要的既有证据限制仍适用；本报告不替代 G1 正式签核。
- 浏览器自动化不能替代人工可用性、无障碍和多浏览器兼容性评审。
- 本候选未接入 PR #1；PR #1 的 head 仍是 `ae017631`，其 CI 通过不等于本候选通过。
- 本目录不包含原始请求包、完整网页抓取、数据库、PDF、构建产物或凭证。
