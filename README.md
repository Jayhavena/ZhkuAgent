# ZhkuAgent · 校园资料查证 Agent

面向仲恺校园公开资料（培养方案、学分规则、教务通知）的检索与查证系统。用户用自然语言提问，Agent 检索本地资料库、给出**带出处的回答**；证据不足或资料已撤回时转人工核实，不凭记忆作答。

```
用户提问 → 规则查证 Agent → 检索知识库（向量 + 关键词） → 引用原文出处 → 回答 / 转人工
```

## 能做什么

- **规则查证**：回答学分构成、课程替代、重修与毕业要求等问题，每个结论都附资料版本与页码。
- **资料治理**：文档入库、分块、向量化、审核发布、下架撤回，回答时按当前发布状态判定能否展示。
- **可追溯执行**：每次运行留下 Run 快照（步骤、工具调用、检索结果），可回放、可核查、可归档。
- **多轮澄清**：信息不足时主动追问；本人历史会话跨进程可恢复（受控模式）。

## 不做什么

- 不推断个人选课资格，不访问真实教务系统，不代办校园事务。
- 不保证模型端到端自主完成；结论以资料原文为准，模型只做组织与引用。
- 定位是本机 / 单机部署的演示与教学项目，不是生产级多租户服务。

## 架构

```
                    ┌──────────────────┐
        ┌───────────┤  agent-runtime   │  共享 Run / Snapshot / ToolCall / Event / RuntimeError
        │           └──────────────────┘
        │                    │
   ┌────▼─────┐        ┌─────▼──────┐
   │  agent   │        │  knowledge │
   │ 通用 Agent│        │ 查证 / 知识库│
   └──────────┘        └────────────┘
```

| 模块 | 职责 |
| --- | --- |
| `agent-runtime/` | 运行生命周期、快照、工具调用协议、事件与统一错误类型。公共库，无独立入口。 |
| `agent/` | 通用 Agent：主循环、内置工具（文件/终端/搜索/抓取/下载/PDF）、会话记忆、RAG 顾问链、前端。 |
| `knowledge/` | 校园业务：资料治理与审核、向量检索、规则查证 Agent、学分核算与人工转接。 |

两个应用都依赖 `agent-runtime`，彼此不依赖；校园规则只存在于 `knowledge`，共享层不含业务。

## 技术栈

Java 21 · Spring Boot 3.4 · Spring AI / Spring AI Alibaba（DashScope）· PostgreSQL + pgvector · Flyway · Maven Wrapper · Vite（前端）· Docker Compose（本地数据库）

## 快速开始

前置：JDK 21、PowerShell、Docker Desktop（起 PostgreSQL + pgvector）、DashScope API Key。

```powershell
# 1. 起数据库
docker compose -f knowledge/infra/docker-compose.yml up -d

# 2. 配置凭证（只放环境变量，不写进仓库）
$env:ZHKU_DB_PASSWORD = "<本机数据库口令>"
$env:DASHSCOPE_API_KEY = "<你的 DashScope Key>"

# 3. 构建（-am 会一并构建 agent-runtime）
.\mvnw.cmd -pl knowledge -am -DskipTests package

# 4. 启动知识库服务
java -jar knowledge/target/knowledge-0.1.0-rc.1.jar
```

默认端口：知识库服务 `8123`（HTTP 接口前缀 `/api`），管理入口 `8124`；通用 Agent 示例用 `18123`，避免冲突。详细启动脚本与演示流程见 [knowledge/README.md](knowledge/README.md) 与 [本机演示手册](docs/manual/)。

> 打包成功只代表编译通过，不代表模型、数据库和网络可用；模型实际调用需要有效 Key 与网络。

## 目录

```
agent-runtime/   共享运行时
agent/           通用 Agent（含前端、内置工具）
knowledge/       校园查证与知识库
docs/            设计、决策、验收与教程（先看 docs/README.md）
tools/           独立小工程与验收脚本（image-search-mcp、t08-acceptance…）
scripts/         本机构建、启动、凭证脚本
```

## 开发约定

- 提交遵循 [docs/git-commit-workflow.md](docs/git-commit-workflow.md)：明确结果 → 验收通过 → 核对暂存差异 → 提交。
- 关键边界与易误用处写中文注释，说明取舍与风险，不逐行复述代码。
- 凭证只从环境变量读取，不入库、不入日志、不写进命令。AI 协作约定见 [AGENTS.md](AGENTS.md)。

## 文档

| 入口 | 内容 |
| --- | --- |
| [docs/README.md](docs/README.md) | 文档索引，说明各目录哪些是当前有效、哪些是历史归档 |
| [docs/design/](docs/design/) | 架构与契约设计 |
| [docs/decisions/](docs/decisions/) | 关键决策记录 |
| [docs/manual/](docs/manual/) | 本机启动与演示手册 |
| [docs/tutorials/](docs/tutorials/) | 模块迁移与实现过程说明 |

## 来源与许可

项目起步于开源教学项目 [liyupi/yu-ai-agent](https://github.com/liyupi/yu-ai-agent) 的工程骨架（Spring Boot + Spring AI 的基础结构、MCP 与工具调用示例），在此基础上重构为校园查证 Agent：包结构、运行边界、知识库治理与检索链路均已重写。上游原项目未声明开源许可，本仓库目前为企业内部/私有仓库（private），未做对外分发；如需开源，请先补齐 LICENSE 并核对上游代码的使用范围。

---

版本 `0.1.0-rc.1` · 维护者：[@Jayhavena](https://github.com/Jayhavena)
