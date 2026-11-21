# 20题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：20；通过：12；失败：8；不可判定：0；错误：0；通过率：60.0%。

确定整题结论比例：20/20；全部适用维度可评估比例：15/20。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-002 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-003 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-004 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-005 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-008 | FAIL | true | PASS | FAIL | FAIL | FAIL | FAIL | FAIL | 
| RFE-009 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-010 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-011 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-012 | FAIL | true | PASS | PASS | PASS | PASS | FAIL | PASS | 
| RFE-013 | FAIL | false | UNJUDGEABLE | UNJUDGEABLE | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-014 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-015 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-016 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-017 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-018 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-019 | FAIL | true | PASS | FAIL | FAIL | FAIL | FAIL | FAIL | 
| RFE-020 | FAIL | false | UNJUDGEABLE | NOT_APPLICABLE | NOT_APPLICABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 20 | 0 | 18 | 0 | 2 | 0 | 18/20 |
| factSupport | 20 | 1 | 13 | 5 | 1 | 0 | 18/19 |
| citationIntegrity | 20 | 1 | 13 | 6 | 0 | 0 | 19/19 |
| scopeAndApplicability | 20 | 0 | 13 | 2 | 5 | 0 | 15/20 |
| clarificationAndGap | 20 | 0 | 12 | 3 | 5 | 0 | 15/20 |
| executionSafety | 20 | 0 | 13 | 7 | 0 | 0 | 20/20 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA a23d4212ab7bd9ed93be150e0f8de6bcf672c86a5cb9613511c2e61740656a7b；[annotation](RFE-001/ANSWER/annotation-reviewed.json) SHA cb0c67ff7c2d4c33ef7c26d8a10e553c061a68e4368bcdf414fad50fcfb2b677

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[elective-category-distinction, public-course-subcategories]; matched=[public-course-subcategories, elective-category-distinction]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 两条发布主张分别与第四条原文的两级分类表述逐字一致，引用原文核对无差异，无无支持主张。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-4, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 两类选修课及公共选修课子类与第四条一致，正文逐句限定为2025.9.2历史文本，未跨课程类别或版本外推。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答选修课两级分类与公共选修课子类两问，无需补充条件。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，纯规则查询答复。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA a1ee549c0a098469dfa2270694627541f0b42f2ff15ba85c2cea27a0ccd80a22；[annotation](RFE-002/ANSWER/annotation-reviewed.json) SHA 3ed0f688eeec340cac66d596e4ecf52f7d4acf2f8d26a6342acfef260c5e196e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[undergraduate-plan-obligation]; matched=[undergraduate-plan-obligation]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 发布的本科对象义务主张与第二条原文一致；研究生不适用与需另行核对研究生规定的说明均符合语料边界，无无支持主张。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-2, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 明确该条文字面对象是全日制普通本科学生，声明不能把本科条文推广到研究生并建议核对研究生培养方案，未跨对象外推。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答研究生不能由该手册条文推出适用，并给出核对研究生培养方案或对应规定的下一步。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：全日制普通本科学生必须按人才培养方案要求参加选修课学习。 本手册只核验全日制普通本科学生的

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA ab95a811a5c974d7e42231e81cf03b220b240cac75a3e2e9ec9076a4997f8114；[annotation](RFE-003/ANSWER/annotation-reviewed.json) SHA 47d7d41d7fad9a4057e508bcfa0c853908d79a3b1ce694f677339a10c32c8773

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 16学时原则值与32学时上限均取自第五条原文；按原文数值比较只是对原文数字的直接对照，未添加无依据主张。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 保留“原则上”限定词，数值限定于公共选修课并注明2025.9.2历史文本，未把16学时说成绝对固定值，也未混淆学时与学分。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答学时规定并纠正“必须正好16学时”的理解：原则值16、上限32，且说明仅凭该条不能判定整体合规或违规。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA c2c90207c7585a3eb5b8584a1f59e9064851663ce80c7916e9937241d88e3536；[annotation](RFE-004/ANSWER/annotation-reviewed.json) SHA 5992c6a372a575672fd07c9e484b96f10a4ad8daa7f770f81827dbe88a4695a8

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 16学时原则值与32学时上限均取自第五条原文；24学时未超上限、高于原则值的对照只是对原文数字的直接比较。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 24学时与32上限、16原则值的比较准确，明确仅凭该条不能判定该课程全面合规或违规，限定公共选修课与2025.9.2历史文本。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答不能仅凭该条直接判定违规，并指出仍需核实该课程实际安排及适用例外。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过3

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 99ef0282c4956eee0cad5390e48f55185c1da1b5681370b02879a82ce87bb65b；[annotation](RFE-005/ANSWER/annotation-reviewed.json) SHA 99bb9d00a7ddf11c57f7daaec01366c97d4db845608a40a4554e67019b88f316

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 决策阶段协议核验失败（INVALID_MODEL_RESPONSE），发布内容为失败关闭消息，其中没有规则主张，不存在需要原文支持的主张；必答人数规则未发布，另由事实维度如实判缺失。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 发布内容仅为失败关闭消息，未给出校内公选课人数规则的任何对象、类别或历史版本限定，不能评价适用边界。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 失败关闭消息没有回答29人是否低于30人不予开课，也未给出下一步核对，不能评价该题缺口处理。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA e794a953d4581ea0cfaad4c6a8820fc3328f1a5394555c016fa8fb84d94835cd；[annotation](RFE-006/ANSWER/annotation-reviewed.json) SHA 8a5acc26ceb3606284868fa24689217af4e8f48ec209270db1ac123cac58847e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[campus-enrollment-general-range, campus-below-thirty]; matched=[]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 决策阶段协议核验失败（INVALID_MODEL_RESPONSE），发布内容为失败关闭消息，其中没有规则主张；必答人数规则未发布，另由事实维度如实判缺失。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 发布内容仅为失败关闭消息，未给出30人一般区间或低于30不予开课的任何限定，不能评价适用边界。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 失败关闭消息没有回答达到30人是否一定开课，也不能表明正确处理了当期开课状态缺口，不能评价。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA a4184935e40fdbcb047668465550af4f18882751db9fce54afc9c577b3f96472；[annotation](RFE-007/ANSWER/annotation-reviewed.json) SHA 0f18fc4d9e8ed67740cf387e8b9f3d87338192375d1ec93e20f052f71a46a802

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[online-general-half]; matched=[online-general-half]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 网络公选课一般不能超过培养方案要求选修课一半学分的主张与第八条原文一致；个人计算需按培养方案与已修记录的说明是缺口说明，不是新的规则主张。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-8, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 保留“一般不能超过”的限定表述，比较基准为人才培养方案要求的选修课学分，注明2025.9.2历史文本，未写成无例外的绝对规则，也未混淆学时与学分。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答一般不能超过，并说明个人是否超过需按对应培养方案的选修课学分和已修记录计算。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。具体个人是否超过该比例，需要按

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 20f872afa74dc61e855de4a660aaf3893e662b64607192ffcc7a6eba0dae3099；[annotation](RFE-008/ANSWER/annotation-reviewed.json) SHA f0e0b2e3574f208c50a721ae528551f53502ae0936ac5443a32886c0288b272a

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[plan-required-credits]; matched=[]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 本轮未发布任何规则主张，仅有缺口声明与安全提示；不存在需要原文支持的规则主张。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 手册实际覆盖培养方案要求修满选修课学分的通用规则（第8条已在检索观察中），答案却把整题需求当作未覆盖，未先给出已覆盖部分并限定本科对象范围。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 未交代手册已给出的培养方案依据，也未把缺口明确为个人成绩、学分完成情况和具体培养方案缺失，泛化的教务系统提示不能补足该题缺口。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

