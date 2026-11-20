# 20题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：20；通过：0；失败：17；不可判定：3；错误：0；通过率：0.0%。

确定整题结论比例：17/20；全部适用维度可评估比例：0/20。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-002 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-003 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-004 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-005 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-008 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-009 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-010 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-011 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-012 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-013 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-014 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-015 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-016 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-017 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-018 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-019 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-020 | FAIL | false | NOT_APPLICABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 20 | 1 | 19 | 0 | 0 | 0 | 19/19 |
| factSupport | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| citationIntegrity | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| scopeAndApplicability | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| clarificationAndGap | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| executionSafety | 20 | 0 | 0 | 17 | 3 | 0 | 17/20 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA ac4659646d37d92cdea1a8dde16e869f7c66b49668dd3f5f69ec4a8fad16b856；[annotation](RFE-001/ANSWER/annotation-pending.json) SHA fd2b731c0fd512985a4cfc0f324d3e8daf8e25cb72c03db20dba60d30078b45f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 0ab58a146d196cd20d5379522986abc1bc89eecdd0fb3176440fb01f3086d43c；[annotation](RFE-002/ANSWER/annotation-pending.json) SHA b1b9c2df651f6b4d3105b400bbdeef7484eb43584bbb798f3e27bb7bc90d9e0b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA fdd1536e6d5ded6d058149db0edd1554684db7c1a2f5451cabb78e405b2a00d7；[annotation](RFE-003/ANSWER/annotation-pending.json) SHA 8f8fd28bd5f8b273d96f8e0484e5ff6a67e56016045785a988a456b14e136861

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA e02103faaf221a9fe3c20411883e2c2ff48cc09fe78c02bbed43c7a620e16e04；[annotation](RFE-004/ANSWER/annotation-pending.json) SHA c6656989147f3733b9fc005e1b77a7c8a737d7e1d5525deaf5b8dee96605dc1c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 86e2c2e39b29063b7f84f2df59dc9d059f9e9d00e358ed0ecf71219d66f49d0d；[annotation](RFE-005/ANSWER/annotation-pending.json) SHA a3aa87092e6307dc8dabbce9a18bd2173ebc811fea29a8ac85899ecbdbc51363

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 1598a81dfa0fb7ed4106b0a9a73237c14fe32d2b6a533275f770cf605f27ea35；[annotation](RFE-006/ANSWER/annotation-pending.json) SHA 674773fdad7ad0e7fc43146130fa9dd0f5d16e0488c3e01f48866b4a71b15050

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA b954bd5314ae10edb55eb7d3c8ca1ba65f3b4304db0ef3abc3fadffcba6150de；[annotation](RFE-007/ANSWER/annotation-pending.json) SHA c5e90ea0110f2bc97779c0b7999988fa289edaac1b3ebf1e0f8d2b96775812f2

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 5213569d57ca48ad270d0295f497b4d016277fed1c545516e5c3c9107ad5f1a3；[annotation](RFE-008/ANSWER/annotation-pending.json) SHA 96bcd7dffcb9eeb14170068b8ffb705609a55a1fadd337a002694e9c951f6299

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-009

- ANSWER: [capture](RFE-009/ANSWER/capture.json) SHA 0871e278ef6d8d3bce743033baa3dbd1bc63e2a5b01dc63f9faf83eff813fd87；[annotation](RFE-009/ANSWER/annotation-pending.json) SHA 23d1eaa752505fae28b65343150fd7dcbaa062792340f1e0a798f867e44df657

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA 11e3dfb9ec1e66d709a99561f2ceeb6a51b42a4f8317f04004cee2ecda157a05；[annotation](RFE-010/ANSWER/annotation-pending.json) SHA ad70e0b15c387ff0924cf43f5404958128ff453f4dccea09d7796fc077ffb6a3

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-011

- ANSWER: [capture](RFE-011/ANSWER/capture.json) SHA 5d79bf8ce616e1a44097234d97923506701ad3e32590ca589d9093ab0c40d7ea；[annotation](RFE-011/ANSWER/annotation-pending.json) SHA 511dd64a5022ef0a831206a34ed6fbbc7b777b37bebb091d8067ae7512da9a3e

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-11]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-012

