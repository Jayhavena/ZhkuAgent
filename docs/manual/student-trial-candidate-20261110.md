# 首个学生试用候选：运行与验收说明

本轮交付为受控工程候选，生产语义修复来自 G1；C1/C2/C3 使用真实身份、审核资料、PostgreSQL、Spring HTTP 和学生页面，模型决策显式模拟。此说明不授权真实供应商调用、部署、共享库写入或对外开放端口；真实学生模型试用仍需新的包/预算授权及对应在线 HTTP 宿主交付，不能把测试租约当长期生产服务。

## 可复跑的零模型验收

要求：Windows 用户终端、Java 21、仓库 Maven wrapper、可访问的离线 Maven 缓存、现有 Node/npm、Docker Desktop、本地已缓存 pgvector/pgvector:0.8.6-pg16-bookworm 与 Nginx 镜像。数据库凭据按既有 Process→User→Machine 解析，再在测试子进程换成独立随机口令，不打印值。需要已经审核的资料根与原 PDF；它们不随此次公开证据包提供，不得跳过指纹/分享资格检查。

在干净候选仓库根目录，先确认下列端口没有被其他服务占用；冲突时换专用端口，不停止已有服务让位。

```powershell
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Phase trial-browser-01 -Tests RuleAgentReviewedBrowserLeaseTest -Port 55524 -ReviewedBrowserLease -AssetsRoot '<已核对的审核资料根>' -EvidenceRoot docs/evidence/student-trial-rerun -CheckEnvironment
# 与预检相同的参数，去掉 -CheckEnvironment 后开始真实临时库和HTTP租约
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Phase trial-browser-01 -Tests RuleAgentReviewedBrowserLeaseTest -Port 55524 -ReviewedBrowserLease -AssetsRoot '<已核对的审核资料根>' -EvidenceRoot docs/evidence/student-trial-rerun
```

PDF 不在受支持的原布局时显式添加 `-HandbookPdf '<确切PDF文件>'`；只读核对，不能从未知位置搜索或自动下载替换。Phase 必须每轮新建，不能复用已迁移库或覆盖旧证据。`-CheckEnvironment` 只做参数/环境预检，不代替数据库或浏览器验收。

保持上述终端运行。出现 `docs/evidence/student-trial-rerun/trial-browser-01/browser-ready.json` 后，在第二终端读取其 backendPort，然后启动同一候选的学生构建：

```powershell
$ready = Get-Content docs/evidence/student-trial-rerun/trial-browser-01/browser-ready.json -Raw | ConvertFrom-Json
.\agent\frontend\start-nginx-preview.cmd -BackendPort $ready.backendPort -WebPort 58095 -ProjectName zhku-student-trial-local -EntryMode student
```

浏览器访问 `http://127.0.0.1:58095/rules`。测试身份为夹具中的 `t06-chain-a` / `t06-chain-b`；合成口令仅在测试源码 `RuleAgentSimulatedChainTempDbHttpTest.CORRECT` 中定义，绝不可用于真实学生账号。也可在 `/register` 注册本次临时库账号并在 `/account` 改密。fixture 使用固定模拟输入 `需要澄清`（PUBLIC/PROFESSIONAL）、`直接查证`、`部分覆盖`、`检索未命中`、`处理失败`；这些不代表自由问答质量。

两账号分别使用独立浏览器 profile 验证任务/原文/取消/澄清隔离；同账号刷新必须只 GET snapshot，不重放 POST。最近 5 条记录仅含定位元数据，不含正文或权限 Token。后端任务按账号+会话 nonce 隔离：浏览器刷新且会话仍有效可恢复；新建服务器会话、退出/换账号、进程重启或任务过期后，旧任务可能 404/409，必须撤回旧结果并提示新建，不弱化 Owner 来强行恢复。

在同一个仍有效的 task/token 上先确认 snapshot/original 可读，再创建租约目录中的 `REVOKE_REVIEW` 文件；确认 `review-revoked.json` 后，snapshot 与 original 都须拒绝旧证据，页面刷新或继续查询不得重现旧结论。撤销动作只改本次临时库且不可当作自然过期。租约最长 900 秒；到期没有 STOP 必须失败，不能以等待超时算验收通过。

## 停止与故障排查

```powershell
New-Item docs/evidence/student-trial-rerun/trial-browser-01/STOP -ItemType File
.\agent\frontend\start-nginx-preview.cmd -Action Stop -ProjectName zhku-student-trial-local
```

等待测试终端返回，核查 results.json 的 sourceDrift=0、resourcesVerified=true、containerExited=true，以及 browser-host-exit.json 的 activeJobs=0/workerExited/backendListenerExited。只清理本次随机容器、网络和进程，不删卷，不重置共享库。lease PASS 仅证明宿主/资源结束；业务矩阵必须独立记录，不以租约 PASS 覆盖真实浏览器失败。

常见边界：缺离线依赖先报告缓存，不假装构建通过；资料/PDF 指纹不匹配立即停止；sourceSha/构建/实际页面 asset 摘要必须对应同一候选；404/409 为任务或依据失效，不应自动重发。503/网络结果未知保留手动查询入口；401 撤回身份和显示。同标签页缓存不可读时降级为本次内存流程，不声称可以刷新恢复。

注册是本应用本地 USER 身份，不是学校 SSO、学籍或邮箱认证。对外网络试用前还须明确邀请范围、HTTPS/安全 Cookie/反代识别、在线宿主配额与新供应商授权；当前回环临时验收不满足这些上线条件。账单仍 UNVERIFIED，12 元历史授权上限不能作为消费证明。
