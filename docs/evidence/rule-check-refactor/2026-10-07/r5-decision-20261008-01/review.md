# Task2B 独立最终代码审查

日期：2026-10-08。结论：**Critical 0 / Important 1 / Minor 0；With fixes。** I1 修复并补定向及相关回归前，不应将本批五状态决策合同标为完成。

## 范围与证据

基线 HEAD 为 `135c9d3e58ddbf99f9507ed3f952bd839fd99b86`。按本目录 `source-before.json`、runner 原字节以及白名单审查三份新增生产 Java、新增测试、runner 单元登记与真实 DRAFT 导出；没有用 `HEAD^` 或整个 dirty 工作区代替本轮增量。runner 相对保存字节仅新增 `RuleCheckDecisionServiceTest` 单元选类。

已读取 `review-package.md`、R5 计划 Task2B、产品范围第 5–6 节，以及 `.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md` 的 Task2B rulings。按 requesting-code-review 的审查模板执行。只读源码及现有证据，未运行数据库、模型、Maven、浏览器或子 agent；唯一写入是本报告。

核对实际 XML 与结果清单：RED 27 项、13 failures、0 errors、0 skipped；GREEN 27 项全过；相关纯单元回归 17 类 160 项全过，全部 XML 哈希匹配。三份生产类、新测试及 runner 当前哈希均与回归 `source-after.json` 一致。runner 合同报告为 29 项通过。以上是现有验收证据核对，不是审查者重新执行测试，也不是全仓通过。

真实导出核对：20 个参考 profile、10 条款定位；当前 JSON 和参考工作表哈希与导出验证清单一致；所有 profile 仍为 DRAFT、runtimeTemplate 为 null；runtime source/chunk/span 均为空。导出脚本只写草案和本批验证记录，不写原始 20/16 题集。

## 实现中的有效约束

- 每个事实的全部绑定都经过 source/chunk 唯一匹配、版本/hash 匹配及现有 verifier 复核；失败不夹带事实正文。4→40 并不能凭合法位置或标识放行。
- 失效冲突侧不会被静默丢弃并选择另一侧；冲突输出要求双方完整核验。
- 缺资料时仅保留明确声明独立、且自身条件已匹配的事实；条件、事实及集合采用防御性复制。
- 真实参考确认与生产规则审核分开留存，没有生成运行时 UUID、激活规则或开放学生展示。

## Issues

### Critical

无。

### Important

**I1：已排除分支的专用条件仍被追问或校验，阻断本可完整回答的结果。**

- 文件：`knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckDecisionService.java:72`（第 72–74 行），同因校验位置为第 28–31 行。
- 复现输入：构造合法 APPROVED 模板，条件定义为 `courseType ∈ {PUBLIC, PROFESSIONAL}` 与 `year ∈ {Y1, Y2}`。事实 P 的 `when={courseType:PUBLIC}`，事实 A 的 `when={courseType:PROFESSIONAL, year:Y1}`，事实 B 的 `when={courseType:PROFESSIONAL, year:Y2}`；三者均绑定有效已检索证据，reader 返回一致且合格的当前快照，模板没有资料缺口或冲突。调用 `decide(template, {courseType:PUBLIC}, snapshots)`。
- 当前结果：第 53 行正确排除 A/B，P 的证据与前提齐全；但第 72–74 行仍遍历模板的全部条件，返回关于 `year` 的 `NEED_CLARIFICATION`。无论用户随后填 Y1 还是 Y2，当前 PUBLIC 分支的结论都不会改变。
- 同因第二输入：在上述调用中额外提供 `year:Y3`（不属于只适用于 PROFESSIONAL 的 Y1/Y2 表）。第 28–31 行在分支过滤前全局校验 year，直接返回 `INSUFFICIENT_EVIDENCE`。PUBLIC 分支不依赖 year，该额外用户条件不应使本已覆盖的 PUBLIC 问题失去答案。若用户选择 PROFESSIONAL 且提供 Y3，则范围拒绝仍正确，应保留。
- 预期：`ANSWERED`，仅输出 P，无关于 year 的追问。若输入选择 PROFESSIONAL 且未提供 year，才应追问 year。
- 理由及影响：这是正常多条件分支表达，不需要错误审核、非法输入或未来 HTTP 装配即可触发。事实条件表已明确 year 只影响被排除的分支，组件却忽略过滤结果；违背“只追问会改变结论的最少条件”，增加无用问答并错误降低状态。没有证据泄漏或错误肯定结论，因此判 Important 而非 Critical。现有测试只覆盖单条件分支，未覆盖此组合。
- 最小修复：先按已知、可识别的条件排除分支，针对仍可能适用的事实 `when` 所需条件执行允许值校验，并收集实际尚缺的条件键，按声明顺序生成问题；不能仅因某个 Condition 出现在定义表就全局要求或校验它。补合同反例：PUBLIC 分支缺 year / 提供无关 Y3 均完整回答；PROFESSIONAL 分支缺 year 仍必要澄清、Y3 仍范围拒绝。若确实需要不直接参与事实前提的全局必要条件，应显式建模其用途，不能让所有条件定义隐式变成全局必填。
- 证据性质：以上为对当前控制流的静态可复现输入分析，本审查未新增或运行该测试；执行者须补 RED→GREEN 证据。

