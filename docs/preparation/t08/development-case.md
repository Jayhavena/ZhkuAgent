# T08 业务复杂度案例：S10 重修/缓考（第18条）——有证据边界的开发案例

日期：2026-10-14（第二轮准备）。**本文件不出新题、不造成功案例。** 案例材料全部来自已曝光且已人工确认的审核快照与 T05 失败批次记录；“实际输出”“参考说明”“预期行为”三部分分开标注。

## 1. 案例选择理由

T05 收口记录（`58e1ef7` 内 T05-CLOSEOUT.md）中 P01 真实批次暴露的正是这一类任务：问题依赖一个学生可补充的条件（课程类别），
答案按分支不同，且审核材料明确列出禁止结论。它是已曝光开发问题中**结构最典型**的一例，适合作为 T08 评分与反例设计的标定案例。

## 2. 相关条款与审核结构（参考说明，来源=已确认审核快照）

| 项 | 内容 |
| --- | --- |
| 条款 | 《选修课管理办法》**第18条**（PDF 物理第77页／印刷第65页），逐字正文为一个检索 chunk |
| 语义规则 | S10（B02 语义审核，`semanticDecision=CONFIRMED`） |
| 审核事实（3 条，均 `proposedIndependentlyAnswerable=true`） | `public-no-deferred-exam`（when: courseType=PUBLIC）：公共选修课不办理缓考；`public-failed-no-retake`（PUBLIC）：公共选修课成绩不合格者不安排重修；`professional-failed-retake-application`（PROFESSIONAL）：专业选修课成绩不合格者可申请重修学习 |
| 决定答案的条件 | **courseType**（公共选修 PUBLIC / 专业选修 PROFESSIONAL），来源标注 `USER_DECLARED_NOT_OFFICIAL_PROOF`——学生声明，不是官方证明 |
| 缺证策略 | gap：`DEFERRED_EXAM_OR_COMBINED` 且 courseType=PROFESSIONAL → “专业选修课缓考依据未取得” → `PARTIALLY_ANSWERED_OR_INSUFFICIENT_BY_INTENT`；仅在该分支生效 |
| 禁止结论（已审核） | ① 可申请不等于已经获批；② 不安排重修不等于必须退学或必须改选；③ 不能用专业重修句推断专业缓考规则 |
| 目录模板 | S10/COMBINED、S10/RETAKE_ONLY、S10/DEFERRED_EXAM_ONLY（分支模板） |

## 3. 决定答案的条件链与容易遗漏的范围

```
问题（如“选修没过可以重修吗”）
  → 条件 courseType 未知？──是→ 必要澄清（ASK_NECESSARY_CONDITION，条件来自已审核定义）
  → 补充后同 task 新 run：
      PUBLIC  → public-failed-no-retake（不安排重修）；问缓考 → public-no-deferred-exam
      PROFESSIONAL → professional-failed-retake-application（可申请重修，≠已获批）
                     问缓考 → gap：专业选修缓考依据未取得（缺证，不是“没有规定”）
```

容易遗漏的范围（每一条都对应评分维度，见 scoring-rules.md）：
1. **“可申请”≠“已获批”**（禁止结论①）——把申请资格说成批准结果即事实/范围错误；
2. **不能跨分支推断**（禁止结论③）——用专业重修句回答缓考问题即事实映射错误；
3. **“不安排重修”≠劝退/改选**（禁止结论②）——超出原文的操作性推断即范围越界；
4. **缺证分支的诚实表达**——专业选修缓考须说“依据未取得、可核对”，不能说“资料没有规定”；
5. **条件来源性质**——courseType 是 USER_DECLARED，回答不得变成个人资格认定。

## 4. 为什么“只返回相似段落”不足

1. **粒度错位**：检索单元是 chunk（整条第18条逐字正文）；答案是 **fact 级 + 条件分支 + 禁止结论**的组合。返回相似段落只完成“定位”，不完成“判断”。
2. **分支依赖**：同一条款下 PUBLIC 与 PROFESSIONAL 结论相反（不安排重修 vs 可申请重修）。没有条件澄清与分支映射，段落本身不构成答案。
3. **禁止结论需要显式约束**：审核材料把三类越界表述列为禁止；相似段落检索不理解这些约束，模型/评分都必须单独核。
4. **缺证与“没有规定”必须分开**：专业选修缓考是“依据未取得”（DATA_GAP），词法零召回也是“未命中”（RETRIEVAL_NO_MATCH）——两者都不是“政策不存在”，需要 issue.kind/reasonCode 精确表达。
5. **决策负担真实存在**：T05 P01 暴露——即使 SEARCH/READ 都成功返回了所需事实，模型仍未在固定调用预算内收敛到 FINISH（见下）。这说明“检索到段落”与“完成受控查证”之间还有完整的决策与核验层。

