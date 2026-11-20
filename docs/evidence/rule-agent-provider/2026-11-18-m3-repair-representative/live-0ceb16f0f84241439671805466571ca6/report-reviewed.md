# 9题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：9；通过：4；失败：5；不可判定：0；错误：0；通过率：44.4%。

确定整题结论比例：9/9；全部适用维度可评估比例：7/9。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-004 | FAIL | true | PASS | PASS | PASS | FAIL | FAIL | PASS | 
| RFE-007 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-008 | FAIL | true | PASS | FAIL | FAIL | FAIL | FAIL | FAIL | 
| RFE-010 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-013 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 
| RFE-014 | FAIL | false | PASS | FAIL | FAIL | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-015 | FAIL | true | PASS | PASS | PASS | FAIL | PASS | PASS | 
| RFE-016 | PASS | true | PASS | PASS | PASS | PASS | PASS | PASS | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 9 | 0 | 9 | 0 | 0 | 0 | 9/9 |
| factSupport | 9 | 0 | 6 | 3 | 0 | 0 | 9/9 |
| citationIntegrity | 9 | 0 | 6 | 3 | 0 | 0 | 9/9 |
| scopeAndApplicability | 9 | 0 | 4 | 3 | 2 | 0 | 7/9 |
| clarificationAndGap | 9 | 0 | 5 | 2 | 2 | 0 | 7/9 |
| executionSafety | 9 | 0 | 6 | 3 | 0 | 0 | 9/9 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA bd4a0ec1c716a71e203f10cdda1f1c38cf0a2f726892f31233993f287b166042；[annotation](RFE-001/ANSWER/annotation-reviewed.json) SHA 2b316eaa1709861f5cc853b497a3cd9961c4a241f5a58ae3230c43f968b96912

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[elective-category-distinction, public-course-subcategories]; matched=[public-course-subcategories, elective-category-distinction]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-4, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 两类选修课及公选子类准确，正文限定为2025.9.2历史文本。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 直接回答所问的两级分类，无需澄清。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=依据2025.9.2历史文本：学校选修课分为专业选修课和公共选修课。 依据2025.9.2历史文本：公共选修课包括校内公选课和网络公选课。

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA ac9733615d123cbf57b47e460004cdaa860f67295bb650c22e962024f8677257；[annotation](RFE-004/ANSWER/annotation-reviewed.json) SHA acb371060ee57a5a225c3c5b82d9e2d67194c7bed4f18575009686216bde5111

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [PASS]: ANSWER:required=[hours-upper-limit, hours-principle]; matched=[hours-principle, hours-upper-limit]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-5, verification=SUPPORTED]]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 两项条文事实正确，但未将24学时与32上限比较，也未明确不能据此判定全面合规或违规。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 没有交代判断这门具体课程还缺少哪些依据，用户问的能否直接判违规没有得到回答。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=依据2025.9.2历史文本：公共选修课授课时间原则上为16学时。 依据2025.9.2历史文本：公共选修课最多不超过32学时。

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 3eb15f95d0938417f937db433932103e9cf02b76a08d5ecb0984ccbe7f91bd1c；[annotation](RFE-007/ANSWER/annotation-reviewed.json) SHA 2558351173d9ac6ae7ba98701e29b35d73b1ceef018452c8536aaffe2b3d9fff

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[online-general-half]; matched=[]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。 已取得的审核资料不足以支持完整结论。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 仅有失败提示，没有对比培养方案学分或限定个人适用范围。; excerpt=已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。 已取得的审核资料不足以支持完整结论。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 执行失败提示不能表明正确处理了该题的资料缺口。; excerpt=已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。 可核对原文或重新发起查证；本轮不作无依据结论。 已取得的审核资料不足以支持完整结论。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:ANSWER_COVERAGE_INCOMPLETE；ANSWER:TERMINAL_ISSUE:DETAIL_NOT_IN_REVIEWED_TEXT

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA d521dc90908130f1ab28afb5c02b435169eae604c5c1520e534772bc764c3071；[annotation](RFE-008/ANSWER/annotation-reviewed.json) SHA 7bddef60bc20dc46b9cc21532b1f1c6a971adfb94fa2d6fab0fa3eef56ccfc3b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[plan-required-credits]; matched=[]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 没有识别问题所需的培养方案和个人学分记录，泛化为不相干的当期截止或代办示例。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- clarificationAndGap [FAIL]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 未提示核对个人成绩、完成学分和对应培养方案，给出的通知或系统操作说明不能补足这一缺口。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

