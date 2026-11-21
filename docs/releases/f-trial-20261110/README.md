# 阶段 F：整合与试用记录

本轮只授权回环、隔离库、合成账号和零模型工程试用。没有公网发布，没有供应商调用或账单结算。源码修复提交 `6d9e0589`；CI 提交 `3a3264e6`。后续仅文档更新不改变已验收代码。PR #5 是最终 main 整合入口，远程合并 SHA 从 GitHub mergeCommit 查询。

## F1 远程整合

| PR | 实际目标 | 合并提交 |
| --- | --- | --- |
| #1 | main | a646313827a5793dfee0a2b12e4b04ee7b8205e6 |
| #2 | feat/zhkuagent-v3-frontend | 65526799bbf53428eb9784aff0bd9fbefe5e959f |
| #3 | feat/zhkuagent-v3-frontend | 0f7c73f2a443e4328a3f3f07480c6be1025f8c54 |
| #4 | release/g1-reviewed-20261110 | e887b7bfb9c2f93bfff99f938ce52eb4e2df17bd |

#1 的 25 提交先按原范围进入 main，再由 `a2c01f43` 合并 feat、`b4d600c6` 合并 G2，最后通过 [PR #5](https://github.com/Jayhavena/ZhkuAgent/pull/5) 进入 main。保留 #1 撤下的 11 个文件：逐项核对 withheld-manifest，重现数量 0。旧历史没有改写。

四 PR 没有 GitHub 人类 Review；本轮是工程 Agent 审查与用户授权整合，不冒充人类审批。独立审查由 OpenCode 请求模型 `opencode-go/deepseek-v4.1-flash` 完成；编排器 `executedModel=null`，无法证明供应商侧实际模型身份。Codex 独立复核生产代码差异、拒绝项/检索健康根因、回归测试、11 项账号 HTTP 测试及浏览器操作结果。

## 修复与已知缺陷

1. 已修：覆盖补全丢失 rejectedTasks；保留拒绝项，补全任务与拒绝项共用四项预算。新增测试先失败、修复后通过。
2. 已修：未启用向量路返回健康空结果，掩盖词法故障。未启用通过已有 RetrieverUnavailableException 协议报告不可用；健康空命中仍保持可用。尚未新增对外三态枚举，未启用和故障共享不可用状态。
3. 保留限制：适用性依赖中文原因匹配和 2025 固定版本。本轮未声称支持换版，在线前需结构化原因及版本参数。
4. **正式学生试用阻断**：默认持久宿主只提供同步 SEALED_REPLAY，未开启学生 Task API。合成账号注册 201、登录 200，学生提问却返回 403，页面显示“请求未获确认”。不能把此启动器与 student 页面配对称为完整可用版本。显式测试租约支持 Task API，但最长 900 秒，不能充当长期服务。
5. 新 checkout 与固定身份卷名称冲突：已增加 `InstanceName`，独享状态、容器、卷；增加只读 `AssetsRoot` 与 `HandbookPdf`，不跳过原件指纹或审核校验。初次启动缺合格原件时确实失败；提供已核对的本地资产根后启动成功。

## F2 可重复的本地环境

在固定代码提交的 Windows checkout 中运行，所有名称和端口须独占。先检查 `ZHKU_DB_PASSWORD` 的 Process→User→Machine 可读性；启动器为专用试用库生成独立随机口令。凭据状态位于忽略的 tmp，不提交。审核资料只读；物理位置仅存在本机执行记录，不随公开文档传播。

持久账号/同步回放宿主（用于账号验收，尚不支持完整学生 Task API）：

```powershell
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly
.\knowledge\scripts\start-persistent-rule-check.cmd -Action Check -InstanceName ftrial20261110 -IdentityPort 55450 -RulePort 55451 -BackendPort 58140
.\knowledge\scripts\start-persistent-rule-check.cmd -InstanceName ftrial20261110 -IdentityPort 55450 -RulePort 55451 -BackendPort 58140 -AssetsRoot '<已核对审核资料绝对根>' -HandbookPdf '<已核对原PDF绝对路径>'
.\knowledge\scripts\start-persistent-rule-check.cmd -Action Stop -InstanceName ftrial20261110 -IdentityPort 55450 -RulePort 55451 -BackendPort 58140
```

