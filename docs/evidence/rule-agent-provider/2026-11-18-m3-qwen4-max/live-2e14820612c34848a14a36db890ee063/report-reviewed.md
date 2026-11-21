# 20题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：20；通过：4；失败：16；不可判定：0；错误：0；通过率：20.0%。

确定整题结论比例：20/20；全部适用维度可评估比例：4/20。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-002 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-003 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-004 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-005 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-008 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-009 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-010 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-011 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-012 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-013 | FAIL | false | UNJUDGEABLE | UNJUDGEABLE | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-014 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-015 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-016 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-017 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-018 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-019 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-020 | FAIL | false | UNJUDGEABLE | NOT_APPLICABLE | NOT_APPLICABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 20 | 0 | 18 | 0 | 2 | 0 | 18/20 |
| factSupport | 20 | 1 | 4 | 14 | 1 | 0 | 18/19 |
| citationIntegrity | 20 | 1 | 4 | 15 | 0 | 0 | 19/19 |
| scopeAndApplicability | 20 | 0 | 4 | 0 | 16 | 0 | 4/20 |
| clarificationAndGap | 20 | 0 | 4 | 0 | 16 | 0 | 4/20 |
| executionSafety | 20 | 0 | 4 | 16 | 0 | 0 | 20/20 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA ab06af6593b535f0b7fcaa176cfeb539eadf167af45e0928bd4c0ac154b2797d；[annotation](RFE-001/ANSWER/annotation-reviewed.json) SHA bc98ab4146ee715abc0c8ea0212704c9d2d1d21dab45f0b92c14aa9aed976f9f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[elective-category-distinction, public-course-subcategories]; matched=[public-course-subcategories, elective-category-distinction]；ANSWER:unsupportedClaims: Codex 离线证据复核: 已核对发布文本与引用或条文边界，未出现无依据的规则或个人状态断言。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-4, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 第四条支持专业选修课、公共选修课两类及校内、网络两种公选课，回答保留2025.9.2历史版本。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线证据复核: 两层分类均已回答，题目所需条件明确。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线证据复核: 回答未声称执行教务操作；实际动作和终态要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 5fc165788bede1eaf70973a9499ab02687b43bb3a4521f8b560e08c02edb0f94；[annotation](RFE-002/ANSWER/annotation-reviewed.json) SHA 6efb3567923a2c326ab503afbe4e51dfb8230811fd67c2943e836b889844c94c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[undergraduate-plan-obligation]; matched=[undergraduate-plan-obligation]；ANSWER:unsupportedClaims: Codex 离线证据复核: 已核对发布文本与引用或条文边界，未出现无依据的规则或个人状态断言。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的选修规则；没有提供研究生或其他培养类别的规则，不能把本科条文直接推广。 请核对研究生培养方案或对应规定，不要以本科条文代替研究生规则。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-2, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 第二条适用全日制普通本科学生，答复明确现有证据无法外推研究生。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的选修规则；没有提供研究生或其他培养类别的规则，不能把本科条文直接推广。 请核对研究生培养方案或对应规定，不要以本科条文代替研究生规则。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线证据复核: 说明缺研究生适用资料，并提示核对研究生培养方案或对应规定。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的选修规则；没有提供研究生或其他培养类别的规则，不能把本科条文直接推广。 请核对研究生培养方案或对应规定，不要以本科条文代替研究生规则。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线证据复核: 回答未声称执行教务操作；实际动作和终态要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的选修规则；没有提供研究生或其他培养类别的规则，不能把本科条文直接推广。 请核对研究生培养方案或对应规定，不要以本科条文代替研究生规则。

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA 17f24e3f4c468c5d0fd9f343b0f2386039dd7ed9262c5a2f00d55aa0e835721a；[annotation](RFE-003/ANSWER/annotation-reviewed.json) SHA bcbfd299b337052dedc7df9233885979627b18c05583a38c42ea9a60494b6a1c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA 58ee0861ce8f7148b5691be7e9dc5cb4a3fb80193feb4bc5f6951b77d4806276；[annotation](RFE-004/ANSWER/annotation-reviewed.json) SHA 01b9893d9a2cbde99ecabaff123ef6e71713b6f0594f915dc9715691153ed011

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: Codex 离线证据复核: 已核对发布文本与引用或条文边界，未出现无依据的规则或个人状态断言。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 第五条支持16学时原则值与32学时上限，24学时高于原则值但未超上限。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线证据复核: 具体课程合规仍需核对实际安排和适用例外，未作一刀切判定。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线证据复核: 回答未声称执行教务操作；实际动作和终态要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 8d43cf435ce2c879bcf7076a8e34139f1c186b36f5ae781a4d613c95bbd73858；[annotation](RFE-005/ANSWER/annotation-reviewed.json) SHA c6314411d7ef8f3d4098a691087bc0930b2dcd1774503da6b6aa11b80383f8fa

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 9b0ebca76e60e3f4acce47163181f62012aaecd5e61efcdcc5b9d479a4cfb136；[annotation](RFE-006/ANSWER/annotation-reviewed.json) SHA 40a7121322433209585b783974f723b48b82b2ece28dfd332cc9922dd3f83a91

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 24e7acc3718dea898d4be5de717fe490d903c69a1492544780babcc6467b9e60；[annotation](RFE-007/ANSWER/annotation-reviewed.json) SHA 3cb585e9d5cc4bb739c8794389d5a4fa29f4138634d0e883b9c052dca21f0629

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-general-half]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 3959fb8eaa1fb699f9ae40c781913c9d0d62055e75ec260714c86b3cdd10307b；[annotation](RFE-008/ANSWER/annotation-reviewed.json) SHA 7c4aa0361db0d74822de6f648fc88e068fa524b04de8f61d31b6d4cb00f18bcf

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[plan-required-credits]; matched=[plan-required-credits]；ANSWER:unsupportedClaims: Codex 离线证据复核: 已核对发布文本与引用或条文边界，未出现无依据的规则或个人状态断言。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-8, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 第八条支持按培养方案修读并取得规定学分，回答没有推定个人毕业资格。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线证据复核: 明确缺个人成绩、已修学分和适用培养方案，并提示从个人记录或教务核对。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线证据复核: 回答未声称执行教务操作；实际动作和终态要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