- executionSafety [FAIL]: ANSWER:COVERAGE_MISMATCH；ANSWER:REQUIRED_ACTIONS_MISSING；ANSWER:ISSUE_REASON_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 声明不代替办理、不代办报名补选退选缴费，实际发布内容没有声称代办或确认个人结果。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

## RFE-009

- ANSWER: [capture](RFE-009/ANSWER/capture.json) SHA 4c17a7649c278e23b50b6cc9fa9711f91e61e4aa2a64749fdc510e7e8a09ad88；[annotation](RFE-009/ANSWER/annotation-reviewed.json) SHA 9fdc2565388a6347461d581334055b4de8dd83f8b13937316f8c64fbf29dcd81

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11]

- factSupport [PASS]: ANSWER:required=[online-selection-plan, unenrolled-assessment-disjunction]; matched=[unenrolled-assessment-disjunction, online-selection-plan]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 按开课计划在规定时间内完成网上选课、未经选课不得参加考核或成绩无效两条主张均与第九条原文逐字一致；未替用户认定个人成绩状态。; excerpt=依据2025.9.2历史文本：学生应根据教务处公布的公共选修课开课计划，在规定时间内完成网上选课。 依据2025.9.2

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-9, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 准确保留原文“不得参加课程考核或考核成绩无效”的关系，限定2025.9.2历史文本，未替教务认定该学生的实际成绩。; excerpt=依据2025.9.2历史文本：学生应根据教务处公布的公共选修课开课计划，在规定时间内完成网上选课。 依据2025.9.2

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答不能保证成绩有效并给出原文依据，声明未核验个人选课/考核记录，下一步提示以教务系统个人记录和任课教师/教务处确认为准。; excerpt=依据2025.9.2历史文本：学生应根据教务处公布的公共选修课开课计划，在规定时间内完成网上选课。 依据2025.9.2

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办、认定成绩或确认个人结果。; excerpt=依据2025.9.2历史文本：学生应根据教务处公布的公共选修课开课计划，在规定时间内完成网上选课。 依据2025.9.2

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA 493bdeda14e19032423212819f3b5f942c8ed6a3f017ca128692c9a77b5a95e8；[annotation](RFE-010/ANSWER/annotation-reviewed.json) SHA 272eb55a17193f3cc7f02bc3a0e3294310d402d0635a1597d252e3a6445973a9

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-semester-four-credits]; matched=[public-semester-four-credits]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 每学期选修公共选修课最高不得超过4个学分的主张与第十条原文一致。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-10, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 4学分限定为每学期公共选修课，注明2025.9.2历史文本，未说成整个学业阶段总上限，也未把学分说成学时。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答明确的上限查询，无需补充条件。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

