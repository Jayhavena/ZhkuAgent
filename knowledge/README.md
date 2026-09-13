# Knowledge 模块

包含资料导入、人工审核、事务发布、关键词问答、管理页面与问答页面；不依赖 Agent 模块或模型 SDK。现有问答行为保持不变，尚未实现答案卡和两轮追问。

## 构建与启动

在仓库根目录执行：

```powershell
.\mvnw.cmd -f knowledge/pom.xml -DskipTests package
.\knowledge\scripts\start-local.ps1 -Mode preview -DataRoot D:/alldesk/Projects/SuperAgent-zhkuagent-mvp
.\knowledge\scripts\start-local.ps1 -Mode admin -DataRoot D:/alldesk/Projects/SuperAgent-zhkuagent-mvp
```

分别在两个终端启动。默认端口 8123、8124；原服务仍占用端口时不要直接运行，另用 -Port 指定端口。页面内跨入口链接保持原来的 8123/8124，临时端口验收时分别直接打开两个地址。

DataRoot 显式指向既存数据目录：读取其中 infra/.env，上传目录固定为其 data/local-admin。原 PDF 在 data/raw，不复制到新项目。数据库连接仍按既有 zhku.db.* / ZHKU_DB_* 规则解析。

knowledge/infra 保留原 Compose 项目名、容器和数据卷声明；本轮不执行 Compose up/down，不重建既有容器，不改变已有初始化脚本挂载。不要使用 down -v。新的代码目录不能被误当作新的数据环境。

## 验证

测试中有真实数据库写入，只能使用专用测试库。先在空临时库执行 KnowledgeSchemaTest 建表，再通过 ReviewedKnowledgeImportCli 导入 knowledge/data/curation 中已有审核 JSON，导入后在临时库给该测试来源设置与 V4 相同的 document_family=STUDENT_HANDBOOK、knowledge_scope=PUBLIC_ELECTIVE，再执行 verify-zhku-local.ps1。这是旧基线的初始化前提，不能对原演示库重复执行。脚本要求显式 DatabaseUrl、DataRoot 和当前环境中的测试库密码，并拒绝原演示库端口 54329。

DataRoot 用作测试工作目录，以便读取既存 PDF 和整理资料。命令行属性 zhku.data-root 默认是模块目录，可在没有真实 PDF 时运行基础测试；完整验收使用原资料目录。

构建输出：普通 knowledge jar 用于 CLI；带 preview/admin 后缀的两个可执行 jar 分别启动问答/管理服务。
