# 仲园 Agent（ZhkuAgent）

一个仓库，两个可独立构建和启动的模块。校园问答已支持两轮澄清追问。

| 目录 | 职责 |
| --- | --- |
| agent/ | Agent 运行时、任务状态、原前端和工具工程 |
| knowledge/ | 校园资料治理、发布与知识问答 |
| docs/ | 设计、历史记录和验收证据 |

## 构建

```powershell
.\mvnw.cmd -DskipTests package
.\mvnw.cmd -f agent/pom.xml -DskipTests package
.\mvnw.cmd -f knowledge/pom.xml -DskipTests package
```

根目录只负责聚合与公共 Java/Spring Boot 版本，两模块不互相依赖。模型依赖仅属于 agent；两边的数据库迁移资源各自隔离。

启动和测试分别见 [Agent 说明](agent/README.md)、[Knowledge 说明](knowledge/README.md)。Agent 并行验收端口使用 18123，知识库默认 8123/8124，仅限本机。

## 数据与历史

迁移分支基于原知识库提交 47a4074，保留共同 Git 历史。两个原工作区继续保留。本机数据库卷、原始 PDF、上传文件和密码不随代码目录搬迁。知识库以明确的 DataRoot 指向原资料目录。

docs 中旧文档描述的是迁移前目录和命令；当前操作以两个模块 README 为准。旧教学功能来源与说明保留在 docs/agent/迁移前README.md。本项目未宣称上线、真实学生使用或完整 Agent 任务闭环。
