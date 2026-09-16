# 仲园 Agent：双 Agent 并行开发与合并协议

> 目的：让两个 Agent 互不阻塞地并行推进，最终以**可预期**的成本合并。
> 性质：协作流程约定，不是功能实现，也不授权任何一方改动对方负责的文件。
> 日期：2026-09-16

---

## 0. 结论：可以，但"能不能省时间"取决于边界切得干不干净

- 本仓库已经具备并行条件：正在使用 **git worktree + 分支**（现有 worktree：`SuperAgent`、`SuperAgent-zhkuagent-mvp`、`ZhkuAgent`）。
- 同一 clone 的多个 worktree **共享对象库和 refs**，因此"合并"就是本地 `git merge`，**不需要远程仓库**。
- 但并行开发的成本不在 git，而在**语义冲突**：两人改同一个契约、抢同一个 Flyway 编号、互相覆盖依赖。这一类冲突无法靠"合并时小心"解决，只能靠**事先切割 + 契约冻结**避免。

一句话：**能并行，但必须先分边界，再分头做。**

---

## 1. 前提：先固化基线（不做这一步，后面全部不成立）

当前 `codex/zhkuagent-modules` 上有**未提交**的阶段 7 成果：`knowledge/.../notice/`、Flyway `V6`–`V8`、阶段 7 计划与证据账本。

必须先把它提交成一个基线提交，否则：

1. 另一个 worktree 从 HEAD 切出去时**看不到这些文件**；
2. 两条线都会去"补"同样的东西，产生**结构性冲突**（不是文本冲突，是重复实现）；
3. 后续任何回归失败都无法归因到具体哪条线。

**动作：先把阶段 7 收口提交（哪怕只是阶段性提交），再开分叉。**

---

## 2. 三类"不可并行写"的资源（本项目特有）

| 资源 | 为什么危险 | 处置方式 |
| --- | --- | --- |
| Flyway 迁移编号 `V9+` | 项目纪律为"只追加、绝不改写历史"；两人各写 `V9__*.sql` 会直接撞车且无法通过重命名解决 | **开分前分段预留**：线 A 用 `V9`–`V11`，线 B 用 `V12`–`V14`；跨段需求走追加 |
| 共享契约（DTO / 接口 / 枚举 / 路由目录） | 双方都改等于每次都冲突，且冲突落在语义层，最难审 | **先冻结**：契约单独一次提交，冻结后才分叉；此后只许追加以保持兼容 |
| 构建文件 `pom.xml`（根 / `agent` / `knowledge`） | 依赖新增会互相覆盖，且顺序敏感 | 约定：**先合并的一方落依赖**，后合并的一方手工对齐；锁定依赖版本，不顺手升级 |

---

## 3. 高冲突文件清单（依据当前代码实测）

以下文件在多条功能线中都可能要改，属于"必须指定唯一负责人"的范围：

**契约与骨架**
- `knowledge/src/main/java/com/zhku/knowledge/answer/GroundedAnswer.java`、`GroundedCitation.java`、`GroundedAnswerService.java`、`PublishedKnowledgeSearcher.java`
- `knowledge/src/main/java/com/zhku/knowledge/conversation/elective/ElectiveRetakeReply.java`、`ElectiveRetakeReplyType.java`
- `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveConversationRouter.java`

**装配与接口**
- `knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewConversationFacade.java`
- `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationController.java`
- `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java`（通知服务在此按开关装配）

**迁移**
- `knowledge/src/main/resources/db/migration/`（`V9` 及以后）

**页面资源**
- `knowledge/src/main/resources/zhku/local-preview/index.html`
- `knowledge/src/main/resources/zhku/local-admin/index.html`

**构建与文档**
- `pom.xml`、`agent/pom.xml`、`knowledge/pom.xml`
- `README.md`、`agent/README.md`、`knowledge/README.md`

**低冲突（可并行）**：`docs/evidence/` 下的证据账本按"一能力一文件"命名，两条线各写各的，基本不会撞。

---

