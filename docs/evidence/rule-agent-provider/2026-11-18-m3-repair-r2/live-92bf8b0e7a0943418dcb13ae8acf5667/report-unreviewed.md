# 9题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：9；通过：0；失败：2；不可判定：7；错误：0；通过率：0.0%。

确定整题结论比例：2/9；全部适用维度可评估比例：0/9。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-004 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-007 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-008 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-010 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-013 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
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

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA 90ca83b45a9dfd84a109da5b754d743ba988adf1018805cbd1b0b3bc1e729e4e；[annotation](RFE-001/ANSWER/annotation-pending.json) SHA 7a9abb7ec7d3429da48650f9152ffacb46ca650aa2c81a021ef51da4cc39fc2d

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA e34fafe45795a8695d697ca38ea60c333f403eb1fbdbbb0aa4c6e53d04faac68；[annotation](RFE-004/ANSWER/annotation-pending.json) SHA 966fc35fc498ad5a0a43cd58949945b0863c2edd7e3e036eeaf899cdd637f6d3

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 2bafed0d22c77eff93651f79f24e18ca98bf3dcac3c9833306c24cf6f88065d0；[annotation](RFE-007/ANSWER/annotation-pending.json) SHA d8da68f1a91991005f07909b13bfc5ec900aabba791b5f24e273fbe7ae36aa3e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 0e4a253982768f2467d45c2418abaddcc286fb26b3ccaa33d578f7c59c18f0a9；[annotation](RFE-008/ANSWER/annotation-pending.json) SHA d2b61e09108376db9de4b432ded64879d11c6b3a70e37572a3b359d3d4eab765

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA e6c59b9a4a4a5a490bdf4ff440d4ffdc6ba2c6f2b5e75492b217c176143dd536；[annotation](RFE-010/ANSWER/annotation-pending.json) SHA 3afa3fce4600ce3429acdad309fc0a485b421d3aa9d4804413a00e1a270162c8

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA d57580353902ef86bb2c987f0f673f83252240f0776dfc891d0ccb95478b11b6；[annotation](RFE-013/ASK/annotation-pending.json) SHA 1bacdabdd8b787c3d3d1136965ae514def70b37978c5651127a375363705cdce
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA e4888874b673f70662871b2612f1b4ce0e553f668f72d0accc48928f33f2566d；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-pending.json) SHA 45003f42378d6f1019887f49d471b6c0a8199705f5e8f6cff31feeb8c07f7467

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ASK:FACT_REVIEW_INCOMPLETE；ASK:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims；RESOLVE_PUBLIC:FACT_REVIEW_INCOMPLETE；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ASK:CITATION_INVENTORY_MISSING；RESOLVE_PUBLIC:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ASK:EXPECTATIONS_MET；ASK:REVIEW_MISSING_OR_UNANCHORED:executionSafety；RESOLVE_PUBLIC:EXECUTION_FAILED_OR_STOPPED；RESOLVE_PUBLIC:TERMINAL_ISSUE:ANSWER_COVERAGE_INCOMPLETE

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA 4a040dafcb156b96b9ee94bb396be9e19737fabf28e488254df2847e461a7910；[annotation](RFE-014/ANSWER/annotation-pending.json) SHA d540302855f160d19676e3f8fa7fc7cda77103f3471e9eae1a89eed7f96bfee3

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 2dd107ee3e05cbadc0adcfed48baf9dc71f06119bd7777dec9a04fe943449356；[annotation](RFE-015/ANSWER/annotation-pending.json) SHA 8c190a92b5bb0b4866293910030723fb2ce904d1af9e96c86ca45548053a3d27

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA a7712c25931a7f591887c6ffa133debb106a915da14271d2a7e24877ffe7ace3；[annotation](RFE-016/ANSWER/annotation-pending.json) SHA 82b6cf14c0699ff751f5be58f250549e5cff821ea322e5f40f851c680ab6c6fd

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：11。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。
