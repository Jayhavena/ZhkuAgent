# 首批10题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：10；通过：0；失败：7；不可判定：3；错误：0；通过率：0.0%。

确定整题结论比例：7/10；全部适用维度可评估比例：0/10。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-002 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-003 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-004 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-005 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-013 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-018 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-020 | FAIL | false | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA 917d9f398b279ebc5ea692c0a44ff79eec466a53cdb3f1f0cab73ff945584a6c；[annotation](RFE-001/ANSWER/annotation-pending.json) SHA 0c10edd43ee1a7a8e96c2e67c11aef495ff261aa29aca9bd78666c9b8793afdf

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 1fe0ce082631a79b8bc5f3bc52ace7de2ca98f06d201a29f5991270899de71c4；[annotation](RFE-002/ANSWER/annotation-pending.json) SHA 6213e355ea97f2032fd32c536f96f051fd308147f982c89e4924096e6760d936

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:RUN_STATE_MISMATCH；ANSWER:COVERAGE_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA 9708004e4674c4cf5c2e3952e46fabd1163d2af7bac6f95a89c84e57fb6f5c6a；[annotation](RFE-003/ANSWER/annotation-pending.json) SHA b1470444fad5defaa3a18c094f6759393e5e22a90b0cfc1a52014d8201518856

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA b6d35cb5e0bd6a9984008449c029ea12019e2a3362b2eb9f6081d9048e6eac89；[annotation](RFE-004/ANSWER/annotation-pending.json) SHA 78cd6a45029f10eb75db5cbc738e88c7944758b2bb92508f4ebf446e3ada0e4f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 6a664c245f333c73e916aa7f0c2ce5237caaa484ca456a13d015aca7c03a2072；[annotation](RFE-005/ANSWER/annotation-pending.json) SHA 878f5a3ecf8d9b3bd61bc10dce7f8c0b6952163a2c884c4433e4474b00158331

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 547a14916d3569c5591cafd330a7a651dfb605cad47d934934fa27cc3d7c4565；[annotation](RFE-006/ANSWER/annotation-pending.json) SHA 5379c73b39dfcf75f8c93b917a4cf420a2be2e9b05aba66c557149b458b5a0eb

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:ANSWER_COVERAGE_INCOMPLETE；ANSWER:TERMINAL_ISSUE:DETAIL_NOT_IN_REVIEWED_TEXT

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 788f0e202b3ee156b9b2e4707d1fd20fa43fc4446b1a64fe1f10c7c31eabda83；[annotation](RFE-007/ANSWER/annotation-pending.json) SHA 7f5e8d0ffb419ff96d466b4ced548c435e6dba5976d409e2c1f42f6decf58d97

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 82cc8edb9bd8b8c64c0bf75f792743dfd90b55344adb36314835d0bd8d79e9b8；[annotation](RFE-013/ASK/annotation-pending.json) SHA 2d433326baed5a955895aa0de3da21e8434adb5839852f77824714828f4bd268
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA 066c2796b07a4303f1adeb6164c0bd6e07ef873be6f9b174fc799d44cb799233；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-pending.json) SHA fa75f4b2b40cbc65ca95f15c6cac5384d11aba0cba05ddc8ed7e6491e1847ec4

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ASK:FACT_REVIEW_INCOMPLETE；ASK:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims；RESOLVE_PUBLIC:FACT_REVIEW_INCOMPLETE；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ASK:CITATION_INVENTORY_MISSING；RESOLVE_PUBLIC:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ASK:EXPECTATIONS_MET；ASK:REVIEW_MISSING_OR_UNANCHORED:executionSafety；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA 4a91b65ebd92db6c5834088fae28e5eb7174b442ddb311c91d4186d32f1d3bd8；[annotation](RFE-018/ANSWER/annotation-pending.json) SHA 5e25500b990d293e5404252e3b1c8eed1619c50e56b36918233b1798cac357ac

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:COVERAGE_MISMATCH；ANSWER:ISSUE_REASON_MISMATCH；ANSWER:CLAIM_PRESENCE_MISMATCH；ANSWER:EVIDENCE_LINKS_MISSING；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA a8d45558b0aa108b67d3f88d05b0499080c146b60d3ffb468c4dc254667192cd；[annotation](RFE-020/ANSWER/annotation-pending.json) SHA 1c352090dcafbe17ed6880489ec7c8419aab5922c52cf2365794c380ff231821

- retrievalRecall [UNJUDGEABLE]: ANSWER:RETRIEVAL_OBSERVATION_MISSING

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。其余冻结10题不属于本批分母；全20题质量不得由本批外推。原文与回答限本地受控留存，不自动发布。
