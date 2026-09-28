# 校园 Agent 最小闭环验收记录（2026-09-28）

本报告对应 `docs/decisions/2026-09-28-campus-agent-vertical-slice.md`。首个场景沿用《本科生学生手册（2025版）》审核片段，策展文件 SHA-256 为 `B3A5A78162D895643098E559267A8141723BD2C7075B85356BC4DBE9A59036A7`。文件声明审核通过、学生可见、分享权确认，定位为 **PDF 第 76—77 页，第 8、10 条**，规则为“每学期公共选修课最高不得超过 4 学分”。这只能证明策展文件内容与合同测试；本轮没有从 PostgreSQL 实际读取它。

## 分阶段结果

| 阶段 | 已实施 | 验收方法与实际结果 |
| --- | --- | --- |
| 1 基线/架构 | 查明 `agent` Spring AI 1.0.0 与 `knowledge` 1.0.9、两个独立启动入口、RAG 扫描断点；抽取 `agent-runtime` 的上下文/任务/状态类型，保留学生预览对高权限工具的 classpath 隔离 | 四模块编译与聚焦运行时测试通过；设计记录列出资料链、通用 Agent 链和断点。未引入 `knowledge -> agent` 整模块依赖 |
| 2 知识/索引 | 预览显式导入条件式 RAG 配置；索引 CLI 可登记 profile、按资格过滤建索引、同哈希重跑不重复写入；失败运行清理本轮条目并记 `FAILED` | 关闭/开启 RAG 的容器装配测试通过；索引参数与重复计数测试通过。**真实库索引、同一发布批次的查询向量＋词法候选＋RRF＋引用贯通未执行**，缺专用 PostgreSQL 和模型密钥。现有检索器还未强制要求 profile 为 `ACTIVE` |
| 3 纵切 | 新 v1 校园入口由模型选择政策查询和 `summarize_course_credits`；政策查询经既有五级回答链裁决通知/高风险规则，计算必须等合格证据返回，服务端核对课程参数并组合结论/出处 | 可控模型＋真实纯计算工具＋HTTP MockMvc 测试通过。政策由受控门面夹具提供，未验证真实 JDBC 证据读取 |
| 4 会话/传输 | 每次运行用共享 `AgentRunContext`、有界线程池、30 秒停止请求、查询与取消端点；旧 `/conversations` JSON/SSE 未改 | 并发隔离、用户取消、超时后释放、执行器拒绝，以及旧 SSE 发送失败和旧控制器回归通过。停止请求后阻塞的模型若不退出，`terminated` 仍为 false，避免提前宣称清理完成；真实供应商的最终超时释放未验证。预览会话和运行快照仍为内存态 |
| 5 评测/交付 | 本文件、README 启动/索引/HTTP 步骤与变更范围；保留历史 `five-level-routing-v1` 报告 | 聚焦 `verify`：`agent-runtime` 6、`agent` 16、`knowledge` 79，合计 **101 运行 / 0 失败 / 0 错误 / 0 跳过**。这是选定的无数据库回归，不是全量或真库 Agent 效果 |

## `campus-agent-v1` 可控验收逐题

语料指纹：`[TEST-FIXTURE] 上述策展 JSON SHA-256 + CampusCreditAgentServiceTest/ControllerTest 的受控政策门面与模型输出`。它不是共享演示库的语料指纹。测试用课程 `ART01=1.5`、`HIS02=2` 学分，均为虚构公共选修课。

| 用例 | 预期 | 实际 | 结论/未覆盖处 |
| --- | --- | --- | --- |
| 正常检索＋计算＋引用 | 模型选政策查询和纯计算，回答 3.5、4 学分上限及来源，不承诺个人资格 | HTTP v1 返回 `SUCCEEDED`、`terminated=true`、三步记录、合计与 PDF 第 76—77 页第 8、10 条 | 可控门面通过；真实 JDBC 未跑 |
| 缺课程输入 | 先追问编号和学分，不把 0 当作合计 | `NEED_CLARIFICATION`，无合计，政策查询次数为 0 | 通过 |
| 无合格资料 | 模型选查询后不执行计算，不给上限结论，转人工 | `HANDOFF/INSUFFICIENT_EVIDENCE` | 通过，门面夹具模拟无资料 |
| 过期或不可见资料 | 资格过滤拒绝，不进入答案 | `RetrievalEligibilityFilterTest` 的 11 项过滤合同通过 | **未做该 HTTP 入口＋真库端到端**；历史审核查询的 SQL 还须在临时库复核 |
| 非法工具参数/伪造学分 | 不计算、不推断 | 负学分、与请求不一致的学分分别得到 `INVALID_TOOL_ARGUMENT` | 通过 |
| 计算早于政策查询 | 不执行计算 | `POLICY_REQUIRED`，无计算步骤 | 通过 |
| 未授权/多工具调用 | 不执行任一越界工具 | `UNKNOWN_TOOL` 或 `TOOL_CALL_LIMIT`，无计算步骤 | 通过 |
| 模型或政策查询失败 | 不泄漏异常内容、不输出 4 学分个人结论 | `MODEL_FAILURE` / `RETRIEVAL_FAILURE`，固定转人工文案 | 通过 |
| 用户取消、超时、执行器拒绝 | 停止请求不冒充已终止；工作线程清理后才有终态 | 取消/超时先 `STOP_REQUESTED, terminated=false`，释放后 `CANCELLED`；拒绝为 `FAILED/EXECUTOR_REJECTED` | 可控阻塞测试通过；永久不响应中断的真实供应商仍可能停留在停止请求态 |
| 并发运行 | 两次请求不共享运行状态 | 不同 `runId`，各自追问并终止 | 通过 |

