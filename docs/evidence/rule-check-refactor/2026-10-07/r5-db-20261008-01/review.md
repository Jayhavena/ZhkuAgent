# R5 Task2A 数据库证据读取：独立最终审查

审查日期：2026-10-08。按 `executing-plans` 的新鲜上下文最终审查与 `requesting-code-review/code-reviewer.md` 执行。只读审查；本报告是唯一写入文件。未执行 Maven、数据库连接、容器操作或模型调用。

## 范围与依据

- BASE = HEAD = `135c9d3e58ddbf99f9507ed3f952bd839fd99b86`，本批尚未提交。以本目录 `source-before.json` 的缺失基线及 runner 保存字节确定增量，不把整个 dirty 工作区视为本批。
- 审查新增 `JdbcRuleCheckEvidenceReader.java`、`JdbcRuleCheckEvidenceReaderTest.java`、`JdbcRuleCheckEvidenceReaderTargetTest.java`，以及 `knowledge/scripts/verify-rule-check-refactor.ps1` 新增的两个选类。
- 核对 `docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md` 的 Task2A / Review Focus、`docs/product/rule-check-scope.md`、本次 review-package，以及 `.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md` 的 Ruling。
- Task1 类型仅作为 consumer 合同读取；既有迁移、临时目标 guard 只用于判断本次实现的约束。未重审 P0、Task1 或历史 dirty。
- 保存的 runner before 哈希与基线一致（`dd7ec88f32c165dc121b3db8d820a0a33b37d3010087c59a4ccaed45d4c7d294`）；实际 diff 仅把目标/连接失败测试加入 Unit、把新 reader DB 测试加入 Database。

## Strengths

- `JdbcRuleCheckEvidenceReader.java:37` 至 `:51` 在同一 SQL 中同时绑定 source/chunk、服务端 profile/chunkingVersion/scope，并检查 ACTIVE、COMPLETED、run/profile/切分版本一致、发布/可见/分享状态、来源版本和片段 checksum。没有候选排序、其他版本回退或模型调用。
- `JdbcRuleCheckEvidenceReader.java:53` 至 `:72` 使用参数绑定及嵌套 try-with-resources；无行返回 empty，多行拒绝，SQL 异常交给 verifier 统一拒绝。由 Snapshot 的非空约束及 verifier 的 RuntimeException 边界保证异常数据不能形成成功摘录。
- `JdbcRuleCheckEvidenceReaderTest.java:35` 在凭证读取、DataSource 创建与迁移之前运行目标 guard；`:64` 在目标未通过时直接返回。`JdbcRuleCheckEvidenceReaderTargetTest.java:17` 实际调用这两个生命周期方法，覆盖原库 URL、远端 URL 与未指定 URL，未借用其他旧 DB 类的安全性来替代本类验证。
- 真库测试把“reader 找不到合格行”和“reader 读到新版本/新正文但 verifier 拒绝旧快照”分开断言。body 变更但存储 hash 未变时，当前实现先由正文精确比较阻断；verifier 合同另有重新计算 hash。该测试证明旧正文不能沿用，不应夸称单独隔离验证了重新 hash 分支。
- 本轮 citation 别名由 approved 保留、reader 使用持久 source/chunk 的 Ruling 与实现一致；没有让数据库生成本轮可自证的引用身份。

## 证据核对

本次仅读取已有报告，并重新计算 XML 与本批源码哈希；不把报告读取表述为审查者重新运行了测试。

| 批次 | 实际 XML 类数/项数 | 失败/错误/跳过 | 核对结果 |
| --- | --- | --- | --- |
| `r5-db-20261008-red-02` | 2 / 18 | 6 / 0 / 0 | XML 哈希全部一致；两个测试类与最终测试源码相同，reader 实现哈希不同，符合壳 RED→实现 GREEN |
| `r5-db-20261008-green-01` | 3 / 37 | 0 / 0 / 0 | XML 哈希全部一致；本批四个文件与 source-after 全部一致 |
| `r5-db-20261008-regression-01` | 18 / 172 | 0 / 0 / 0 | 最终报告已存在；XML 哈希全部一致；本批四个文件与 source-after 全部一致；日志有 BUILD SUCCESS |

GREEN 与最终相关回归的 results 均记载 exitCode 0、sourceDrift 0、resourcesVerified true、newJavaRemaining 空、containerExited true。database 报告均记载专用回环测试库、V21、connectionsAfterTests 0；最终 database-attempt 记载随机容器名且 volumeMounted false。providerAllowed false 的证据是选类及密钥移除声明，未进行网络抓包验证。

本目录 `runner-contract-final.json` 另记载 29 项合同检查通过、databaseOperations 0、providerCalls 0；其范围是生产预检查与调用顺序，外部执行器使用计数替身，不替代上述实际数据库验收。runner 的 `git diff --check` 无问题。

本次范围的最终 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `JdbcRuleCheckEvidenceReader.java` | `dab69e41c7804ec76edef905ebf11f357f45d81110e5ed5a49ce2ea0865b5201` |
| `JdbcRuleCheckEvidenceReaderTest.java` | `abc535cb269a43e9d2152d969d47d325f0fc52ff9f4000641157149f40697911` |
| `JdbcRuleCheckEvidenceReaderTargetTest.java` | `71392a0daab32e19735240cd3eb90bc978bed6e2263953a563e2f854d98c75e0` |
| `verify-rule-check-refactor.ps1` | `44d6b6a6fe9adac78ad089350eeaba588d8e760b924e228b467b4d4d6527b4d1` |

