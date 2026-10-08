# Knowledge 模块

**当前3C-1、3C-2、3C-3已完成，12项门禁通过。** 本轮最后相关后端55类371项、前端46项及构建通过；完整网页在普通终端启动、不同PID重启与退出已复验。[本轮验收与限制](../docs/evidence/milestone-3c/2026-10-07/3c-3/acceptance.md)。迁移V21仅验证于专用临时库，原演示库未升级；本轮供应商调用0，不能把下文历史P1/P4当成3C真实模型或人工验收。

## 3C提交与资源边界

受控学分POST必须携带UUIDv4 `Idempotency-Key`，键按可信用户隔离。同键同规范化内容确认原Run；不同内容409，不新增任务。GET `/api/zhku/controlled-preview/v1/submissions/{submissionKey}`查询本人提交；只有原调用在同一事务创建并确认提交后可以派发，重复、查询及UNKNOWN确认均不补派发。旧受控Run查询/取消和本人历史仍保留；3C的提交外层响应区别于下文旧版本原九字段Snapshot说明。

用户在途2、实例执行2/队列16、排队10秒、总停止30秒；每任务最多3次模型外发、有效输出参数2048、零自动模型重试。停止期限不是物理退出保证或费用封顶；实际名额在清理/发布或连接退出后按各自资源规则释放。提交与归档保留7天，活动记录不因到期自动续跑；到期关闭记录两表同事务清理。前端刷新仅恢复提交提示，不恢复原问题/课程/答案；没有原快照只能查询。

非供应商验收入口：`.\knowledge\scripts\verify-campus-submission-temp-db.cmd -Batch 3C-3 -Port 55437`，自动新证据名，明确55类并拒绝供应商类/空测试。包含回退测试，需要先保留本轮隔离旧版沙箱构建；不是全新环境的一键无前置验收。生产打包探针只构造真实Factory，仍须继承已配置环境密钥，绝不调用模型。普通网页启动器默认模式会使用真实供应商；本轮只执行其`-SmokeWeb`启动/重启/关闭检查，不自动开启新的真实模型验收。

**当前受控 Vue 网页版本已获用户确认：P4 人工功能验收通过，功能开发收口。** [网页启动与演示](../docs/manual/controlled-campus-web-demo.md)使用专用临时库、真实模型和本机 Nginx；[P1真实模型12题证据](../docs/evidence/controlled-web-demo/2026-10-06/p1-closeout/acceptance.md)已通过。[人工确认与最终收尾](../docs/evidence/controlled-web-demo/2026-10-06/p4/final-closeout/manual-acceptance.md)记录用户报告及当前演示退出状态；下方4A/3B的模型替身及计数属于历史验收。自动测试与人工报告分别记载，不宣称生产部署、所有浏览器质量检查或真实学生效果。

当前 **4A＋3B 已验收**，见 [整合核验](../docs/evidence/milestone-4a/2026-10-05/A/20-4a-3b-integration-review.md)。Session 恢复仅限本人重修澄清控制状态：旧PID退出后旧Cookie失效，同一稳定用户重新登录可继续澄清，不恢复完整聊天记录或自动续跑Run。合成账号、真实回环HTTP、不同PID及归属隔离已验证，4A/3B该时点模型业务使用src/test替身；当前有限供应商证据见开头。原单人匿名入口独立保留，受控入口及3B会话开关默认关闭。非本机/生产多人权限、备份恢复和真实学生效果未验收。

[2026-10-04三批收口验收](../docs/evidence/single-session/2026-10-04/r22/README.md)及其中去重407项、[4A独立验收（V19时点）](../docs/evidence/milestone-4a/2026-10-05/A/18-4a-acceptance.md)与 [4A/V19测试报告](../docs/evidence/milestone-4a/2026-10-05/A/17-final-test-report.md)保留为历史。当前数字和新XML见 [3B最终测试报告](../docs/evidence/milestone-3b/2026-10-05/B/08-final-test-report.md)，相关覆盖去重591项，不是全仓覆盖。前序 [3A验收](../docs/evidence/milestone-3a/05-验收记录.md)不改写其“Runtime未改”和272项成绩。

