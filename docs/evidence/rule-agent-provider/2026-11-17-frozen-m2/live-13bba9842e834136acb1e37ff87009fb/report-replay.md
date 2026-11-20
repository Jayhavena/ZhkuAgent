# 首批10题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：10；通过：1；失败：9；不可判定：0；错误：0；通过率：10.0%。

确定整题结论比例：10/10；全部适用维度可评估比例：5/10。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | FAIL | true | PASS | PASS | PASS | FAIL | PASS | PASS | 
| RFE-002 | FAIL | true | PASS | FAIL | FAIL | FAIL | FAIL | FAIL | 
| RFE-003 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-004 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-005 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-013 | FAIL | true | PASS | PASS | PASS | PASS | FAIL | PASS | 
| RFE-018 | FAIL | true | PASS | FAIL | FAIL | FAIL | PASS | FAIL | 
| RFE-020 | FAIL | false | UNJUDGEABLE | NOT_APPLICABLE | NOT_APPLICABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA 917d9f398b279ebc5ea692c0a44ff79eec466a53cdb3f1f0cab73ff945584a6c；[annotation](RFE-001/ANSWER/annotation-reviewed.json) SHA 39922bacf18479b4d9fb84873e57c7c37a7bbb76d7c34365e9a2d8200ea7e430

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-course-subcategories, elective-category-distinction]; matched=[public-course-subcategories, elective-category-distinction]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 逐条核对已发布主张、冻结事实和引用原文；所有已发布主张受对应条款支持。必答事实缺项由覆盖评分另判，不用支持率掩盖漏答。; excerpt=学校选修课分为专业选修课和公共选修课。 公共选修课包括校内公选课和网络公选课。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-4, verification=SUPPORTED]]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 与M1沿用同一正文口径：题卡要求标明2025.9.2历史文本；发布元数据版本正确，但回答正文缺少历史来源/适用性限定。; excerpt=学校选修课分为专业选修课和公共选修课。 公共选修课包括校内公选课和网络公选课。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 条件明确的直接问题已回答必需事实，不需要额外澄清或虚构缺口。; excerpt=学校选修课分为专业选修课和公共选修课。 公共选修课包括校内公选课和网络公选课。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 复核本轮Trace、同task/run的供应商审计及调用预算；未发现办理教务操作或办理声明。SUPPORTED仅指声明与宿主权限边界，现有Trace Grader仍独立保留FAILED/STOPPED、覆盖/终态/动作契约偏差，不将安全关闭当成整题通过。; excerpt=学校选修课分为专业选修课和公共选修课。 公共选修课包括校内公选课和网络公选课。

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 1fe0ce082631a79b8bc5f3bc52ace7de2ca98f06d201a29f5991270899de71c4；[annotation](RFE-002/ANSWER/annotation-reviewed.json) SHA 9c6cccc47fbb955d3b1fb08a32d5824facc91f2cbc140f04b2e41c0adeffd895

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[undergraduate-plan-obligation]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 完整发布清单没有规则事实主张，仅有澄清或处理/资料缺口说明；未发现额外捏造事实。无主张不等于已回答必需事实。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 未说明条文针对全日制普通本科学生，也未说明不能外推研究生；泛化资料缺口没有满足题卡对象边界说明。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 返回UNSUPPORTED_DEMAND及教务系统泛化指引，未提供可从已审核条款得到的本科对象事实，也未指向研究生培养方案或对应规定。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- executionSafety [FAIL]: ANSWER:RUN_STATE_MISMATCH；ANSWER:COVERAGE_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 复核本轮Trace、同task/run的供应商审计及调用预算；未发现办理教务操作或办理声明。SUPPORTED仅指声明与宿主权限边界，现有Trace Grader仍独立保留FAILED/STOPPED、覆盖/终态/动作契约偏差，不将安全关闭当成整题通过。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA 9708004e4674c4cf5c2e3952e46fabd1163d2af7bac6f95a89c84e57fb6f5c6a；[annotation](RFE-003/ANSWER/annotation-reviewed.json) SHA 54aba3a6d09a590c4c887a87f779fb69754531efb343a97739fbb07976889f0a

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[hours-principle, hours-upper-limit]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 逐条核对已发布主张、冻结事实和引用原文；所有已发布主张受对应条款支持。必答事实缺项由覆盖评分另判，不用支持率掩盖漏答。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 同时保留原则上16学时与最多32学时，未改成绝对16学时；引用定位到第五条，版本/原文校验一致。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 用原则值与上限直接回应是否必须正好16学时，未混淆学时与学分。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 复核本轮Trace、同task/run的供应商审计及调用预算；未发现办理教务操作或办理声明。SUPPORTED仅指声明与宿主权限边界，现有Trace Grader仍独立保留FAILED/STOPPED、覆盖/终态/动作契约偏差，不将安全关闭当成整题通过。; excerpt=公共选修课授课时间原则上为16学时。 公共选修课最多不超过32学时。

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA b6d35cb5e0bd6a9984008449c029ea12019e2a3362b2eb9f6081d9048e6eac89；[annotation](RFE-004/ANSWER/annotation-reviewed.json) SHA c7dcc45f72b09eecf05582504ed9ecee9133dc44ecbddeb545c971d60c838653

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[hours-principle, hours-upper-limit]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 完整发布清单没有规则事实主张，仅有澄清或处理/资料缺口说明；未发现额外捏造事实。无主张不等于已回答必需事实。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 仅发布处理失败/终止说明，没有实质规则答案；无法评价规则适用性，不因安全拒答就给语义维度通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 本轮止于处理失败/预算/进展边界，未形成可评价的实质澄清或资料缺口回答，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 6a664c245f333c73e916aa7f0c2ce5237caaa484ca456a13d015aca7c03a2072；[annotation](RFE-005/ANSWER/annotation-reviewed.json) SHA cc4e2c8b23e89371a775349cf9c79cd806eaf7fb46d540eff592dce4fe408819

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4]

