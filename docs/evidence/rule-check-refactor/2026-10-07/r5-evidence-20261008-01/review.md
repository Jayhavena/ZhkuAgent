# R5 Task1 最终组件审查

审查日期：2026-10-08。新鲜上下文独立审查，按 `requesting-code-review/code-reviewer.md` 与 `executing-plans` 的最终审查要求执行。

## 范围与方法

- 基线与审查时 HEAD 均为 `135c9d3e58ddbf99f9507ed3f952bd839fd99b86`。本批未提交，以本目录 `source-before.json` 的新文件声明及 runner 的 before 字节定义增量；未审整个 HEAD/工作树差异。
- 逐行审查四个新生产类型：`RuleCheckEvidenceSnapshot`、`RuleCheckEvidenceSpan`、`RuleCheckVerifiedExcerpt`、`RuleCheckEvidenceVerifier`，以及 `RuleCheckEvidenceVerifierTest`；runner 只审 `$unit` 新增 `RuleCheckEvidenceVerifierTest` 的两行增量。
- 已阅读产品范围、主路线图 R5、细化计划 Task1/Review Focus、审查包与本计划 ledger 的两条 Ruling。保留当前 checkout、按独立增量交付，以及先实现确定性组件的取舍，与本次限定范围一致。
- 只读取源码、文档、已有日志/JSON/XML并计算哈希；没有运行 Maven、数据库、模型或新增测试。唯一写入文件为本报告。

## Strengths

- `RuleCheckEvidenceVerifier.java:60` 从服务端原文按指定位置取得 `actualQuote`，随后做精确字符串比较；没有 trim、模糊匹配或直接转发模型文字。正常引用和合法范围不能放行 4→40。
- `RuleCheckEvidenceVerifier.java:30` 扫描整个本轮集合并拒绝重复 citation ID；`RuleCheckEvidenceVerifier.java:68` 以持久 source/chunk 身份重读一次。最新资格、身份、revision、全文及 SHA-256 都必须一致，文字相同不能绕过版本变化。
- `RuleCheckEvidenceVerifier.java:48` 与 `:56` 在读取当前证据之前检查 UTF-16 原文完整性和跨度边界；整数极值不参与加减运算，避免溢出造成边界绕过。拒绝结果不含提议文本，reader 的运行时异常不向返回对象泄漏。
- 快照、提议与产物分开，关键中文注释明确了信任边界和“来源一致不代表语义完整”。组件没有引入控制器、Spring 装配、模型或第三套 RAG 链。
- runner 的新增类为纯 JDK 组件测试；before/current 差异只有显式白名单登记，数据库和供应商限制未在本批改动。

## Review Focus 核对

| 关注项 | 核对结果与证据 |
| --- | --- |
| 原文 4、提议 40、合法位置 | 核验器 `:60`–`:63` 拒绝不一致；测试 `:43` 固定该反例，并通过 `rejectBeforeRead` 断言 reader 调用为 0。 |
| 重复文字/重复引用 | 测试 `:112` 断言采用第二处文字的指定位置，并拒绝错位；测试 `:60` 覆盖目标重复及其他引用重复，均拒绝。 |
| 撤回、版本/hash/身份变化 | 核验器 `:78`–`:82` 统一阻断；测试 `:128`–`:145` 分别覆盖这些变化和正文追加例外。内存替身只证明组件比较逻辑。 |
| UTF-16、负数/越界/空白 | 测试 `:51`、`:91`、`:99`、`:107` 覆盖非法提议、整数边界、两个代理对切割边界及不完整原文；正常完整代理对可通过。 |
| 原文真实但不相关或遗漏例外 | 本组件不作相关性/完整性判断，与计划边界一致。没有将逐字摘录、模型评分或本次单测解释为业务结论可发布。 |

以上文件行号分别指向 `knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceVerifier.java` 和 `knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceVerifierTest.java`。

## Issues

### Critical (Must Fix)

未发现。

### Important (Should Fix)

未发现。

### Minor (Nice to Have)

