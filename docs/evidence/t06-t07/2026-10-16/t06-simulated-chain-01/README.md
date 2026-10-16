# T06 SIMULATED 整链：真实身份 + 真实已审核资料 + 真实回环 HTTP（本轮通过）

> 时间 2026-10-16。对应交接文档 [T06-HANDOFF.md](../t06-handoff/T06-HANDOFF.md) 第 3 节第 1 项
> 「SIMULATED 工程模式下的真实身份／资料 HTTP 整链」——**该项本轮完成并通过**；第 3 节其余项未动。
> 全部为本地提交，未 push／PR／标签／部署／合并。

## 0. 一句话状态

**整链通过（1 项测试，10.42 秒，0 失败 0 跳过）**：真实临时库身份登录 → CSRF → 受理 → 等待澄清 →
续答 → 真实资料证据 → 原文核对 → 身份失效 → 退出，全部走真实回环 HTTP；**决策段仍是宿主显式提供的
模拟执行器（模型尝试 0、供应商调用 0）**，不得读作在线可用。

## 1. 身份与执行范围

| 项 | 值 |
| --- | --- |
| worktree / 分支 | `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` ／ `codex/t06-controlled-launch` |
| 本轮基类 | `9eb9015`（交接文档提交）；本轮新增测试后提交见文末 |
| 装配 | `RuleAgentHttpConfiguration` + `RuleAgentControlledLaunchConfiguration(SIMULATED)` + `RuleAgentReviewedToolsConfiguration` + `RuleCheckPreviewSecurityConfiguration` |
| 新增文件 | `knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentSimulatedChainTempDbHttpTest.java`（**仅测试，未改主源码**） |
| 共享合同 | v3 未改；`RuleAgentOnlineHttpConfiguration.java` 与基线零差异（在线工厂未被本轮触碰） |
| 资料根 | `D:/alldesk/Projects/ZhkuAgent`（主工作目录，只读；`-Dzhku.t06.root` 指定） |
| 检索词来源 | 取自已审核绑定正文（`catalogue.reviewedCatalogue(root)` 的第一个 `evidenceBindings().quote()`），**不是合成文本** |
| 外发 | 供应商调用 0、模型密钥未读取 |

## 2. 临时库（一次性，每轮重建）

| 项 | 值 |
| --- | --- |
| 容器（本轮三个） | `zhku-t06-tmpdb-10162100`(55690) → `10163400`(55691) → `10164100`(55692) |
| 镜像 | `pgvector/pgvector:0.8.6-pg16-bookworm`（本机已有，未拉新镜像） |
| 库名 / 用户 | `zhkuagent_rulecheck_e2e` ／ `zhkuagent` |
| 空库证明 | 每轮迁移前 `information_schema.tables`（排除系统 schema）计数 = 0 |
| 迁移 | `ZhkuFlywayTestSupport.migrate`，18 个迁移 applied，至 v22 |

## 3. 实际通过项（逐条对应测试断言）

| # | 能力 | 实际观察 |
| --- | --- | --- |
| 1 | 匿名访问 | `GET /tasks/capabilities` → **401** `AUTHENTICATION_REQUIRED` |
| 2 | 真实身份：错误口令 | 登录 → **401**（正式仓储 `PreviewIdentityRepository`，非替身） |
| 3 | 真实身份：正确口令 | 登录 → 200；`capabilities` → **200** |
| 4 | 角色边界 | `MAINTAINER` → **403** `ACCESS_DENIED`（不继承 USER） |
| 5 | CSRF | 不带令牌 `POST /tasks` → **403**，且按 `clientRequestId` 查询 → **404**（未产生任务） |
| 6 | 受理 | 带令牌 `POST /tasks` → **202**，`Cache-Control: no-store` |
| 7 | 幂等 | 同键重复受理 → `duplicate=true`，**执行次数未增加**；同键换问题 → **409** `TASK_CONFLICT` |
| 8 | 跨主体隔离 | 另一账号读任务 / 查请求键 → **404** `TASK_NOT_AVAILABLE` |
| 9 | ASK 澄清 | 首轮执行后 `taskStatus=WAITING_USER`（带 `continuationToken`） |
| 10 | 旧版本 | `expectedVersion=当前-1` 续答 → **409** |
| 11 | 续答 | 正确版本续答 → **202**；执行后 `RESOLVED` |
| 12 | 真实资料证据 | 结果 `evidence` **非空**，来自已审核片段（revision `2025.9.2`） |
| 13 | 原文 | 跨主体 → 404；随机令牌 → 404；本人 → **200** 且正文与证据 quote 一致 |
| 14 | 身份失效 | 退出后重新登录 → 原任务 **404**（归属随会话） |
| 15 | 端口未被注入覆盖 | 本机 `SERVER__PORT=53359`（已被占用）；本测试用系统属性固定随机端口，实际绑定端口 ≠ 53359 |
| 16 | 退出 | 上下文关闭后端口不可连接、`activeJobs=0`、执行队列空 |

原始交换（17 条，含方法/路径/状态/实际响应）见 [`simulated-chain-tempdb.json`](simulated-chain-tempdb.json)，
构建日志见 [`maven.log`](maven.log)。

