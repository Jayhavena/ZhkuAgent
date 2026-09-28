# Knowledge 模块

包含资料导入、人工审核、事务发布、关键词问答、管理页面与问答页面；不依赖 Agent 模块。问答已支持答案卡和两轮澄清追问。

## 构建与启动

在仓库根目录执行：

```powershell
.\mvnw.cmd -pl knowledge -am -DskipTests package
.\knowledge\scripts\start-local.ps1 -Mode preview -DataRoot D:/alldesk/Projects/SuperAgent-zhkuagent-mvp
.\knowledge\scripts\start-local.ps1 -Mode admin -DataRoot D:/alldesk/Projects/SuperAgent-zhkuagent-mvp
```

分别在两个终端启动。默认端口 8123、8124；原服务仍占用端口时不要直接运行，另用 -Port 指定端口。页面内跨入口链接保持原来的 8123/8124，临时端口验收时分别直接打开两个地址。

DataRoot 显式指向既存数据目录：上传目录固定为其 data/local-admin；只有环境中没有数据库密码时才读取其中 infra/.env。原 PDF 在 data/raw，不复制到新项目。数据库连接仍按既有 zhku.db.* / ZHKU_DB_* 规则解析。

启动脚本每次先检查当前进程、Windows 用户级和系统级的 `ZHKU_DB_PASSWORD`；启用 RAG 时同样检查 `DASHSCOPE_API_KEY`，并只把可用值注入所启动的 Java 进程。已有进程值优先，不能被 DataRoot 的 `infra/.env` 覆盖。数据库密码在三个环境级别都没有时才回退到该文件，以兼容原本地数据目录。显式启用 RAG / 校园 Agent 时，`DASHSCOPE_API_KEY` 必须能从环境变量读取。脚本结束后恢复调用方原有进程环境，不打印凭证值。

knowledge/infra 保留原 Compose 项目名、容器和数据卷声明；本轮不执行 Compose up/down，不重建既有容器，不改变已有初始化脚本挂载。不要使用 down -v。新的代码目录不能被误当作新的数据环境。

## 验证

测试中有真实数据库写入，只能使用专用测试库。先在空临时库执行 KnowledgeSchemaTest 建表，再通过 ReviewedKnowledgeImportCli 导入 knowledge/data/curation 中已有审核 JSON，导入后在临时库给该测试来源设置与 V4 相同的 document_family=STUDENT_HANDBOOK、knowledge_scope=PUBLIC_ELECTIVE，再执行 verify-zhku-local.ps1。这是旧基线的初始化前提，不能对原演示库重复执行。脚本要求显式 DatabaseUrl、DataRoot 和当前环境中的测试库密码，并拒绝原演示库端口 54329。

DataRoot 用作测试工作目录，以便读取既存 PDF 和整理资料。命令行属性 zhku.data-root 默认是模块目录，可在没有真实 PDF 时运行基础测试；完整验收使用原资料目录。

## 校园 Agent 纵切与索引（本机预览）

有 Docker/pgvector 镜像时，可先运行独立的**临时库自动化验收**：

```powershell
.\knowledge\scripts\verify-campus-temp-db.ps1
```

脚本生成随机数据库密码，启动无持久卷的专用容器，运行迁移、审核发布、索引/候选、词法降级评测及校园 Agent HTTP 闭环测试，结束后停止并删除容器。模型工具选择使用可控替身；它不验证真实供应商，也不调用原演示库。端口冲突时可指定 `-Port`，但不可使用原演示库的 54329 端口。

根目录构建三个模块（含共享运行上下文）：

```powershell
.\mvnw.cmd -pl knowledge -am -DskipTests package
```

`index` 可执行 jar 只在显式 `--live` 下写索引，必须把 `ZHKU_DB_URL` 明确设为专用目标库，并从环境变量提供 `ZHKU_DB_PASSWORD` 与模型 profile 所需密钥。本仓库默认 profile 是 `dashscope`；测试时先在**专用临时测试库**建表、按审核流程发布资料，再执行：

```powershell
$env:ZHKU_DB_URL = 'jdbc:postgresql://127.0.0.1:<临时库端口>/<临时库名>'
$env:ZHKU_DB_USER = '<临时库用户>'
# ZHKU_DB_PASSWORD 与 DASHSCOPE_API_KEY 由当前进程环境安全提供，不写入命令或仓库。
& .\scripts\Import-ZhkuCredentials.ps1 -RequireDatabase -RequireDashScope
java -jar .\knowledge\target\knowledge-0.0.1-SNAPSHOT-index.jar --live --scope=PUBLIC_ELECTIVE --chunking-version=fact-aware-v1
```

CLI 根据预览 YAML 的模型 profile 登记 `BUILDING` 索引 profile；每次运行记录 `runId`、合格候选数、新增条目数。相同 profile/切分版本/来源哈希重跑时新增为零；内容变化须新切分版本。**发布不会自动建索引**：审核发布只更新资料表，CLI 索引读取已发布、学生可见、分享权确认且范围匹配的片段。规则级学分上限使用独立的 `PUBLIC_ELECTIVE_CREDIT_LIMIT` 已发布证据，优先于 RAG；通用 RAG 的示例范围 `PUBLIC_ELECTIVE` 与 YAML 一致。当前检索器仍按配置 profile 检索，尚未强制检查其 `ACTIVE` 状态；不能把 `BUILDING` 误称为已完成上线评测。

默认预览仍关闭模型。具备供应商密钥且**明确选择目标库**时，显式启用预览校园入口：

```powershell
.\knowledge\scripts\start-local.ps1 -Mode preview -DataRoot <既存数据目录> -Port 18123 -EnableRag -EnableCampusAgent
```

临时库验收若不用既存 DataRoot，可在已提供临时库密码的会话中直接启动 preview jar，并将 `--zhku.db.url` 指向与索引 CLI 相同的临时库。`-EnableRag` / `-EnableCampusAgent` 对应的 Java 参数分别是 `--zhku.rag.enabled=true` / `--zhku.campus-agent.enabled=true`；不要把密码写在 Java 参数里。

新 JSON 合同与旧 `/conversations` JSON/SSE 路径独立。创建运行：`POST /zhku/local-preview/campus-agent/v1/runs`，请求体示例：

```json
{"question":"这两门虚构公选课学分合计及每学期上限？","courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},{"courseId":"HIS02","category":"公共选修课","credits":2}]}
```

返回 `runId` 后可 `GET /zhku/local-preview/campus-agent/v1/runs/{runId}` 查看状态、步骤、工具结果、出处；`POST /.../{runId}/cancel` 请求取消。`state=STOP_REQUESTED` 只表示停止请求已收到，`terminated=true` 才表示清理完成。若省略 `courses`，模型可以选择追问；它若提交非法工具参数或资料不可核验，服务端会转人工。每次运行及预览会话均只在内存中保存，最多保留 128 条运行，进程重启后消失，**不是 JDBC 运行时持久化**。

构建输出：普通 knowledge jar 供既有 CLI 使用；带 preview/admin/index 后缀的可执行 jar 分别启动问答、管理服务和显式索引任务。
