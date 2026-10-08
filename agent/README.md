# Agent 模块（迁移第一步）

## Milestone 1A 运行结果约定

一次运行必须显式完成才能进入 `SUCCEEDED`。步数耗尽或 `doTerminate(outcome="BLOCKED")` 为 `INCOMPLETE`；有效 `doTerminate(outcome="COMPLETED")` 为 `SUCCEEDED`，这里只证明模型声明完成，不证明答案质量。没有参数的旧终止调用不再被接受。工具输出的 JSON 为 `{"outcome":"COMPLETED"}` 或 `{"outcome":"BLOCKED"}`；Java 方法返回结构化 Map，避免框架二次转义。

模型和工具异常分别以固定 `MODEL_FAILURE`、`TOOL_FAILURE` 跨运行边界传递，清理异常为 `CLEANUP_FAILURE`。清理开始不等于结束；`termination` 在清理收敛后通知等待方，清理失败会降级为 `FAILED`。停止请求仍是协作式取消，无法强制结束忽略中断的供应商调用。搜索工具的 0—4 条结果属于合法短结果，异常响应不能作为正常搜索文本返回。

新增 `INCOMPLETE` 和结束工具参数是兼容性变化，严格匹配枚举或调用旧无参数工具的客户端需要更新。本阶段不提供新认证、任务恢复或长期记忆。实施、测试和验收见 [Milestone 1A 验收记录](../docs/evidence/2026-10-02-milestone-1a-验收记录.md)。

本模块来自原 SuperAgent 工作区，已带入未提交的任务状态机、数据库迁移与测试，以及 pom.xml 的测试依赖改动。Java 包名已调整为 com.zhku.agent，业务逻辑保持不变。

## 独立构建

在 ZhkuAgent 根目录运行：

```powershell
.\mvnw.cmd -pl agent -am -DskipTests package
```

运行上下文现位于共享 `agent-runtime` 模块；从根目录用 `-am` 一并构建依赖。

## 启动

使用绝对 jar 路径，可从指定的运行目录启动。若要继续使用原 Agent 的本机文件位置，应在原运行目录启动；不要迁移或复制 tmp 等本机数据。

```powershell
# 模型密钥从当前会话环境提供，不写入命令或仓库。
& .\scripts\Import-ZhkuCredentials.ps1 -RequireDashScope
java -jar D:/alldesk/Projects/ZhkuAgent/agent/target/agent-0.1.0-rc.1.jar --server.address=127.0.0.1 --server.port=18123
```

默认 8123 与当前知识库问答端口相同，因此并行验收使用 18123。外置 application-local.yml 未复制进模块；需要原有本机配置时通过 spring.config.additional-location 显式指定原文件。启动成功不代表模型实际调用可用。

代码中的相对 tmp 路径仍取决于 Java 工作目录。启用可选 MCP 配置时，其工具 jar 路径已调整为 tools/image-search/target，按 agent 目录运行或改用外置绝对路径。默认未启用 MCP，不在本轮连接外部工具。

## 前端与工具

- frontend：原 Vue 前端，进入该目录后按锁定依赖安装并运行 npm run build。
- tools/image-search：原独立图片 MCP 工程，保留自身构建；本轮未验证其外部服务能力。
- Dockerfile：构建上下文使用仓库根目录（需要父 pom），通过 -f agent/Dockerfile 指定；本轮只验证本机 jar，未构建 Docker 镜像。

## 迁移边界

本模块的 db/migration 只有任务数据库迁移，不含知识库迁移。不要给任务模块配置知识库数据库连接。当前没有新增会话状态、答案卡或两轮追问实现。