knowledge 不依赖通用 `agent` 应用，但依赖公共库 `agent-runtime`。政策、证据、资料可见性、工具白名单、调用顺序和转人工规则仍由 knowledge 负责。

## Milestone 2：校园运行与共享契约

校园 HTTP 入口通过 `CampusCreditAgentManager.start` 创建共享 `AgentRunContext`，使用 `AgentRunTask.create` 绑定可取消任务。Service 保留自己的 `decide` 循环，在实际开始、步骤、工具提议、守卫拒绝、已观测结果和结束边界使用共享 Run / ToolCall / Event / Error；这不是仅在最终答案上转换类型，也不意味着两条 Agent 主循环已经合并。

政策回答链、学生可见证据、工具白名单、先政策后计算、课程输入一致性、来源完整性和转人工判断仍在 knowledge。共享 Runtime 不解释校园字段；校园工具 JSON 只是其不透明载荷。共享工具成功不代表政策证据合格或整次任务成功。`POLICY_ROUTE` 保留为领域步骤，不新增共享事件类型。

正常 Manager 路径的顺序为：业务终态 → 清理收敛 → termination Future 完成 → Service 构造 Result → 可选的有界最终归档尝试（3A）→ Manager 一次发布完整 completion → `publishTermination()` 设置共享发布标记并提交终态事件 → 可选的异步观察器交付。运行状态可以先成为终态，不能据此宣布完整结果已发布。Future 回调处可能尚无最终结果，最终事件回调处结果已完整可读，但迟到查询仍可能因TTL/容量回收返回不存在。

三处同名字段须按各自边界理解：

| 表达 | `terminated=true` 的依据 |
| --- | --- |
| Service `Result` | 业务与清理已收敛，termination Future 已完成；该重载返回后由宿主发布结果 |
| Manager 原 HTTP `Snapshot` | termination Future 完成且完整 completion 已发布；九字段 HTTP 结构保留 |
| 共享 `AgentRunSnapshot` | 宿主已经调用 `publishTermination()` 设置发布标记 |

Manager 的完整结果先于最终事件回调可读；HTTP 的 true 可以早于最终事件，共享 true 也可以早于观察器返回。`RUN_TERMINATED` 是已有最终运行事件，不新增独立“业务完成”事件，不承诺消费确认或线程池线程销毁。直接 `service.run(Request)` 自行发布，传入 Context 的重载由宿主负责最后发布。

当前预览的四参数 Service 构造使用NONE观察模式，不启动事件交付owner；可选观察器使用R1.3有界异步交付，与停止控制解耦。不保留持久事件，也不提供查询/回放API。观察积压/溢出会丢诊断，不改变执行结果；永久阻塞观察器可以占住有限交付资源，detach或关闭返回不等于回调线程物理退出。具体容量及固定门槛见 [R1.3验收](../docs/evidence/single-session/2026-10-04/r13/README.md)。固定安全 Error 不等于工具载荷整体脱敏，不能把完整载荷自动写入日志。

Milestone 2 只补一个组合测试文件和说明，使用可控模型、政策入口与时间触发；Controller、Manager、Service、Runtime、Registry 和成功算术工具使用真实项目实现。该阶段专用临时库验收验证真实资料发布/可见性，仍使用模型替身。未验收真实供应商、多用户、重启恢复或事件持久化。历史命令、结果与文件哈希见 [Milestone 2 验收记录](../docs/evidence/milestone-2/05-验收记录.md)，不能作为 2.5 本轮重跑结果。

Milestone 1B 已完成共享契约，证据见 [1B 验收记录](../docs/evidence/milestone-1b/05-验收记录.md)。它没有实现身份权限，也没有合并两条主循环。

## Milestone 1A 本机可靠性边界

校园 Agent 总共保留最多 128 条运行记录，包括排队、运行和停止中任务。只有清理已收敛且结果完整发布的记录可以回收：完成后 10 分钟按请求惰性清理，查询不续期；满额时优先淘汰最早完成的记录，不保证至少保留 10 分钟。同一完成时间用发布序号稳定排序。全部名额仍被未完成任务占用时拒绝新请求。被回收编号的查询和取消沿用 `404 RUN_NOT_FOUND`。