本轮确认注册成功、重启后同一账号可重新登录；身份库仍有 1 个合成账号。Stop 保留本轮专用身份卷，删除本轮规则临时容器，释放监听。没有删除、重置或写入既有共享库。`-SkipBuild` 仅用于已核验的新鲜 jar；正式复跑省略它。

完整学生任务的零模型工程环境使用显式测试租约，不能与持久宿主混称同一个产品：

```powershell
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Phase f-browser-new -Tests RuleAgentReviewedBrowserLeaseTest -Port 55534 -ReviewedBrowserLease -AssetsRoot '<已核对审核资料绝对根>' -EvidenceRoot docs/evidence/f-trial-local
# 等待该批次 browser-ready.json；读取其中 backendPort 后另开终端：
.\agent\frontend\start-nginx-preview.cmd -BackendPort <backendPort> -WebPort 58146 -ProjectName zhku-flease20261110 -EntryMode student
# 浏览器打开 http://127.0.0.1:58146/rules；只使用手册中的合成夹具。
# 完成后在租约目录创建 STOP，并停止专属 Nginx：
.\agent\frontend\start-nginx-preview.cmd -Action Stop -ProjectName zhku-flease20261110
```

每轮新建 Phase；检查 results.json 的 passed/sourceDrift/resourcesVerified/containerExited 与 browser-host-exit.json。租约期间不提交、不编辑源文件、不运行其他 Java 启停，不覆盖旧证据。缺原件或指纹失败即停，禁止下载替代资料或放宽守卫。

## F3 实际验证与失败记录

- G2 原候选：前端 201/201、学生构建成功；本轮同代码新 checkout 的 Nginx 构建也成功。
- 修复后离线定向 Java：130/130，0 fail/error/skip。包括拒绝项、禁用检索、证据先行、混合检索、发布门禁、供应商本地故障替身、真实回环任务 HTTP。没有真实供应商。
- 真实临时 PostgreSQL 账号安全：11/11（注册 3、登录安全 5、remember-me 重启 1、滑动登录 2）；sourceDrift=0、resourcesVerified=true、containerExited=true。19 个迁移到 v23。
- 首轮 f-browser-01：页面任务 202→200、模拟结果、刷新只 GET、撤销后 409 并撤回旧结论；宿主退出 activeJobs=0、worker/listener 退出。但总验收 **FAIL**：租约期间 HEAD 改变和其他宿主重启，sourceDrift=1、新 Java 进程存在。原始结果保留，不能把业务成功冒充整体验收通过。
- 第二轮 f-browser-02：固定 `3a3264e6`，总验收 **PASS**；sourceDrift=0、resourcesVerified=true、containerExited=true、newJavaRemaining=[]、activeJobs=0，worker/listener 均退出。浏览器登录→提问 202→快照 200→原文 200→刷新仅 GET 200→撤销资格→刷新 409、旧结论消失。两轮均显式 STOP；没有把首轮失败覆盖成成功。
- 远程 workflow_dispatch `42821606412` 在 `3a3264e6` 的 trial-contracts 与 replay 均 SUCCESS；包含 130 Java 定向测试及 201 前端测试/构建。后续文档提交需要对应远程检查状态，不以旧运行冒充新 HEAD 检查。
- 未配置数据库的全量 Maven：knowledge 1916 项，29 failures、299 errors、15 skipped；包括缺专用数据库凭据/URL、资料和证据目录等前提。不能报告全量通过，也未逐项证明所有失败都是环境问题。
- 初次定向执行因缺证据目录失败，补齐绝对证据目录后 130 项通过。初次并行构建出现 class 文件读取失败，后续串行重跑通过；不保留“并行编译通过”的错误结论。
- Preview 截图连续超时；备用原生浏览器不可用。通过同一 Preview 的 DOM 操作/读取及页面 Resource Timing 核验，未取得截图，不冒充独立人类使用者验收。