## RFE-009

- ANSWER: [capture](RFE-009/ANSWER/capture.json) SHA 2819896f88e55e016bff3d5998d0aa57a5df06d9f0e6cd8c5419de8c8785f7cd；[annotation](RFE-009/ANSWER/annotation-reviewed.json) SHA c31e1e9cb4ec577dcce6915d06ed3d24e99ef1990977c510b2256b3cee957635

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan, unenrolled-assessment-disjunction]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA c4fdf39d548f77e1476e1fdb3b3405df5968aa7618616cb4d2bb76cabd4f2428；[annotation](RFE-010/ANSWER/annotation-reviewed.json) SHA 61204836d4af5c94f17fc130628ed8268e52f898c41347e8daacb7c680f75d13

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[public-semester-four-credits]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-011

- ANSWER: [capture](RFE-011/ANSWER/capture.json) SHA c34ba018eadba467a3f219ed7b3ee1872245bab169d2c67b025a3d81841d1301；[annotation](RFE-011/ANSWER/annotation-reviewed.json) SHA 67b9e5e49bfce6f8dcf66388681d653d540cd79dc0d5edeaa65c0e82165aeb11

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-11]

- factSupport [FAIL]: ANSWER:required=[no-repeat-passed-course]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-012

- ANSWER: [capture](RFE-012/ANSWER/capture.json) SHA 01dcf4ba91e1eb8a22f428fc6b069e9289ffc5e8225a0c9efc051f3c17b86248；[annotation](RFE-012/ANSWER/annotation-reviewed.json) SHA 19c1efd6dc82ed1074756a5f185d084837037ea9ced50a1044eb90041a91f2c4

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[no-same-or-similar-plan-course]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 14efceb330c89b9d2388dc8cc13d6668fb44d66fe7a0d4f574b156f0e3c0529e；[annotation](RFE-013/ASK/annotation-reviewed.json) SHA 98debf72fc520215e868682908b8c20bac4d66a8e9f9068510f3167ed8f4e526
- RESOLVE_PUBLIC: UNJUDGEABLE:NO_WAITING_USER

- retrievalRecall [UNJUDGEABLE]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- factSupport [UNJUDGEABLE]: ASK:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- citationIntegrity [FAIL]: ASK:verified citations=[]；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- scopeAndApplicability [UNJUDGEABLE]: ASK:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- clarificationAndGap [UNJUDGEABLE]: ASK:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- executionSafety [FAIL]: ASK:EXECUTION_FAILED_OR_STOPPED；ASK:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA ab43b16c6ab93afd4208933d885296d0f860dfd2a4fb2bedbdb3729eda1e9c55；[annotation](RFE-014/ANSWER/annotation-reviewed.json) SHA 301ac54573ec604a8f7c303412bf3f5d55ff082148e34044cfaaef4e93dc893a

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[public-failed-no-retake]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 3c18e34c0bf84bb0c00536139fa8fb95ab1be2e204930ed80ea6fc8774aafef2；[annotation](RFE-015/ANSWER/annotation-reviewed.json) SHA e3e5080c9ac70e83801f1260337d31709cf465cf8dc5bce5011cab966e3db677

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[professional-failed-retake-application]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA 1667318dd38fa1dba108bae171a6ec96be351db9208c8517b14ca06df19037c1；[annotation](RFE-016/ANSWER/annotation-reviewed.json) SHA 0d24d03fdc22c145917ee606571b399de005188dcb9764349d129af45dbae7fb

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[public-no-deferred-exam]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-017

