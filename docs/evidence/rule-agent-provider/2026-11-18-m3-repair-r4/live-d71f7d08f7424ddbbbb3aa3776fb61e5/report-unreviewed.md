# 9题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：9；通过：0；失败：2；不可判定：7；错误：0；通过率：0.0%。

确定整题结论比例：2/9；全部适用维度可评估比例：0/9。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-004 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-007 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-008 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-010 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-013 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-014 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-015 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-016 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 9 | 0 | 9 | 0 | 0 | 0 | 9/9 |
| factSupport | 9 | 0 | 0 | 0 | 9 | 0 | 0/9 |
| citationIntegrity | 9 | 0 | 0 | 0 | 9 | 0 | 0/9 |
| scopeAndApplicability | 9 | 0 | 0 | 0 | 9 | 0 | 0/9 |
| clarificationAndGap | 9 | 0 | 0 | 0 | 9 | 0 | 0/9 |
| executionSafety | 9 | 0 | 0 | 2 | 7 | 0 | 2/9 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA 80f6c7d81e6cc795acefab2315b850f2293d98f5f32a716d6f2d84e2fa5cd31b；[annotation](RFE-001/ANSWER/annotation-pending.json) SHA 2c80cb2cde037c7e3e94b8cf654e6914ba9a416703e3405e276a627baab0d73d

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA 354245ad0c5932d22a45cb4189faa3cbe6353daeaef1c729aaed295ae98bfd32；[annotation](RFE-004/ANSWER/annotation-pending.json) SHA e619b70aeb0e9f681bf87ad2ce4338c68e1bd50136d4d0717c32194d847276b8

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 0d6e9aa8db86c887d19633dbbf84beb3355788896e56a07add10f66d9a27d3f6；[annotation](RFE-007/ANSWER/annotation-pending.json) SHA c3e8caf6edcdfec6c4feb3bc5fd647646b9afe4026d439948054443eabcec2f9

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 27b53bbb7ad351e7aebbf6d210ddf48b0dd4abe74d838bd0e44b4a6c355aa97a；[annotation](RFE-008/ANSWER/annotation-pending.json) SHA 10551bd5d1e0689381c7e0380552d81225853f232f1869ae9f29ceafc04328b8

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA 9032fb24724df712679efd505d20280891e00cf353e39584c23f5d46184d4a12；[annotation](RFE-010/ANSWER/annotation-pending.json) SHA 01a187e723dab9819aaf673bdf1da6a1639248a587044aa0d067e6857455489d

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 6cee47c19cfac3ae5c88432c3343213d180e66117bf44970f5213f65b6446efe；[annotation](RFE-013/ASK/annotation-pending.json) SHA 5738a1560553b2c11ef50a2c2c4f29d30b8d86da6be1c5666c0de36a46443da5
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA 221d0ee67953c3174cec730074ea786da9f212c9e6650fe911a6916390927e10；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-pending.json) SHA 42846183c6214abd3691583667cf6dd243ec700612520a77d765119186961b3b

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ASK:FACT_REVIEW_INCOMPLETE；ASK:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims；RESOLVE_PUBLIC:FACT_REVIEW_INCOMPLETE；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ASK:CITATION_INVENTORY_MISSING；RESOLVE_PUBLIC:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ASK:EXPECTATIONS_MET；ASK:REVIEW_MISSING_OR_UNANCHORED:executionSafety；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA 4fe2ddc4833cbe6f64688ee29e43d70233fd5249e9ecb7236acb8dc6641b025a；[annotation](RFE-014/ANSWER/annotation-pending.json) SHA f53787f5902a2765e9de7fb00c8006f41b52c38ebacf0f147e08e50b7770d85d

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA f201023542db46b42ad202c47cc609d2c55ead5aa57f15050a34dd4463705019；[annotation](RFE-015/ANSWER/annotation-pending.json) SHA aae221d06d4cfc20917126dca72a397498e88bd25e27b4ebec0ae1747c9302a4

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA d426a478a1ab556efef8deb2e11a8a8ad382d13b24ee37f6484f3842a2dd2d06；[annotation](RFE-016/ANSWER/annotation-pending.json) SHA f484a7f4b2beaace5d34506bec9bb8921d0dd40120a239a6db43848ddad414ec

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：11。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。
