# 旧恋爱领域默认运行时隔离：证据账本

## 记录 01：默认隐藏与 legacy 保留

### 验证环境

- 验证日期：2026-09-08（Asia/Shanghai）。
- Git 基线：`70900b2 docs(task): plan durable task foundation`。
- 后端：Java 21、Spring Boot 3.4.4、Maven Surefire 3.5.2。
- 前端：Vite 4.5.14。
- 原始输出目录：`target/evidence/legacy-love-isolation/`（由 `target/` 忽略，不进入 Git）。

### 工作区边界

本阶段开始前，工作区已经存在与本改动无关的未提交或未跟踪内容，包括持久任务基础的
`pom.xml`、实施计划、工具注册、应用配置以及 `task` 骨架。它们没有被本阶段暂存、删除或
改写。结论仅覆盖本账本列出的恋爱领域隔离文件，不把整个工作区描述为干净状态。

### RED → GREEN 记录

| 行为 | RED 现象 | GREEN 结果 | 原始输出 |
| --- | --- | --- | --- |
| 旧 HTTP/SSE 路由不可达 | 校正 MockMvc context path 后，4 个旧路由中 2 个返回 200、2 个进入旧流式处理并报错；均说明旧映射仍存在。 | 4 项均返回 404。 | `02-endpoint-red-corrected.log`、`03-endpoint-green.log` |
| 默认容器不创建 `LoveApp` | 应用上下文发现 `loveApp` Bean。 | 1 项通过，默认上下文无该 Bean。 | `04-love-app-runtime-red.log`、`05-love-app-runtime-green.log` |
| 默认容器不创建恋爱 RAG Bean | 文档加载器、向量库、云端顾问分别被发现。向量库测试先后修正了替身依赖和空文档测试装配问题，最终 RED 明确发现 `loveAppVectorStore`。 | 3 项通过，三类 Bean 都未注册。 | `06-love-document-loader-red.log`、`09-love-vector-store-red-corrected.log`、`10-love-cloud-advisor-red.log`、`11-love-rag-runtime-green.log` |

第一次路由测试误把 servlet context path `/api` 写入 MockMvc 请求，导致 4 项错误地通过。
该现象记录于 `01-endpoint-red.log`；修正请求路径后才将 `02` 作为有效 RED 证据。向量库测试
第一次缺少测试替身依赖、第二次输入空文档，也都未作为有效业务 RED；修正后保留 `09` 作为
有效 RED 证据。

### 阶段回归

| 验证 | 结果 | 能证明什么 |
| --- | --- | --- |
| 默认隔离聚焦测试 | 8 项通过，0 失败、0 错误、0 跳过 | 旧路由和已覆盖的 Love/RAG Bean 不在默认运行时暴露。 |
| Agent 生命周期回归 | 25 项通过，0 失败、0 错误、0 跳过 | 本阶段没有破坏已覆盖的运行上下文隔离、取消、SSE 生命周期、执行器拒绝与终止时序行为。 |
| Vite production build | 103 个模块转换成功 | 移除默认路由后前端可构建。 |
| 默认入口与 bundle 扫描 | `love-master`、`love_app`、恋爱相关词和 `ai-love-avatar` 均未命中 | 当前默认前端入口与构建产物不携带旧领域字符串或资源路径。 |
| 提交前空白检查 | `git diff --check` 退出码为 0 | 本阶段与工作区现有改动均未发现空白错误。 |

生命周期回归日志中的异常栈来自故意注入的失败、超时、连接断开和发送失败场景；Surefire 汇总
为 25 项通过，不能把这些预期日志误读为回归错误。

### 可以证明

1. 默认后端不再通过四个旧 `/ai/love_app/**` 路由提供服务；
2. 默认 Spring 上下文不注册已覆盖的 `LoveApp`、恋爱文档加载器、本地向量库和云端 RAG 顾问；
3. 默认前端路由、首页、SEO 元信息和生产 bundle 已不包含旧领域入口；
4. 既有 25 项 Agent 生命周期回归在本阶段执行时保持通过。

### 不能证明

1. 尚未物理删除旧源文件、恋爱语料、历史测试或 Git 历史；
2. 尚未验证 `features.legacy-love.enabled=true` 恢复旧功能后的外部模型、工具和云端 RAG 可用性；
3. 尚未验证已部署服务器的旧静态资源或 CDN 缓存已失效；
4. 不证明性能、吞吐量、资源节省、多实例安全或线上故障率；
5. 不实现开源项目研究 Agent、任务持久化、恢复、GitHub 调研或新 RAG 知识库。