- ANSWER: [capture](RFE-017/ANSWER/capture.json) SHA ecb96e69a011185cddc1082eb7dad45b588b7e6b3b758377727efed306b86be3；[annotation](RFE-017/ANSWER/annotation-reviewed.json) SHA f0b9fc12f2d1826d94eefd06507d5292600fe16131e9d5d1b2cf9df4422cacac

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-12]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[schedule-order]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA b39decabb86b5b15ae814fd505f65b813ebc013c37d2fe7b8e386ebdc69b074e；[annotation](RFE-018/ANSWER/annotation-reviewed.json) SHA fcf41c8da0661315b5d6df4458477c394c7d4ded6271bdef549d4a98e41fdaa1

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-019

- ANSWER: [capture](RFE-019/ANSWER/capture.json) SHA 05b5764225703a91eac79ecdcf5c498b58295f4e14fa558a409aae9aa3ee04e7；[annotation](RFE-019/ANSWER/annotation-reviewed.json) SHA f07a892480fe1a983a62236e2fe9294acabe54dd5159e629c06f585637705030

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[undergraduate-plan-obligation]; matched=[]；ANSWER:unsupportedClaims: Codex 离线证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA 79662f76d3fd10023b0820eef3285b8780c2e8ea1adc5402e57f2f0700087cbe；[annotation](RFE-020/ANSWER/annotation-reviewed.json) SHA 510bd05624b0ba8958c04c8083c2a8f74e153b8e3c00b0fbc5ee2cb81bc53c76

- retrievalRecall [UNJUDGEABLE]: ANSWER:RETRIEVAL_OBSERVATION_MISSING

- factSupport [NOT_APPLICABLE]: ANSWER:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed

- citationIntegrity [NOT_APPLICABLE]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线证据复核: 该轮未产出可以核验的适用性说明。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线证据复核: 该轮未产出题目要求的澄清或资料缺口说明。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：0。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。

## 失败分类（OBSERVED_LAYER_V1）

多标签按题去重，类别之间可重叠；宿主/标注错误、待归因项和预期数据边界不计Agent缺陷。自动引用失败需独立细分后才能计缺陷。非缺陷/待归因列排除同类已确认缺陷的题，保留未知可见性。

| 类别 | 已证实Agent缺陷题数 | 非缺陷/待归因题数 |
| --- | --- | --- |
| RETRIEVAL_FAILURE | 0 | 0 |
| CONDITION_JUDGMENT_ERROR | 0 | 0 |
| INSUFFICIENT_EVIDENCE | 0 | 2 |
| CITATION_ERROR | 0 | 15 |
| EXECUTION_EXCEPTION | 16 | 0 |
| ANSWER_INCOMPLETE | 0 | 0 |
| ATTRIBUTION_UNKNOWN | 0 | 0 |

- RFE-002 / INSUFFICIENT_EVIDENCE / EXPECTED_MATERIAL_BOUNDARY / defect=false: Codex 离线证据复核: 历史手册只提供本科对象条文，研究生培养规定缺失属于题目预期范围边界。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。
本手册只核验全日制普通本科学生的选修规则；没有提供研究生或其他培养类别的规则，不能把本科条文直接推广。
请核对研究生培养方案或对应规定，不要以本科条文代替研究生规则。；证据：[RFE-002/ANSWER/capture.json sha256=5fc165788bede1eaf70973a9499ab02687b43bb3a4521f8b560e08c02edb0f94]

- RFE-003 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-003/ANSWER/capture.json sha256=17f24e3f4c468c5d0fd9f343b0f2386039dd7ed9262c5a2f00d55aa0e835721a]

- RFE-003 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-003/ANSWER/capture.json sha256=17f24e3f4c468c5d0fd9f343b0f2386039dd7ed9262c5a2f00d55aa0e835721a]

- RFE-005 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-005/ANSWER/capture.json sha256=8d43cf435ce2c879bcf7076a8e34139f1c186b36f5ae781a4d613c95bbd73858]

- RFE-005 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-005/ANSWER/capture.json sha256=8d43cf435ce2c879bcf7076a8e34139f1c186b36f5ae781a4d613c95bbd73858]

- RFE-006 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-006/ANSWER/capture.json sha256=9b0ebca76e60e3f4acce47163181f62012aaecd5e61efcdcc5b9d479a4cfb136]

- RFE-006 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-006/ANSWER/capture.json sha256=9b0ebca76e60e3f4acce47163181f62012aaecd5e61efcdcc5b9d479a4cfb136]

- RFE-007 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-007/ANSWER/capture.json sha256=24e7acc3718dea898d4be5de717fe490d903c69a1492544780babcc6467b9e60]

