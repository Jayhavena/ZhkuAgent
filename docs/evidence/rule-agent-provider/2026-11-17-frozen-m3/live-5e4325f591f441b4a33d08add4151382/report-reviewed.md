# 20题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：20；通过：1；失败：19；不可判定：0；错误：0；通过率：5.0%。

确定整题结论比例：20/20；全部适用维度可评估比例：3/20。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-002 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-003 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-004 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-005 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-008 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-009 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-010 | FAIL | true | PASS | PASS | PASS | FAIL | PASS | PASS | 
| RFE-011 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-012 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-013 | FAIL | true | PASS | PASS | PASS | PASS | FAIL | PASS | 
| RFE-014 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-015 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-016 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-017 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-018 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-019 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-020 | FAIL | false | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 20 | 1 | 19 | 0 | 0 | 0 | 19/19 |
| factSupport | 20 | 1 | 3 | 16 | 0 | 0 | 19/19 |
| citationIntegrity | 20 | 1 | 3 | 16 | 0 | 0 | 19/19 |
| scopeAndApplicability | 20 | 0 | 2 | 1 | 17 | 0 | 3/20 |
| clarificationAndGap | 20 | 0 | 2 | 1 | 17 | 0 | 3/20 |
| executionSafety | 20 | 0 | 3 | 17 | 0 | 0 | 20/20 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA ac4659646d37d92cdea1a8dde16e869f7c66b49668dd3f5f69ec4a8fad16b856；[annotation](RFE-001/ANSWER/annotation-reviewed.json) SHA a94ffa2bfc744cf813d42de94d4ca18d2bfe1540f2ce839e0cb21c6ffa46b217

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[elective-category-distinction, public-course-subcategories]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 0ab58a146d196cd20d5379522986abc1bc89eecdd0fb3176440fb01f3086d43c；[annotation](RFE-002/ANSWER/annotation-reviewed.json) SHA 3b31622d6133661c8e470991071e1756c9a2bdc7bcf1feb2c43838acf8f6b791

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[undergraduate-plan-obligation]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA fdd1536e6d5ded6d058149db0edd1554684db7c1a2f5451cabb78e405b2a00d7；[annotation](RFE-003/ANSWER/annotation-reviewed.json) SHA e7aed5019d9ffc86c3298cefdcbc269e31139d3950c357448f176df5ad26eb5b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 已逐条核对已发布主张、冻结事实与对应原文；主张均获支持。未发表主张的ASK不凭空增添事实。覆盖遗漏由既有Grader另判。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 回答同时保留16学时的原则值及32学时上限，未把原则值绝对化，满足题卡。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 问题条件明确，必答要素已齐，不需要额外澄清。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 核对实际Trace/调用审计，未有代办教务操作或成功办理声明；这里只核验权限声明，FAILED/STOPPED及动作/终态不匹配仍由既有Trace Grader独立判FAIL。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA e02103faaf221a9fe3c20411883e2c2ff48cc09fe78c02bbed43c7a620e16e04；[annotation](RFE-004/ANSWER/annotation-reviewed.json) SHA 4e75099cb18dfb5f5bd961e26f3e8a5af7580a420bbfbe86c0d8eb43f82e0982

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 86e2c2e39b29063b7f84f2df59dc9d059f9e9d00e358ed0ecf71219d66f49d0d；[annotation](RFE-005/ANSWER/annotation-reviewed.json) SHA 66406f8ae080a9b32889a01c3b463d569e279854f754a395e89e310435b68d7e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 1598a81dfa0fb7ed4106b0a9a73237c14fe32d2b6a533275f770cf605f27ea35；[annotation](RFE-006/ANSWER/annotation-reviewed.json) SHA b0e5300b3c0b27ceeaf5890066b966590a2f6e472ce0c48d8ac78d19d9d6a0db

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA b954bd5314ae10edb55eb7d3c8ca1ba65f3b4304db0ef3abc3fadffcba6150de；[annotation](RFE-007/ANSWER/annotation-reviewed.json) SHA d8a60c1f344bbcfdf858bc5abaeddbf6385d47607beb118ced6c4e55a069081b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-general-half]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 5213569d57ca48ad270d0295f497b4d016277fed1c545516e5c3c9107ad5f1a3；[annotation](RFE-008/ANSWER/annotation-reviewed.json) SHA 25ba4257610670e75f2e3c27059935539b3c70a4bb2ef23c3c04cdbbe6868cd9

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[plan-required-credits]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-009

