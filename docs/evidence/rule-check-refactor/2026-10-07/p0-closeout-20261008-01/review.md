# P0 最终独立审查

审查日期：2026-10-08。结论：**With fixes — 当前有 1 项 Important，修复并补证据后可完成本批工程收口；不能据此宣布校园查证上线或学生试点就绪。**

## 审查范围与证据

按本目录 `review-package.md`、产品范围、P0 计划及 `.superpowers/sdd/2026-10-07-rule-check-p0-plan/progress.md` 的 rulings 审查，采用 `superpowers:requesting-code-review/code-reviewer.md` 模板。基线是 R0–R3 的 before/changes.diff 与 R4 的 before/changes-review.diff，不是整个 dirty 工作区相对 HEAD 的差异。未执行测试、数据库、供应商、浏览器或 Git 状态变更；只读源码和已有记录，唯一写入文件为本报告。

已读取最终 `p0-20261008-regression-01/results.json`，并独立读取其 19 份 XML：共 142 项，失败、错误、跳过均为 0，XML 名称、计数及 SHA-256 与 results 一致。检查该批 source-after 的 522 个当前文件，全部哈希一致。database.json 记录专用 `127.0.0.1:55439/zhkuagent_rulecheck_e2e`、V21、测试后连接 0；results 记录临时容器和新增 Java 进程退出。这是对已有验收证据的复核，不是审查者重新运行验收。

R2 当前页面与 browser-after/served-page.html 的 SHA-256 都是 `6d3f17a28dac0ed98d313cb54c71f71646d57a16e4712b456bf716bfd1825e5a`。已读其 10 项浏览器观察、验收范围及真实合成数据库终态说明；没有把这些场景当学生质量样本。

## 做得好的部分

- R1 在 `HybridRetrievalService.java:38,65,79` 保持旧入口、分开每路候选预算与最终数量，再融合截断；`HybridRetrievalServiceTest.java:207` 的替身实际遵守 limit。共享第二名胜出反例能暴露原缺陷，默认 20 明确仍待校准。
- R2 的管理员 DTO 在 `ZhkuLocalAdminController.java:377,391` 返回原文；页面 `index.html:577` 使用 textContent，空白页在 578 行明确提示人工处理。568–598 行区分待审/结束状态、保留跨分页筛选草稿、禁止未保存内容通过，服务端审核边界仍保留。历史真实浏览器证据对应当前页面字节。
- R3 的 `ModelOutputParser.java:39,47,54,59` 分别检查根对象、字段真实类型、有限 0–1 分值和引用项；`AnswerEvidenceGuard.java:41–60` 对直接构造的 record 重复检查关键下发边界，包括 NaN 与空引用。`ModelResponse` 的既有不可变列表构造已拒绝 null 引用项；composer 的受控降级与本机 HTTP 适配器合同都有针对性覆盖。
- R4 默认构造为线上模式，只有明确命名的 `forOfflineEvaluation` 允许 BUILDING；RETIRED 不可读。线上装配仍使用默认构造，离线入口调用只出现在两个已声明 eval 测试中。向量类 59–65、94–98 行先检查 profile 状态和维度，之后才调用 embedding；120 行在 embedding 返回后再次于查询检查状态。
- R4 两路 SQL 都过滤 PUBLISHED、STUDENT_VISIBLE、分享权利、scope、source_version、entry/chunk checksum、COMPLETED run、run/profile 一致及 run/entry 切分版本一致。向量类 119–132 行的资格位于 ORDER BY/LIMIT 前；词法类 94–105 行过滤后仍在 Java 按词面评分再截断。Java 最终资格复核仍保留，null scope 两参数绑定一致。
- `JdbcCandidateRetrieverTest.java:289–299` 用真实不合格最近向量与更远合格向量检查 LIMIT，并统计 ResultSet 实际读取行；现有查询计划记录返回一行。状态、维度、退役竞态、版本/hash/切分版本/run 漂移矩阵和显式离线兼容覆盖了五类 Review Focus。
- R0 runner 对测试选择、批次和目标先拒绝再执行，临时库随机口令不放命令参数，无卷容器与资源退出可追溯；XML 缺失、陈旧、零测试、跳过、失败、源码漂移和非零退出都不放行。已有独立合同与普通 Windows 终端证据没有被混成真实供应商证据。

## Issues

### Critical

未发现本批新增的 Critical 问题。

### Important

**P0-REVIEW-01：新增真实 profile 激活会在直接运行测试时退役原库的在役 profile。**

