# 仲园 Agent 测试分层与本机一键验收设计

## 1. 目标

为仲园 Agent 建立可重复、可解释的本机验收方式，使开发者不必手动拼接数据库密码、启动 PostgreSQL、导入首批资料和选择测试类。

验收命令应明确区分：

1. 页面与接口的快速回归；
2. 依赖本机 PostgreSQL 的知识库集成验收；
3. 仓库中依赖模型或其他外部环境的通用 Agent 测试。

本设计不追求让完整 `mvn test` 立即全绿，而是先让仲园 Agent 的提交门槛稳定、可复现且不掩盖仓库其他模块的问题。

## 2. 背景与问题

当前仲园 Agent 已有两类可用测试：

- 不访问数据库的页面、HTTP 接口、配置隔离测试；
- 真实访问本机 PostgreSQL 的资料模式、发布器、关键词检索和公共选修问答测试。

但它们存在三个问题：

1. 数据库测试只读取 Java 系统属性 `zhku.db.password`，而本机预览服务还可读取环境变量 `ZHKU_DB_PASSWORD`，规则不一致；
2. 新建数据库时需要先启动容器、执行迁移并导入已审核资料，顺序依赖人工记忆；
3. 直接运行完整 `mvn test` 会混入需要模型密钥或其他外部环境的通用 Agent 测试，导致失败原因难以区分。

## 3. 范围

### 3.1 本次包含

1. 一个 PowerShell 本机验收脚本：`scripts/verify-zhku-local.ps1`。
2. 一个可复用的本机知识库连接配置单元，统一读取 URL、用户名和密码。
3. 仲园快速回归测试清单与真实数据库集成测试清单。
4. Docker 引擎检查、项目 PostgreSQL 容器启动、健康等待、模式迁移、已审核资料导入和分层结果汇总。
5. 开发者文档中对命令、前置条件、失败含义和完整仓库测试边界的说明。

### 3.2 本次不包含

- 自动启动 Docker Desktop、自动重启 WSL、修改 Windows 系统设置；
- 自动配置 DashScope、Ollama 或其他模型密钥；
- 将完整 `mvn test` 改造成仲园 Agent 的提交门槛；
- 引入 Testcontainers、Maven Profile、第三方测试框架或持续集成服务；
- 改变知识资料审核、发布权限或学生可见性规则；
- 自动合并分支或推送远程仓库。

## 4. 核心决策

### 4.1 使用 PowerShell 脚本编排，而非 Maven Profile

脚本需要处理 Docker 检查、容器启动、健康等待、`.env` 读取和已审核资料导入。这些步骤跨越 Maven 之外的本机环境，放入 Maven Profile 会把构建配置与开发机生命周期耦合。当前 MVP 优先选择一个显式、可读、可在 PowerShell 中单独调试的脚本。

### 4.2 统一数据库配置读取规则

新增的配置单元应遵循以下优先级：

```text
Java 系统属性 → 进程环境变量 → 本机默认 URL / 用户名
```

其中：

| 配置项 | 系统属性 | 环境变量 | 本机默认值 |
| --- | --- | --- | --- |
| 数据库 URL | `zhku.db.url` | `ZHKU_DB_URL` | `jdbc:postgresql://127.0.0.1:54329/zhkuagent` |
| 数据库用户名 | `zhku.db.user` | `ZHKU_DB_USER` | `zhkuagent` |
| 数据库密码 | `zhku.db.password` | `ZHKU_DB_PASSWORD` | 无 |

密码缺失时必须抛出明确错误，但错误信息不得包含密码本身。已有 `LocalKnowledgePublisherFactory` 与全部仲园真实数据库测试都改为复用这一规则。

### 4.3 自动导入已审核的公共选修资料

脚本在模式迁移成功后，调用既有 `ReviewedKnowledgeImportCli` 导入：

```text
data/curation/2025版学生手册2025.9.2-公共选修候选知识片段.json
```

该资料已在前序流程中经人工审核。发布器采用来源 ID 与片段索引的稳定标识并执行更新式写入，因此重复执行验收命令应更新同一份本机验证资料，而不是持续新增重复片段。

自动导入的影响仅限本机 PostgreSQL。它不会将资料标记为学生可见，也不会改变资料的公开授权状态。

## 5. 分层与执行流程

```text
开发者执行 verify-zhku-local.ps1
        │
        ├─ 第一层：快速回归（无数据库）
        │     页面 / HTTP / 配置隔离，共 11 项
        │
        ├─ 第二层前置检查
        │     .env → Docker 引擎 → PostgreSQL 容器健康
        │
        ├─ 第二层准备
        │     Flyway 模式迁移 → 导入已审核公共选修资料
        │
        └─ 第二层：真实数据库集成验收
              模式 / 发布器 / 关键词检索 / 有依据问答
```

### 5.1 第一层：快速回归

脚本首先运行以下测试：

```text
ZhkuLocalPreviewPageControllerTest
ZhkuLocalPreviewPageResourceTest
ZhkuLocalPreviewControllerTest
ZhkuLocalPreviewConfigurationTest
ZhkuLocalPreviewApplicationTest
```

该层共 11 项测试，不读取数据库密码，也不要求 Docker 运行。它验证页面路由、UTF-8、状态渲染边界、HTTP 问答契约和预览启动隔离。

第一层失败时，脚本应立即停止，返回非零退出码；不得启动或修改数据库。

