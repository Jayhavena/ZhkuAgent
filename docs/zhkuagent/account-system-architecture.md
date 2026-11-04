# 账号系统：现状、设计与验收边界

## M1：改造前现状（静态源码审查）

- 查证服务是 `zhku-rule-check-preview` 受控本机预览，而非现成的公网生产服务。其安全守卫强制独立 profile、本机绑定、独立 HttpOnly/Strict Cookie、15 分钟空闲期及非持久会话；不能直接把当前配置视为生产配置。
- `preview_identity` 已在 V19 建表，以服务端 UUID 为主键、用户名数据库唯一，保存 BCrypt、角色、启用状态与身份版本。`PreviewIdentityRepository` 在未知账号时也计算 dummy hash，登录及后续请求验证数据库当前角色、启用状态和版本。身份记录持久，但现有容器 Session 不持久，当前没有公开注册流程。
- Spring Security 使用表单登录和 CSRF，登录变更 Session ID；默认拒绝未授权路径，USER 和 MAINTAINER 互不继承。现有频控为进程级全局每分钟 20 次，非持久且非用户独立限流。
- `/auth/csrf` 返回会话关联的 CSRF token；前端仅在内存保存 token，以同源 Cookie 请求。身份变化时前端清除结果并通过 BroadcastChannel 通知其他窗口，但广播不能作为授权或可靠跨窗口事务锁。
- 任务 owner 是 `(稳定 userId, sessionNonce)`，幂等键、查询和取消关联此 owner。新的登录会清原 owner；同一账号不同 Session 不能互相取得任务。现有任务存储为内存，后端重启不会恢复任务；本次不能改变 Agent Runtime 语义或承诺任务恢复。
- 澄清续答使用独立于 CSRF 的每 Session nonce，五分钟过期并一次消费；迟到请求不能清理较新轮次。必须继续保持原有行为。
- V19/V20 的历史匿名记录保持 NULL，不能自动认领；迁移必须追加，不能改已运行的迁移或破坏已有身份/任务。

## M2：设计约束

1. 在已有身份模型上增加正式注册与账户元数据，数据库唯一性为最终保证，服务端生成 UUID，普通注册只创建 USER。密码必须限长、校验且用 BCrypt 保存；不向日志或前端返回密码和 hash。
2. 会话凭据必须具有足够随机性，数据库仅保存凭据的不可逆摘要；会话有效期和账号生命周期分离。正常登出撤销当前会话并删除 Cookie，密码更新使其他会话失效。默认保持登录 30 天并滑动续期，不保持登录时浏览器关闭后不恢复。
3. 不绕过 CSRF、角色检查、现有身份版本检查与登录后 Session fixation 防护；认证主体必须来自服务端，任务归属继续使用原有 userId 和独立 nonce。浏览器不应将密码、认证凭据存入 localStorage。
4. Cookie 的 Secure 属性必须区分本机 HTTP 与生产 HTTPS；受控预览的本机安全约束不能被描述成已经具备公网部署能力。
5. 以现有 v2 `/api/zhku/controlled-preview/v2/auth` 为兼容接口，设计注册、登录、注销、me、修改密码。注册和修改密码为 JSON，登录沿用表单；所有写操作接受 CSRF 检查。前端在受保护路由恢复身份，登录成功回到安全的同源原路径。
6. 真实 PostgreSQL 验证只使用专用临时测试库，不对既有演示库执行写入。自动化替身或静态测试不能冒充浏览器、后端重启或真实数据库验收。

## 长期运行入口

原 `knowledge/scripts/start-controlled-rule-check.cmd` 启动一次性数据库容器，退出即销毁，不能用于正式账号的长期保存；因此不改它，而是新增独立入口 `knowledge/scripts/start-persistent-rule-check.ps1`（`.cmd` 仅作透传包装）。