- ANSWER: [capture](RFE-012/ANSWER/capture.json) SHA 73df5bf973ed31555944a6d66d2f26f0a32e5558ac369d11fc26780f9f18ba38；[annotation](RFE-012/ANSWER/annotation-pending.json) SHA 33a842ca05db876a47008036a549441aa1561439e1071423a0b793c97b5e0adf

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 1d5f6bc2b47a4888ac7a6b4e14ee0095eb61306c3e0bd58d74dfe7b37d98790c；[annotation](RFE-013/ASK/annotation-pending.json) SHA 6e11865ff7dd67d54dd5ab0b1c35e0753b1ff4cad2ed67d04b8f366fae87bd36
- RESOLVE_PUBLIC: [capture](RFE-013/RESOLVE_PUBLIC/capture.json) SHA d22d5450e83ca4b3fc70c02c9457fb3baaf613ddfafeb7764c8265bc60c271c9；[annotation](RFE-013/RESOLVE_PUBLIC/annotation-pending.json) SHA 45af399a0e225c7ab4a3e6bc5b23957d2709e62c177ca2d8f65ef59b441569aa

- retrievalRecall [PASS]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ASK:FACT_REVIEW_INCOMPLETE；ASK:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims；RESOLVE_PUBLIC:FACT_REVIEW_INCOMPLETE；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ASK:CITATION_INVENTORY_MISSING；RESOLVE_PUBLIC:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ASK:EXPECTATIONS_MET；ASK:REVIEW_MISSING_OR_UNANCHORED:executionSafety；RESOLVE_PUBLIC:EXPECTATIONS_MET；RESOLVE_PUBLIC:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA 92628c8099f68aa8a7dc9dd5c8d6faaca1a0746b004f00388a604eb642820017；[annotation](RFE-014/ANSWER/annotation-pending.json) SHA 3d26779beaa2902696f29d2d627d72b647355e3ec69504b85516142acd3b33b0

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 2cbf83a6dc92899c446d93af19889c9bb84ba52d14e80d8a4e18c73f6f843def；[annotation](RFE-015/ANSWER/annotation-pending.json) SHA fbf242f96c5a47f05461c3e30d014753d6e1fc15179bbc61e572be23d638607a

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA d90d58ba5e963dec205b12eaf1b94ee05bfdad5433a4ed7b604ddd9e52627a31；[annotation](RFE-016/ANSWER/annotation-pending.json) SHA 10a971aba35e9110c587c53616b5196e52059049f916ed500d80270b25bee642

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

## RFE-017

- ANSWER: [capture](RFE-017/ANSWER/capture.json) SHA c70ed39bb0ba7944a7bb0b9df523a8708d8c6c67098f29069000e00652fbf667；[annotation](RFE-017/ANSWER/annotation-pending.json) SHA c7e8e5bae8efc9190a062d76d6f88e68e9a8ef6e113c1d660124662cd919476b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-12]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA b6a76e61877b5264cbc10ecd77e6818fa68020203e8b0f527ba0507d1d69fe89；[annotation](RFE-018/ANSWER/annotation-pending.json) SHA c8ceccaeb6ca1dbf88b3c9f4292a38bfadc3be213d1b7103c57df7b0bcf8f208

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

## RFE-019

- ANSWER: [capture](RFE-019/ANSWER/capture.json) SHA 52ed8fd77ddae4a90d43c05e13d610a20a14677c3f88a4f947123136b6dba507；[annotation](RFE-019/ANSWER/annotation-pending.json) SHA a20f045a3bc30baa6835797aca9046946ff7cb029b5203f87740b52bbde60f49

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA 7b8efb4332e7b193282234b89eab54e31107f96649d4e8af99c632c712b831d8；[annotation](RFE-020/ANSWER/annotation-pending.json) SHA 346c938eb6bb363f36a532cc8751309a6f1cf12f9c5b4eb425b72984cb089683

- retrievalRecall [NOT_APPLICABLE]: ANSWER:required=[]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:BUDGET_EXCEEDED

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：0。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。