### 5.2 第二层：本机环境准备

第一层通过后，脚本按顺序执行：

1. 确认 `infra/.env` 存在且包含非空 `ZHKU_DB_PASSWORD`；
2. 调用 Docker 客户端检查引擎是否可用；失败时提示用户启动或修复 Docker Desktop，不操作 WSL；
3. 使用 `infra/docker-compose.yml` 启动本项目 PostgreSQL 容器；
4. 最多轮询 30 秒，等待容器健康状态为 `healthy`；
5. 仅在当前 PowerShell 进程设置 `ZHKU_DB_PASSWORD`，不输出值；
6. 运行 `KnowledgeSchemaTest`，通过 Flyway 执行并验证模式迁移；
7. 构建运行时类路径并调用 `ReviewedKnowledgeImportCli` 导入已审核候选资料。

脚本不自行创建 `.env`，不改变 Docker 监听地址，不停止容器，也不删除数据库或卷。

### 5.3 第二层：真实数据库集成验收

准备成功后，脚本运行：

```text
ReviewedKnowledgePublisherTest
PublicElectiveKeywordSearchAcceptanceTest
PublicElectiveGroundedAnswerAcceptanceTest
```

这些测试分别证明：

- 审核状态、发布事务、归档和学生可见性边界正确；
- 已导入的公共选修资料能按预期关键词定位原始出处；
- 已覆盖问法返回 `GROUNDED` 与引用，资料范围外的奖学金问题返回 `INSUFFICIENT_EVIDENCE`。

任意测试失败时，脚本返回非零退出码，并报告失败发生在“资料准备”还是“集成验收”。

### 5.4 第三层：完整仓库诊断

完整 `mvn test` 保留为单独的仓库诊断命令，不在 `verify-zhku-local.ps1` 内自动运行。

它可能失败于通用 Agent 的模型自动配置、文件工具、网页工具或其他与仲园本机 MVP 无关的外部依赖。该结果必须单独记录，不能覆盖前两层的仲园验收结论，也不能被描述为仲园页面回归失败。

## 6. 脚本行为与安全边界

### 6.1 正常输出

脚本应以固定阶段标题输出：

```text
[1/3] 仲园快速回归
[2/3] 本机数据库准备与资料导入
[3/3] 仲园真实数据库集成验收
```

成功时输出各阶段“通过”；失败时输出阶段名称、可见错误摘要和建议动作。日志不得包含数据库密码、完整连接凭据或未审核资料内容。

### 6.2 失败处理

| 情况 | 脚本行为 | 建议动作 |
| --- | --- | --- |
| `.env` 缺失或密码为空 | 不启动测试，返回非零 | 恢复本机 `infra/.env` 配置。 |
| Docker 引擎不可用 | 不修改 WSL，返回非零 | 启动或修复 Docker Desktop。 |
| 容器 30 秒内不健康 | 不删除容器或卷，返回非零 | 查看 Docker 容器日志。 |
| 模式迁移失败 | 不导入资料，返回非零 | 检查迁移脚本和数据库状态。 |
| 资料导入失败 | 不运行依赖资料的验收测试，返回非零 | 检查候选 JSON 的审核状态和资料格式。 |
| 集成测试失败 | 返回非零并保留容器 | 查看具体测试报告与资料数据。 |

## 7. 实施文件与职责

| 文件 | 变更 | 职责 |
| --- | --- | --- |
| `scripts/verify-zhku-local.ps1` | 新建 | 编排快速回归、Docker、迁移、导入和集成验收。 |
| `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/LocalKnowledgeDatabaseSettings.java` | 新建 | 统一解析本机 URL、用户和密码。 |
| `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/LocalKnowledgePublisherFactory.java` | 修改 | 使用共享数据库配置单元创建数据源。 |
| `src/test/java/com/yupi/yuaiagent/zhku/KnowledgeSchemaTest.java` | 修改 | 使用共享数据库配置，不再只读取系统属性。 |
| `src/test/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgePublisherTest.java` | 修改 | 使用共享数据库配置。 |
| `src/test/java/com/yupi/yuaiagent/zhku/knowledge/publication/PublicElectiveKeywordSearchAcceptanceTest.java` | 修改 | 使用共享数据库配置。 |
| `src/test/java/com/yupi/yuaiagent/zhku/knowledge/answer/PublicElectiveGroundedAnswerAcceptanceTest.java` | 修改 | 使用共享数据库配置。 |
| `docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md` | 修改 | 记录一键验收命令、分层含义与完整仓库测试边界。 |

实际 Java 包路径应沿用 `com.zhku.knowledge.publication`，避免创建仅为测试存在的平行配置体系。

## 8. 验收标准

在新建或空的本机 PostgreSQL 数据库中，只要 Docker 引擎可用且 `infra/.env` 有有效密码，开发者执行：

```powershell
.\scripts\verify-zhku-local.ps1
```

应获得以下结果：

1. 快速回归通过，且无需数据库；
2. PostgreSQL 容器启动并变为健康状态；
3. 知识库模式完成迁移；
4. 已审核公共选修资料成功导入；
5. 真实数据库发布、检索和有依据问答测试通过；
6. 未输出数据库密码；
7. Docker 不可用、配置缺失、迁移失败和资料导入失败均能给出明确且不破坏环境的提示。

完成上述标准不代表完整 `mvn test` 已经全绿，也不代表通用 Agent 的模型依赖问题已经解决。
