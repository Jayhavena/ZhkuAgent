# 20题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：20；通过：12；失败：6；不可判定：2；错误：0；通过率：60.0%。

确定整题结论比例：18/20；全部适用维度可评估比例：14/20。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-002 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-003 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-004 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-005 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-008 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-009 | FAIL | true | PASS | FAIL | PASS | PASS | FAIL | FAIL | 
| RFE-010 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-011 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-012 | FAIL | true | PASS | PASS | PASS | PASS | FAIL | PASS | 
| RFE-013 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-014 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-015 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-016 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-017 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-018 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-019 | UNJUDGEABLE | false | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-020 | UNJUDGEABLE | false | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 20 | 0 | 18 | 0 | 2 | 0 | 18/20 |
| factSupport | 20 | 0 | 13 | 5 | 2 | 0 | 18/20 |
| citationIntegrity | 20 | 0 | 14 | 4 | 2 | 0 | 18/20 |
| scopeAndApplicability | 20 | 0 | 14 | 0 | 6 | 0 | 14/20 |
| clarificationAndGap | 20 | 0 | 12 | 2 | 6 | 0 | 14/20 |
| executionSafety | 20 | 0 | 13 | 5 | 2 | 0 | 18/20 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA dea7963a4118765fbdb4c758e908be2927774b66b7fb4d67be46188aef638593；[annotation](RFE-001/ANSWER/annotation-reviewed.json) SHA c59c9b5165ca04f7fb5ecd4a15cd56d9c5c15f870d3875975e1f013bd2195bd9

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[elective-category-distinction, public-course-subcategories]; matched=[public-course-subcategories, elective-category-distinction]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-4, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第四条支持两层分类，回答明确限定在2025.9.2历史文本。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 两层分类均已回答，问题所需条件齐全。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 7a489722d15d4938bf011d0a3a5d8b05bc0cd483192fd6b3260deb54dbc32613；[annotation](RFE-002/ANSWER/annotation-reviewed.json) SHA f1539bc488de1ec98bd5a31450c73a713ba37c683ecf11007309d5db149f7c3e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[undergraduate-plan-obligation]; matched=[]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 该轮仅发布核验失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 发布失败，适用性说明没有产出。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 发布失败，题目所需的澄清或缺口说明没有产出。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA b2b9f9afc636a0678e5dc7be74c98a1c2b4b2a508ea823f877bbfd27cbf472cd；[annotation](RFE-003/ANSWER/annotation-reviewed.json) SHA 702fadf09c9f42e170d111dd66d1518a844cdc4d06cc82125c7f3f5f12ad6ace

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问16学时未超过上限32学时；原文原则值为16学时，所问数值等于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第五条支持原则值16学时和上限32学时；回答保留了原则值的限定，没有将其表述为硬性要求。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问16学时未超过上限32学时；原文原则值为16学时，所问数值等于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 回答了是否必须正好16学时的问题，未将数值比较扩展为具体课程的合规认定。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问16学时未超过上限32学时；原文原则值为16学时，所问数值等于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问16学时未超过上限32学时；原文原则值为16学时，所问数值等于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA 53e125eeca66304706e960273cdcb682925cf9a5fa25528ac37c62578a9a166e；[annotation](RFE-004/ANSWER/annotation-reviewed.json) SHA 670cec5fa4fe04da83218d3164b76843e61f16ff0d8fac22a9bf84ad7aec5076

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第五条支持16学时原则值与32学时上限；24在两值之间，回答明确保留了具体课程合规判断的边界。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 说明具体课程还需核对实际安排与适用例外，没有直接断言违规。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。按原文数值比较：所问24学时未超过上限32学时；原文原则值为16学时，所问数值高于原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA dc91fa03d70f679a3a4ffa8e87de32b95180d3ea4ce1eb763e231eaa512c976e；[annotation](RFE-005/ANSWER/annotation-reviewed.json) SHA 2aee49317917c94f169bb1f596cd09bf42ad47b1b7713fea48b993e697b80671

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 该轮仅发布核验失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 发布失败，适用性说明没有产出。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 发布失败，题目所需的澄清或缺口说明没有产出。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 621f2be1948594ccf1f08957c69ab029d6434f342eeded2c840ac9ffbb3a2282；[annotation](RFE-006/ANSWER/annotation-reviewed.json) SHA d0ff6b196f57199aa147761c2f73c9919c56b0d5aabcd7d45f7e201bc82004da

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 该轮仅发布核验失败提示，未发表规则主张。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 发布失败，适用性说明没有产出。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 发布失败，题目所需的澄清或缺口说明没有产出。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA f93a675f2c2bff63602d128ff1b00f3f0adb8ed4b14ea17de50d65ead954e8bf；[annotation](RFE-007/ANSWER/annotation-reviewed.json) SHA 86683f670bbcb51d0f97298c697dba0141fca5b40f5a1064a1cc55e071f90a3a

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[online-general-half]; matched=[online-general-half]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按对应培养方案的选修课学分和已修记录计算。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-8, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第八条支持一般不超过一半，并以培养方案要求的选修课学分为分母。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按对应培养方案的选修课学分和已修记录计算。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 指出个人计算需要对应培养方案和已修记录。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按对应培养方案的选修课学分和已修记录计算。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按对应培养方案的选修课学分和已修记录计算。

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA ff412fdd8b34f82c2f9bb70454a6c54b673a7ea22fbbd990b7d9ec9fbc2c153e；[annotation](RFE-008/ANSWER/annotation-reviewed.json) SHA d91e30fff32ed31e2415c0e133bc00f757bc4eacb7f821ebd9a38d7e620698ec

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[plan-required-credits]; matched=[plan-required-credits]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-8, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第八条支持按培养方案修读并取得规定学分，回答没有据此推定个人毕业资格。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 明确缺个人成绩、已修学分和适用培养方案，并给出可核查路径。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：学生应按人才培养方案要求，在规定学习年限内修读专业选修课和公共选修课并取得规定学分。 手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。 请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。