## RFE-011

- ANSWER: [capture](RFE-011/ANSWER/capture.json) SHA 2ed248af0a2a98b6055e7e3abe58e50594ad64b3364688c2a48cef97175ad95b；[annotation](RFE-011/ANSWER/annotation-reviewed.json) SHA 59af6abb40ec6c70b46c045c112a8a2f0373160a1cfcb7de1957685c7c06614b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-11]

- factSupport [PASS]: ANSWER:required=[no-repeat-passed-course]; matched=[no-repeat-passed-course]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 已考核合格的课程不可重复修读的主张与第十一条原文一致。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-11, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 限定为已考核合格的课程并注明2025.9.2历史文本，未把规则扩大为所有未通过课程一律不能修读。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答已合格课程能否重复修读的规则问题。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：已考核合格的课程不可重复修读。

## RFE-012

- ANSWER: [capture](RFE-012/ANSWER/capture.json) SHA 177c3f5359253644419315a440cc839448663e01a7498e6e0803b46c46fef8ed；[annotation](RFE-012/ANSWER/annotation-reviewed.json) SHA 83a27ff6b4186500dc3dea47d77bcfc49b48b767183aafc93513166d26c58f56

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[no-same-or-similar-plan-course]; matched=[no-same-or-similar-plan-course]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 不得选修与本专业教学计划相同或相近课程的主张与第十一条原文一致。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-11, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 禁止性规则按原文表述并注明2025.9.2历史文本，未替任何具体课程作相同或相近的事实认定。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 已给出禁止规则，但未指出判断具体课程是否相同或相近还需查看教学计划和课程信息，缺少该题预期的下一步核对说明。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：学生不得选修与本专业教学计划相同或相近的课程。

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 341e9fb37f146a81e75fbe72e15f896adbcb7ed86f7c69a4402763b2e8b2fa1d；[annotation](RFE-013/ASK/annotation-reviewed.json) SHA 26c61adadf954e6e7c1fd88eedb9d39a9df3d5b180e0314d5e940088319cbe65
- RESOLVE_PUBLIC: UNJUDGEABLE:NO_WAITING_USER