可控问答记录（`CampusAgentPreviewControllerTest`）：问题“这两门虚构公选课学分合计及每学期上限？”；输入 `ART01 1.5`、`HIS02 2`；步骤为 `POLICY_ROUTE → lookup_public_elective_credit_limit → summarize_course_credits`；回答包含“合计 3.5 学分”“每学期公共选修课最高不得超过 4 学分”及《本科生学生手册（2025版）》`PDF 第 76—77 页，第 8、10 条`，并要求个人方案和当期通知人工核实。来源字段由服务端政策结果提供，不由模型编造。

## 环境与未执行验收

- 本机 `127.0.0.1:54329` 连接失败，Docker daemon 不存在；进程环境无 `ZHKU_DB_PASSWORD`、`DASHSCOPE_API_KEY`。因此没有新建或写入任何数据库，也没有真实 embedding / ChatModel 调用。
- 未运行 `JdbcRetrievalIndexingTest`、`JdbcCandidateRetrieverTest`、`JdbcCreditLimitEvidenceFinderIntegrationTest`、`RetrievalAcceptanceEvalTest` 等写库/真库测试；需要专用临时 PostgreSQL（含 pgvector、Flyway 迁移）、测试凭据及审核资料。索引 CLI 打包已通过；无 `ZHKU_DB_URL` 的拒绝式启动冒烟已执行，不代表索引成功。
- 未重跑 `five-level-routing-v1`：2026-09-27 合流复验仍是 **2/5**，该数据集和历史报告未修改。本轮不调整其预期；当期通知测试装配与旧预期问题仍在。
- 真实供应商最小冒烟还需与 profile 端点匹配的密钥、明确的临时库及完成索引的同一批发布资料。真实模型选择工具的稳定性、检索质量和永久阻塞时的资源释放仍未验证。

## 复现与变更范围

构建与无数据库回归命令（在根目录运行；本机 Maven 使用现有缓存 `'-Dmaven.repo.local=C:/Users/nangua/.m2/repository'`）：

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/nangua/.m2/repository' -pl agent,knowledge -am '-Dtest=AgentRunContextTest,BaseAgent*Test,AgentExecutorConfigTest,CampusCreditAgentServiceTest,CampusCreditAgentManagerTest,CampusAgentPreviewControllerTest,ZhkuLocalPreviewApplicationTest,ZhkuLocalPreviewConversationControllerTest,LocalPreviewAnswerChainTest,RagProviderConfigurationTest,IndexPublishedKnowledgeCliTest,RetrievalIndexingRepeatTest,RetrievalEligibilityFilterTest,HybridRetrievalServiceTest,HybridCandidateFusionTest,CourseCreditSummaryToolTest,CampusToolRegistryTest,ConversationStreamProcessorTest,CreditLimitEvidenceCurationTest,PublicElectiveCreditLimitServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

临时库索引、显式开启预览、HTTP 请求与轮询步骤见 `knowledge/README.md`。本轮变更文件清单（移动的运行时类按新位置列出，旧位置删除）：

- 构建与共享运行时：`pom.xml`、`agent-runtime/pom.xml`、`agent-runtime/src/main/java/com/zhku/agent/agent/runtime/{AgentRunContext,AgentRunState,AgentRunTask,AgentStopReason}.java`、`agent-runtime/src/test/java/com/zhku/agent/agent/runtime/AgentRunContextTest.java`、`agent/pom.xml`、`agent/Dockerfile`、`agent/README.md`。
- 知识装配与索引：`knowledge/pom.xml`、`knowledge/README.md`、`knowledge/scripts/start-local.ps1`、`knowledge/src/main/java/com/zhku/knowledge/{feedback/FeedbackPreviewConfiguration,publication/LocalKnowledgePublisherFactory,rag/RagProviderConfiguration,retrieval/IndexPublishedKnowledgeCli,retrieval/RetrievalIndexRepository,retrieval/RetrievalIndexingService,retrieval/JdbcRetrievalIndexRepository,tools/modelcall/CourseCreditChatModelFactory,web/ZhkuLocalPreviewApplication}.java`。
- 校园入口：`knowledge/src/main/java/com/zhku/knowledge/campusagent/{CampusCreditAgentService,CampusCreditAgentManager}.java`、`knowledge/src/main/java/com/zhku/knowledge/web/{CampusAgentPreviewConfiguration,CampusAgentPreviewController}.java`。
- 验收代码与记录：`knowledge/src/test/java/com/zhku/knowledge/{campusagent/CampusCreditAgentServiceTest,campusagent/CampusCreditAgentManagerTest,retrieval/IndexPublishedKnowledgeCliTest,retrieval/RetrievalIndexingRepeatTest,rag/RagProviderConfigurationTest,web/CampusAgentPreviewControllerTest,web/ZhkuLocalPreviewApplicationTest}.java`、`docs/decisions/2026-09-28-campus-agent-vertical-slice.md`、本文件。

既有未跟踪 `career/`、`.learnings/` 与 PM 文档未修改。

项目表述：**已实现**本机受控校园学分 Agent、共享运行生命周期、只读政策路由与纯计算工具、版本化 JSON 入口和可控验收；**已接线**预览 RAG 条件装配及显式 profile 索引入口，但同批已发布资料的真库混合检索尚未贯通验收；**已在真实供应商验证**：无。本项目没有接入真实教务系统，也没有上线。