已核验答案仍为 `ANSWERED/SUCCEEDED`；缺输入与证据不足为 `NEED_CLARIFICATION` 或 `HANDOFF`，运行状态为 `INCOMPLETE`。执行异常为 `FAILED`；取消及运行超时为 `CANCELLED`，由 `stopReason` 区分。`terminated=true` 表示业务、清理收敛并且结果已发布，不表示线程池线程已销毁，也不保证清理一定成功。

成功任务的 POST 202、GET 200 和 JSON 字段不变。已接收任务即使很快执行失败也返回 POST 202；容量或调度拒绝为 503。显式开启 3A 归档后，归档准入未确认、未就绪、失锁、繁忙或容量不足也返回固定错误 503，并且不投递 Agent。新增 `INCOMPLETE` 会影响严格枚举客户端。

当前首次返回、轮询和取消读取均重新校验原政策来源，与历史共用许可判断。来源撤回、不可见、版本/范围改变或许可不可用时隐藏answer/source/steps，固定提示人工核实，仍保留原state/status/failureCategory；不能把SUCCEEDED读成当前正文获授权。Manager缓存/归档执行事实不改；判断时点是对外组装响应，不能追回已发字节。详见 [R1.5](../docs/evidence/single-session/2026-10-04/r15/README.md)。

校园同步模型通过Factory.open返回资源句柄，Spring/CLI/测试各自拥有唯一关闭者，不按Run创建；model()借出有准入保护的ChatModel，最多2在途，profile连接/完整读取时限、初始1请求/零自动重试。自有body池2/队列16、I/O池4/队列64；close有界排空，未物理收敛不归还owner名额。JDK内部共享fallback不在自有池硬上限承诺内，任意永久阻塞不保证线程内硬终止；流式/RAG路径不属于本轮验收。详见 [R1.4](../docs/evidence/single-session/2026-10-04/r14-completion/README.md)。

原本机预览的有效 `server.address` 必须为回环地址；空值、主机名和任意网卡地址会阻止启动。原匿名入口只供单人，回环绑定本身不提供身份或归属隔离。下文4A为独立身份入口，不开放治理或SSE；3B仅显式开启本人重修澄清会话。不得通过转发、隧道或反向代理扩大为非本机服务。生产部署和真实用户试用仍需独立验收。Milestone 1B共享契约本身不提供权限。

当前快速测试使用模型与工具替身，不能代替真实供应商或临时数据库验收。完整证据见 [Milestone 1A 验收记录](../docs/evidence/2026-10-02-milestone-1a-验收记录.md)。

既有功能包含资料导入、人工审核、事务发布、关键词问答、管理页面与问答页面；问答支持答案卡和两轮澄清追问。2.5 没有新增校园业务。

## 构建与启动

在仓库根目录执行：

```powershell
$mavenRepo = Join-Path $env:USERPROFILE '.m2/repository'
.\mvnw.cmd "-Dmaven.repo.local=$mavenRepo" -pl knowledge -am -DskipTests package
.\knowledge\scripts\start-local.ps1 -Mode preview -DataRoot D:/alldesk/Projects/SuperAgent-zhkuagent-mvp
.\knowledge\scripts\start-local.ps1 -Mode admin -DataRoot D:/alldesk/Projects/SuperAgent-zhkuagent-mvp
```

分别在两个终端启动。默认端口 8123、8124；原服务仍占用端口时不要直接运行，另用 -Port 指定端口。页面内跨入口链接保持原来的 8123/8124，临时端口验收时分别直接打开两个地址。

普通预览页面为 `http://127.0.0.1:8123/api/zhku/local-preview`。以上脚本默认不启用 RAG / 校园 Agent；需要已有合法数据库配置和 DataRoot。这些启动示例本轮只核对脚本和配置，没有启动监听服务。不要用它们宣称校园 Agent 已实现离线浏览器演示；详细条件见 [演示指南](../docs/evidence/milestone-2.5/02-本地预览与演示指南.md)。