- 主要位置：`knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcCandidateRetrieverTest.java:332–333`；正常排名测试从 109 行等处调用它。关联位置：该类 46–47 行仍把原演示库 `54329/zhkuagent` 作为缺省 URL，69 行在目标校验前迁移；103–104、494–509 行的清理只删除测试夹具。
- 机制已从源码确认：`EmbeddingProfileSwitcher.java:33–38` 查找全库当前 ACTIVE 并调用切换；`JdbcEmbeddingProfileStore.java:44–48,101–118` 把找到的旧 profile 退役，然后激活测试 profile。测试结束只删除测试 profile，不恢复原 profile。
- 复现条件与结果：在已有 ACTIVE profile、凭证可用的原演示库环境，IDE 或裸 Maven 直接选该类正常排名测试且未显式设置 URL。首次 `activateProfile()` 将业务 ACTIVE 改为 RETIRED；测试清理删除临时 ACTIVE 后，原库留下无 ACTIVE 的状态，正常线上检索因此不可用。此路径仅静态复核，没有为复现而操作原库。
- 本批归属：R4 之前夹具只登记 BUILDING，不会为了正常排名断言切换原库 ACTIVE。本次新增的全局状态副作用提高了既有缺省 URL 风险，不能仅以 RC-10 旧问题开放或“应使用安全 runner”排除。
- 严重性：Important，工程收口前应修。触发需要直接运行测试并连到已有资料库；本次隔离 Acceptance 不受影响，也没有证据表明原库已被改动。它是可造成实际业务不可用的测试安全缺口，不是格式建议。
- 建议修复：该类在任何迁移、连接、夹具写入前要求显式专用临时目标，并严格拒绝缺省/原库/外部 host/其他库与 URL 扩展参数；允许已登记的非 54329 回环端口。目标被拒绝后，`@AfterEach` 也必须不连接、不清理。保留 BUILDING/RETIRED 反例和真实激活行为，不能通过删掉激活断言或放宽线上状态来消除失败。补无需连接数据库的目标拒绝证据，再经专用入口复验受影响的真库集合并刷新当前源码证据。

### Minor

没有另外列出可操作的 Minor 问题；未把压缩代码风格或后续产品能力当作本批缺陷。

## Declined to judge（逐项保留，交执行者裁定）

- G0 当前资料、2026 正文/有效性/传播资格、人工金标准及正式锁定报告集：未完成且明确阻断后续产品门禁，不属于本批代码通过可证明的事项。
- RC-04 一般事实支持、个人资格、数字/否定/条件/例外和完整注入防护：R3 明确只修结构；已有 4→40、合法引用仍 ANSWERED 的 semantic-gap 记录，仍是已知失败，不能关闭父项。
- RC-05/R6 完整时间映射、取代关系、发布清单、不同切分版本的选择与索引内容完整性：当前读取仅验证已声明字段间的一致性；没有因此认定完整生命周期成立。
- 检索后到最终模型回答期间的撤回/权限变化以及完整输出前重新取证：属于后续固定查证工作流；本批的 embedding 后 SQL 状态检查只覆盖其中一个窗口。
- R1 的 10/20/40 真实资料候选预算比较、总体召回率和查证耗时收益：没有锁定人工参考，工程反例及默认 20 不代表最佳预算。
- 真实模型格式违约比例、付费供应商与完整学生端链路：仅审查固定替身/本机 HTTP 桩与选类证据，未运行供应商、学生 HTTP 端到端或旧 3C 全量门禁。
- 重复 JSON 键、尾随 JSON、额外字段、完整 JSON Schema 和精确小数边界政策：R3 验收明确不扩展这些政策，本次不把四字段的严格类型检查说成完整 schema 校验。
- R2 实际 PDF 上传/提取/OCR、原件可视化和跨页条款准确性：浏览器证据使用 SQL 准备的提取态合成候选，没有声称覆盖这些流程。
- R2 后端分页、大文档网络与内存规模：20 条仅约束 DOM，接口仍全量返回；该取舍在 ruling 与验收中明示，规模能力未验收。
- R2 请求在途时继续编辑、重复点击或多个异步加载交错的草稿保护：既有请求函数就未冻结输入或处理并发版本；本批保证的是翻页/筛选保留，现有 10 场景没有验证网络竞态。本次不据其通过声称具备并发编辑保障，后续需单独处理。
- 其他旧测试裸运行时默认原库、IDE/Maven 绕过入口锁以及全仓安全审计：没有全仓修改或验证；只把本批新增的 profile 激活副作用作为上面的 Important 单列，不将其一起排除。
- 全机“绝无外网流量”、真实生产规模性能与部署迁移准备：记录依据是选类、回环桩与凭证移除，非全机网络抓包；小合成库的精确查询计划不是生产性能报告。
- Task 6 尚在组装的台账、最终哈希汇总和回退演练收尾：审查包明确标注待完成，没有已宣称完成可否定；不把缺失元数据误报为假通过，也不提前替其判定通过。

## Recommendations 与 Assessment

先修 P0-REVIEW-01，并以新批次保存拒绝不安全直接运行与专用临时库回归证据。源码变更后，本报告对 522 个哈希的一致性陈述只适用于修复前审查快照，不能沿用为修复后的证明；最终台账应记录该问题、处理决定与复验结果。

**Ready to merge / 当前工程收口：With fixes。** 本批生产修复与计划及已批准的 offline 兼容取舍一致，现有 19 类 142 项证据可核对；新增数据库测试副作用仍需先封住。完成该修复、必要回归与 Task 6 收尾后，可标记“P0 局部工程修复技术验证”；G0、一般事实语义及完整时效仍未通过，不能标记产品上线准备完成。

审查快照：JdbcCandidateRetrieverTest SHA-256 `c4ba93a57af6633fff1403803cd7b38ceacccf83b0dc89319e0f6bd5867b8db6`；JdbcVectorCandidateRetriever `e121c4503964c547f1ac22659a533e091dc392faacbdfd8568914dd571aed76d`；JdbcLexicalCandidateRetriever `a03a0952a0d13c5c566119425cc462e9dc1bc09dc4763b4845b99a729540f1c5`。