1. **当前快照允许不同 citation 别名的约定缺少正向回归用例。**
   - 位置：`knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceVerifierTest.java:29`；对应实现 `knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceVerifier.java:77`。
   - 证据：正常路径返回 `ORIGINAL`，其余当前快照用例也使用 `ev-1`；目前没有 reader 返回其他 citationId、但 source/chunk/revision/checksum/text 均一致的成功场景。
   - 影响：当前实现正确，但日后若误将本轮别名加入持久身份比较，已有用例仍可能全绿，实际 reader 的合法结果却被拒绝。
   - 建议：后续补一个成功用例，返回如 `stored-chunk-alias` 的当前快照，断言通过、产物保留本轮 `ev-1`、reader 调用一次。该项不阻塞本次组件验收。

## 已有验证证据核对

| 批次 | 已核对结果 |
| --- | --- |
| `r5-20261008-red-01` | 实际 XML 为 19 项、17 个断言失败、0 error/skip；失败包括默认拒绝不能满足成功路径及明确拒绝原因。它证明新组件合同缺失，不是旧 RAG 漏放的复现。 |
| `r5-20261008-green-01` | 实际 XML 为 19 项、0 failure/error/skip；XML 哈希与结果清单一致。 |
| `r5-20261008-regression-01` | 15 个 XML 共 130 项、0 failure/error/skip；全部 XML 哈希与结果清单一致，日志显示 BUILD SUCCESS。 |
| 本目录 `runner-contract-final.json` | 保存的报告为 29/29 通过，计数替身报告数据库/供应商操作为 0；本审查没有重跑入口合同。 |

六个审查文件的当前 SHA-256 均与 GREEN 和相关回归的 before/after 清单一致，两个批次运行内均无本批源码漂移。RED 的测试源码哈希与当前一致，核验器哈希不同，符合先壳后实现的记录；其余五个文件一致。runner 基线字节哈希与本目录 `source-before.json` 一致。三个批次均记录 `resourcesVerified=true`、无残留新 Java、`database=false`、`providerAllowed=false`；这些是已有 runner 记录，不等同于本次网络抓包或真实服务验收。

审查时核验器 SHA-256：`0e7f8365f756040413675ac4a5822d766e3acdcfd6bd510a255abb3260d6de3f`。测试 SHA-256：`904a56d08dfc3f2b19bbe24b0e341c3da8082fd72379f0c44a7ac88487cc9325`。

## Recommendations

将别名正向用例记为可延后的 Minor。Task1 的阶段结论保持 `COMPONENT_VERIFIED_NOT_INTEGRATED`；后续数据库读取准备、学生入口装配与真实质量验收分别提供独立范围和证据。

## Declined to judge

- 摘录对问题的相关性、条件/例外完整性及个人适用性：这是审核模板、应用状态和人工参考的责任；Task1 明示只核验来源一致，不能据此关闭 RC-04-SEMANTICS。
- 真库读取的事务/竞态、读取后至下发之间再次撤回、当前账号权限及输出前装配：本批只有内存 reader，Task2/Task2A 的数据库适配和应用边界须单独验证；没有将它们作为本组件已承诺完成的功能。
- 服务端快照/reader 的可信创建，以及绕过 verifier 直接构造公开 record 的应用误用：类型名称和公开构造器不构成授权证明；本包已明确由应用创建可信输入，当前没有学生反序列化或调用入口可供审查。
- PDF 原件字节审核、传播资格、完整时效、真实模型效果、浏览器流程和用户收益：文本 SHA-256 与本次纯单元结果无法证明这些事项；G0/G2 及相关资料/人工门禁保持独立。
- 旧 RAG 的 4→40 漏放、P0 实现及历史 dirty 改动：均不在 source-before 定义的本批增量内；本审查不复审 P0，也不据新组件用例关闭旧链路已知风险。

## Assessment

**Ready to merge? Yes，仅限本次 Task1 组件增量。**

没有发现 Critical 或 Important 问题，当前实现满足确定性核验合同，已有定向与相关回归报告均可与本批源码对齐。一个非阻塞测试缺口可延后处理；本结论不覆盖整个脏工作树、Task2/Task2A 后续实现、学生发布或整个 R5 完成状态。
