# Task2D独立只读审查

审查者：`/root/r5_audit_review`，gpt-6-astra，high，fresh context。父agent保存返回报告。审查对象为15条白名单before字节到source-review.json当前源码；没有修改文件、索引、HEAD或运行Maven/数据库/容器/供应商。

## Strengths

1. 精确绑定符合目标。JdbcRuleCheckCitationAuditRepository.java:40–58的参数化INSERT SELECT同时匹配固定profile/切分/scope、source/chunk身份、来源版本、entry/chunk checksum及捕获全文。较新BUILDING或其他切分不能抢审计，没有先查latest再插入的空隙；缺条目零行拒绝。
2. 误用路径提前拒绝。新仓储连接前拒绝四参CAPTURE、错误路由及不完整绑定；旧仓储第34行连接前拒绝CAPTURE。旧四参构造器和RETRIEVAL_AUGMENTED有实际真库兼容验证；未知路由仍受CHECK限制。
3. 失败不会变成证据。SQL要求ACTIVE、COMPLETED及run/entry配置一致，已发布、学生可见、分享确认和scope匹配。V9唯一约束保证固定profile/chunk/切分至多一条entry；引用唯一约束阻止重复成功。零行/SQL异常均失败，Capture返回空快照；部分尝试留痕的边界明示。
4. 配置和召回限定准确。快照无召回entryId，绑定固定配置下符合捕获资料的当前entry；不证明原召回entry或自动校验跨组件构造参数一致。账本裁定准确，后续必须统一配置并验收切换。
5. 迁移克制。V22只扩充CHECK，完整保留V9五个旧路由，不更新/删除历史行；当前Git迁移差异未发现V1–V21修改。执行记录是随机无卷回环临时库，V22、连接0、容器退出。
6. 真库证据对应。JdbcRuleCheckCitationAuditTest.java:143连接词法SQL、共享混合服务、reader、Capture、新JDBC审计及规则决策；撤回后证据不足。此条测试向量为空替身，不能单独说证明了双路向量审计闭环。

## Evidence verification

实际XML解析并重算SHA：

|批次|类数|测试|失败|异常|跳过|退出码|
|---|---:|---:|---:|---:|---:|---:|
|Unit RED|2|18|6|0|0|1|
|DB RED|1|18|6|0|0|1|
|Unit GREEN|2|18|0|0|0|0|
|综合验收|22|242|0|0|0|0|

- XML指纹匹配；RED为断言失败，未以环境异常/跳过冒充。
- 综合19类183纯单元、3类59真库；新审计18、目标合同6，计数一致。
- 当前15条审查指纹、11份已存在before字节指纹匹配；542条源码指纹匹配。
- runner合同29通过，外部执行器为计数替身，不能扩大为真实数据库验收。
- 原件PDF/manifest/curation实际指纹与source-readiness.json匹配；原容器停止/未连接依据保存盘点和记录，本审查没有操作原库。
- changes-review.diff只含knowledge路径；另外对照before计划/账本，不漏文档增量。

## Issues

Critical：无。Important：无；未发现使不符捕获身份/版本/正文或当前字段资格成功入新审计链的缺陷。

Minor M1：CitationAuditRecord.java:8及15–16类注释把所有事件描述为“下发”，新增CAPTURE后不准确，可能使统计人员误把捕获时间当送达时间，也未符合AGENTS注释同步要求。建议改为通用审计事件并明确CAPTURE时间含义，保留字段名/四参接口并说明新增绑定字段。运行逻辑正确，不阻断组件收口。

## Recommendations

收口同步处理M1并将新仓储/V22要求补入正式合同或交接。保留配置一致性、未追踪召回entry和最终下发窗口限制，不标完整R5或学生入口完成。

## Declined to judge

- HTTP身份/CSRF、客户端DTO隔离、自然语言匹配、页面：未装配，归后续任务。
- 自动跨组件配置一致及原召回entry追踪：已限定为固定配置下符合捕获资料的当前entry，后续须验证装配。
- 整批审计事务、跨条款一致性、最终HTTP撤回窗口：单条SQL不能证明，未承诺或运行并发验收。
- 索引后续更新的完整历史重放：只证明写入匹配，未提供历史正文快照或永久不可变保证。
- 真实2025语义、条件例外、传播资格和当前年份适用性：合成APPROVED仅用于测试，真实DRAFT、版本别名未自动映射，不替代人工放行。
- 供应商效果、费用、报告质量和学生收益：无真实执行/参与者证据。
- 历史dirty、旧自由RAG事实风险、默认全仓测试：超出before白名单，242选类不关闭历史风险。
- 生产数据上V21→V22升级演练：未执行且不触碰原库；保留旧路由/行判断来自静态迁移，实际仅新临时库。
- 逐个资格谓词完全隔离注入、连接关闭失败、非默认事务DataSource：部分测试同时改变撤回/可见性或分享/可见性，SQL静态支持但不声称每项都有独立动态证据。

## Assessment

Ready to merge? Yes，限Task2D，附Minor注释改进。实现满足固定配置、捕获内容匹配及失败拒绝，XML/源码支持执行结果；不代表HTTP集成、真实规则放行或完整R5完成。

本报告对应修注释前的source-review.json；M1处理及最终验收见review-resolution.json与r5-audit-final-20261008-01，不进行重复审查。
