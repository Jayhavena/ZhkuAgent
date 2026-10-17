# T06 专用临时库：资料安装工程局部通过；真实身份登录**未通过**（未用替身掩盖）

## 身份与执行范围

| 项 | 值 |
| --- | --- |
| worktree / 分支 | `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` ／ `codex/t06-controlled-launch` |
| HEAD | `768ff1ae89a17835b2930654bbbb2511da40b4a5`（工作区/暂存区开始时干净） |
| 成套提交 | `75274a1` 与 `7d8b5e4` 均在 HEAD 历史内；`autowireCandidate` 出现次数 **0**（缺陷未残留） |
| 共享合同 | v3，未改；T05 核心源码、既有 HTTP 测试、前端、样例均未触碰 |
| 执行模式 | 专用临时库真实迁移 + 已审核资料安装 + 正式身份仓储校验；**供应商调用 0、模型密钥未读取** |
| 资料根 | `D:/alldesk/Projects/ZhkuAgent`（主工作目录，**只读**资料源，不加载其代码） |

## 临时库身份（独立命名、无既有数据）

| 项 | 值 |
| --- | --- |
| 容器名 | `zhku-t06-tmpdb-*`（本次四个：`10141934 / 101439 / 101441 / 101443`，前三个为诊断轮，均已移除） |
| 镜像 | `pgvector/pgvector:0.8.6-pg16-bookworm`（本机已有，未拉取新镜像） |
| 库名 / 用户 | `zhkuagent_rulecheck_e2e` ／ `zhkuagent`（沿用受控组件内置的库名校验） |
| 端口 | 127.0.0.1 `55680→55681→55682→55683`（每轮重建，避开 54329 与原演示库） |
| 挂载卷 | **无**（容器整体为一次性数据） |
| 空库证明 | 每次迁移前 `information_schema.tables`（排除系统 schema）计数 = **0** |

凭证按 Process→User→Machine 逐次复核：`ZHKU_DB_PASSWORD` process/user 均可用（machine 无），
只输出可用性、未打印值；容器口令与测试口令均取自该环境变量，未落盘。
`DASHSCOPE_API_KEY` **未读取**（本轮无在线路径）。

## 实际结果

### 通过（有实际证据）

1. **空库 → 迁移 → 已审核资料安装**：`Reviewed2025ControlledCatalogue.installOnIsolatedDatabase`
   成功执行；安装期间断言 `knowledge_chunk > 0`、`settings().referenceRevision()=="2025.9.2"`、
   首个片段 `content_checksum` 为 64 位十六进制（实测值形如 `056217B8…FFEB2`，**大写**）均通过。
2. **批准指纹校验确实生效**：以 **worktree** 作资料根时被组件拒绝（`B03批准指纹变化`）；
   改用已核对的只读资料根后通过。这既是"安装成功"的前提，也是指纹未被放宽的反证。
3. **安装作用域受约束**：组件 `close()` 会主动卸载本次安装的数据（`knowledge_source/knowledge_chunk` 归零），
   说明安装是**任务级作用域**而非长期发布；实测 `public_tables=26` 为迁移结果，与安装数据分开。
4. 迁移一致性：`public` schema 建出 26 张表（V1–V22 全量执行），无失败。

### 未通过（真实卡点，未用替身掩盖）

**真实身份仓储登录返回空**：`preview_identity` 行已实际落库（实测计数 **1**，口令为符合
`^\$2[aby]\$(10|11|12)\$…` 约束的 BCrypt 哈希、`enabled=true`），但
`PreviewIdentityRepository.authenticate("t06-tempdb-user", 正确口令)` 返回 `Optional.empty()`。
错误口令/未知账号同样为空，因此**无法区分"正确口令被误拒"与"仓库未命中"**。

- 未定位根因。单测耗时 16.7 秒，与仓库内"有界等待/连接初始化"特征吻合，疑与等待超时或初始化相关，
  但**本轮只有现象、没有结论**，下一步需单独定位（不得据此改设计）。