原始本机执行记录在忽略的 tmp 与未提交的 docs/evidence/f-trial-local；提交中只收纳无凭据的摘要及结果回执。真实审核条文参与证据绑定，用户输入/账号为合成，模型结论为显式模拟；这不符合“全部资料均合成”的严格口径。

## F4 在线准入：NO_GO

| 准入项 | 证据现状 / 关闭条件 |
| --- | --- |
| 在线宿主 | 只有本地回放与限时测试租约；需固定 SHA 长期 Task API 宿主及退出恢复验收 |
| HTTPS / Cookie | 本轮仅 HTTP 回环；需 HTTPS、Secure/HttpOnly/SameSite、代理转发信任与 CSRF 验收 |
| 邀请与范围 | 本地开放注册不是邀请制；需邀请码、人数上限、失效/撤销、管理员审计 |
| 供应商账单 | 实际人民币账单未核验；旧 12 元授权上限不等于消费证明；本轮新增调用 0 |
| 模型质量 | 旧 P02 2 题 8 调用无法证明当前候选整体可靠性；需冻结测试集与独立评分 |
| 隐私与数据 | 仅合成账号和既有受控原件；需真实学生告知、最小采集、留存/删除、访问审计与备份恢复要求 |
| 当前产品缺陷 | 默认持久宿主 Task API 缺失、适用性原因/版本耦合未解决 |

本地工程回放可复跑；默认“持久宿主+学生页”的正式试用 NO_GO。少量真实学生在线试用、生产发布均 NO_GO。没有开公网、没有修改全局编码/PATH、没有以预算上限推导实际账单。

## 下一阶段 A / B 验收契约

以下是待冻结的评测方案，不是已达成指标，也不自行授权真实模型费用。

| 指标 | 计数口径 |
| --- | --- |
| Task Success Rate | 冻结任务集中正确终态且满足所有子问题/拒绝披露/证据约束的任务数 ÷ 全部尝试；故障不得移出分母 |
| Tool Selection Accuracy | 与人工标注允许集合一致的工具决策数 ÷ 可评分工具决策数；同时记录无工具/多余调用 |
| Clarification Accuracy | 应澄清且正确澄清、无需澄清且未多问分别统计 precision/recall；未知条件不得猜测 |
| Evidence Grounding Rate | 可由本轮有效证据支持的实质性断言数 ÷ 全部实质性断言数 |
| Citation Validity | 可定位且原文支持、版本匹配、资格有效的引用数 ÷ 全部引用数 |
| Applicability Accuracy | 人群、时点、规则版本、课程类型等适用性结论正确数 ÷ 已标注适用性案例数 |
| Cost per Successful Task | 所有尝试（含失败/重试）的已结算总费用 ÷ 成功任务数；无成功或无账单时标 undefined/unverified |
| 幂等正确率 | 同一用户/幂等键重复与并发提交只产生一个业务执行的案例数 ÷ 全部幂等案例 |
| 非法迁移率 | 观察到的未被拒绝的非法状态迁移数 ÷ 非法迁移尝试数，目标 0；保留合法迁移对照 |
| 取消终止延迟 | 取消请求被接受到执行/资源实际退出的时差，报告 p50/p95/max，超时计失败 |
| 恢复成功率 | 注入网络/检索/进程故障后达到预期可恢复或安全拒绝终态的案例数 ÷ 注入案例数 |
| 队列等待时间 | 服务端入队到开始执行的时差，按并发/队列长度记录 p50/p95/max 与拒绝数 |
| 资源释放正确率 | 完成/失败/取消/关闭后连接、线程、任务槽、容器全部回到基线的案例数 ÷ 全部退出案例 |

每条记录绑定代码/数据/配置 SHA、输入类别、预期/实际状态、工具与证据身份、耗时、费用来源、评分者。开发集与盲测集分开；每项报告分子/分母与不可评分原因。按检索无结果/故障、混合子问题、版本/人群冲突、撤销、重复提交、排队取消、供应商超时、重启和资源泄漏分层。先获得零模型故障矩阵，再单独批准真实模型批次和费用，不能把本轮 0 调用当成模型质量或零成本结论。
