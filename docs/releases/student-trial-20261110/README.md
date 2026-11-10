# 学生试用候选 2026-11-10

状态：**受控工程试用候选（CONTROLLED_ENGINEERING_TRIAL_CANDIDATE）**
版本：`0.1.0-rc.1`
发布分支：`release/g2-student-trial-20261110`
代码基础：`0a08ebffd7a5dfb0ca1642fb318ce3e712ad9210`
验收证据：`a35be4b9ef3d10e0052b4726d64546ab2079ab3e`
审查 HEAD：`96e6fb09`（G1 签核合并；代码树不变）

本候选把 G1 的规则发布语义修复与 G2 的账号隔离、撤销资格、刷新恢复修复合成一个可审查的树。它是**本地受控工程候选**，不是生产部署，也不是真实模型质量或全校学生准入的证明。

## 1. 身份与范围

| 项目 | 值 |
| --- | --- |
| 仓库 | `https://github.com/Jayhavena/ZhkuAgent` |
| 代码提交 | `0a08ebff`（G1 `94a89aa3` + 5 个 G2 提交） |
| G1 签核 | `ba3efc54`（10/10 文件逐字节一致；128 项离线测试） |
| G2 证据 | `a35be4b9`（1 项租约宿主 + 12 组浏览器检查 + 11 项账号安全） |
| 前端 | Vue 3 / Vite 4，版本 `0.1.0-rc.1` |
| 后端 | Java 21 / Spring Boot，版本 `0.1.0-rc.1` |
| 数据库 | PostgreSQL 16 + pgvector 0.8.6，Flyway schema `23` |
| 模型 | G2 为工程回放，**0 次供应商调用** |

不包含：真实模型决策、真实学生账号、学校 SSO、生产 HTTPS、对外邀请、真实账单结算。

## 2. 快速启动（本地受控试用）

前提：Windows 用户终端、Java 21、仓库 Maven wrapper、Node/npm、Docker Desktop、本地已缓存 `pgvector/pgvector:0.8.6-pg16-bookworm` 与 Nginx 镜像。数据库凭据按 Process → User → Machine 解析；不要把密码写入命令行或日志。

```powershell
# 1) 只确保 Docker 引擎就绪，不碰共享演示库
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly

# 2) 启动持久化本地规则查证宿主（专用身份库，不是共享演示库）
.\knowledge\scripts\start-persistent-rule-check.cmd

# 3) 另开终端，启动学生页面（后端端口以宿主输出为准，默认 58130）
.\agent\frontend\start-nginx-preview.cmd -BackendPort 58130 -WebPort 58095 -ProjectName zhku-student-trial-local -EntryMode student
```

浏览器访问 `http://127.0.0.1:58095/rules`。注册入口为 `/register`，账号页为 `/account`；这是本应用本地 USER 身份，不是学校 SSO、学籍或邮箱认证。

停止：

```powershell
.\agent\frontend\start-nginx-preview.cmd -Action Stop -ProjectName zhku-student-trial-local
.\knowledge\scripts\start-persistent-rule-check.cmd -Action Stop
```

只停止本次启动的容器/进程；不要执行 `docker compose down -v`，不要删除共享数据卷或重置密码。

## 3. 数据库与迁移

- 镜像：`pgvector/pgvector:0.8.6-pg16-bookworm`。
- 初始化 SQL：`knowledge/infra/init/001-enable-pgvector.sql`（仅首次初始化执行）。
- Flyway 迁移：`knowledge/src/main/resources/db/migration/`，当前 schema 版本 `23`，目录内 19 个迁移文件（版本号有跳号，不是 23 个文件）。
- 验收使用一次性隔离库 `zhkuagent_rulecheck_e2e`（回环地址、专用端口）；不写共享演示库，不挂载数据卷。
- 持久化试用宿主使用专用身份库；Stop 只停止容器，不删除数据卷。数据库初始化/迁移失败时先停，不要用空库或旧卷冒充成功。

## 4. 账号与权限