## 4. 建议分工：两条线 + 一个冻结层

### 4.0 冻结层（先做、单独提交，双方共同评审）

| 冻结内容 | 说明 |
| --- | --- |
| 统一路由契约 | `AnswerRoute` 及各分支的判定签名（澄清 → 通知 → 高风险规则 → 检索增强 → 拒答/人工） |
| 引用白名单契约 | 证据/引用的稳定标识结构（供生成层输出与服务端校验共用） |
| HTTP 响应契约 | 现有会话接口新增字段（如引用 ID 列表、工具调用摘要）——**只追加，不改旧字段语义** |
| 配置键与 Profile | 新增环境变量、开关的命名规范，避免两边各起一套 |
| Flyway 号段 | 按第 2 节分段预留 |

**冻结层是并行的前提**：没有它，两条线会在第 3 节的共享文件上反复互踩。

### 4.1 线 A：检索 + 生成 + 评测（对应规划文档 P0-1/4/5、P1-6）

- 新增：向量检索、混合融合（RRF）、受控 RAG 生成、引用校验、降级
- 新增：版本化 Eval 集与指标报告
- 主要落点：`knowledge/.../rag/`、`routing/`、`answer/` 改造、pgvector 索引与 profile
- 独占：`V9`–`V11`

### 4.2 线 B：工具 + 编排 + 体验（对应规划文档 P0-2/3、P1-7/8/10）

- 新增：校园工具集（学分计算、选课冲突检测等只读/纯计算工具）+ Function Calling
- 扩展：统一路由编排器（在冻结契约上落地顺序）、会话持久化、流式输出、答案卡
- 主要落点：`knowledge/.../tools/`（新增）、`PublicElectiveConversationRouter` 扩展、`ConversationStore` 换实现、前端页面
- 独占：`V12`–`V14`

> 若目标是**最快形成"是 Agent"的定性**，线 B 优先（工具调用是分水岭）；若目标是**最快产出可量化数字**，线 A 优先（评测指标）。这两条线的先后取决于简历目标岗位。

---

## 5. 合并顺序与门禁

```
① 冻结层（契约）先合并          ← 必须是第一个
② 先合"不依赖对方"的那条线
③ 后合的线负责解冲突并跑全量验收
④ 合并后统一跑：知识模块全量测试 + 本机一键验收脚本
```

**合并门禁（缺一不算合完）：**
- 契约测试全绿（共享契约的行为不能因合并而变）；
- 迁移可在一张空库上从 `V1` 顺序跑到最新（验证号段没有断层）；
- 不出现"两边各自实现一遍同一能力"的重复代码。

---

## 6. 远程策略（重要，容易踩坑）

- 当前 `origin` 指向 **上游教学仓库** `https://github.com/liyupi/yu-ai-agent.git`，**不是你的仓库**。
- 因此：**不要 `git push origin`**。合并走本地 `git merge` 即可（同一 clone 的 worktree 共享 refs）。
- 如果另一个 Agent 运行在**独立的 clone 或另一台机器**上，需要先准备一个**你自己的裸库或远程**作为中转，再走分支合并。

### 创建并行 worktree 的参考命令

```powershell
Set-Location D:\alldesk\Projects\ZhkuAgent
git switch -c codex/campus-agent-line-b
git worktree add D:\alldesk\Projects\ZhkuAgent-lineB codex/campus-agent-line-b
```

或一步创建分支并挂载 worktree：

```powershell
git worktree add -b codex/campus-agent-line-b D:\alldesk\Projects\ZhkuAgent-lineB
```

---

## 7. 待确认事项

1. **另一个 Agent 是否与本仓库共用同一文件系统？**（决定"本地 merge"还是"需要中转远程"）
2. **分工是否按第 4 节？** 还是你已有划分——若有，我按你的划分反推冲突清单与号段分配。
3. **合并节奏**：持续小步合并（推荐，冲突小、可归因），还是一次性收口（冲突集中、难归因）？
4. **我负责哪条线？** 明确后我按冻结契约推进，不越界改对方文件。