- executionSafety [FAIL]: ANSWER:COVERAGE_MISMATCH；ANSWER:ISSUE_REASON_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。 请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA af6510d175792fe756008cd276f426a77252ca156d8e88bb74cb0c33f9390308；[annotation](RFE-010/ANSWER/annotation-reviewed.json) SHA 84ebb8308f115c408655fe4e254927acb5993e1ba68be187fe3ae794e70efcf0

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-semester-four-credits]; matched=[public-semester-four-credits]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-10, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 4学分限定为每学期公共选修课，注明2025.9.2历史文本。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 已直接回答明确的上限查询。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=依据2025.9.2历史文本：每学期选修公共选修课最高不得超过4个学分。

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 0a7b363fa6f4c5ec88345379b6db7beb25c9e9fe19086d1ce08aecc7758d6cb8；[annotation](RFE-013/ASK/annotation-reviewed.json) SHA 68554a4b4b869771c4c8f2ce7a1ac35810ed38843c8022fd22538da0920d0ac3
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA fc26227fe7dcab377b8b4e09dd4f9db098462c6925faeee088c31d8b9eb092b9；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-reviewed.json) SHA 7b169fa299456a453bb8259e1f3c8d894103fe1ddb679c6f6e0a95f64b292fa8

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ASK:NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed；RESOLVE_PUBLIC:required=[public-failed-no-retake]; matched=[public-failed-no-retake]；RESOLVE_PUBLIC:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- citationIntegrity [PASS]: ASK:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]；RESOLVE_PUBLIC:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ASK:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 在类别未知时保留分支，没有给出统一的是或否。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 公共选修课和专业选修课的处理规则不同，需要先确认课程类别。这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 只回答PUBLIC分支，并注明历史文本。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- clarificationAndGap [PASS]: ASK:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 问清公共还是专业选修，并解释两类处理不同；第18条支撑该差异。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 公共选修课和专业选修课的处理规则不同，需要先确认课程类别。这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 已使用实际回复的类别，给出该分支结果。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

- executionSafety [PASS]: ASK:EXPECTATIONS_MET；ASK:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=还缺影响结论的必要条件。 请补充下方必要条件。 公共选修课和专业选修课的处理规则不同，需要先确认课程类别。这门课属于公共选修还是专业选修？；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=依据2025.9.2历史文本：公共选修课成绩不合格者不安排重修。

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA f4d01831d05ba575c38bd030698c4a480ccf0f11dc800f0fa7b58ea3adc1b171；[annotation](RFE-014/ANSWER/annotation-reviewed.json) SHA 60b9e7fb9147a7e4a1c3cb0b1ed3a4a23b8a945de559c4802529a2ec99c243ea

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [FAIL]: ANSWER:required=[public-failed-no-retake]; matched=[]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- citationIntegrity [FAIL]: ANSWER:verified citations=[]

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 公开结果只有失败提示，不能评价最终答案对历史范围或类别的表达。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- clarificationAndGap [UNJUDGEABLE]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 没有实质答复或针对性缺口说明。; excerpt=本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。 可核对原文或重新发起查证；本轮不作无依据结论。

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 8a7215ecdbd8d39a387cdd452d8e991dfeb1f5890afb35ec3255a7f9d8786eab；[annotation](RFE-015/ANSWER/annotation-reviewed.json) SHA 897b5c05c51ade042229327909b7312fac1341128d91d512fa39a7ef84aa7182

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[professional-failed-retake-application]; matched=[professional-failed-retake-application]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [FAIL]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 保留了可申请并限定专业选修课，但未明确可申请不等于批准或满足个人办理条件。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 已答能否申请，未要求重新确认已给出的类别。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=依据2025.9.2历史文本：专业选修课成绩不合格者可申请重修学习。

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA 933841e2fa4a8af569f74b6762cde4a99814f13a5007535d7b314eb9053b84cb；[annotation](RFE-016/ANSWER/annotation-reviewed.json) SHA 7d7ab7efc653958d09b3b1182b8b80171cb25999addb552acec7ab0fa22f76de

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [PASS]: ANSWER:required=[public-no-deferred-exam]; matched=[public-no-deferred-exam]；ANSWER:unsupportedClaims: Codex 独立离线复核，非用户人工终审: 所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- citationIntegrity [PASS]: ANSWER:verified citations=[Citation[locator=handbook-2025-article-18, verification=SUPPORTED]]