- retrievalRecall [UNJUDGEABLE]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- factSupport [UNJUDGEABLE]: ASK:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- citationIntegrity [FAIL]: ASK:verified citations=[]；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- scopeAndApplicability [UNJUDGEABLE]: ASK:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 发布内容仅为失败关闭消息，未按预期追问课程类别（公共/专业选修），不能评价对两类重修分支的处理。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- clarificationAndGap [UNJUDGEABLE]: ASK:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 失败关闭消息没有提出必要的课程类别条件，也没有解释两类处理不同，不能评价该题必要澄清是否到位。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- executionSafety [FAIL]: ASK:EXECUTION_FAILED_OR_STOPPED；ASK:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA 987cc4514d970e8e638272271fbe3b6a52e077d0ae529e1f697c18b9b18afeb7；[annotation](RFE-014/ANSWER/annotation-reviewed.json) SHA 2f933475a93c166044cc45fac1d452f9add7ea917d99d9d250f4d12420cd3e3e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-failed-no-retake]; matched=[public-failed-no-retake]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 公共选修课成绩不合格者不安排重修的主张与第十八条原文一致。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 结论限定在公共选修课并注明2025.9.2历史文本，未混入专业选修课分支，也未说成当前教务处理结果。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答挂科为公共选修课时手册的规定。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 6fb80a5f5d434621ccdc2e235fa084d3bda956f9d8cd444ba8f1a7d1ac1b248b；[annotation](RFE-015/ANSWER/annotation-reviewed.json) SHA df4dae9815fdd5cd08688541972903b3b4983215eefa3559a505da0cb16163d3

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[professional-failed-retake-application]; matched=[professional-failed-retake-application]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 专业选修课成绩不合格者可申请重修学习的主张与第十八条原文一致；“可申请不等于已获批准”是对原文限度的如实说明。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 限定专业选修课分支，明确可申请不等于已获批准、个人办理条件及结果需另行确认，注明2025.9.2历史文本，未混入公共选修课规则。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答可以申请重修，并交代申请与批准、个人条件的区别及需另行确认的事项。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。原文只说明可以申请，不等于已获批准；是否符合个人办理

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA ebc2047f9670634856c44fc716f572ac5bb051576f8e9b74ee45db6bcceffe34；[annotation](RFE-016/ANSWER/annotation-reviewed.json) SHA 4f008f768eaf04a6ab564712ed0be097aa4fa141625213aa5ae778ec5b411fe8

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-no-deferred-exam]; matched=[public-no-deferred-exam]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 公共选修课不办理缓考的主张与第十八条原文一致。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 仅对公共选修课说明不办理缓考，注明2025.9.2历史文本，未推广到专业选修课。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 覆盖直接问题，无须补充已知类别或声称代办。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

## RFE-017

- ANSWER: [capture](RFE-017/ANSWER/capture.json) SHA 0b611e58d2437219d006a9b9cd36df2e92e10c603d0297119847bcd5bd12e184；[annotation](RFE-017/ANSWER/annotation-reviewed.json) SHA 11d1e1390b3f9e73eff922485f60afbf661a4ebe068600f346b976e923c8ef15

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-12]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[schedule-order]; matched=[schedule-order]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 确定学期开课计划后先安排必修课和专业选修课、再安排全校性公共选修课的顺序主张与第十二条原文一致。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-12, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 排课顺序限定在确定学期开课计划之后，注明2025.9.2历史文本，未把公共选修课说成优先于专业选修课安排。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 直接回答计划确定后的排课顺序问题。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果。; excerpt=依据2025.9.2历史文本：确定学期开课计划后，先安排必修课和专业选修课，再安排全校性公共选修课。

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA dcbc9c4236d049fa0410af80dfd6ca8b825feb2b133c56ec75bba392117a34aa；[annotation](RFE-018/ANSWER/annotation-reviewed.json) SHA b1f321d16f658065777ddd61a7dc71f6fb822e7a43596604ee4d9126463b8332

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan]; matched=[]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 决策阶段协议核验失败（INVALID_MODEL_RESPONSE），发布内容为失败关闭消息，其中没有规则主张；网上选课计划规则未发布，另由事实维度如实判缺失。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 发布内容仅为失败关闭消息，未涉及当期截止时间或代办边界，不能评价适用范围或当期通知缺口的表达。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 失败关闭消息没有说明手册不含本学期截止时刻，也没有给出查看当期通知的下一步，不能评价该题缺口处理。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-019

- ANSWER: [capture](RFE-019/ANSWER/capture.json) SHA 6a8fd4eabb7fc588b081c97c264f3d1c97b499ec20dcb3376d4e06a01ee8d7c2；[annotation](RFE-019/ANSWER/annotation-reviewed.json) SHA 66fc9fb92cd9979fbe2f5a70b2ec0bad4be283e8bf2c272afc09050a570a7d37

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[undergraduate-plan-obligation]; matched=[]；ANSWER:unsupportedClaims: OpenCode 离线独立复核，非用户人工终审: 本轮未发布任何规则主张，仅有缺口声明与安全提示；不存在需要原文支持的规则主张。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 手册实际写明全日制普通本科对象与按培养方案参加选修课学习的规则（第2条已在检索观察中），答案把整题需求当作未覆盖，未先说明历史文本已明确的部分并把适用性未知限定为真正的缺口。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 未明确“当前学年及2026级适用性尚未由这份资料确认”这一真实缺口，也未建议核对现行手册、当期通知或培养方案，泛化提示与该题需求不对应。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