DataRoot 显式指向既存数据目录：上传目录固定为其 data/local-admin；只有环境中没有数据库密码时才读取其中 infra/.env。原 PDF 在 data/raw，不复制到新项目。数据库连接仍按既有 zhku.db.* / ZHKU_DB_* 规则解析。

启动脚本每次先检查当前进程、Windows 用户级和系统级的 `ZHKU_DB_PASSWORD`；启用 RAG 时同样检查 `DASHSCOPE_API_KEY`，并只把可用值注入所启动的 Java 进程。已有进程值优先，不能被 DataRoot 的 `infra/.env` 覆盖。数据库密码在三个环境级别都没有时才回退到该文件，以兼容原本地数据目录。显式启用 RAG / 校园 Agent 时，`DASHSCOPE_API_KEY` 必须能从环境变量读取。脚本结束后恢复调用方原有进程环境，不打印凭证值。

knowledge/infra 保留原 Compose 项目名、容器和数据卷声明；2.5 不执行 Compose up/down，不重建既有容器，不改变已有初始化脚本挂载。不要使用 down -v。新的代码目录不能被误当作新的数据环境。

## 验证

优先运行已有 1A/1B/2 相关回归（不运行真实模型，不使用数据库）：

```powershell
.\docs\evidence\milestone-2\verify.ps1
```

该入口默认包含 `CampusRuntimeIntegrationTest`；不使用 `-AfterRollback`。本轮实际结果见 [2.5 范围、基线与验证](../docs/evidence/milestone-2.5/04-范围基线与验证.md)。其选择不是全仓库全部测试。下面的旧知识库验证流程有独立初始化前提，不能与校园 Golden Task 混为一谈。

测试中有真实数据库写入，只能使用专用测试库。先在空临时库执行 KnowledgeSchemaTest 建表，再通过 ReviewedKnowledgeImportCli 导入 knowledge/data/curation 中已有审核 JSON，导入后在临时库给该测试来源设置与 V4 相同的 document_family=STUDENT_HANDBOOK、knowledge_scope=PUBLIC_ELECTIVE，再执行 verify-zhku-local.ps1。这是旧基线的初始化前提，不能对原演示库重复执行。脚本要求显式 DatabaseUrl、DataRoot 和当前环境中的测试库密码，并拒绝原演示库端口 54329。

DataRoot 用作测试工作目录，以便读取既存 PDF 和整理资料。命令行属性 zhku.data-root 默认是模块目录，可在没有真实 PDF 时运行基础测试；完整验收使用原资料目录。

## 校园 Agent 纵切与索引（本机预览）

独立临时库自动化验收入口为 `knowledge/scripts/verify-campus-temp-db.ps1`。2.5 记录的随机口令进入 Docker 参数问题已由 2.6 修复：仅按环境变量名继承口令，并恢复原 POSTGRES_PASSWORD。容器环境仍可被有权限者读取，这不是加密或完整安全审计。2.6 在 2026-10-03 重新通过 11 项真实临时库验收及 188 项相关回归，模型为替身；[新验收记录](../docs/evidence/milestone-2.6/05-验收记录.md)与 2.5 未重跑的历史记录分开。

有 Docker 服务及原 pgvector 镜像时，在仓库根目录执行：

```powershell
# 每次数据库操作重新检查已有凭证；临时库脚本另生成隔离口令，不用原演示库口令建库。
& .\scripts\Import-ZhkuCredentials.ps1 -UseDatabase
.\knowledge\scripts\verify-campus-temp-db.ps1 -Port 55432
```

脚本生成随机数据库密码，启动无持久卷的专用容器，运行迁移、审核发布、索引/候选、词法降级评测及校园 Agent HTTP 闭环测试，结束后停止并删除容器。模型工具选择使用可控替身；它不验证真实供应商，也不调用原演示库。端口冲突时可指定 `-Port`，但不可使用原演示库的 54329 端口。

脚本存在默认关闭的 `-RunLiveProvider` 选项，会追加 `CampusCreditAgentLiveProviderTest` 并调用供应商。2.5 禁止运行该选项。既有独立供应商冒烟不证明校园 Agent 真实模型端到端通过。

在根目录构建 knowledge 及共享依赖（不选择 agent 应用）：