## 5. 实际输出（T05 P01 失败样本，仅陈述已发生事实）

来源：T05-CLOSEOUT.md 第四/六/十一节（`t05-31` 与 `t05-34-p01-closeout` 批次）。

| 步骤 | 实际发生 | 说明 |
| --- | --- | --- |
| SEARCH | 签发 10 个候选 | 候选预算内 |
| READ | 成功返回 **16 条已核验事实**（含 P01 所需两条 `T02/ARTICLE4` 事实） | 读取与事实绑定真机可用 |
| 第 3 次决策 | 模型**再次选择 READ** → 被 `BUDGET_EXCEEDED` 拒绝（3 次决策上限用尽） | FINISH 无席位 |
| 终态 | `STOPPED/INCOMPLETE`；FINISH、独立核验、发布**均未执行** | coverage=NONE，主张 0 |
| 另一候选（t05-34） | 首请求 4557B，20031ms 无响应 → MODEL_TIMEOUT，实际尝试 1 次，剩余 4 次关闭 | usage/费用**未知，未记 0** |

**不得从该样本得出**：检索失败、资料缺失、范围限制或任何学校政策结论；也不得把两批不同配置混为可靠性统计（T05 收口第七/十一节明确）。

## 6. 预期行为（验收预期，非新题、非答案）

按[验收矩阵](acceptance-matrix.md)类别 B/E/F 的预期（正式判定以运行前核对的参考答案为准）：
- 缺 courseType 时：**只问该必要条件**，展示已知部分，不追问学号/成绩等无关信息；
- 补充 PUBLIC：按 `public-failed-no-retake` 解释；不劝退、不改选、不谈获批；
- 补充 PROFESSIONAL：按 `professional-failed-retake-application` 解释“可申请”；问缓考则按 gap 表达缺证；
- 全程：主张绑定 factId 与 quote 跨度；范围表述不越个人资格/现行适用；引用仅展示相关段落。

## 7. “20条”口径核对（数量≠可答题数）

对已确认审核材料实测（`rule-check-2025-verbatim-import-draft.json` / `rule-check-2025-b02-semantic-review.json` / C05 主题目录 / Article4 增量）：

| 口径 | 数量 | 说明 |
| --- | --- | --- |
| 条款原文 chunk | **10** | 第 2/4/5/6/8/9/10/11/12/18 条，一条一款一个 chunk |
| 语义规则 | **10** | S01–S10，一条款一规则 |
| 审核事实 fact | **20** | S01:1, S02:2, S03:2, S04:2, S05:3, S06:2, S07:1, S08:2, S09:2, S10:3；**“20条”指这 20 条审核事实** |
| 目录模板（rule/intent） | **16** | 10 GENERAL + 4 gap 分支 + S10 的 2 个分支（C05 草案断言 `declared.size()==16`） |
| Article4 增量事实 | **+2** | `professional-elective-study-arrangement`、`public-elective-self-selection`（T02/ARTICLE4，独立增量审核） |
| 当前合计 | **17 模板 / 22 事实** | 装配 = 基础目录 + Article4 覆盖层 |

**结论**：“20条”是**审核事实数**，不是条款数（10）、不是模板数（16/17）、更不是可答题数。
可答题数取决于“问题→事实→条件→分支”的映射覆盖率，只能由正式 T08 的 G3 新报告集测定；
`proposedIndependentlyAnswerable=true` 只是审核时的提议属性，不构成“已验证可答”。C05 主题目录另标注为
`DEVELOPER_PROPOSED_LOCAL_ENGINEERING_OVERLAY_NOT_HUMAN_POLICY_APPROVAL`，引用时不得当作人工政策批准。

## 8. 未验证范围

- 本案例的“预期行为”未在真实链路上执行过（T05 P01 未到达 FINISH/发布）；
- S10 之外条款的分支/缺证结构未逐一展开（正式 T08 按矩阵覆盖）；
- 检索对该案例相关问法的召回表现未测（见 retrieval-scheme.md 第 5 节）。