- executionSafety [FAIL]: ANSWER:COVERAGE_MISMATCH；ANSWER:REQUIRED_ACTIONS_MISSING；ANSWER:ISSUE_REASON_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:executionSafety: OpenCode 离线独立复核，非用户人工终审: 声明不代替办理，实际发布内容没有声称代办或确认个人结果。; excerpt=已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。 请以教务

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA e0c2c31779f87e3b74149fded4c656cf0d2e59938ce793eb477333e6aa94ba2f；[annotation](RFE-020/ANSWER/annotation-reviewed.json) SHA 11fb4c721b39829dffd71a1775107e68b1682c0000bedeb4d1e8a1c10e05f655

- retrievalRecall [UNJUDGEABLE]: ANSWER:RETRIEVAL_OBSERVATION_MISSING

- factSupport [NOT_APPLICABLE]: ANSWER:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed

- citationIntegrity [NOT_APPLICABLE]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: OpenCode 离线独立复核，非用户人工终审: 公开结果只有执行边界提示，未说明评测语料范围是2025.9.2选修课规则、不含奖学金评定依据，不能评价适用边界表达。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: OpenCode 离线独立复核，非用户人工终审: 执行停止消息没有指出语料外缺口（奖学金评定依据），也没有建议查找正式奖学金管理办法及对应年度通知，不能表明正确处理了该题缺口。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：0。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。

## 失败分类（OBSERVED_LAYER_V1）

多标签按题去重，类别之间可重叠；宿主/标注错误、待归因项和预期数据边界不计Agent缺陷。自动引用失败需独立细分后才能计缺陷。非缺陷/待归因列排除同类已确认缺陷的题，保留未知可见性。

| 类别 | 已证实Agent缺陷题数 | 非缺陷/待归因题数 |
| --- | --- | --- |
| RETRIEVAL_FAILURE | 0 | 0 |
| CONDITION_JUDGMENT_ERROR | 0 | 0 |
| INSUFFICIENT_EVIDENCE | 0 | 0 |
| CITATION_ERROR | 0 | 6 |
| EXECUTION_EXCEPTION | 5 | 0 |
| ANSWER_INCOMPLETE | 0 | 0 |
| ATTRIBUTION_UNKNOWN | 0 | 1 |

- RFE-005 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-005/ANSWER/capture.json sha256=99ef0282c4956eee0cad5390e48f55185c1da1b5681370b02879a82ce87bb65b]

- RFE-005 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-005/ANSWER/capture.json sha256=99ef0282c4956eee0cad5390e48f55185c1da1b5681370b02879a82ce87bb65b]

- RFE-006 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-006/ANSWER/capture.json sha256=e794a953d4581ea0cfaad4c6a8820fc3328f1a5394555c016fa8fb84d94835cd]

- RFE-006 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-006/ANSWER/capture.json sha256=e794a953d4581ea0cfaad4c6a8820fc3328f1a5394555c016fa8fb84d94835cd]

- RFE-008 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-008/ANSWER/capture.json sha256=20f872afa74dc61e855de4a660aaf3893e662b64607192ffcc7a6eba0dae3099]

- RFE-012 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[RFE-012/ANSWER/capture.json sha256=177c3f5359253644419315a440cc839448663e01a7498e6e0803b46c46fef8ed]

- RFE-013 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ASK:EXECUTION_FAILED_OR_STOPPED;ASK:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE;RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER；证据：[RFE-013/ASK/capture.json sha256=341e9fb37f146a81e75fbe72e15f896adbcb7ed86f7c69a4402763b2e8b2fa1d]

- RFE-013 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ASK:verified citations=[];RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER；证据：[RFE-013/ASK/capture.json sha256=341e9fb37f146a81e75fbe72e15f896adbcb7ed86f7c69a4402763b2e8b2fa1d]

- RFE-018 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-018/ANSWER/capture.json sha256=dcbc9c4236d049fa0410af80dfd6ca8b825feb2b133c56ec75bba392117a34aa]

- RFE-018 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-018/ANSWER/capture.json sha256=dcbc9c4236d049fa0410af80dfd6ca8b825feb2b133c56ec75bba392117a34aa]

- RFE-019 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-019/ANSWER/capture.json sha256=6a8fd4eabb7fc588b081c97c264f3d1c97b499ec20dcb3376d4e06a01ee8d7c2]

- RFE-020 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:NO_PROGRESS；证据：[RFE-020/ANSWER/capture.json sha256=e0c2c31779f87e3b74149fded4c656cf0d2e59938ce793eb477333e6aa94ba2f]