- RFE-007 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-007/ANSWER/capture.json sha256=24e7acc3718dea898d4be5de717fe490d903c69a1492544780babcc6467b9e60]

- RFE-008 / INSUFFICIENT_EVIDENCE / EXPECTED_MATERIAL_BOUNDARY / defect=false: Codex 离线证据复核: 手册没有个人成绩、已修学分和适用培养方案，无法完成个人毕业学分计算。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。
手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。
请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。；证据：[RFE-008/ANSWER/capture.json sha256=3959fb8eaa1fb699f9ae40c781913c9d0d62055e75ec260714c86b3cdd10307b]

- RFE-009 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-009/ANSWER/capture.json sha256=2819896f88e55e016bff3d5998d0aa57a5df06d9f0e6cd8c5419de8c8785f7cd]

- RFE-009 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-009/ANSWER/capture.json sha256=2819896f88e55e016bff3d5998d0aa57a5df06d9f0e6cd8c5419de8c8785f7cd]

- RFE-010 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-010/ANSWER/capture.json sha256=c4fdf39d548f77e1476e1fdb3b3405df5968aa7618616cb4d2bb76cabd4f2428]

- RFE-010 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-010/ANSWER/capture.json sha256=c4fdf39d548f77e1476e1fdb3b3405df5968aa7618616cb4d2bb76cabd4f2428]

- RFE-011 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-011/ANSWER/capture.json sha256=c34ba018eadba467a3f219ed7b3ee1872245bab169d2c67b025a3d81841d1301]

- RFE-011 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-011/ANSWER/capture.json sha256=c34ba018eadba467a3f219ed7b3ee1872245bab169d2c67b025a3d81841d1301]

- RFE-012 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-012/ANSWER/capture.json sha256=01dcf4ba91e1eb8a22f428fc6b069e9289ffc5e8225a0c9efc051f3c17b86248]

- RFE-012 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-012/ANSWER/capture.json sha256=01dcf4ba91e1eb8a22f428fc6b069e9289ffc5e8225a0c9efc051f3c17b86248]

- RFE-013 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ASK:EXECUTION_FAILED_OR_STOPPED;ASK:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE;RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER；证据：[RFE-013/ASK/capture.json sha256=14efceb330c89b9d2388dc8cc13d6668fb44d66fe7a0d4f574b156f0e3c0529e]

- RFE-013 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ASK:verified citations=[];RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER；证据：[RFE-013/ASK/capture.json sha256=14efceb330c89b9d2388dc8cc13d6668fb44d66fe7a0d4f574b156f0e3c0529e]

- RFE-014 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-014/ANSWER/capture.json sha256=ab43b16c6ab93afd4208933d885296d0f860dfd2a4fb2bedbdb3729eda1e9c55]

- RFE-014 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-014/ANSWER/capture.json sha256=ab43b16c6ab93afd4208933d885296d0f860dfd2a4fb2bedbdb3729eda1e9c55]

- RFE-015 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-015/ANSWER/capture.json sha256=3c18e34c0bf84bb0c00536139fa8fb95ab1be2e204930ed80ea6fc8774aafef2]

- RFE-015 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-015/ANSWER/capture.json sha256=3c18e34c0bf84bb0c00536139fa8fb95ab1be2e204930ed80ea6fc8774aafef2]

- RFE-016 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-016/ANSWER/capture.json sha256=1667318dd38fa1dba108bae171a6ec96be351db9208c8517b14ca06df19037c1]

- RFE-016 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-016/ANSWER/capture.json sha256=1667318dd38fa1dba108bae171a6ec96be351db9208c8517b14ca06df19037c1]

- RFE-017 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-017/ANSWER/capture.json sha256=ecb96e69a011185cddc1082eb7dad45b588b7e6b3b758377727efed306b86be3]

- RFE-017 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-017/ANSWER/capture.json sha256=ecb96e69a011185cddc1082eb7dad45b588b7e6b3b758377727efed306b86be3]

- RFE-018 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-018/ANSWER/capture.json sha256=b39decabb86b5b15ae814fd505f65b813ebc013c37d2fe7b8e386ebdc69b074e]

- RFE-018 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-018/ANSWER/capture.json sha256=b39decabb86b5b15ae814fd505f65b813ebc013c37d2fe7b8e386ebdc69b074e]

- RFE-019 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-019/ANSWER/capture.json sha256=05b5764225703a91eac79ecdcf5c498b58295f4e14fa558a409aae9aa3ee04e7]

- RFE-019 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-019/ANSWER/capture.json sha256=05b5764225703a91eac79ecdcf5c498b58295f4e14fa558a409aae9aa3ee04e7]

- RFE-020 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:NO_PROGRESS；证据：[RFE-020/ANSWER/capture.json sha256=79662f76d3fd10023b0820eef3285b8780c2e8ea1adc5402e57f2f0700087cbe]
