# 给另一个 Agent 的协作说明（可直接整段转发）

---

你好，我们（两个 AI Agent）在同一台机器上协作开发 **仲园 Agent（ZhkuAgent）** 校园 Agent 项目。代码只在本地，不推送远程。请先读完本说明再动手，**未读完不要写代码**。

## 一、你的工作环境

- 仓库：`D:\alldesk\Projects\ZhkuAgent`（Git，当前主线 `codex/zhkuagent-modules`）
- 你的专属工作区（worktree）：`D:\alldesk\Projects\ZhkuAgent-lineB`
- 你的分支：`codex/campus-line-b`
- 创建命令（若尚未创建，在仓库根目录 PowerShell 执行）：

```powershell
Set-Location D:\alldesk\Projects\ZhkuAgent
git worktree add -b codex/campus-line-b D:\alldesk\Projects\ZhkuAgent-lineB
```

- 之后你的所有操作都在 `D:\alldesk\Projects\ZhkuAgent-lineB` 里进行，**不要进入 `D:\alldesk\Projects\ZhkuAgent` 主工作区操作**。

## 二、先读的三份文档（都在仓库里）

1. `docs/zhkuagent/仲园Agent-校园Agent功能规划与能力清单.md` — 项目现状与整体规划
2. `docs/plans/2026-09-16-并行协作操作手册.md` — 分工与协作规则（最重要）
3. `docs/plans/agent-sync/SYNC-STATUS.md` — 同步状态板，你每次开工/收工/合并前后都要更新

## 三、你负责的范围：线 B「工具 + 编排 + 体验」

交付内容（对应规划文档 P0/P1 中的线 B 项）：

1. 校园工具集 + Function Calling：学分计算、选课冲突检测等**只读或纯计算**工具（不代办、不写校务系统）
2. 统一路由编排器落地：澄清 → 当期通知 → 高风险规则 → 检索增强 → 拒答/人工
3. 会话持久化（将 `ConversationStore` 从内存实现换成数据库实现）
4. 流式输出（SSE）接入校园问答
5. 前端答案卡升级

**你可以自由新建/修改的目录：** `knowledge/src/main/java/com/zhku/knowledge/tools/`（新包）、`knowledge/src/main/resources/zhku/local-preview/` 页面资源。

**你的 Flyway 迁移号段：`V12`–`V14`**（线 A 用 V9–V11，不要占用）。

## 四、你不能直接改的文件（要改先在 SYNC-STATUS「待协商事项」登记并等我确认）

- `GroundedAnswer.java` / `GroundedCitation.java` / `GroundedAnswerService.java` / `PublishedKnowledgeSearcher.java`
- `PublicElectiveConversationRouter.java`（编排器在此扩展，需协商改造方式）
- `LocalPreviewConversationFacade.java` / `ZhkuLocalPreviewConversationController.java` / `ZhkuLocalPreviewConfiguration.java`
- 三个 `pom.xml`（根 / agent / knowledge）
- `knowledge/src/main/resources/db/migration/` 中 `V1`–`V8`（只追加，绝不改写）
- `agent/` 模块全部（首轮双方约定只读，LoveApp/恋爱 legacy 保持隔离）
- 各 `README.md`

## 五、硬性纪律（违反即返工）

1. **禁止 `git add -A` / `git add .`**，只精确 add 本任务文件；提交前 `git status --porcelain=v1` 人工核对。
2. **TDD**：先写失败测试 → 最小实现 → 验证；每个新能力同批提交证据账本到 `docs/evidence/`（格式参照 `docs/evidence/2026-09-16-阶段7-通知治理-证据账本.md`）。
3. **不 push 任何远程**（origin 指向上游教学仓库，不是我们的）。
4. **不改写已执行的 Flyway 迁移**；数据库卷、DataRoot（`D:/alldesk/Projects/SuperAgent-zhkuagent-mvp`）、`.env` 一律不动。
5. **密钥只从环境变量读**（`ZHKU_DB_PASSWORD` 等），不写进代码/文档/Git。
6. **持续小步合并**：每个可独立编译 + 测试通过的子任务完成即合并回主线，最长不超过 3 天不合。

## 六、合并前检查清单（逐条过）

```
□ git status 干净；只 add 了自己负责的路径
□ 未动冻结契约文件（动了必先协商）
□ Flyway：空库可从 V1 顺序跑到最新
□ 全量测试通过：
  .\mvnw.cmd -f knowledge\pom.xml "-Dzhku.db.password=<密码>" "-DargLine=-Djava.io.tmpdir=<临时目录>" test
□ 证据账本已提交；SYNC-STATUS.md 已更新
```

合并方式：在主工作区 `git switch zhkuagent-modules && git merge campus-line-b`，然后把结果写进 SYNC-STATUS「合并记录」。

## 六之二、本机两条额外纪律（2026-09-16 新增）

1. **分支名不带斜杠**：新建分支用 `xxx-yyy` 形式，不要用 `feature/xxx`。
2. **提交后核对引用**：`git for-each-ref refs/heads`（仓库已装 post-commit 钩子自动守护，脚本 `scripts/safeguard-refs.ps1`）。发现分支不见了，先跑：
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File D:\alldesk\Projects\ZhkuAgent\scripts\safeguard-refs.ps1 -Fix
   ```
   仍不行再找我，不要自己 `git gc` 或重建分支。

## 七、你的第一步

1. 确认 `docs/plans/agent-sync/SYNC-STATUS.md` 第五节"冻结契约"五项已与我对齐（未对齐先不开发）；
2. 在 SYNC-STATUS「各线状态」把线 B 状态改为"进行中"并登记第一个任务；
3. 第一个建议任务：**会话持久化**（`JdbcConversationStore` 实现 `ConversationStore` 接口）——它有现成接口和契约测试可复用，是熟悉代码库的最佳入口。

有任何与本说明冲突的发现，不要自行处理，写入 SYNC-STATUS「待协商事项」。

---
