# 下线旧恋爱示例域（2026-09-28）

此前的[默认隔离决策](2026-09-08-legacy-love-runtime-isolation.md)只关闭公开入口与默认 Bean，仍保留 `LoveAppTest`。该测试显式开启旧域并向模型提交恋爱问题，因此执行全量测试时会出现恋爱 APP 输出；旧应用类和三份语料也仍进入 Agent 构建产物。

本次按用户要求物理移除 `agent/` 内的旧应用、专用 RAG/PGVector 原型、三份恋爱语料、调用真实模型的旧测试，以及前端未挂载的页面和 API。`MyKeywordEnricher`、`MyTokenTextSplitter` 仅被该旧向量库使用，一并移除；相应的 Markdown Reader、PGVector Store、Vector Store Advisor 依赖从 `agent/pom.xml` 删除。Agent 任务持久化所需的 JDBC、PostgreSQL 和 Flyway 依赖保留。

`agent-runtime`、通用 Agent 生命周期和工具、`knowledge` 校园入口均不依赖这些旧类。新增类路径防回归测试，阻止旧应用或语料重新进入运行产物。历史设计/迁移文档作为证据保留，不参与构建或运行。

验证：防回归测试先因 `LoveApp.class` 仍在类路径中失败；删除后对 `agent-runtime`、`agent`、`knowledge` 干净构建与聚焦回归为 102 通过、0 失败、0 错误、0 跳过。Agent jar 无旧应用/语料条目，前端 Vite 构建通过且产物无旧页面内容。数据库及真实供应商测试未运行。
