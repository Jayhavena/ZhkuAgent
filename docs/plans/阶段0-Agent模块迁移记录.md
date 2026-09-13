# 阶段 0：Agent 模块首步迁移记录

分支：codex/zhkuagent-modules；工作区：D:/alldesk/Projects/ZhkuAgent；基线：47a4074。

## 本轮实施

- 建立 agent 独立工程，迁入原主工作区的源码、测试、前端和图片工具工程，共 133 个来源文件。
- 带入主工作区未跟踪任务代码、SQL、测试，以及 pom.xml 的未提交依赖改动。
- 保留原包名与业务逻辑。修改仅涉及显式主类、可选 MCP jar 相对路径、Docker 忽略目录及说明文档。
- agent-import-manifest.json 记录迁入前的逐文件哈希。pom.xml、mcp-servers.json、.dockerignore 是预期调整项。
- 尚未移除根目录旧代码，也未抽离 knowledge；当前是并行验收中间态，不宣称整个模块迁移完成。

## 验证

- 原主工作区迁移前：TaskStateMachineTest 与 TaskStepStateMachineTest 共 25 项通过。
- 新模块：状态、生命周期、执行器、旧功能隔离与工具注册共 59 项通过，零失败、零错误、零跳过。
- 独立 package 成功；检查可执行 jar 无 zhku 类和知识库 SQL。
- 使用占位密钥在 127.0.0.1:18123 临时启动成功；读取 /api/v3/api-docs 确认 Agent 路由存在、知识库路由不存在。没有调用模型或任务业务接口；验证进程已停止。
- 前端锁定依赖离线安装及生产构建通过。
- 数据库约束与迁移测试共 5 项通过，零失败、零错误、零跳过；只使用 Testcontainers 临时数据库。合计 64 项测试通过。

## 保留事项

没有提交、合并或推送。两个原工作区的代码与分支不变。未改动知识库容器、数据卷、PDF 和历史迁移记录。

下一步抽离 knowledge 并建立根聚合构建，之后统一清除迁移工作区的重复旧目录及更新文档路径。两轮追问继续后置。