## Review Focus 逐项结论

1. **双身份与服务端范围：通过静态审查。** SQL 对 source/chunk 分别绑定，并通过关联确认二者关系；profile、scope、切分版本没有由检索结果或模型放宽。DB 反例覆盖错误 source/chunk、scope 改变、配置切分版本不回退及 scope 注入字串。
2. **资格与索引漂移：现有字段口径通过。** 撤回、内部可见、分享资格收回、scope、BUILDING/RETIRED、RUNNING/FAILED/ROLLED_BACK、修订与 checksum 变化均有 DB 路径。撤回/分享反例同时改成 INTERNAL_ONLY 符合 V2 的学生可见约束，不能据此声称独立隔离证明了每一个 SQL 谓词。run/profile 不一致由 SQL 阻断，专项回归缺口见 M1。
3. **正文与存储 hash 不一致：阻断旧摘录。** reader 返回当前正文；verifier 校验正文、版本和 checksum，不会只凭 SQL 的两个存储 hash 相等沿用旧 quote。未发现绕过。
4. **裸 DB 测试目标：本新增类在连接/迁移前拒绝。** 无 URL 默认值；guard 拒绝后 cleanup 不连接。此结论不外推到白名单内其他既有 DB 类。
5. **时序声明：边界准确。** `withdrawalBetweenReadsBlocksOldQuote` 证明顺序真实读取与更新之间的撤回可被下一次读发现；reader 注释明确读后到 HTTP 下发的窗口仍在。未发现本批声称并发线性化或流式撤回已经完成。
6. **失败关闭：静态实现符合。** 配置/UUID 非空校验在连接前；SQL 异常、空结果及重复行不会产生成功摘录，资源由 try-with-resources 关闭。连接失败有纯单元执行证据；重复结果因现有唯一约束正常不可构造，本次对该防御分支及异常资源关闭做静态判断，未声称有故障注入运行证据。

## Issues

### Critical (Must Fix)

无。

### Important (Should Fix)

无。

### Minor (Nice to Have)

**M1：补充 run 与 entry 的 profile 不一致反例。**

- 位置：`knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckEvidenceReaderTest.java:109`（run 状态测试）及 `:139`（run/entry 切分版本测试）；对应生产谓词 `knowledge/src/main/java/com/zhku/knowledge/rulecheck/JdbcRuleCheckEvidenceReader.java:45`。
- 当前测试覆盖 run 状态和切分版本，尚未把一个 ACTIVE profile 的 entry 指向另一 profile 的 COMPLETED run。V9 的两个独立外键允许这种跨 profile 关联，所以不是不可达数据形态；将来误删 `index_run.profile_id = entry.profile_id` 时，当前新增测试缺乏直接回归保护。
- 当前 SQL 已明确阻断，因此没有确认的漏放缺陷；按回归覆盖改进列为 Minor，不阻断本次未集成准备组件。
- 建议后续以第二个 BUILDING profile 及其 COMPLETED run 构造该不一致关联，断言 reader empty、verifier 无 excerpt；夹具需恢复关联并清理第二组 run/profile，保持原库 guard 与唯一 ACTIVE 约束。

## Recommendations

本批可以按 `DB_ADAPTER_VERIFIED_NOT_INTEGRATED` 记录；M1 按流程留账。进入真实学生应用时，应继续履行 Task2 的审核模板、服务端身份装配和输出链要求，不能凭此 reader 的 `eligible=true` 宣布完整资格判定完成。

## Declined to judge

- **完整生效时间、被取代关系、发布清单与真实审核资格：** Task2A 明确只映射现有存储字段，G0 未过，尚无真实审核材料；这些仍 OPEN，本报告不将其视为已满足的产品能力。
- **读完成后至 HTTP/流式下发的撤回竞态：** 本批无学生下发入口且仅验证顺序数据库变化；不据此判断端到端线性化、事务/发布策略已经完成。
- **摘录相关性、条件/例外完整性、自由事实支撑及旧 RAG 的 4→40：** 此 adapter 只重读证据，语义与旧链路问题保持 OPEN；本批没有修复或重新验收这些能力。
- **真实账号权限、浏览器、模型调用、成本及用户收益：** 尚未装配学生入口，无相应运行证据，属于后续 Task2–4 与外部资料/参与者依赖。
- **整个历史 dirty 工作区、P0/Task1 的重新审查、其他既有 DB 测试的裸启动风险：** 超出精确增量；本次仅核对消费合同和新类目标保护，不能把其结果外推到全仓。
- **生产连接池/连接超时预算与故障后端到端响应时间：** 当前适配器接收 DataSource，尚无应用装配和延迟合同；本次只判断异常返回时受控拒绝，不承诺阻塞连接的时间上界。

## Assessment

**Ready to merge? Yes，仅限本次四路径增量作为未集成准备组件。**

本次未发现 Critical/Important 问题；一个 Minor 为可补强的真实数据库回归覆盖。37 项定向及 172 项相关回归与本次源码一致，支持现有字段读取适配已验证；该结论不授权整个 dirty 工作区合并、学生发布或关闭 G0/R5/语义与时效缺口。