```powershell
$mavenRepo = Join-Path $env:USERPROFILE '.m2/repository'
.\mvnw.cmd "-Dmaven.repo.local=$mavenRepo" -pl knowledge -am -DskipTests package
```

`index` 可执行 jar 只在显式 `--live` 下写索引，必须把 `ZHKU_DB_URL` 明确设为专用目标库，并从环境变量提供 `ZHKU_DB_PASSWORD` 与模型 profile 所需密钥。本仓库默认 profile 是 `dashscope`；测试时先在**专用临时测试库**建表、按审核流程发布资料，再执行：

```powershell
$env:ZHKU_DB_URL = 'jdbc:postgresql://127.0.0.1:<临时库端口>/<临时库名>'
$env:ZHKU_DB_USER = '<临时库用户>'
# ZHKU_DB_PASSWORD 与 DASHSCOPE_API_KEY 由当前进程环境安全提供，不写入命令或仓库。
& .\scripts\Import-ZhkuCredentials.ps1 -RequireDatabase -RequireDashScope
java -jar .\knowledge\target\knowledge-0.1.0-rc.1-index.jar --live --scope=PUBLIC_ELECTIVE --chunking-version=fact-aware-v1
```

CLI 根据预览 YAML 的模型 profile 登记 `BUILDING` 索引 profile；每次运行记录 `runId`、合格候选数、新增条目数。相同 profile/切分版本/来源哈希重跑时新增为零；内容变化须新切分版本。**发布不会自动建索引**：审核发布只更新资料表，CLI 索引读取已发布、学生可见、分享权确认且范围匹配的片段。规则级学分上限使用独立的 `PUBLIC_ELECTIVE_CREDIT_LIMIT` 已发布证据，优先于 RAG；通用 RAG 的示例范围 `PUBLIC_ELECTIVE` 与 YAML 一致。当前检索器仍按配置 profile 检索，尚未强制检查其 `ACTIVE` 状态；不能把 `BUILDING` 误称为已完成上线评测。

默认预览仍关闭模型。`start-local.ps1` 的 `-EnableCampusAgent` 要求同时提供 `-EnableRag`，会通过 `CampusAgentPreviewConfiguration` 装配真实供应商 ChatModel，并要求环境中的 API Key。**这不是测试替身开关，2.5 不执行。**前面的索引 `--live` 示例也属于后续真实模型验证，不是当前演示步骤。

临时库验收若不用既存 DataRoot，可在已提供临时库密码的会话中直接启动 preview jar，并将 `--zhku.db.url` 指向与索引 CLI 相同的临时库。`-EnableRag` / `-EnableCampusAgent` 对应的 Java 参数分别是 `--zhku.rag.enabled=true` / `--zhku.campus-agent.enabled=true`；不要把密码写在 Java 参数里。

新 JSON 合同与旧 `/conversations` JSON/SSE 路径独立。Controller 映射为 `/zhku/local-preview/campus-agent/v1/runs`；实际服务器还有配置中的 `/api` 前缀。创建运行：`POST /api/zhku/local-preview/campus-agent/v1/runs`，请求体示例：

```json
{"question":"这两门虚构公选课学分合计及每学期上限？","courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},{"courseId":"HIS02","category":"公共选修课","credits":2}]}
```

返回 `runId` 后可 `GET /api/zhku/local-preview/campus-agent/v1/runs/{runId}` 查看原九字段快照；`POST /api/zhku/local-preview/campus-agent/v1/runs/{runId}/cancel` 请求取消。HTTP 字段为 `runId`、`state`、`stopReason`、`terminated`、`status`、`answer`、`failureCategory`、`steps`、`source`，不是共享事件历史查询接口。完整请求、有限轮询和取消示例见 [演示指南](../docs/evidence/milestone-2.5/02-本地预览与演示指南.md)。默认不开启的校园端点不能直接作为本阶段浏览器演示入口。

`STOP_REQUESTED` 只表示停止请求已收到；Manager HTTP 的 `terminated=true` 表示终止 Future 已完成且完整结果已发布。停止采用协作式中断，不保证供应商硬停止。若省略 `courses`，模型可以选择追问；非法工具参数或资料不可核验时转人工。