- 未用测试替身替代：既有 `RuleAgentTaskHttpTest` 用 Mockito 假身份仓储，本项**没有**走替身，所以这里必须记未通过。

### 本轮未执行（时段内主动停止，非技术结论）

- **HTTP 层（SIMULATED 工程模式下的身份登录、任务归属隔离、原文读取与资格撤回）**：未执行。
  理由：身份仓储登录未通过是它的前置；且在 20 分钟上限内先止损，不做连续扩展。
- 审计与原文资格撤回写入：未执行。
- 启动故障／HTTP 故障／模拟业务失败／资料缺失的区分矩阵：本轮的装配级失败矩阵已在
  `launch-compatibility-01` 覆盖，但**未在真实库上下文重跑**。

## 实际命令

```bash
# 空库证明（每次迁移前）
docker exec -e PGPASSWORD="$ZHKU_DB_PASSWORD" <容器> psql -U zhkuagent -d zhkuagent_rulecheck_e2e -At \
  -c "select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')"
# → 0

# 迁移 + 已审核资料安装 + 正式身份仓储校验
./mvnw.cmd -pl knowledge '-Dtest=RuleAgentReviewedIdentityTempDbTest' -DfailIfNoTests=false \
  "-Dzhku.db.url=jdbc:postgresql://127.0.0.1:55683/zhkuagent_rulecheck_e2e" \
  "-Dzhku.t06.root=D:/alldesk/Projects/ZhkuAgent" \
  "-Dzhku.data-root=C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent" test
# → Tests run: 1, Failures: 1（安装断言全通过，失败在真实身份登录）
```

## 新增非 Web 装配检查与实际库测试分开统计

| 类别 | 类 | 结果 |
| --- | --- | --- |
| 非 Web 装配检查（上一轮） | `RuleAgentLaunchCompatibilityTest` / `RuleAgentControlledLaunchTest` | 8 + 6 通过 |
| **实际数据库测试（本轮）** | `RuleAgentReviewedIdentityTempDbTest` | 1 运行 / **1 失败（真实身份登录）** |
| 既有 HTTP 测试 | `RuleAgentTaskHttpTest` | **未运行**（宿主端口冲突问题由用户协调，未修改该测试） |
| ReviewedTools 检查 | `RuleAgentReviewedToolsConfigurationTest` | 3 项中 **1 项跳过**，不得记为全部实际执行 |

## 资源退出证据

| 项 | 结果 |
| --- | --- |
| 本次创建的容器 | 4 个，均按**名称精确确认身份后**逐个移除；`docker ps -a --filter name=zhku-t06-tmpdb` 计数 **0** |
| 监听端口 | 127.0.0.1:55680/55681/55682/55683 监听数 **0**（已退出） |
| 其他容器 | 未触碰（`zhkuagent-postgres` 仍 Exited、其他项目容器清单与清理前一致） |
| java 进程 | `tasklist` 查 `java.exe` **无残留** |
| 未按进程名批量终止 | 遵守；仅 `docker rm -f <精确容器名>` |
| 数据库写入范围 | 仅本轮临时库；原演示库 `zhkuagent-postgres` 未启动、未写入 |

## 未验证范围（明确标注）

- 真实身份登录（未通过，见上）；不同主体／会话的相互隔离；原文资格撤回后的拒绝。
- SIMULATED 工程模式下的 HTTP 端到端、实际绑定地址/端口未被 `SERVER__PORT` 覆盖的验证。
- 资料版本／正文指纹／引用跨度的**跨进程读回复核**（本轮仅在同一测试内核对，且被 `close()` 卸载）。
- 在线分支真机、中文编码与普通用户终端启动、浏览器整体验收。
- T05：仍未通过，旧额度已关闭，本轮未复用、亦未外发。

## 提交

| 项 | 值 |
| --- | --- |
| 提交 SHA | 见返回回执 |
| 范围 | `RuleAgentReviewedIdentityTempDbTest.java` + 本 README |
| 本地操作 | 仅本地提交；不 push／PR／标签／部署／改写历史；不合并其他 Agent 分支 |