## 4. 本轮失败轨迹（原样保留，不改写为通过）

| 轮次 | 现象 | 根因 | 处理 |
| --- | --- | --- | --- |
| 1 | 编译失败 `RuleAgentSimulatedChainTempDbHttpTest.java:270` | lambda 引用非 effectively final 的 `port` | 增加 `boundPort` 副本 |
| 2 | `临时库必须为空 expected 0 but was 26` | **容器未重建**：迁移后不再是空库，空库断言必然失败 | 每轮 `docker rm -f` 后新建容器（这本身印证"临时库必须一次性"） |
| 3 | 任务 `FAILED`（期望 `WAITING_USER`），内部异常 `IllegalArgumentException: 非法规则查证合同`，栈顶 `Clarification.<init>` | 合同要求澄清的 `allowedValues` 为 **2–32** 个，夹具只给了 1 个 | 改为两个可取值；顺带发现既有 `RuleAgentControlledLaunchTest` 中同样写法的单值澄清**从未被执行**（其用例未触发 waiting 分支），本次才暴露 |

> 第 3 轮异常能被看见，靠的是测试内显式捕获模拟执行器内部异常并打印——否则所有执行期异常都会被
> `RuleAgentTaskStore` 统一记成 `FAILED`，掩盖真实卡点。

## 5. 三层分开记账（不可互相替代）

| 层 | 本轮状态 |
| --- | --- |
| 真实数据库身份 + 真实已审核资料安装 + 真实回环 HTTP | **通过**（本轮范围） |
| 决策段（模型） | **工程模拟**：宿主显式 `ruleAgentSimulatedExecutor`，`Mode=ENGINEERING_REPLAY`、模型尝试 0，不得读作在线 |
| 真正在线链路 | **未执行**（本轮无授权需求；在线装配未被本轮启用） |

## 6. 实际命令

```bash
# 每轮：专用一次性临时库（端口避开 54329 与原演示库）
docker run -d --name zhku-t06-tmpdb-<ts> -e POSTGRES_USER=zhkuagent -e POSTGRES_PASSWORD="$ZHKU_DB_PASSWORD" \
  -e POSTGRES_DB=zhkuagent_rulecheck_e2e -p 127.0.0.1:55692:5432 pgvector/pgvector:0.8.6-pg16-bookworm
docker exec -e PGPASSWORD="$ZHKU_DB_PASSWORD" zhku-t06-tmpdb-<ts> psql -U zhkuagent -d zhkuagent_rulecheck_e2e \
  -At -c "select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')"   # → 0

# 整链验收
./mvnw.cmd -pl knowledge -Dtest=RuleAgentSimulatedChainTempDbHttpTest -DfailIfNoTests=false \
  "-Dzhku.db.url=jdbc:postgresql://127.0.0.1:55692/zhkuagent_rulecheck_e2e" \
  "-Dzhku.t06.root=D:/alldesk/Projects/ZhkuAgent" \
  "-Dzhku.rulecheck.evidenceDir=<本目录绝对路径>" test
# → Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.42 s / BUILD SUCCESS
```

## 7. 资源退出核对

| 项 | 结果 |
| --- | --- |
| 本轮创建容器 | 3 个，**按名称精确确认后**逐个 `docker rm -f`；`docker ps -a --filter name=zhku-t06-tmpdb` 计数 **0** |
| 监听端口 | 55690/55691/55692 监听数 **0** |
| java 进程 | `tasklist /FI "IMAGENAME eq java.exe"` 无残留（**未按进程名批量终止**，本机会话并发 mvn 会误伤） |
| 其他容器 | `zhkuagent-postgres`（Exited）、`auragate-postgres` 等未触碰 |
| 写入范围 | 仅本轮一次性临时库；原演示库未启动、未写入 |

## 8. 未验证范围（明确标注，不隐含通过）

- **代理转发**：本机无 nginx（`command -v nginx` 未找到），仓库 `agent/frontend/nginx.conf` 依赖容器内
  `host.docker.internal` 与 `ZHKU_CONTROLLED_BACKEND_PORT`；**本轮未做代理实际响应归档**，计划要求的
  "不能仅用 mock 控制器证明代理正确"仍未满足。
- 取消路径（`/cancel`）在真实库上下文未跑；容量/并发、跨进程读回资料指纹、多实例与重启（源码明确不支持）。
- 既有 `RuleAgentTaskHttpTest` **仍未运行**：本机 `SERVER__PORT=53359` 已被占用，该测试用
  `setDefaultProperties` 设置随机端口，优先级低于环境变量；**未修改该测试**，端口由用户协调。
- 在线分支真机、中文编码与普通用户终端启动脚本、浏览器整体验收。
- 本轮"通过"仅限 SIMULATED 工程模式；不构成产品质量、可靠性或学生收益结论（属 T08/T09/T10）。

## 9. 提交

| 项 | 值 |
| --- | --- |
| 范围 | `RuleAgentSimulatedChainTempDbHttpTest.java` + 本目录（`README.md` / `maven.log` / `simulated-chain-tempdb.json`） |
| 本地操作 | 仅本地提交；不 push／PR／标签／部署／改写历史 |