正常受控 Golden Task 使用两门虚构公选课 1.5 + 2，已发布、学生可见证据给出 4 学分上限：预期 `SUCCEEDED/ANSWERED`、合计 3.5 及原来源。将来源撤回为 `INTERNAL_ONLY` 后，预期 `INCOMPLETE/HANDOFF/INSUFFICIENT_EVIDENCE`，不再给出计算结论或出处；证据不足组合场景同样转人工。可见性修改只存在于专用临时库测试中，不修改原演示库。

默认校园运行仍在内存中保存，最多 128 条，重启后在线查询记录消失。3A 显式开启时另存 Run 准入与最终事实；重启不恢复在线任务，只将遗留准入标为中断，使用独立历史接口查询。默认预览会话仍在内存中；已有 `JdbcConversationStore` 类不表示预览已持久化 Session。

## Milestone 3A：可选 Run 归档与历史

`zhku.campus-agent.archive.enabled` 缺省为 false，不改变既有启动脚本或配置。开启要求 `zhku-local-preview`、校园 Agent 开关、明确的 `zhku.db.url` 和回环 `server.address`；归档拒绝 URL 中的凭据及所有查询参数。凭据仍从环境读取。新目标须预先完成迁移至 V18，归档配置不自动迁移，也不允许拿原演示库做写入验收。

新增只读路径：`GET /api/zhku/local-preview/campus-agent/v1/history/{runId}`、`GET /api/zhku/local-preview/campus-agent/v1/history?limit=20`。后续页传返回的 `nextCursor`；limit 为 1–100。列表只有元数据，详情 `execution` 与允许展示的 `result` 分开。历史没有取消/续跑/删除端点，也没有 `terminated`；旧 runs 查询/取消在缓存回收后仍返回 404。

归档状态：`ADMITTED` 已确认准入；`FINALIZED` 最终执行事实已原子归档，不代表执行一定成功；`INTERRUPTED` 旧 owner 的执行结果未确认，execution 为空，不伪造 CANCELLED。归档失败或回执未知不会把真实在线结果改成失败；会关闭新增准入，待运维修复后重启对账，禁止自动重放。

默认保留 7 天（可配 1–30）、最多 10,000 条（可配 1–10,000）。启动/准入只清理一批最多 200 条到期关闭记录，未到期不因压力淘汰；仍满则拒绝。只保存白名单答案与引用，最多 16 KiB UTF-8；题目、课程输入、Prompt、工具明细和异常原文不归档。来源撤回、内部不可见、版本改变或缺失时隐藏 answer/source，保留原执行成功事实；引用仅为 `REFERENCE_ONLY`，不承诺不可变原文。

关闭顺序是关闭准入、有界排空（最多 5 秒）、封写、释放资源；不主动停止宿主任务，不借用用户取消/运行超时原因。实际 JVM 的整体退出不保证 5 秒。启用该机制的参与实例互斥，不约束关闭归档的其他进程。

受控重启演示与运维步骤见 [3A 实施记录](../docs/evidence/milestone-3a/02-实施记录.md)，实际结果见 [测试报告](../docs/evidence/milestone-3a/04-测试报告.md)。独立审查的容量清理缺陷已修正；本轮修正后 29 无库、44 新真库、188 原回归、11 原 Golden 共 272 项通过，7 个实际 JVM 用例包含在新真库中。测试启动器只在 src/test，不提供生产假模型启动方式。本阶段不执行 `-RunLiveProvider`。

后续五项能力的依赖、候选文件、数据范围、真实 HTTP 与非本机门禁见 [后端开发路线草案](../docs/superpowers/plans/2026-10-03-post-3a-backend-roadmap.md)，该文档不代表身份、Session 持久化、自动续跑或真实模型已完成。

构建输出：普通 knowledge jar 供既有 CLI 使用；带 preview/admin/index/controlled 后缀的可执行 jar 分别启动原问答、管理、索引和4A受控入口。

## Milestone 4A：本机受控身份与Run归属