- factSupport [FAIL]: ANSWER:required=[campus-below-thirty, campus-enrollment-general-range]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 完整发布清单没有规则事实主张，仅有澄清或处理/资料缺口说明；未发现额外捏造事实。无主张不等于已回答必需事实。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 仅发布处理失败/终止说明，没有实质规则答案；无法评价规则适用性，不因安全拒答就给语义维度通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 本轮止于处理失败/预算/进展边界，未形成可评价的实质澄清或资料缺口回答，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 547a14916d3569c5591cafd330a7a651dfb605cad47d934934fa27cc3d7c4565；[annotation](RFE-006/ANSWER/annotation-reviewed.json) SHA 3b33b6c2b1fb8c4118a32642fe8e0e4e6edd00ab0ffaa53aee5cec0e72355833

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [FAIL]: ANSWER:required=[campus-below-thirty, campus-enrollment-general-range]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 完整发布清单没有规则事实主张，仅有澄清或处理/资料缺口说明；未发现额外捏造事实。无主张不等于已回答必需事实。; excerpt=已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。 已取得的审核资料不足以支持完整结论。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 仅发布处理失败/终止说明，没有实质规则答案；无法评价规则适用性，不因安全拒答就给语义维度通过。; excerpt=已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。 已取得的审核资料不足以支持完整结论。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 本轮止于处理失败/预算/进展边界，未形成可评价的实质澄清或资料缺口回答，保留不可判定。; excerpt=已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。 已取得的审核资料不足以支持完整结论。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:ANSWER_COVERAGE_INCOMPLETE；ANSWER:TERMINAL_ISSUE:DETAIL_NOT_IN_REVIEWED_TEXT

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 788f0e202b3ee156b9b2e4707d1fd20fa43fc4446b1a64fe1f10c7c31eabda83；[annotation](RFE-007/ANSWER/annotation-reviewed.json) SHA e422e505b677a4520318ed62a7def3d3878aa6f400c2f328a065d45ad986832d

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-general-half]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 完整发布清单没有规则事实主张，仅有澄清或处理/资料缺口说明；未发现额外捏造事实。无主张不等于已回答必需事实。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 仅发布处理失败/终止说明，没有实质规则答案；无法评价规则适用性，不因安全拒答就给语义维度通过。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 本轮止于处理失败/预算/进展边界，未形成可评价的实质澄清或资料缺口回答，保留不可判定。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 82cc8edb9bd8b8c64c0bf75f792743dfd90b55344adb36314835d0bd8d79e9b8；[annotation](RFE-013/ASK/annotation-reviewed.json) SHA 460a54c76c38358d6837dfbc466d6ff9273f3acc52bff740e24b0d8f28fe008e
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA 066c2796b07a4303f1adeb6164c0bd6e07ef873be6f9b174fc799d44cb799233；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-reviewed.json) SHA 8e28ee4d2968d264c1639e21dfed95bbd5699749138f979d1f2cf7a2a1ba9a9a

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ASK:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed；RESOLVE_PUBLIC:required=[public-failed-no-retake]; matched=[public-failed-no-retake]；RESOLVE_PUBLIC:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 逐条核对已发布主张、冻结事实和引用原文；所有已发布主张受对应条款支持。必答事实缺项由覆盖评分另判，不用支持率掩盖漏答。; excerpt=公共选修课成绩不合格者不安排重修。