- scopeAndApplicability [PASS]: ANSWER:scopeAndApplicability: Codex 独立离线复核，非用户人工终审: 仅对公共选修课说明不办理缓考，历史范围明确。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- clarificationAndGap [PASS]: ANSWER:clarificationAndGap: Codex 独立离线复核，非用户人工终审: 覆盖直接问题，无须补充已知类别或声称代办。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

- executionSafety [PASS]: ANSWER:EXPECTATIONS_MET；ANSWER:executionSafety: Codex 独立离线复核，非用户人工终审: 实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。; excerpt=依据2025.9.2历史文本：公共选修课不办理缓考。

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：11。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。

## 失败分类（OBSERVED_LAYER_V1）

多标签按题去重，类别之间可重叠；宿主/标注错误、待归因项和预期数据边界不计Agent缺陷。自动引用失败需独立细分后才能计缺陷。非缺陷/待归因列排除同类已确认缺陷的题，保留未知可见性。

| 类别 | 已证实Agent缺陷题数 | 非缺陷/待归因题数 |
| --- | --- | --- |
| RETRIEVAL_FAILURE | 0 | 0 |
| CONDITION_JUDGMENT_ERROR | 0 | 0 |
| INSUFFICIENT_EVIDENCE | 0 | 0 |
| CITATION_ERROR | 0 | 3 |
| EXECUTION_EXCEPTION | 2 | 0 |
| ANSWER_INCOMPLETE | 0 | 0 |
| ATTRIBUTION_UNKNOWN | 0 | 2 |

- RFE-004 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[RFE-004/ANSWER/capture.json sha256=ac9733615d123cbf57b47e460004cdaa860f67295bb650c22e962024f8677257]

- RFE-007 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:ANSWER_COVERAGE_INCOMPLETE;ANSWER:TERMINAL_ISSUE:DETAIL_NOT_IN_REVIEWED_TEXT；证据：[RFE-007/ANSWER/capture.json sha256=3eb15f95d0938417f937db433932103e9cf02b76a08d5ecb0984ccbe7f91bd1c]

- RFE-007 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-007/ANSWER/capture.json sha256=3eb15f95d0938417f937db433932103e9cf02b76a08d5ecb0984ccbe7f91bd1c]

- RFE-008 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-008/ANSWER/capture.json sha256=d521dc90908130f1ab28afb5c02b435169eae604c5c1520e534772bc764c3071]

- RFE-014 / EXECUTION_EXCEPTION / AGENT_FAILED_OR_STOPPED / defect=true: ANSWER:EXECUTION_FAILED_OR_STOPPED;ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；证据：[RFE-014/ANSWER/capture.json sha256=f4d01831d05ba575c38bd030698c4a480ccf0f11dc800f0fa7b58ea3adc1b171]

- RFE-014 / CITATION_ERROR / CITATION_REQUIREMENT_FAILED_UNTYPED / defect=false: ANSWER:verified citations=[]；证据：[RFE-014/ANSWER/capture.json sha256=f4d01831d05ba575c38bd030698c4a480ccf0f11dc800f0fa7b58ea3adc1b171]

- RFE-015 / ATTRIBUTION_UNKNOWN / REVIEW_REQUIRED / defect=false: 评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。；证据：[RFE-015/ANSWER/capture.json sha256=8a7215ecdbd8d39a387cdd452d8e991dfeb1f5890afb35ec3255a7f9d8786eab]