最小可重复演示直接运行已有验收脚本。先启动Docker，使用JDK21、Maven和已有用户缓存；所有其他会话保存并暂停源码写入，禁止并行Maven：

```powershell
$mavenRepo = Join-Path $env:USERPROFILE '.m2/repository'
.\knowledge\scripts\verify-campus-identity-temp-db.ps1 -Port 55435 -MavenRepo $mavenRepo -ExpectedMigration 20
```

脚本使用无卷专用临时库 `zhkuagent_run_archive_e2e`，随机环境口令、V20、合成账号/资料及test-only模型；不访问54329演示库，不调用真实模型。当前源码最高迁移为V20，上例必须显式指定 `-ExpectedMigration 20`。默认选择真实HTTP、实际PID、旧入口兼容及受控配置四类，接线后新执行28项通过，见 [整合核验](../docs/evidence/milestone-4a/2026-10-05/A/20-4a-3b-integration-review.md)。原 [4A独立验收](../docs/evidence/milestone-4a/2026-10-05/A/18-4a-acceptance.md)使用V19，适用于3B激活V20前的源码时点，原记录及哈希保留。退出后检查临时容器不存在；全部HTTP端口/线程/专用连接及子PID退出由实际测试断言，Probe不单独计数。

受控生产入口是 `ControlledPreviewApplication` / `knowledge-0.1.0-rc.1-controlled.jar`，只显式扫描security/controlled并导入校园构造链。要求独立profile `zhku-controlled-preview`、`zhku.controlled-preview.enabled=true`、`zhku.campus-agent.enabled=true`、`zhku.campus-agent.archive.enabled=true`，以及显式 `zhku.db.url` / `zhku.db.username`。数据库只能使用127.0.0.1专用目标、端口不可为54329，不能把口令放URL或配置覆盖；子进程口令仅来自 `ZHKU_DB_PASSWORD`。同时禁止旧preview/admin、RAG、streaming。业务conversations默认关闭，3B显式开启条件见下节。缺条件在连接数据库/占宿主锁前失败。

生产模型复用已有 `CourseCreditChatModelFactory.open`；需明确完整provider profile元数据及环境密钥，启动核验不证明供应商行为。本轮没有实际供应商调用；不能把上述测试启动器改称离线生产启动开关。

V19不会自动创建账号。`PreviewIdentityBootstrapCli`需已迁移的专用目标，读取环境 `ZHKU_DB_URL`、`ZHKU_DB_USERNAME`、`ZHKU_DB_PASSWORD`和三个 `ZHKU_PREVIEW_USER_A_PASSWORD` / `ZHKU_PREVIEW_USER_B_PASSWORD` / `ZHKU_PREVIEW_MAINTAINER_PASSWORD`；无命令参数。口令16–64个ASCII字符，仅存BCrypt。一次事务创建 `user-a`、`user-b`、`maintainer`；冲突不重置、不盲目重试未知commit，不记录口令/hash。账号UUID在重启后稳定，登录HttpSession不持久化，业务Conversation Session属于3B。

实际URL前缀 `/api/zhku/controlled-preview/v1`。所有请求各自使用独立Cookie容器；先GET CSRF，随后POST登录表单 `username/password`并提交返回的CSRF参数/头，登录后重新GET CSRF。Cookie不写入仓库/报告。

| 相对路径 | 方法与行为 |
| --- | --- |
| `/auth/csrf` | GET200，匿名可用，no-store |
| `/auth/login` | POST表单+CSRF，200/401/429/503，无302 |
| `/auth/me` | GET有效主体；匿名401 |
| `/auth/logout` | POST有效登录+CSRF，204，旧Cookie失效 |
| `/runs` | USER POST202；身份从服务端Principal传入，body不能指定userId/role/authRevision/sessionId |
| `/runs/{runId}`、`/runs/{runId}/cancel` | GET200或POST200+CSRF；他人/不存在/无主资源统一404 |
| `/history?limit=…&cursor=…`、`/history/{runId}` | USER GET；先SQL归属过滤后分页，当前资料许可仍必须满足 |

成功请求body示例仍为原校园Request，不新增身份字段：