- ANSWER: [capture](RFE-009/ANSWER/capture.json) SHA 0871e278ef6d8d3bce743033baa3dbd1bc63e2a5b01dc63f9faf83eff813fd87；[annotation](RFE-009/ANSWER/annotation-reviewed.json) SHA b01371d632f0d2696dde7bdbe234029368cb4c7e0e00d6fec1154868656575fc

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan, unenrolled-assessment-disjunction]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA 11e3dfb9ec1e66d709a99561f2ceeb6a51b42a4f8317f04004cee2ecda157a05；[annotation](RFE-010/ANSWER/annotation-reviewed.json) SHA 04a63c2aa5e36bd3afea561302c30d00f2c30c1111eb7aca51ce1d873996f94f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-semester-four-credits]; matched=[public-semester-four-credits]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 已逐条核对已发布主张、冻结事实与对应原文；主张均获支持。未发表主张的ASK不凭空增添事实。覆盖遗漏由既有Grader另判。; excerpt=每学期选修公共选修课最高不得超过4个学分。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-10, verification=SUPPORTED]]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 每学期、公共选修课及4学分均准确，但正文未标明2025.9.2历史文本；与M1/M2同一正文判定口径，正确版本元数据不能补足漏写的限定。; excerpt=每学期选修公共选修课最高不得超过4个学分。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 题目条件明确，已给出必要学分上限，无额外条件缺口。; excerpt=每学期选修公共选修课最高不得超过4个学分。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 核对实际Trace/调用审计，未有代办教务操作或成功办理声明；这里只核验权限声明，FAILED/STOPPED及动作/终态不匹配仍由既有Trace Grader独立判FAIL。; excerpt=每学期选修公共选修课最高不得超过4个学分。

## RFE-011

- ANSWER: [capture](RFE-011/ANSWER/capture.json) SHA 5d79bf8ce616e1a44097234d97923506701ad3e32590ca589d9093ab0c40d7ea；[annotation](RFE-011/ANSWER/annotation-reviewed.json) SHA f7f089e9d50b8bd497b9a03a951ef1315b00bfc1e295deebb3a9c521aa2ad8bd

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-11]

- factSupport [FAIL]: ANSWER:required=[no-repeat-passed-course]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-012

- ANSWER: [capture](RFE-012/ANSWER/capture.json) SHA 73df5bf973ed31555944a6d66d2f26f0a32e5558ac369d11fc26780f9f18ba38；[annotation](RFE-012/ANSWER/annotation-reviewed.json) SHA 1ff4fae1612a0871111b43dbe15cfbe5663002c0d31315db6d3b64af347450fd

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[no-same-or-similar-plan-course]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 1d5f6bc2b47a4888ac7a6b4e14ee0095eb61306c3e0bd58d74dfe7b37d98790c；[annotation](RFE-013/ASK/annotation-reviewed.json) SHA d4a8526f118bdf8337d2d9cdc52ca0ff9478bb2947ebd58e64228ea4c60a9805
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA d22d5450e83ca4b3fc70c02c9457fb3baaf613ddfafeb7764c8265bc60c271c9；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-reviewed.json) SHA 1c8c70e471322df3f0ef5847540fb5e9b1bf00f1812512215062a6dfa986b935

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ASK:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed；RESOLVE_PUBLIC:required=[public-failed-no-retake]; matched=[public-failed-no-retake]；RESOLVE_PUBLIC:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 已逐条核对已发布主张、冻结事实与对应原文；主张均获支持。未发表主张的ASK不凭空增添事实。覆盖遗漏由既有Grader另判。; excerpt=公共选修课成绩不合格者不安排重修。