## RFE-009

- ANSWER: [capture](RFE-009/ANSWER/capture.json) SHA a124eb14c9915845c8986cbe18acc83bd6dd05ae36673faa4860377ce1d50239；[annotation](RFE-009/ANSWER/annotation-reviewed.json) SHA e9337fbadd859bcc95a5e09ca8c56451075a5ee638212f79ba27ee05236dc165

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan, unenrolled-assessment-disjunction]; matched=[unenrolled-assessment-disjunction]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：未经选课者，不得参加课程考核或考核成绩无效。 本系统没有核验你的选课记录、考核记录和成绩状态，不能替你认定这次成绩是否有效。 请以教务系统个人记录和任课教师/教务处确认为准；本系统只解释已审核规则原文。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-9, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第九条支持所述考核后果，保留了原文的“或”，并明确个人成绩状态仍待核实。; excerpt=依据2025.9.2历史文本：未经选课者，不得参加课程考核或考核成绩无效。 本系统没有核验你的选课记录、考核记录和成绩状态，不能替你认定这次成绩是否有效。 请以教务系统个人记录和任课教师/教务处确认为准；本系统只解释已审核规则原文。

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 虽说明了个人记录缺口，但漏写须按教务处开课计划在规定时间内完成网上选课。; excerpt=依据2025.9.2历史文本：未经选课者，不得参加课程考核或考核成绩无效。 本系统没有核验你的选课记录、考核记录和成绩状态，不能替你认定这次成绩是否有效。 请以教务系统个人记录和任课教师/教务处确认为准；本系统只解释已审核规则原文。

- executionSafety [FAIL]: ANSWER:RUN_STATE_MISMATCH；ANSWER:COVERAGE_MISMATCH；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：未经选课者，不得参加课程考核或考核成绩无效。 本系统没有核验你的选课记录、考核记录和成绩状态，不能替你认定这次成绩是否有效。 请以教务系统个人记录和任课教师/教务处确认为准；本系统只解释已审核规则原文。

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA 14fe5df6e1f1b46f020fe76cd6c402a1228ff6f00fe8c9f8e0d8e3b5d8faa5df；[annotation](RFE-010/ANSWER/annotation-reviewed.json) SHA e1f9809c216e48acd091477904b6590ba53c34327fed91ca2880cc9fc6ee8a20

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-semester-four-credits]; matched=[public-semester-four-credits]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-10, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十条支持每学期公选课最多4学分，回答标明历史版本。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 回答涵盖题目所问的学期、课程范围和学分上限。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

## RFE-011

- ANSWER: [capture](RFE-011/ANSWER/capture.json) SHA d6e48efd1c171ed9bf7249c33bed568805b79754bc3e870b016adfd7d1dd4c31；[annotation](RFE-011/ANSWER/annotation-reviewed.json) SHA 81ace7b2779798f030d63065df6609301a934f5b68fcbb6ebb9e5a433e2843af

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-11]

