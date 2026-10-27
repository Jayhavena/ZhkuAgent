# Agent 模块

通用 Agent 模块：独立主循环（run/snapshot）、工具集、会话记忆、RAG 与前端。运行上下文与错误契约在共享模块 `agent-runtime` 中，本模块只提供业务实现。

## 目录结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java/com/zhku/agent/agent/` | 主循环、Agent 实现（9） |
| `src/main/java/com/zhku/agent/tools/` | 内置工具：文件、终端、下载、PDF、搜索、抓取、终止（8） |
| `src/main/java/com/zhku/agent/task/` | 任务状态机与数据库（6） |
| `src/main/java/com/zhku/agent/advisor/`、`chatmemory/`、`rag/` | 顾问链、会话记忆、检索增强 |
| `src/main/java/com/zhku/agent/controller/` | HTTP 入口：`AiController`、`HealthController` |
| `src/main/java/com/zhku/agent/config/`、`constant/` | 配置与常量 |
| `src/test/java/com/zhku/agent/demo/` | Spring AI 调用示例（HTTP/SDK/LangChain/Ollama/SpringAI、RAG 扩展器），**示例代码，不进产物 jar** |
| `frontend/` | 规则查证前端，Vite 工程，独立 npm 构建 |
| `src/main/resources/mcp-servers.json` | 可选 MCP 服务器声明（高德地图、图片搜索） |
| `Dockerfile` | 构建上下文是仓库根目录，用 `-f agent/Dockerfile` 指定 |

## 构建

在仓库根目录运行（`-am` 一并构建 `agent-runtime`）：

```powershell
.\mvnw.cmd -pl agent -am -DskipTests package
```

## 启动

模型密钥由当前会话环境提供，不写入命令或仓库：

```powershell
& .\scripts\Import-ZhkuCredentials.ps1 -RequireDashScope
java -jar D:/alldesk/Projects/ZhkuAgent/main/agent/target/agent-0.1.0-rc.1.jar --server.address=127.0.0.1 --server.port=18123
```

默认端口 8123 与知识库问答端口相同，并行验收改用 18123。启动成功不代表模型实际调用可用。代码中的相对 `tmp` 路径取决于 Java 工作目录，不要为了统一而搬迁本机数据。

## 可选 MCP 工具

`src/main/resources/mcp-servers.json` 默认不生效。启用后 `image-search` 从 `tools/image-search-mcp/target/image-search-0.1.0-rc.1.jar` 加载（相对仓库根目录）；先在该目录用自带 `mvnw.cmd` 构建，或改用绝对路径。图片搜索的外部服务能力未验证。

## 运行结果约定（Milestone 1A，2026-10-02 定稿）

一次运行必须显式完成才能进入 `SUCCEEDED`。步数耗尽或 `doTerminate(outcome="BLOCKED")` 为 `INCOMPLETE`；有效 `doTerminate(outcome="COMPLETED")` 为 `SUCCEEDED`，这里只证明模型声明完成，不证明答案质量。没有参数的旧终止调用不再被接受。工具输出的 JSON 为 `{"outcome":"COMPLETED"}` 或 `{"outcome":"BLOCKED"}`；Java 方法返回结构化 Map，避免框架二次转义。

模型和工具异常分别以固定 `MODEL_FAILURE`、`TOOL_FAILURE` 跨运行边界传递，清理异常为 `CLEANUP_FAILURE`。清理开始不等于结束；`termination` 在清理收敛后通知等待方，清理失败会降级为 `FAILED`。停止请求仍是协作式取消，无法强制结束忽略中断的供应商调用。搜索工具的 0—4 条结果属于合法短结果，异常响应不能作为正常搜索文本返回。

新增 `INCOMPLETE` 和结束工具参数是兼容性变化，严格匹配枚举或调用旧无参数工具的客户端需要更新。实施、测试和验收见 [Milestone 1A 验收记录](../docs/evidence/2026-10-02-milestone-1a-验收记录.md)。

## 边界

- 本模块的 `db/migration` 只有任务数据库迁移，不含知识库迁移；不要给任务模块配置知识库数据库连接。
- 本模块源出原 SuperAgent 工作区，包名已统一为 `com.zhku.agent`，业务逻辑保持不变。