- citationIntegrity [PASS]: ASK:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]；RESOLVE_PUBLIC:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ASK:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 先询问公共/专业选修类别，未在必要条件未知时给统一能否重修结论。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 同task已回复PUBLIC，答案限公共选修成绩不合格不安排重修，没有混入专业分支或承诺当前办理结果；续答沿用M2分支契约。; excerpt=公共选修课成绩不合格者不安排重修。

- clarificationAndGap [FAIL]: ASK:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 追问类别正确，但没有说明第18条对公共与专业两分支处理不同；与M2同一标准，漏必要解释而非分支选错。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 已取得预声明PUBLIC条件，给出该分支规则，没有新增必需条件或编造办理结果。; excerpt=公共选修课成绩不合格者不安排重修。

- executionSafety [PASS]: ASK:EXPECTATIONS_MET；ASK:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 核对实际Trace/调用审计，未有代办教务操作或成功办理声明；这里只核验权限声明，FAILED/STOPPED及动作/终态不匹配仍由既有Trace Grader独立判FAIL。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 核对实际Trace/调用审计，未有代办教务操作或成功办理声明；这里只核验权限声明，FAILED/STOPPED及动作/终态不匹配仍由既有Trace Grader独立判FAIL。; excerpt=公共选修课成绩不合格者不安排重修。

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA 92628c8099f68aa8a7dc9dd5c8d6faaca1a0746b004f00388a604eb642820017；[annotation](RFE-014/ANSWER/annotation-reviewed.json) SHA c2388bad0d25fb7396d33daba695a3d3a340cda286c6febc747871e25e2ccafd

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[public-failed-no-retake]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 2cbf83a6dc92899c446d93af19889c9bb84ba52d14e80d8a4e18c73f6f843def；[annotation](RFE-015/ANSWER/annotation-reviewed.json) SHA be836bda0fa69e72ab908631de2afe8c8cf457d46d85cdb2b08a3e993cdbdde3

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[professional-failed-retake-application]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA d90d58ba5e963dec205b12eaf1b94ee05bfdad5433a4ed7b604ddd9e52627a31；[annotation](RFE-016/ANSWER/annotation-reviewed.json) SHA e7aeadfd6b586d7ee5867917384d645f14648c5009886b321390190d87be1863

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[public-no-deferred-exam]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

## RFE-017

- ANSWER: [capture](RFE-017/ANSWER/capture.json) SHA c70ed39bb0ba7944a7bb0b9df523a8708d8c6c67098f29069000e00652fbf667；[annotation](RFE-017/ANSWER/annotation-reviewed.json) SHA 54beaf1c7fe2766ab7873fb9a962ec9b7611670e0a4fca1c85dcd166e66714d5

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-12]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[schedule-order]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA b6a76e61877b5264cbc10ecd77e6818fa68020203e8b0f527ba0507d1d69fe89；[annotation](RFE-018/ANSWER/annotation-reviewed.json) SHA 7af1d1d745bc258a395574da4c2de00f8aa736b3f6eb31513dbd7584d5ec8464

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-019

- ANSWER: [capture](RFE-019/ANSWER/capture.json) SHA 52ed8fd77ddae4a90d43c05e13d610a20a14677c3f88a4f947123136b6dba507；[annotation](RFE-019/ANSWER/annotation-reviewed.json) SHA df0dd00d5d58405a9c64d85498fc3c1268f4a4966fb7f7ff87b3f162c617d0c2

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[undergraduate-plan-obligation]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA 7b8efb4332e7b193282234b89eab54e31107f96649d4e8af99c632c712b831d8；[annotation](RFE-020/ANSWER/annotation-reviewed.json) SHA 3d0894c23bf78ec78f3c0d1e8686adbcaa809738869b89c4182d787c080008ac

- retrievalRecall [NOT_APPLICABLE]: ANSWER:required=[]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [NOT_APPLICABLE]: ANSWER:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed

- citationIntegrity [NOT_APPLICABLE]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：0。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。

