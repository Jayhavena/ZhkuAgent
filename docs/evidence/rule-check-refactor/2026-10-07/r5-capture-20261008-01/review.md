# R5 Task2C 独立只读审查报告

审查者：`/root/r5_capture_review`，gpt-6-astra，high，fresh context。父agent保存返回报告；审查者未运行Maven、数据库、供应商或修改工作区。

**结论：Ready to merge — Yes，限本批证据连接组件增量。** 未发现需要修复的Critical、Important或Minor问题。本结论不代表学生入口、真实资料发布或整个R5已完成。

## 审查范围与方法

审查基线为source-before.json对应的before字节，目标为source-review.json对应的当前白名单源码；没有将历史dirty工作区纳入本次结论。

已阅读审查包、增量、白名单实际源码、Task2C计划、决策合同、范围规范及progress最后三条Task2C Ruling，并追读原文核验器、当前JDBC reader、混合检索、旧回答映射及审计仓储。

- before文件哈希全部一致。
- 12个审查目标的当前哈希全部匹配source-review.json。
- 本批knowledge源码与数据库GREEN、最终回归保存的source-after.json一致。
- 未运行Maven、数据库、供应商，也未修改文件、HEAD或索引。

## Strengths

1. 来源身份沿真实SQL关联传递，兼容路径不会伪造身份。RetrievedChunk.java:23保留七参构造器并明确设为null；RuleCheckEvidenceCapture.java:37在分配别名或读取当前证据前拒绝它。词法、向量两路分别将关联得到的source.id AS source_id传入构造器，未使用chunkId推造sourceId。
2. 整批成功才返回证据，失败没有部分成功出口。RuleCheckEvidenceCapture.java:29先复制输入，拒绝null、空集合、null元素及重复chunk；工厂空值、空白、重复别名和运行时异常均返回空快照拒绝结果。核验或审计失败也不会返回前面已处理的快照。
3. 复用已有完整核验规则，没有用合法ID代替正文核验。RuleCheckEvidenceCapture.java:55对每个候选提交完整正文跨度；现有verifier同时检查实际SHA-256、UTF-16、正文、当前资格、来源/片段身份及版本。成功后集合不可修改。
4. 审计语义和验收范围表述准确。RuleCheckEvidenceCapture.java:61使用独立RULE_CHECK_CAPTURE路由，并明确逐条写入可能留下部分尝试记录；没有声称数据库事务原子性或学生已经看到回答。

## 重点逐项结论

|检查项|结论与依据|
|---|---|
|旧七参对象、两路sourceId、旧v1 DTO|通过。两路SQL均传递真实来源ID。旧链显式构造ApprovedEvidence、ComposedAnswerCitation和CitationView，没有直接序列化RetrievedChunk；新增sourceId未沿这些路径增加对外响应字段。v1学分入口也未改动。|
|重复chunk/别名、异常工厂、null/空输入|通过。同chunk即使来自不同对象或带不同元数据也会按chunkId拒绝；重复别名、空白/null别名、工厂异常均不返回快照。|
|4→40、撤回、身份/版本/hash漂移、reader故障|通过。正文改变但保留旧hash会在本轮核验拒绝；即使正文与hash一起改变，仍须与当前reader的正文、身份、版本和hash一致。任意一项核验拒绝都会令整个批次返回空集合。|
|部分审计失败|通过。测试明确第二条审计失败、第一条已留痕时，捕获仍拒绝且快照为空。路由和注释没有将其解释为已下发或事务提交。|
|当前资格与信任边界|通过。内部临时eligible=true只有在当前reader核验成功后才可返回；JDBC reader按服务端profile、切分版本、scope和已有资格字段读取。捕获不替代后续决策重读，也未声称关闭最终下发窗口。|
|测试证据与产品结论|通过。保存证据明确为合成临时资料、固定embedding和内存审计；没有据此放行2025资料或关闭RC-04-SEMANTICS。|

身份、hash和资格变化的部分反例位于本次回归包含的既有RuleCheckEvidenceVerifierTest；新capture测试验证复用路径及批次拒绝，没有将这些既有反例误算成新增测试。

## 测试证据复核

直接读取保存的XML并重新统计、核对XML哈希：

|阶段|类数|测试数|失败/异常/跳过|
|---|---:|---:|---|
|capture定向GREEN|1|12|0/0/0|
|数据库GREEN|2|41|0/0/0|
|最终纯单元回归|18|177|0/0/0|

三个阶段均记录源码无漂移、资源退出通过，XML哈希一致。数据库证据记录专用回环临时库、合成资料、迁移版本21、测试后连接数0。脚本合同保存29项通过，并明确外部执行器使用计数替身。

这些属于既有执行证据的只读复核，不是本审查重新运行测试。41项数据库测试包含既有reader测试，不能表述成新增41项捕获测试。

## Issues

Critical：无。Important：无。Minor：无。

## Recommendations

按计划保存本次审查并完成本批状态收口即可，无需为通过本次审查扩大实现范围。最终说明应继续采用RETRIEVAL_CAPTURE_VERIFIED_NOT_HTTP_INTEGRATED，保留真实资料审核、运行时模板绑定及学生应用验收的独立门禁。

## Declined to judge

- 真实2025规则的语义正确性、条件完整性、模板选择及资料展示资格：Task2C只连接服务端检索与当前核验，合成证据不能裁定这些事项；B-01/B-02仍开放。
- 自然语言入口、HTTP身份/CSRF、模型选择和学生页面：本批未装配这些路径，不能把未来入口缺失判为本组件缺陷。
- 最终下发/历史再展示撤回窗口，以及跨片段原子一致性：代码和计划明确仅作顺序当前核验；未承诺或提供并发线性化证据。
- JDBC审计实际持久化、最新索引条目解析与核验profile的绑定一致性：本批真库连接验收使用内存审计；既有JDBC仓储仍按chunk解析最新索引条目。本报告不据此认可未来完整JDBC审计装配，需在该装配验收时判定。
- 公开record被其他服务端代码手工构造或未来被当作客户端DTO：当前限定可信服务端调用，无新增HTTP反序列化路径；不能将尚不存在的客户端旁路判为本批漏洞。
- 完整来源生命周期、有效期和发布清单：reader只承诺当前schema已有字段，完整语义仍归R6，未因本次通过而完成。
- 真实供应商表现、费用、报告集质量和用户收益：本次没有对应执行或参与者证据。
- 历史dirty改动、旧自由RAG的事实支持风险及全仓默认测试风险：不属于本批before→当前白名单增量，相关历史风险仍保留。

## Assessment

Ready to merge? Yes，限Task2C本批增量。

来源身份传递、整批核验、审计失败拒绝和旧响应映射符合计划；保存的执行证据与当前源码一致。该组件可按限定阶段收口，不能据此启用学生入口或宣称真实规则与用户收益已验收。
