# T07 集成进主线（zhkuagent-modules）—— 2026-10-16

本目录记录 **T07 学生入口线合入主线 `zhkuagent-modules`** 的干跑、裁决与验证证据。

结论一句话：**T07 已可合入主线，且只有一处机械冲突；T05 与 T06/T07 之间的 13 个 Java 冲突不在本次范围内，根因是 T05 停在旧版本上演进。**

---

## 一、真实拓扑（与"大家都从 88e57d6 出发"的假设不同）

| 分支 | HEAD | 与主线 `zhkuagent-modules` 的 merge-base |
|---|---|---|
| `zhkuagent-modules`（主线） | `ec1e362` | — |
| `codex/t05-online-evidence` | `1c270c4` | `58e1ef7`（**T05 是从主线上分出的**） |
| `codex/t06-controlled-launch` | `a03af69` | `5ee26d8` |
| `codex/t07-student-entry` | `c052c45` | `5ee26d8` |
| `codex/t06-t07-student-http`（= `88e57d6`） | `88e57d6` | `5ee26d8`（**它本身不在主线上**） |

要点：
- `88e57d6` 是 T06/T07 的共同基线，但它是另一个分支的 HEAD，**没有合入过主线**。
- 因此"合 T07 进主线"必然同时把这 490 个文件带进主线，这是三方合并的自然结果，
  不是主动改写他人的文件。
- T05 的基线是主线上的 `58e1ef7`，与 T06/T07 不在同一个分叉点上。

## 二、合并干跑（只读，未动任何 ref）

| 组合 | merge-base | 冲突数 | 结果 |
|---|---|---|---|
| T05 × 主线 | `58e1ef7` | 0 | tree `a2730073` |
| T06 × 主线 | `5ee26d8` | 1 | tree `b4dfc7f4` |
| **T07 × 主线** | `5ee26d8` | **1** | tree `09163037`（冲突版） |
| T06 × T07 | `88e57d6` | 0 | tree `341c3710`（文件交集 0） |
| T05 × T06 | `5ee26d8` | **14** | 1 个 ps1 + **13 个 ruleagent Java** |

## 三、唯一冲突与裁决

`knowledge/scripts/verify-rule-check-refactor.ps1`，形态是**两侧往同一行头部插入测试类名**：

- 共同祖先 `5ee26d8`：`c785c29d`
- 主线侧：`42e6e77e`，插入 `'RulePublicationGateTest',`
- 基线 `88e57d6` 侧（T06/T07 同）：`57ad1336`，插入
  `'RulePublicationGateTest','RuleAgentReviewPayloadBudgetTest','RuleAgentTaskToolsRegistryTest',`
  `'RuleAgentReviewedToolsConfigurationTest','RuleAgentTaskBrowserHttpTest','RuleAgentTaskHttpTest',`

基线侧是主线侧的**严格超集**（两边各只改 1 行，`--numstat` 均为 `1 1`），
故取并集 = 直接采用基线侧 blob `57ad1336db7f9c9d3df74cfd19020193b626aecd`。
**属机械裁决，无语义取舍。**

## 四、不在本次范围内的冲突（需另行裁决）

13 个 `ruleagent` Java/测试文件在 T05 × T06 上冲突。以 `RuleAgentRunner.java` 的 blob 为例：

| 角色 | blob |
|---|---|
| 共同祖先 `5ee26d8` | `3c39d78b` |
| 主线 `ec1e362` | `2f82467a` |
| T06 `a03af69` | `2f82467a`（与主线**完全相同**） |
| T07 `c052c45` | `2f82467a`（与主线**完全相同**） |
| T05 `1c270c4` | `6b79b0bf`（**在旧版本上另行演进**） |

即主线/T06/T07 三方 Java 已一致，只有 T05 停在旧版本继续改。
**这不是设计分歧，是版本陈旧；如何取舍应由 T05/T06 线或你裁决，T07 不代为决定。**

## 五、合并结果校验（零丢失）

裁决后的合并树相对两侧：

- 相对主线：**125 新增 / 9 修改 / 0 删除** —— 主线上那 387 个"基线缺失"文件（实为主线在
  `5ee26d8` 之后新增，非删除）全部保留，包括 `docs/career/` 三个文件。
- 相对 T07：**387 新增 / 2 修改 / 0 删除**。
- 2 个 M 为 `docs/evidence/rule-agent-progress.md`（双方都改的共享进度文件，由 git 自动合并，
  **未人工改写任何一方记录**）与 `knowledge/scripts/start-rule-agent-probe.ps1`。

## 六、在合并提交上的验证

| 项 | 结果 |
|---|---|
| 前端全量回归 `node --test` | **176 / 176 pass，0 fail** |
| 学生构建 | exit 0，24 个资产，hash 与 T07 线留存记录**完全一致** |
| 工程构建（`VITE_ENTRY_MODE=engineering --outDir dist-engineering`） | exit 0，134 modules，24 个资产，hash 与 T07 线留存记录**完全一致** |

资产 hash 完全一致说明：合并后的前端源码与 T07 线**字节级等价**（vite 按内容生成 hash）。

## 七、边界声明

- 本轮**只合 T07**，未合 T05、T06 的代码。
- 未执行 push / PR / 打标签 / 部署 / 历史改写。
- 学生入口与结果展示仍为**工程局部结论**：真实身份 + 真实资料的浏览器验收尚未执行，
  不得据此表述为"学生验证有效"或"真实在线整链通过"。
- 两窗口浏览器验收的证据绑定到脚手架 `50f8122` 与受控模拟宿主，
  **不是**在主线合并提交上取得，本轮不重复计为整链证据。