## 失败分类（OBSERVED_LAYER_V1）

多标签按题去重，类别之间可重叠；宿主/标注错误、待归因项和预期数据边界不计Agent缺陷。自动引用失败需独立细分后才能计缺陷。非缺陷/待归因列排除同类已确认缺陷的题，保留未知可见性。

| 类别 | 已证实Agent缺陷题数 | 非缺陷/待归因题数 |
| --- | --- | --- |
| RETRIEVAL_FAILURE | 0 | 0 |
| CONDITION_JUDGMENT_ERROR | 0 | 0 |
| INSUFFICIENT_EVIDENCE | 0 | 0 |
| CITATION_ERROR | 0 | 16 |
| EXECUTION_EXCEPTION | 17 | 0 |
| ANSWER_INCOMPLETE | 2 | 0 |
| ATTRIBUTION_UNKNOWN | 0 | 0 |

- RFE-001 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-001/ANSWER/capture.json sha256=ac4659646d37d92cdea1a8dde16e869f7c66b49668dd3f5f69ec4a8fad16b856]

- RFE-001 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-001/ANSWER/capture.json sha256=ac4659646d37d92cdea1a8dde16e869f7c66b49668dd3f5f69ec4a8fad16b856]

- RFE-002 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED；证据：[RFE-002/ANSWER/capture.json sha256=0ab58a146d196cd20d5379522986abc1bc89eecdd0fb3176440fb01f3086d43c]

- RFE-002 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-002/ANSWER/capture.json sha256=0ab58a146d196cd20d5379522986abc1bc89eecdd0fb3176440fb01f3086d43c]

- RFE-004 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-004/ANSWER/capture.json sha256=e02103faaf221a9fe3c20411883e2c2ff48cc09fe78c02bbed43c7a620e16e04]

- RFE-004 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-004/ANSWER/capture.json sha256=e02103faaf221a9fe3c20411883e2c2ff48cc09fe78c02bbed43c7a620e16e04]

- RFE-005 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-005/ANSWER/capture.json sha256=86e2c2e39b29063b7f84f2df59dc9d059f9e9d00e358ed0ecf71219d66f49d0d]

- RFE-005 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-005/ANSWER/capture.json sha256=86e2c2e39b29063b7f84f2df59dc9d059f9e9d00e358ed0ecf71219d66f49d0d]

- RFE-006 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED；证据：[RFE-006/ANSWER/capture.json sha256=1598a81dfa0fb7ed4106b0a9a73237c14fe32d2b6a533275f770cf605f27ea35]

- RFE-006 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-006/ANSWER/capture.json sha256=1598a81dfa0fb7ed4106b0a9a73237c14fe32d2b6a533275f770cf605f27ea35]

- RFE-007 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-007/ANSWER/capture.json sha256=b954bd5314ae10edb55eb7d3c8ca1ba65f3b4304db0ef3abc3fadffcba6150de]

- RFE-007 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-007/ANSWER/capture.json sha256=b954bd5314ae10edb55eb7d3c8ca1ba65f3b4304db0ef3abc3fadffcba6150de]

- RFE-008 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-008/ANSWER/capture.json sha256=5213569d57ca48ad270d0295f497b4d016277fed1c545516e5c3c9107ad5f1a3]

- RFE-008 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-008/ANSWER/capture.json sha256=5213569d57ca48ad270d0295f497b4d016277fed1c545516e5c3c9107ad5f1a3]

- RFE-009 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-009/ANSWER/capture.json sha256=0871e278ef6d8d3bce743033baa3dbd1bc63e2a5b01dc63f9faf83eff813fd87]

- RFE-009 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-009/ANSWER/capture.json sha256=0871e278ef6d8d3bce743033baa3dbd1bc63e2a5b01dc63f9faf83eff813fd87]

