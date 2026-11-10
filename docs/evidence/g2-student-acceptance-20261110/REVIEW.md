# G2 代码与证据独立审查

日期：2026-11-10
审查对象：`94a89aa3..0a08ebff`（G2 代码）与 `a35be4b9`（G2 证据）
审查 HEAD：`96e6fb09`（`a35be4b9` + G1 签核 `ba3efc54` 的 docs-only 合并）
结论：**G2_CODE_AND_EVIDENCE_ACCEPTED_FOR_CONTROLLED_TRIAL_DELIVERY**

本审查只读代码、测试和已保存证据，不重新运行 G2 测试、不调用模型、不改产品代码。接受范围仅限受控工程试用，不代表真实模型质量、生产部署或正式发布。

## 身份

| 项目 | 值 |
| --- | --- |
| G1 交付 | `94a89aa3b10a3fcd364fe3d96dd15ae67b3bd7e1` |
| G2 代码 | `0a08ebffd7a5dfb0ca1642fb318ce3e712ad9210` |
| G2 证据 | `a35be4b9ef3d10e0052b4726d64546ab2079ab3e` |
| G1 签核 | `ba3efc54dbf2ee7e5c34a396ccaa9a45bb3ede35` |
| 审查 HEAD | `96e6fb09`（merge，代码树不变） |
| 代码范围 | 4 个修复提交 + 1 个租约测试提交 |
| 证据范围 | 15 个文件（README、总记录、12 组浏览器检查、账号安全汇总） |

G2 提交链：

```text
94a89aa3  G1 交付
  -> b5498e5e  fix(frontend): recover bounded account-scoped task metadata after reload
  -> 71e154a3  fix(frontend): preserve recovery metadata across auth expiry transitions
  -> 2421bf0b  fix(rule-agent): reject revoked evidence in task snapshot responses
  -> 72a6671c  fix(frontend): retract cached results when snapshot eligibility is rejected
  -> 0a08ebff  test(integration): expose explicit reviewed student browser acceptance lease
  -> a35be4b9  evidence: record G2 student candidate final acceptance
```

## 生产代码审查

### C1：账号隔离

- 任务快照、原文、取消、消息四类端点继续走既有 `owner` 校验；G2 没有放宽 Owner 或角色边界。
- 最终浏览器证据中，独立账号对四类操作均得到 404 `TASK_NOT_AVAILABLE`，且无他人历史。
- 未发现阻断性越权路径。

### C2：撤销资格后的快照拒绝

- `RuleAgentTaskController.snapshot` 在返回 `snapshot.result()` 前，对每条 evidence 复用 `currentEvidence`，读取当前原文资格并校验 sourceId/chunkId/revision/checksum/span/quote。
- 原文端点与快照端点共用同一校验方法，避免只修原文而漏掉快照。
- 任一条 evidence 失效即抛 `EXPIRED`，返回 409，不返回旧 claim/evidence；读取在 TaskStore 锁外，最多 8 条，不重跑 Agent。
- 回归先断言 409 且响应不含原文，再恢复资格；这是有效的资格边界测试，不依赖任务过期。
- 残余风险：逐条读取不是跨证据的原子数据库快照；在读取与响应之间发生撤销，存在很小的时间窗。当前不接受为更强的一致性承诺，但不是已证实的阻断。

### C3：有限恢复

- `rule-task-history.js` 只保存 `owner` + 最多 5 条 `taskId/runId/turnId/version/recordedAt`；不保存问题正文、原文、continuationToken 或 CSRF。
- 缓存键绑定账号 UUID；严格校验结构、UUID、版本、时间、重复项和 4096 字节上限；损坏/跨账号/超限时清空。
- 15 分钟过期；刷新恢复只 GET 当前账号可访问的 snapshot，重新取得服务端权限，不自动重发 POST。
- 404/409 删除该条元数据并撤回旧答案/原文；503/网络未知保留手动查询入口；401 撤回身份显示。
- 退出、换账号、跨窗口身份变化清缓存；`AUTH_EXPIRED` 保留元数据以便同账号重新登录后恢复。
- `stopReason` 监听避免 epoch 先递增导致误用上一轮 reason 清缓存；迟到响应受 generation + epoch guard 拒绝。
- 残余风险：仅同标签页、约 15 分钟；不支持服务器重启或跨设备历史；不是校园统一身份/SSO。

### 测试与租约入口