```json
{"question":"公共选修课每学期最多修多少学分？","courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},{"courseId":"HIS02","category":"公共选修课","credits":2}]}
```

此例只在合成证据已发布且可见时得到3.5/4学分结论。新接口返回 `{run, resultVisibility}`：九字段Run结构保留，VISIBLE需要本人已确认归档及当前来源许可；RUNNING/NOT_APPLICABLE/HIDDEN/UNCONFIRMED分别表达正在运行、无正文、许可隐藏或未确认完整存储。步骤自由detail不对外提供。来源撤回或数据库不可用不从Manager缓存正文回退；执行SUCCEEDED不等于正文可展示。若准入已确认而投影失败，固定503可能含accepted/runId，应查询本人确认事实，不能据网络错误自动重复提交。

MAINTAINER可登录查看自身身份，但没有USER任务权限，也没有治理功能。受控模式中旧JSON/SSE、治理、反馈、静态页、默认认证路径全部拒绝；不安全请求可能先CSRF403，匿名受保护GET为401。被拒绝的旧路径POST可能创建匿名CSRF会话并覆盖客户端同名Cookie，需要重新登录；这不开放资源权限，也不承诺登录Cookie不受此类误操作影响。

原4A/V19不同PID验收证明稳定账号、旧Cookie失效、本人历史仍可读及未确认遗留Run为INTERRUPTED，当时未覆盖业务Session恢复，也不自动重放Run；该历史范围见 [4A独立验收](../docs/evidence/milestone-4a/2026-10-05/A/18-4a-acceptance.md)。当前3B已验收重修澄清控制状态恢复，见下节。当前回退关闭受控入口，保留V20及私有行；不能改owner为NULL或让旧无NULL过滤二进制读取受控库。

## 3B业务会话（2026-10-05后续验收）

3B现已完成V20/匿名NULL隔离、owned JDBC、重修澄清接线、实际HTTP与跨PID恢复。该事实补充上文4A历史时点；登录HttpSession仍只在内存，业务Session只存七列澄清控制状态及owner，不存消息、Prompt或完整历史。

默认开关`zhku.controlled-preview.conversations.enabled=false`；明确true时要求原受控profile、同一显式回环专用数据库和V20。原校园政策内部MemorySession保持原用途。会话请求不提供身份/owner/TTL字段，Principal仅从框架认证取；USER本人可访问，维护者无继承权限。

前缀 `/api/zhku/controlled-preview/v1/conversations`，所有响应JSON且no-store：

| 请求 | 输入与结果 |
| --- | --- |
| POST前缀 | JSON `{}`+CSRF；201返回原六字段ConversationState，服务端生成一个UUID |
| GET `/{sessionId}` | 200六字段状态；读不续TTL，跨PID重登录后可恢复pending |
| POST `/{sessionId}/messages` | JSON仅message、expectedRevision+CSRF；200沿用ConversationReply |
| POST `/{sessionId}/cancel` | JSON仅expectedRevision+CSRF；一次CAS，已取消幂等200，不续TTL |

例如新建返回revision0后发送`{"message":"选修课挂科了可以重修吗？","expectedRevision":0}`，GET取得pending/revision1，随后发送`{"message":"专业选修课","expectedRevision":1}`。只有已发布且当前获准的原重修依据才返回结论；成功清pending为revision2。读取/失败/重启/取消不续期，真实pending写入或清除成功才按服务器时间滑动10分钟。他人/无主/不存在404，到期410，消息遇取消或revision竞争409，DB/确认未知503不降级或盲目重试，中途许可撤销401并清当前登录。

没有列表、SSE、Run关联、notice/adjustment/credit-limit/RAG/guide/telemetry能力或真实模型调用。本轮原Application实际HTTP/PID依赖test-only合成校园模型，会话调用0；不宣称生产供应商main联网或生产多用户。接线后4A、原相关/Golden/归档及构建新跑通过，相关去重591项；[3B验收](../docs/evidence/milestone-3b/2026-10-05/B/07-3b-acceptance.md)、[测试/XML/PID证据](../docs/evidence/milestone-3b/2026-10-05/B/08-final-test-report.md)。回退保留V20和私有owner，只关开关。