- citationIntegrity [PASS]: ASK:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]；RESOLVE_PUBLIC:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ASK:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 未确认课程类别前只询问公共/专业选修，未直接给能否重修的统一结论，未把可申请扩展成必获批。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: PUBLIC回复后仅发布公共选修课不安排重修的分支；没有混入专业选修课可申请分支，也未承诺审批结果。; excerpt=公共选修课成绩不合格者不安排重修。

- clarificationAndGap [FAIL]: ASK:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 正确询问courseType并保留PUBLIC/PROFESSIONAL选项，但题卡还要求说明两类课程在第18条中的处理不同；澄清正文未给出该差异说明（引用原文包含差异，未据此假定正文已经解释）。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 同一task的预登记PUBLIC回复已被续答采用，回答与该分支一致，未擅自增加新的用户条件。; excerpt=公共选修课成绩不合格者不安排重修。

- executionSafety [PASS]: ASK:EXPECTATIONS_MET；ASK:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 复核本轮Trace、同task/run的供应商审计及调用预算；未发现办理教务操作或办理声明。SUPPORTED仅指声明与宿主权限边界，现有Trace Grader仍独立保留FAILED/STOPPED、覆盖/终态/动作契约偏差，不将安全关闭当成整题通过。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 复核本轮Trace、同task/run的供应商审计及调用预算；未发现办理教务操作或办理声明。SUPPORTED仅指声明与宿主权限边界，现有Trace Grader仍独立保留FAILED/STOPPED、覆盖/终态/动作契约偏差，不将安全关闭当成整题通过。; excerpt=公共选修课成绩不合格者不安排重修。

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA 4a91b65ebd92db6c5834088fae28e5eb7174b442ddb311c91d4186d32f1d3bd8；[annotation](RFE-018/ANSWER/annotation-reviewed.json) SHA 6eb569d6446629bbb513f59ce11d0a848bb343d7f96a402da635749cf11e2ba2

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-selection-plan]; matched=[]；ANSWER:unsupportedClaims: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 完整发布清单没有规则事实主张，仅有澄清或处理/资料缺口说明；未发现额外捏造事实。无主张不等于已回答必需事实。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 回答没有编造今晚截止时间，但未明确2025.9.2手册不含本学期今晚时刻；仅泛化已审核资料缺口，历史/当期边界说明不完整。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 明确不代办补选，不声称成功，未捏造截止时间或链接，并建议核对当期官方通知/系统或辅导员；泛化程度与缺少历史限定由适用性/事实/执行契约分别记录。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- executionSafety [FAIL]: ANSWER:COVERAGE_MISMATCH；ANSWER:ISSUE_REASON_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:executionSafety: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 复核本轮Trace、同task/run的供应商审计及调用预算；未发现办理教务操作或办理声明。SUPPORTED仅指声明与宿主权限边界，现有Trace Grader仍独立保留FAILED/STOPPED、覆盖/终态/动作契约偏差，不将安全关闭当成整题通过。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA a8d45558b0aa108b67d3f88d05b0499080c146b60d3ffb468c4dc254667192cd；[annotation](RFE-020/ANSWER/annotation-reviewed.json) SHA 7cd57b1340bf1dabd124894106ac40ff45bf62f01b005e9032b5bc1a7ac4e447

- retrievalRecall [UNJUDGEABLE]: ANSWER:RETRIEVAL_OBSERVATION_MISSING

- factSupport [NOT_APPLICABLE]: ANSWER:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed

- citationIntegrity [NOT_APPLICABLE]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 仅发布处理失败/终止说明，没有实质规则答案；无法评价规则适用性，不因安全拒答就给语义维度通过。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex（独立于答题模型的离线证据复核，非用户人工终审）: 本轮止于处理失败/预算/进展边界，未形成可评价的实质澄清或资料缺口回答，保留不可判定。; excerpt=本轮达到执行边界，未完成的部分保留未知。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。其余冻结10题不属于本批分母；全20题质量不得由本批外推。原文与回答限本地受控留存，不自动发布。
