# Agent 模块（迁移第一步）

本模块来自原 SuperAgent 工作区，已带入未提交的任务状态机、数据库迁移与测试，以及 pom.xml 的测试依赖改动。Java 包名已调整为 com.zhku.agent，业务逻辑保持不变。

## 独立构建

在 ZhkuAgent 根目录运行：

```powershell
.\mvnw.cmd -f agent/pom.xml -DskipTests package
```

根 pom.xml 已是统一聚合构建，也可单独指定 agent/pom.xml 构建本模块。

## 启动

使用绝对 jar 路径，可从指定的运行目录启动。若要继续使用原 Agent 的本机文件位置，应在原运行目录启动；不要迁移或复制 tmp 等本机数据。

```powershell
# 模型密钥从当前会话环境提供，不写入命令或仓库。
java -jar D:/alldesk/Projects/ZhkuAgent/agent/target/agent-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18123
```

默认 8123 与当前知识库问答端口相同，因此并行验收使用 18123。外置 application-local.yml 未复制进模块；需要原有本机配置时通过 spring.config.additional-location 显式指定原文件。启动成功不代表模型实际调用可用。

代码中的相对 tmp 路径仍取决于 Java 工作目录。启用可选 MCP 配置时，其工具 jar 路径已调整为 tools/image-search/target，按 agent 目录运行或改用外置绝对路径。默认未启用 MCP，不在本轮连接外部工具。

## 前端与工具

- frontend：原 Vue 前端，进入该目录后按锁定依赖安装并运行 npm run build。
- tools/image-search：原独立图片 MCP 工程，保留自身构建；本轮未验证其外部服务能力。
- Dockerfile：构建上下文使用仓库根目录（需要父 pom），通过 -f agent/Dockerfile 指定；本轮只验证本机 jar，未构建 Docker 镜像。

## 迁移边界

本模块的 db/migration 只有任务数据库迁移，不含知识库迁移。不要给任务模块配置知识库数据库连接。当前没有新增会话状态、答案卡或两轮追问实现。