### Minor

无另外独立的非阻塞发现。

## Review Focus 逐项结论

| 重点 | 有限范围结论 |
| --- | --- |
| 原文 4 / 绑定 quote 40 / 合法位置 | 拒绝。verifyFact 传递绑定 quote，verifier 必须逐字符匹配原文；存在专项反例。 |
| 同 source/chunk 多 alias | 拒绝。匹配数必须为 1，不选第一项；存在专项反例。 |
| 未审、退役、撤回、版本/正文/hash 漂移、reader 故障 | 未审/退役在读取前拒绝；其余经当前 verifier 拒绝，已失败事实不会输出。决策测试覆盖状态、撤回、版本与故障，底层核验器覆盖正文/hash。 |
| 缺关联例外与独立部分回答 | 每事实全部证据绑定必需，不能用部分绑定下结论；缺口时只输出已知条件下的 independent 事实。独立声明的语义真实性属于审核职责。 |
| 冲突双方与资料缺口 | 双方核验后才冲突，缺一方不挑另一方；资料缺口分支不追加用户问题。必要追问另存在 I1。 |
| 不可变与条件合法 | 集合及 map 防御性复制，重复定义/事实 ID、未声明前提值拒绝，已声明条件的非法输入值受控拒绝；但分支条件使用存在 I1。 |
| 真实参考不伪标生产已审 | 符合。20 个参考 profile 不等于生产模板；DRAFT、不可见、未激活、运行时字段空缺均保留。 |

## Declined to judge

- 模板事实与证据的语义相关性、完整例外及独立性是否审核正确：本组件明定由服务端规则审核负责，quote 一致不能代替该审核。
- 真实 PDF 来源、传播资格与当前年份适用性：本批无实际放行审核，草案保留 UNKNOWN/false，不因参考确认推断资格。
- 自然语言理解、规则选择、HTTP 身份/CSRF、客户端不能伪造服务端对象及学生页面：本批没有装配这些入口，缺少未来 controller 不算当前组件新增 bug。
- 顺序重读后到最终 HTTP/流式下发窗口，以及跨条款原子快照：当前设计明确不保证，本次没有并发或端到端证据；未来装配仍须处理。
- 公开 record 构造器被其他服务代码直接用于拼造已核验输出：当前合理调用经过服务端模板和 verifier；不将假设中的越界调用当本批已存在的暴露路径。
- 真实参考问题自动转换任意生产规则、冲突发现算法、全局语义完整性：草案未激活且组件只消费已审声明，不能外推这类能力。
- 数据库目标、连接池超时、真实账号、模型费用及用户收益：本批只进行纯单元决策与静态导出，不重做 Task2A 或声称上述内容已验证。
- 历史 dirty、旧自由 RAG 事实支撑及全仓默认测试安全：不属本次增量；没有把新组件测试解释为旧 4→40 已修或全仓全过。

## Assessment

**With fixes。** 核验链、冲突完整性和草案资格边界符合这次受控组件范围，但 I1 是实际有限条件决策错误，应在本轮修复并验证。修复后最多可将 Task2B 组件标记为经合同验证、尚未集成；不能据此宣称 R5 全部完成、真实规则已审核发布或学生查证闭环已完成。
