# T06 口令夹具修正与真实身份仓储复验：**通过**（工程局部）

## 身份

| 项 | 值 |
| --- | --- |
| worktree / 分支 | `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` ／ `codex/t06-controlled-launch` |
| 起始 HEAD | `3b7f6bf80539520a1f3696da135cba4a6067c551`（工作区与暂存区开始时均干净） |
| 成套提交 | `3b7f6bf` 与其前的 `75274a1`、`7d8b5e4` 均在历史内；`autowireCandidate` 残留 0 |
| 共享合同 | v3，未改；既有 HTTP 测试、浏览器宿主、前端、样例、T05 源码均未触碰 |
| 生产代码 | **未改**（本轮只改测试夹具，未放宽口令规则、未绕过 BCrypt、未改超时/权限/异常分类） |
| 供应商调用 | **0**；`DASHSCOPE_API_KEY` 未读取；未创建任何 Web 监听 |

## 结论：**上一轮的失败是测试夹具问题，已证实**

直接原因是口令夹具不满足 `PreviewIdentityRepository.validPassword` 的正式约束
（**16–64 位、全部为可打印 ASCII**）：上一轮"正确口令"`t06-tempdb-pass` 只有 15 位，
"错误口令"`wrong-password` 只有 14 位，因此在数据库查询与 BCrypt 校验之前就被拒绝，
统一返回 `Optional.empty()`。

**修正夹具后，真实身份仓储在真实临时库上全部通过**——证实根因是夹具，且**未发现其他问题**。
上一轮"16.7 秒耗时疑为认证超时"的猜测**不成立**，本轮不以耗时推断超时。

## 实际改动（仅测试夹具）

| 文件 | 改动 |
| --- | --- |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentReviewedIdentityTempDbTest.java` | 口令夹具改为 16–64 位可打印 ASCII（正确／格式合格但内容错误／未知账号三组）；每次 `authenticate` 用新的 `char[]`；新增"传入数组被清空"、"主体 userId/role/authRevision 与写入值一致"、"仓储退出可核对"断言；不合格输入的拒绝**复用既有测试**，不重复堆砌 |

## 临时资源身份（独立命名、专用空库）

| 项 | 值 |
| --- | --- |
| 容器 | `zhku-t06-tmpdb-2013`（`zhkuagent_rulecheck_e2e`，端口 55684）／`zhku-t06-archive-2017`（`zhkuagent_run_archive_e2e`，端口 55685） |
| 镜像 | `pgvector/pgvector:0.8.6-pg16-bookworm`（本机已有，未拉取） |
| 挂载卷 | 无（一次性数据） |
| 空库证明 | 两库迁移前 `information_schema.tables`（排除系统 schema）= **0** |
| 拒绝目标 | 127.0.0.1 回环专用库；未触及 54329 与原演示库 |

凭证按 Process→User→Machine 逐次复核：`ZHKU_DB_PASSWORD` process/user 可用（machine 无），
只输出可用性，未打印口令或哈希。

## 验收项逐条结果

| 要求 | 结果 | 证据 |
| --- | --- | --- |
| 格式合格的正确口令返回主体，userId／角色／当前许可符合写入值 | **通过** | `RuleAgentReviewedIdentityTempDbTest`：`userId` 相等、`role=USER`、`authRevision=0` |
| 格式合格但内容错误的口令被拒绝（不能靠长度提前拒绝） | **通过** | 同一测试：18 位合规口令 → `empty` |
| 格式合格的未知账号被拒绝 | **通过** | 同一测试：`t06-tempdb-unknown` + 合规口令 → `empty` |
| 不合格输入被拒绝 | **通过（复用既有测试）** | `PreviewIdentityRepositoryIntegrationTest` 真实库 **8/8**；`PreviewIdentityBudgetTest` **2/2**（不合格输入被拒绝并清空且不访问数据库） |
| 仓储关闭、连接与工作线程退出可核对 | **通过** | 同一测试：`status()` → `closed=true`、`inFlight=0`、`liveThreads=0` |
| 已审核资料安装（未重复安装，与身份分开记录） | **通过** | 同一次运行内仍通过：revision=`2025.9.2`、片段正文指纹 64 位十六进制 |

命令：

```bash
# 1) 身份 + 资料安装（rulecheck 专用库，端口 55684）
./mvnw.cmd -pl knowledge '-Dtest=RuleAgentReviewedIdentityTempDbTest' -DfailIfNoTests=false \
  "-Dzhku.db.url=jdbc:postgresql://127.0.0.1:55684/zhkuagent_rulecheck_e2e" \
  "-Dzhku.t06.root=D:/alldesk/Projects/ZhkuAgent" \
  "-Dzhku.data-root=C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent" test
# → Tests run: 1, Failures: 0, Errors: 0, Skipped: 0（12.87s）

# 2) 口令边界（run_archive 专用库，端口 55685）
./mvnw.cmd -pl knowledge '-Dtest=PreviewIdentityRepositoryIntegrationTest' -DfailIfNoTests=false \
  "-Dzhku.db.url=jdbc:postgresql://127.0.0.1:55685/zhkuagent_run_archive_e2e" test
# → Tests run: 8, Failures: 0, Errors: 0, Skipped: 0（27.85s）

# 3) 无库口令边界
./mvnw.cmd -pl knowledge '-Dtest=PreviewIdentityRepositoryIntegrationTest,PreviewIdentityBudgetTest' ...
# → PreviewIdentityBudgetTest 2/2 通过；IntegrationTest 因"缺少专用临时库地址"在初始化期失败（见下）
```

**关于第 3 条命令**：`PreviewIdentityRepositoryIntegrationTest` 经 `ArchiveTestDatabase` 读取
`zhku.db.url`，未配置时抛 `缺少专用临时库地址` 并**整体失败（非跳过）**。这是该测试的既有前置要求，
不是本轮缺陷；已在第 2 条命令中用真实库跑通 8/8。

## 资源退出证据

| 项 | 结果 |
| --- | --- |
| 本次创建的容器 | 2 个，均按**名称精确确认**后 `docker rm -f`；`docker ps -a` 中 `zhku-t06*` 计数 **0** |
| 监听端口 | 55684／55685 **无 LISTENING**；仅剩客户端侧 `TIME_WAIT`（TCP 正常回收，非监听） |
| 其他容器 | 未触碰 |
| java 进程 | `java.exe` **无残留** |
| 数据库写入 | 仅上述两个临时库；原演示库 `zhkuagent-postgres` 未启动、未写入 |

## 未验证范围（明确标注）

- **SIMULATED 工程模式下的真实身份／资料 HTTP 整链**（主体与会话隔离、原文读取与资格撤回、绑定端口不被 `SERVER__PORT` 覆盖）：本轮按要求**未执行**，是下一时段唯一项。
- 既有 `RuleAgentTaskHttpTest`：仍为"尚未完整验证"（端口冲突由用户协调，本测试未修改）。
- `RuleAgentReviewedToolsConfigurationTest` 3 项中 **1 项跳过**，不得记为全部实际执行。
- 在线分支真机、中文编码、浏览器整体验收：未执行。
- T05：仍未通过，旧额度已关闭，本轮未复用、未外发。

## 提交

| 项 | 值 |
| --- | --- |
| 提交 SHA | 见返回回执 |
| 范围 | `RuleAgentReviewedIdentityTempDbTest.java` + 本 README |
| 本地操作 | 仅本地提交；不 push／PR／标签／部署／改写历史；上一轮失败证据保留未改写 |
