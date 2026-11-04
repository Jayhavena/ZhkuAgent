# 账号系统验证记录

## 环境和边界

- 项目工作区含本任务之外的既有未提交修改；本次验证不覆盖或清理这些修改。
- 真实数据库测试仅允许使用回环地址、独立临时 PostgreSQL 库 `zhkuagent_rulecheck_e2e`，不得写入原演示库。连接密码从当前进程或 Windows 用户/系统环境逐次读取，不记录值。
- 2026-11-04：项目 Docker 就绪脚本以 `-DockerOnly` 通过；运行器预检识别 Java、Maven wrapper、Docker 和专用临时库目标。预检未启动数据库，也不是认证验收。
- 用户账号持久化与 Agent 内存任务持久化是不同能力；本次不改变 Agent Runtime 或承诺重启后恢复任务。

## 执行记录

- 2026-11-04：在 `agent/frontend` 执行 `node --test --experimental-test-isolation=none tests/*.test.mjs`，200 项通过，退出码 0；输出包含 Vue Router 无匹配警告和 `WebSocket server error: Port is already in use`，此结果不等同于真实浏览器或后端联调。`npm run build` 构建通过。
- 2026-11-04：`verify-rule-check-refactor.cmd -Mode Database -Phase accounts-security-20261104 -Tests RuleCheckRegisteredAccountHttpTest,RuleCheckRememberMeHttpRestartTest,RuleCheckPersistentLoginSecurityHttpTest,RuleCheckSlidingLoginHttpTest,RuleCheckPreviewHttpTest` 使用隔离 PostgreSQL 容器执行；21 个测试全部通过、无跳过，迁移至 V23，结束时数据库连接为零、容器已退出。包括双 JVM 的持久 Cookie 恢复与注销重放、账号隔离、修改密码、滑动续期等自动化 HTTP 测试；并不等同真实浏览器关闭重开验收。运行器整体结果仍为失败：它检测到新出现的 Java 进程 61508；复核该进程为独立 IntelliJ JPS 编译服务，非测试进程；用户确认由其启动，未干预该进程。详见 `docs/evidence/rule-check-refactor/2026-10-07/accounts-security-20261104/results.json`，不得将批次整体标记通过。
- 2026-11-04：第二次隔离数据库回归 `accounts-security-20261104-r2` 运行 54 项，52 项通过、2 项失败，资源核查通过、容器退出；失败均在配置测试，分别涉及受控资料资格和显式导入清单，仍在调查。该批次整体未通过，见 `docs/evidence/rule-check-refactor/2026-10-07/accounts-security-20261104-r2/results.json`。
- 2026-11-04：新增双库重启测试后，`mvn -o -pl knowledge -am -DskipTests test-compile` 通过；仅说明代码可编译，并未执行双库测试。
- 2026-11-04：`pwsh -NoProfile -NonInteractive -File knowledge/scripts/verify-rule-check-dual-postgres.ps1` 使用两个全新独立临时 PostgreSQL 容器运行双 JVM HTTP 测试，报告 `DUAL_POSTGRES_VERIFIED tests=1`，退出码 0；验证账号只写身份库、重启后凭持久 Cookie 恢复、注销后重放失效，临时容器按创建 ID 清理。首次脚本虽产生通过的默认 Surefire 报告，但由于错误假设报告目录可通过属性覆盖，脚本报失败；修正为核对默认报告更新时间后重新执行通过。此测试不证明长期持久卷、真实浏览器关闭重开或生产部署。
- 2026-11-04：持久身份本机宿主和安全配置单元测试共 20 项通过，测试不启动真实宿主或连接数据库。日志中的 `LOGIN_RATE_LIMIT` 堆栈来自限流测试；Mockito 动态附加 agent 有未来 JDK 兼容性警告。
- 2026-11-04：补充回放入口账户导航后，前端子任务报告 Node 测试 202 项通过，默认与 replay 构建均成功；存在既有路由及 WebSocket 端口占用警告，不替代真实浏览器验收。
- 受控资料测试依赖的学生手册原件在当前环境缺失，资料检查按 fail-closed 拒绝；不能通过更改预期掩盖。另一项旧测试缺少既有 Agent 导入，已修正严格白名单，单独重跑该测试方法通过；原 54 项批次仍为失败。
- 2026-11-04：补齐受控资料原件（`SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf`，sha256 `dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`，与 `docs/product/rule-check-2025-verbatim-import-draft.json` 的 `sourceFileSha256` 一致）后，`mvn -o -pl knowledge -Dtest=RuleCheckPreviewConfigurationTest test` 13 项通过、0 跳过，第二轮批次的两项失败均已消除。`mvn -o -q -pl knowledge -am -DskipTests package` 产出 `knowledge/target/knowledge-0.1.0-rc.1-persistent.jar`，MANIFEST 的 `Start-Class` 为 `com.zhku.knowledge.controlled.PersistentRuleCheckLocalHost`，并含独立身份迁移 SQL。
- 2026-11-04：新增长期本地入口 `knowledge/scripts/start-persistent-rule-check.ps1` / `.cmd`：身份库用命名卷 `zhkuagent-rulecheck-identity-data`（Stop 只 `docker stop`，脚本无删卷路径），规则库用 `--tmpfs` 临时容器，三端口只发布到回环且互不相同。实测 Check/Start/Stop/Restart/Status 与 `cmd` 退出码透传均正常，Start 约 14 秒输出 `PERSISTENT_RULECHECK_READY backend=http://127.0.0.1:58130`。
- 2026-11-04：真实数据库重启验收（长期入口，非 tmpfs 身份库）：注册 `zhku-persist-c3` → 201 → Stop → Start → 同账号登录 200，返回同一 `userId`；身份库 `preview_identity` / `preview_persistent_login` / `flyway_schema_history` 三表存在且 V1/V2 `success=t`；仅携 `ZHKU_RULE_CHECK_REMEMBER` Cookie 在重启后 `GET /auth/me` 仍为 200；`docker rm -f` 身份库容器但保留卷后重建，旧账号仍可登录 200。
- 2026-11-04：真实浏览器端到端（Chrome 156 headless，CDP 脚本 `agent/frontend/tests/manual/persistent-login-browser.mjs`，回放构建 + 同源 Vite 代理到 58130）：`register`、`login`（保持登录默认勾选）、`verify-session`（关闭浏览器重开同一 profile）、后端 Stop/Start 后再次 `verify-session`、`logout`、`replay-after-logout` 六个阶段全部 PASS，全程 `userId` 一致（`e2e-lead-a1` / `168d0427-a10b-446d-abde-1ef31898676e`）；注销后 `/rules` 跳登录且 `/auth/me` 401，旧持久 Cookie 不能恢复身份。证据见 `tmp/e2e-a/*.json`（本地临时目录，不入库）。
- 2026-11-04：HTTP 契约补充验证：改密原密码错误 → 401 `AUTHENTICATION_FAILED`；改密成功 → 204 且另一并发会话 `/auth/me` 立即 401、旧密码登录 401、新密码登录 200；缺 CSRF 头的注册 → 403；重复用户名 → 409；`remember-me=true` 的 Cookie 为 `Max-Age=2592000; HttpOnly; SameSite=Strict` 且 Path 限定在 `/api/zhku/controlled-preview/v2`；`remember-me=false` 下发 `Max-Age=0`。
- 本轮修复的界面缺陷：账号页挂载时身份尚未恢复会导致详情永远停在“未提供”，`agent/frontend/src/views/RuleAccount.vue` 在身份后到时补拉一次账号详情；前端全量测试 202 项通过、replay 构建通过。
- 2026-11-04：以一次性临时容器（`pgvector/pgvector:0.8.6-pg16-bookworm`，`--tmpfs`、回环随机端口、随机口令、结束后按容器 ID 删除）直接运行账号相关真实数据库批次：`RuleCheckRegisteredAccountHttpTest`(3)、`RuleCheckRememberMeHttpRestartTest`(1)、`RuleCheckPersistentLoginSecurityHttpTest`(5)、`RuleCheckSlidingLoginHttpTest`(2)、`RuleCheckPreviewHttpTest`(12)，共 23 项全部通过，`BUILD SUCCESS`，失败/错误/跳过均为 0。之所以不再走 `verify-rule-check-refactor` 批次：该脚本会把用户自启的 IntelliJ JPS 进程误判为并发测试进程而整体判失败，本次改用等价隔离条件后单列记录，不把整体批次标为通过。
- 仍未验证或未实现：登录限流 429 路径未做实机触发（进程内全局限流会锁住整分钟）、生产 TLS 下的 `Secure` Cookie 与反向代理部署、30 天到期与滑动续期的长周期实测、持久化校园 Agent 任务/归档的身份分库（规则库侧 `V19`/`V20`/`V21` 外键仍指向规则库身份表，当前分库只覆盖查证账号与持久登录）、多实例共享限流与审计。受控回放不是在线模型服务，不能表述为生产可用。