- 记录显示：前端 201 项 Node 测试通过、构建通过；后端扩展组 32 项（31 PASS、1 既有 Host 显式分派 SKIP、0 FAIL/ERROR）。本次审查未重跑这些测试。
- `verify-rule-check-refactor.ps1` 新增显式 `-ReviewedBrowserLease -AssetsRoot` 入口；浏览器测试必须单选，普通测试不得夹带租约参数；`-CheckEnvironment` 只做预检。
- 54 项启动器契约检查覆盖：必须显式选择、拒绝混合测试、拒绝缺资料根、普通测试拒绝租约、只生成零模型参数、参数在整条函数链声明。
- 租约入口只传递 `zhku.t06.reviewedBrowserLease=true`、资料根和 source SHA，不注入供应商密钥。

## 证据审查

### 机器记录

`acceptance.json` 固定：

- `testedCommit=0a08ebff`；`mode=SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS`；
- `providerCalls=0`，`browserMatrixVerdict=PASS_WITH_EXPLICIT_LIMITS`；
- `sourceDrift=0`，`resourcesClosed=true`，`connectionsAfterTests=0`；
- 数据库迁移 `23`，未挂载数据卷；容器退出；
- 4 个实际加载资产的字节数和 SHA-256 与本地 `dist` 一致；
- 36 个前端源码文件、4 个后端类的 SHA-256；
- 5 个关键原始证据文件的 SHA-256；
- 4 个中间尝试标记为 `NOT_ACCEPTED`，未混入通过结论。

### 浏览器矩阵

12 组 JSON 覆盖：注册、等待澄清刷新、已完成任务刷新、活动任务刷新与取消、会话失效、重新登录边界、跨账号隔离、撤销前正控、撤销后快照/原文、撤销后刷新、显式退出、加载资产。每个文件只含状态、HTTP 码、计数和布尔观察，不含原文或 Token。

### 账号安全矩阵

`account-security.json` 汇总 4 个测试类共 11 项：注册账号 3、持久登录安全 5、记住我重启 1、滑动登录 2；0 失败/错误/跳过。该矩阵使用 `SYNTHETIC_FIXTURES`，与已审核资料的浏览器主链是两组独立证据，不能互相替代。

### 脱敏与可复核性

- 本目录不含原始请求/响应、审核原文、凭据、数据库、PDF、构建输出或本机绝对路径。
- `sourceAssetsRoot` 明确写为“位置省略”；`actualInvoiceCny=null`、`invoiceStatus=UNVERIFIED`。
- 原始 `maven.log`、`command.json`、`results.json`、XML 和宿主退出记录保留本地 `docs/evidence/student-trial-20261110/`；`acceptance.json` 记录其中 5 个文件的摘要。
- 因此本包可复核“已保存证据与结论一致”，但不能仅凭本包重放原始浏览器交互或核对真实账单。

## 发现清单

| 级别 | 发现 | 处置 |
| --- | --- | --- |
| 阻断 | 无 | — |
| 非阻断 | 快照资格逐条读取非原子 | 接受为有界读取；不作强一致性声明 |
| 非阻断 | 恢复仅同标签页/15 分钟 | 页面明确提示；不声称服务器历史 |
| 非阻断 | 503/网络未知保留入口 | 不自动重放 POST；需人工继续查询 |
| 非阻断 | 账号安全矩阵为合成夹具 | 与已审核资料矩阵分开标注 |
| 非阻断 | 真实模型未参与 | 明确为工程回放；模型质量未验证 |

## 接受结论

**接受 `0a08ebff` 的 G2 生产代码与 `a35be4b9` 的脱敏证据，用于受控学生试用候选交付。**

条件：

1. G1 签核 `ba3efc54` 与 G1 PR #3 的对应关系保持；
2. G2 PR 以 G1 分支为 base，审查者能看到“G2 相对 G1”的干净差异；
3. 不把本结论扩展为真实模型质量、生产部署、正式发布或全校学生准入；
4. 不修改 `0a08ebff` 代码后继续引用本证据；任何代码变更必须重新对应证据；
5. 实际账单、在线宿主、HTTPS/安全 Cookie、邀请范围和供应商授权仍为未完成事项。

## 复核入口

```powershell
git diff --stat 94a89aa3 0a08ebff
git diff 94a89aa3 0a08ebff -- knowledge/src/main agent/frontend/src
Get-Content docs/evidence/g2-student-acceptance-20261110/acceptance.json -Raw | ConvertFrom-Json
Get-ChildItem docs/evidence/g2-student-acceptance-20261110/*.json | ForEach-Object {
  Get-Content $_.FullName -Raw | ConvertFrom-Json | Out-Null
  "OK $($_.Name)"
}
```

本目录未附独立重放脚本；`acceptance.json` 的 `sourceEvidenceDigests` 可用于核对本地原始证据，原始浏览器交互和真实账单不能仅凭本包重放。