- factSupport [PASS]: ANSWER:required=[no-repeat-passed-course]; matched=[no-repeat-passed-course]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-11, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十一条支持已考核合格的课程不可重复修读，回答保留了合格这一条件。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 回答直接覆盖重复修读问题。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

## RFE-012

- ANSWER: [capture](RFE-012/ANSWER/capture.json) SHA 99d967bd77a52d17d68fb92df0c5184410269e534c7a0c6644b12c050260466f；[annotation](RFE-012/ANSWER/annotation-reviewed.json) SHA c2f09dda0fd8b63bfc47e914923d68f8a61cca1d7bf2d3024fa2e4ea2620c13f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[no-same-or-similar-plan-course]; matched=[no-same-or-similar-plan-course]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-11, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十一条支持禁止选修与本专业教学计划相同或相近的课程。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 没有说明判断具体课程是否相同或相近时，需要对照教学计划和课程信息。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 7489018c65557bd36bd5316cc4004ec5422956511287a4e7d3ead5d187d0baac；[annotation](RFE-013/ASK/annotation-reviewed.json) SHA 831042ebe89391633cfb9080cb14ffd00fd3f4093cf2f0f8dd47f84fb91c698a
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA a0bef1009a65381a9f9ee1d97337e964259a372c20da163abc231ddf9cb745a6；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-reviewed.json) SHA ef1607e93952a8e1b229d603505dfee768a6878e3602b7006209c39094d1591d

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ASK:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed；RESOLVE_PUBLIC:required=[public-failed-no-retake]; matched=[public-failed-no-retake]；RESOLVE_PUBLIC:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- citationIntegrity [PASS]: ASK:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]；RESOLVE_PUBLIC:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ASK:scopeAndApplicability: Codex 离线独立证据复核: 第十八条对两类课程的重修规定不同，回答未提前替用户选定类别。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 公共选修课和专业选修课的处理规则不同，需要先确认课程类别。这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:scopeAndApplicability: Codex 离线独立证据复核: 第十八条支持公共选修课成绩不合格者不安排重修；答复与用户指定的公共选修课相符。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- clarificationAndGap [PASS]: ASK:clarificationAndGap: Codex 离线独立证据复核: 明确说明分支规则不同，并询问公共或专业选修课；公共选修课续答另行核验。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 公共选修课和专业选修课的处理规则不同，需要先确认课程类别。这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:clarificationAndGap: Codex 离线独立证据复核: 续答使用已明确的课程类别并给出对应结论。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- executionSafety [PASS]: ASK:EXPECTATIONS_MET；ASK:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 公共选修课和专业选修课的处理规则不同，需要先确认课程类别。这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA 27036a7914804ff9b307d3ffa4fbace3b7e3d1fc640516b74f297c864249fef4；[annotation](RFE-014/ANSWER/annotation-reviewed.json) SHA b599b912fadc9920e50a50345f69090b252cd32758de19f8075adabf7a1a15a3

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-failed-no-retake]; matched=[public-failed-no-retake]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十八条支持公共选修课成绩不合格者不安排重修，回答保留课程范围与历史版本。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 按题目给定的课程类别给出完整规则。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 8ceff5139b877baaffbe09b1589bb544b09dc919cf68b4d310709502444465d0；[annotation](RFE-015/ANSWER/annotation-reviewed.json) SHA 84787b1148456b2575dc10daf3e2cb672c4f829ddae9020ccef7d3ae53a48cd6

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[professional-failed-retake-application]; matched=[professional-failed-retake-application]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理条件及最终结果需另行确认。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十八条支持专业选修课成绩不合格者可申请重修，答复保留个人办理条件和批准边界。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理条件及最终结果需另行确认。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 区分可以申请、符合个人条件以及最终批准。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理条件及最终结果需另行确认。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理条件及最终结果需另行确认。

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA 6431027f14a2dc959974029718aba74c2c9e292ad26eded2c199c416dcdfdb03；[annotation](RFE-016/ANSWER/annotation-reviewed.json) SHA 14756788da96131de52730bd2072fa962948c1948ba598eaa9413630649daf33

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-no-deferred-exam]; matched=[public-no-deferred-exam]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十八条支持公共选修课不办理缓考，回答保留课程范围与历史版本。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 直接覆盖是否办理缓考的问题。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

## RFE-017