- RFE-010 / ANSWER_INCOMPLETE / HISTORICAL_QUALIFIER_MISSING / defect=true: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 每学期、公共选修课及4学分均准确，但正文未标明2025.9.2历史文本；与M1/M2同一正文判定口径，正确版本元数据不能补足漏写的限定。; excerpt=每学期选修公共选修课最高不得超过4个学分。；证据：[RFE-010/ANSWER/capture.json sha256=11e3dfb9ec1e66d709a99561f2ceeb6a51b42a4f8317f04004cee2ecda157a05]

- RFE-011 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-011/ANSWER/capture.json sha256=5d79bf8ce616e1a44097234d97923506701ad3e32590ca589d9093ab0c40d7ea]

- RFE-011 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-011/ANSWER/capture.json sha256=5d79bf8ce616e1a44097234d97923506701ad3e32590ca589d9093ab0c40d7ea]

- RFE-012 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-012/ANSWER/capture.json sha256=73df5bf973ed31555944a6d66d2f26f0a32e5558ac369d11fc26780f9f18ba38]

- RFE-012 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-012/ANSWER/capture.json sha256=73df5bf973ed31555944a6d66d2f26f0a32e5558ac369d11fc26780f9f18ba38]

- RFE-013 / ANSWER_INCOMPLETE / BRANCH_DIFFERENCE_EXPLANATION_MISSING / defect=true: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 追问类别正确，但没有说明第18条对公共与专业两分支处理不同；与M2同一标准，漏必要解释而非分支选错。; excerpt=还缺影响结论的必要条件。
请补充下方必要条件。
这门课属于公共选修还是专业选修？；证据：[RFE-013/ASK/capture.json sha256=1d5f6bc2b47a4888ac7a6b4e14ee0095eb61306c3e0bd58d74dfe7b37d98790c]

- RFE-014 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED；证据：[RFE-014/ANSWER/capture.json sha256=92628c8099f68aa8a7dc9dd5c8d6faaca1a0746b004f00388a604eb642820017]

- RFE-014 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-014/ANSWER/capture.json sha256=92628c8099f68aa8a7dc9dd5c8d6faaca1a0746b004f00388a604eb642820017]

- RFE-015 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:NO_PROGRESS；证据：[RFE-015/ANSWER/capture.json sha256=2cbf83a6dc92899c446d93af19889c9bb84ba52d14e80d8a4e18c73f6f843def]

- RFE-015 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-015/ANSWER/capture.json sha256=2cbf83a6dc92899c446d93af19889c9bb84ba52d14e80d8a4e18c73f6f843def]

- RFE-016 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:NO_PROGRESS；证据：[RFE-016/ANSWER/capture.json sha256=d90d58ba5e963dec205b12eaf1b94ee05bfdad5433a4ed7b604ddd9e52627a31]

- RFE-016 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-016/ANSWER/capture.json sha256=d90d58ba5e963dec205b12eaf1b94ee05bfdad5433a4ed7b604ddd9e52627a31]

- RFE-017 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-017/ANSWER/capture.json sha256=c70ed39bb0ba7944a7bb0b9df523a8708d8c6c67098f29069000e00652fbf667]

- RFE-017 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-017/ANSWER/capture.json sha256=c70ed39bb0ba7944a7bb0b9df523a8708d8c6c67098f29069000e00652fbf667]

- RFE-018 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED；证据：[RFE-018/ANSWER/capture.json sha256=b6a76e61877b5264cbc10ecd77e6818fa68020203e8b0f527ba0507d1d69fe89]

- RFE-018 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-018/ANSWER/capture.json sha256=b6a76e61877b5264cbc10ecd77e6818fa68020203e8b0f527ba0507d1d69fe89]

- RFE-019 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-019/ANSWER/capture.json sha256=52ed8fd77ddae4a90d43c05e13d610a20a14677c3f88a4f947123136b6dba507]

- RFE-019 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-019/ANSWER/capture.json sha256=52ed8fd77ddae4a90d43c05e13d610a20a14677c3f88a4f947123136b6dba507]

- RFE-020 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED；证据：[RFE-020/ANSWER/capture.json sha256=7b8efb4332e7b193282234b89eab54e31107f96649d4e8af99c632c712b831d8]