- 注册：用户名/密码/确认密码；密码 BCrypt 存储；注册只创建 USER。
- 登录：会话 Cookie + CSRF；`remember-me=true` 使用 30 天持久登录 Cookie（HttpOnly、SameSite=Strict、Path 限定）。
- 账号页：查看账号、修改密码；修改后其他会话失效。
- 角色：USER / MAINTAINER；G2 学生页只使用 USER。
- 隔离：任务按 `(userId, sessionNonce)` 归属；跨账号 snapshot/original/cancel/message 均拒绝。
- 刷新恢复：同标签页最近 5 条定位元数据，约 15 分钟；只 GET 当前账号可访问的快照，不重放 POST；退出/换账号清缓存。
- 验收：11 项账号安全 HTTP 回归通过（注册 3、持久登录 5、记住我重启 1、滑动登录 2）。该矩阵使用合成夹具，与已审核资料浏览器矩阵分开。

未验证：真实 429 限流路径、TLS 下 `Secure` Cookie、反向代理识别、30 天到期/长期吊销、学校 SSO。

## 5. Agent 与评测限制

- G2 学生页矩阵：真实临时 PostgreSQL、真实 Spring HTTP、Nginx 学生页、只读已审核资料；模型决策显式模拟（`SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS`），`providerCalls=0`。
- G3/G4 P02：真实 `qwen3.8-flash`，8 次调用，2 PASS / 0 FAIL / 0 NOT_EVALUABLE；证据在 PR #2（`86771a7c`），**不在本分支**。
- 实际人民币账单：`UNVERIFIED`；12 元是历史授权上限，不是消费证明。
- 真实模型质量、全量规则语义、在线学生 E2E、生产发布均未验证。

## 6. 证据与审查

| 文档 | 内容 |
| --- | --- |
| `docs/evidence/g2-student-acceptance-20261110/README.md` | G2 最终验收结论、12 组检查、边界 |
| `docs/evidence/g2-student-acceptance-20261110/acceptance.json` | 机器记录：提交、资产、类、原始证据摘要 |
| `docs/evidence/g2-student-acceptance-20261110/REVIEW.md` | G2 代码/测试/证据独立审查 |
| `docs/evidence/g1-formal-review-20261110/SIGNOFF.md` | G1 独立签核回执 |
| `docs/evidence/release-candidate-20261110/CANDIDATE-GRAPH.md` | 候选谱系、差异表、冲突与依赖 |
| `docs/manual/student-trial-candidate-20261110.md` | 零模型浏览器验收的完整复跑手册 |

发布清单：`docs/releases/student-trial-20261110/release-manifest.json`。

## 7. 故障排查

| 现象 | 处理 |
| --- | --- |
| Docker 未就绪 | 先运行 `ensure-zhku-postgres.cmd -CheckOnly`；只在配置漂移时用 `-RepairConfiguration`，不删除卷 |
| 端口占用 | 换专用端口；不要停止已有服务让位 |
| 401 | 重新登录；不要复用旧任务 Token |
| 403 / CSRF | 重新获取 CSRF；检查同源 Cookie |
| 404 / 409 | 任务或依据已过期/撤销；撤回旧结果，新建查证；不要自动重发 POST |
| 503 / 网络未知 | 保留手动查询入口；不要伪造成功或自动重放 |
| 资料/PDF 指纹不匹配 | 立即停止；不得跳过指纹或分享资格检查 |
| 刷新后看不到旧任务 | 仅同标签页约 15 分钟；退出/换账号/服务器重启/跨设备不支持 |
| 中文乱码 | 在启动前把当前会话 `Console.InputEncoding`、`Console.OutputEncoding`、`OutputEncoding` 设为 UTF-8；不要改系统语言 |
| 真实模型调用失败 | 本候选不需要供应商密钥；真实试用必须单独授权、预算与在线宿主 |

## 8. 准入结论

- **受控本地工程试用：GO**，条件是 G1/G2 PR 审查完成、使用隔离数据库、仅回环访问、不接真实模型。
- **真实学生在线试用：NO-GO**，直到完成在线宿主、HTTPS/安全 Cookie、邀请范围、供应商预算与账单核验、真实模型质量评估。
- **生产发布：NO-GO**；本候选不构成发布批准。

最终 head 以 G2 PR 的 head 或 `git rev-parse release/g2-student-trial-20261110` 为准；文件不能包含自身提交 SHA。