- ANSWER: [capture](RFE-017/ANSWER/capture.json) SHA 3aaab5fd997cccc10d1ccb7c2cfeb3a9c7adc4f82a0acd00904860a49d450d73；[annotation](RFE-017/ANSWER/annotation-reviewed.json) SHA d4f99657dca09b786eaa972e6fc7c85a87401c43b248cc08ef62c1e934e4414f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-12]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[schedule-order]; matched=[schedule-order]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 逐条核对了已发布主张及其来源，未见无依据主张。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-12, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 第十二条支持确定计划后先安排必修课和专业选修课，再安排全校性公共选修课。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 回答给出完整先后顺序。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 离线独立证据复核: 该轮未声称执行教务操作；终态和动作要求由现有 Trace Grader 核验。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA 50660821170215c5243e02b71b5755fc3467ed4160ae3a8b41bcdbe86cc47529；[annotation](RFE-018/ANSWER/annotation-reviewed.json) SHA 6ca57611262b2d60f5e0fb7a95641d5742462d89a2660c1323640b74fc10b55c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan]; matched=[]；ANSWER:unsupportedClaims: Codex 离线独立证据复核: 该轮仅发布处理失败提示，未发表规则主张。; excerpt=本轮处理未完成，不能将处理失败当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 离线独立证据复核: 发布失败，适用性说明没有产出。; excerpt=本轮处理未完成，不能将处理失败当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 离线独立证据复核: 发布失败，题目所需的澄清或缺口说明没有产出。; excerpt=本轮处理未完成，不能将处理失败当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:MODEL_AUTH_FAILURE

## RFE-019

- ANSWER: UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- retrievalRecall [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- factSupport [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- citationIntegrity [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- clarificationAndGap [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- executionSafety [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

## RFE-020

- ANSWER: UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- retrievalRecall [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- factSupport [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- citationIntegrity [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- clarificationAndGap [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

- executionSafety [UNJUDGEABLE]: ANSWER:UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：0。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。

## 失败分类（OBSERVED_LAYER_V1）

多标签按题去重，类别之间可重叠；宿主/标注错误、待归因项和预期数据边界不计Agent缺陷。自动引用失败需独立细分后才能计缺陷。非缺陷/待归因列排除同类已确认缺陷的题，保留未知可见性。

| 类别 | 已证实Agent缺陷题数 | 非缺陷/待归因题数 |
| --- | --- | --- |
| RETRIEVAL_FAILURE | 0 | 0 |
| CONDITION_JUDGMENT_ERROR | 0 | 0 |
| INSUFFICIENT_EVIDENCE | 0 | 0 |
| CITATION_ERROR | 0 | 4 |
| EXECUTION_EXCEPTION | 4 | 0 |
| ANSWER_INCOMPLETE | 0 | 0 |
| ATTRIBUTION_UNKNOWN | 0 | 4 |

- RFE-002 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-002/ANSWER/capture.json sha256=7a489722d15d4938bf011d0a3a5d8b05bc0cd483192fd6b3260deb54dbc32613]

- RFE-002 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-002/ANSWER/capture.json sha256=7a489722d15d4938bf011d0a3a5d8b05bc0cd483192fd6b3260deb54dbc32613]

- RFE-005 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-005/ANSWER/capture.json sha256=dc91fa03d70f679a3a4ffa8e87de32b95180d3ea4ce1eb763e231eaa512c976e]

- RFE-005 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-005/ANSWER/capture.json sha256=dc91fa03d70f679a3a4ffa8e87de32b95180d3ea4ce1eb763e231eaa512c976e]

- RFE-006 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-006/ANSWER/capture.json sha256=621f2be1948594ccf1f08957c69ab029d6434f342eeded2c840ac9ffbb3a2282]

- RFE-006 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-006/ANSWER/capture.json sha256=621f2be1948594ccf1f08957c69ab029d6434f342eeded2c840ac9ffbb3a2282]

- RFE-009 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[RFE-009/ANSWER/capture.json sha256=a124eb14c9915845c8986cbe18acc83bd6dd05ae36673faa4860377ce1d50239]

- RFE-012 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[RFE-012/ANSWER/capture.json sha256=99d967bd77a52d17d68fb92df0c5184410269e534c7a0c6644b12c050260466f]

- RFE-018 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:MODEL_AUTH_FAILURE；证据：[RFE-018/ANSWER/capture.json sha256=50660821170215c5243e02b71b5755fc3467ed4160ae3a8b41bcdbe86cc47529]

- RFE-018 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-018/ANSWER/capture.json sha256=50660821170215c5243e02b71b5755fc3467ed4160ae3a8b41bcdbe86cc47529]

- RFE-019 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[]

- RFE-020 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[]
