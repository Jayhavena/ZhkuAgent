# 校园 Agent 最小闭环：边界与实施顺序（2026-09-28）

## 已核实基线

`knowledge` 使用 Spring AI 1.0.9（Boot 3.4.4），`agent` 使用 Spring AI 1.0.0 和 Spring AI Alibaba 1.0.0.2。两个启动入口分别是 `ZhkuLocalPreviewApplication`（8123）和 `AgentApplication`（默认 8123）；后者的默认工具包含通用文件、终端等能力。预览入口只扫描 `web`、`feedback`，因此包外的 `RagProviderConfiguration` 虽有开关，显式开启时也不会自动装配。预览会话仍是 `InMemoryConversationStore`，JDBC 实现尚未接到该入口。`five-level-routing-v1` 合流复验为 2/5；通知级测试装配及部分旧预期存在问题，不能用该数字声称 Agent 效果。

资料链（现状）：审核/策展 JSON → `ReviewedKnowledgePublisher` 事务发布 → `knowledge_source`/`knowledge_chunk`；此处 **没有发布后索引触发** → `RetrievalIndexingService.indexProfile`（只有测试调用）→ `JdbcLexicalCandidateRetriever` + `JdbcVectorCandidateRetriever` → `RetrievalEligibilityFilter` → `HybridCandidateFuser` (RRF) → `RetrievalAugmentedAnswerService` → 引用守卫及审计 → `LocalPreviewAnswerChain` 的第四级 → JSON/SSE。由于扫描断点和默认 `zhku.rag.enabled` 关闭，预览入口当前不会走该第四级。前三级按澄清、当期通知、高风险规则短路。

通用 Agent 链（现状）：`AiController` → `DefaultAgentFactory` → `BaseAgent.runStream` 为每次运行建 `AgentRunContext`/`AgentRunTask` → `ToolCallAgent` 让模型选通用工具 → 有界执行器执行 → 状态/停止原因/清理后终止信号 → SSE。此链 **没有校园知识查询及学生权限边界**。

## 选择

使用 **共享生命周期小模块 + knowledge 边界适配**：仅把无高权限工具的 `AgentRunContext`、`AgentRunTask` 和状态类型移到 `agent-runtime`，两个应用依赖它；学生入口仍只在 `knowledge`，不依赖 `agent` 模块、不扫描其包。预览显式导入 RAG 配置，保留默认关闭。首个场景的政策来源采用既有 `PUBLIC_ELECTIVE_CREDIT_LIMIT` 高风险规则查询；模型只能选择获准的知识查询和纯计算，服务端校验并组装结论。普通问答的五级路由仍负责当期通知和高风险规则优先级；校园入口由模型选择政策查询工具，查询时经该链裁决，只有匹配已发布学分规则时才允许纯计算与组装答案。

未采用 `knowledge -> agent` 整模块依赖：会带入 Spring AI 1.0.0、Alibaba starter 和高权限工具，破坏预览 classpath 隔离。未采用双服务 HTTP 互调：增加部署、鉴权和跨服务取消成本，首个纵切不需要。共享模块里的 Spring AI `Message` API 需在 1.0.0/1.0.9 组合构建中复验；若发现二进制不兼容，应将消息持有从共享生命周期拆出，而不是放宽工具隔离。

## 按依赖排序的实施与验收

1. 提取共享运行上下文；根聚合构建和 Agent 现有生命周期测试通过，预览 classpath 不含通用 Agent/高权限工具。
2. 修复预览 RAG 显式装配，补 profile 登记与索引 CLI；关闭时无模型 bean，开启时有检索/模型 bean，临时库验证资格过滤、词法/向量、RRF、引用。发布只改变资料状态；索引必须显式执行。当前检索器按配置 profile 查询，尚未机械强制 `ACTIVE` 状态；`BUILDING` 不代表已完成上线评测。
3. 新建校园学分入口；可控模型测试追问、政策查询、纯计算、非法调用、无证据与失败，最终答案只引用服务端证据。
4. 新入口复用运行上下文与有界执行器；验证隔离、取消/超时/发送失败/拒绝，以及清理完成后终止。原 JSON/SSE 不改；新入口独立版本化字段。
5. 用临时测试库和虚构课程做端到端评测，逐题记预期/实际/失败。保留 v1 历史报告；供应商条件不足时明确未做真实冒烟。内存会话仍只限进程生命周期，不能称为运行时持久化。

数据纪律：数据库写入测试只允许专用临时测试库；不重发或清理共享演示数据。任何真实模型凭据仅从环境变量取得。