- 身份库用 Docker 命名卷 `zhkuagent-rulecheck-identity-data` 持久保存，Stop 只 `docker stop` 容器，脚本中不存在 `docker rm` 身份库或删卷的代码路径；规则库仍是 `--tmpfs` 临时容器，Stop 时按容器名回收。两者端口只发布到 `127.0.0.1`，且与宿主端口互不相同，54329 的共享演示库被显式拒绝。
- 宿主是 `knowledge/target/knowledge-0.1.0-rc.1-persistent.jar`（`Start-Class` 为 `com.zhku.knowledge.controlled.PersistentRuleCheckLocalHost`）。宿主靠 stdin 的 `STOP` 优雅关闭，遇 EOF 会自行退出，所以 Start 派生一个隐藏的监管进程持有 stdin；Stop 通过 `stop.request` 通知监管进程写 `STOP`。
- 随机身份库凭据只经 `$env:POSTGRES_PASSWORD` 传给 docker 并写入 `tmp/rule-check-identity/state.json`（`tmp` 已被 `.gitignore` 忽略），不出现在命令行、日志或仓库中。
- 受控资料仍由规则临时库每次重建，受控回放不是在线模型服务；这个入口只解决"账号与登录会话跨重启存活"，不改变 Agent Runtime 语义。

## 当前实现（仍在验证）

- 复用 `preview_identity`，数据库约束保证用户名唯一；新注册仅创建 USER，密码以 BCrypt 哈希保存，账号 ID 由服务端生成。V23 增加持久登录记录；分库模式另有专用身份迁移，不复制规则业务数据。历史匿名任务不会认领给新用户。
- 接口前缀为 `/api/zhku/controlled-preview/v2/auth`：`GET /csrf` 取得 CSRF；`POST /register` 接受 `username`、`password`、`confirmPassword`，成功后引导登录；`POST /login` 沿用表单字段，`remember-me=true` 启用持久登录；`GET /me` 提供 userId、username、accountStatus、createdAt 等；`GET /account` 查看账号；`PUT /password` 校验原密码及确认密码，变更后要求重新登录；`POST /logout` 注销。写操作均需 CSRF。
- 保持登录 Cookie 为 32 字节随机凭据，库内仅存 SHA-256 摘要；恢复时校验身份状态及版本。有效期 30 天，活跃请求在剩余不超过 29 天时原子续期并刷新 Cookie。注销撤销绑定摘要；密码修改更新身份版本并撤销长期凭据。稳定凭据避免并发轮换导致的掉线与注销竞态，但凭据遭窃后在撤销或过期前可被重放，这是当前本地预览的风险，不应作为生产安全结论。
- 前端 `/login`、`/register`、`/account` 提供账号流程，访问 `/rules` 前恢复身份并在成功登录后返回经校验的原路径。认证 Cookie 不存入 localStorage。既有 `(userId, sessionNonce)` 归属和 CSRF 不应被浏览器广播取代。
- 分库模式将身份表及持久令牌放入指定独立 PostgreSQL，规则库仍维持现有临时库与资料审核约束。长期本机入口已用命名卷实测：注册 → Stop → Start → 同账号登录同一 `userId`，仅带持久 Cookie 也能恢复；删除身份库容器但保留卷后重建，旧账号仍可登录。原规则库的校园归档/会话表存在指向该库 `preview_identity` 的外键，分库不能直接满足这些表的身份关联；目前受控回放不应被描述为已覆盖持久化校园 Agent 归档或完整生产业务。

## 验证状态（截至 2026-11-04）

- 已验证：注册/登录/保持登录默认勾选/账号页/注销/注销后旧会话失效，六阶段真实 Chrome（headless + CDP）全通过；后端 Stop/Start 后浏览器仍恢复同一身份；HTTP 层改密撤销其他会话、缺 CSRF 403、重复用户名 409、`Max-Age=2592000`。
- 已验证（自动化）：双 JVM 重启恢复、注销重放 401、两用户续答隔离、滑动续期、身份分库隔离；前端 202 项测试与 replay/默认构建通过。
- 未验证：429 限流实机路径、生产 TLS/Secure Cookie、30 天到期与长周期滑动、分库下的校园任务归档、多实例限流与审计。

## 待完成确认

具体测试结果见验证记录；跨窗口广播不保证网络响应顺序，需特别验证认证切换时旧响应不能使身份或结果混淆。频控当前为单进程全局预算，生产多实例限流、TLS 和部署边界尚未设计验收。
