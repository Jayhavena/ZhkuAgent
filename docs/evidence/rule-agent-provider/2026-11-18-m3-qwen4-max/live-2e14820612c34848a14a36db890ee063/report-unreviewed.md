# 20题 Agent 质量报告

RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。

分母：20；通过：0；失败：16；不可判定：4；错误：0；通过率：0.0%。

确定整题结论比例：16/20；全部适用维度可评估比例：0/20。FAIL可与局部不可判定共存，因此两种比例分别报告。

| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RFE-001 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-002 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-003 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-004 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-005 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-006 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-007 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-008 | UNJUDGEABLE | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | 
| RFE-009 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-010 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-011 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-012 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-013 | FAIL | false | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-014 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-015 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-016 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-017 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-018 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-019 | FAIL | false | PASS | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 
| RFE-020 | FAIL | false | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | UNJUDGEABLE | FAIL | 

| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| retrievalRecall | 20 | 0 | 18 | 0 | 2 | 0 | 18/20 |
| factSupport | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| citationIntegrity | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| scopeAndApplicability | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| clarificationAndGap | 20 | 0 | 0 | 0 | 20 | 0 | 0/20 |
| executionSafety | 20 | 0 | 0 | 16 | 4 | 0 | 16/20 |

## RFE-001

- ANSWER: [capture](RFE-001/ANSWER/capture.json) SHA ab06af6593b535f0b7fcaa176cfeb539eadf167af45e0928bd4c0ac154b2797d；[annotation](RFE-001/ANSWER/annotation-pending.json) SHA 43395f8366c9b216161c10d0e85bfca2a66fcbbf74296685b008ca00a085be8a

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-4]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-002

- ANSWER: [capture](RFE-002/ANSWER/capture.json) SHA 5fc165788bede1eaf70973a9499ab02687b43bb3a4521f8b560e08c02edb0f94；[annotation](RFE-002/ANSWER/annotation-pending.json) SHA 2076cf410d453b76a7d04b1ff1839b74c47546843b2884f4f0df940ac40f372c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-003

- ANSWER: [capture](RFE-003/ANSWER/capture.json) SHA 17f24e3f4c468c5d0fd9f343b0f2386039dd7ed9262c5a2f00d55aa0e835721a；[annotation](RFE-003/ANSWER/annotation-pending.json) SHA 0413452db87acb8546d46b908b4e8722b1591789ecfd742d8c7e168840abf67c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-004

- ANSWER: [capture](RFE-004/ANSWER/capture.json) SHA 58ee0861ce8f7148b5691be7e9dc5cb4a3fb80193feb4bc5f6951b77d4806276；[annotation](RFE-004/ANSWER/annotation-pending.json) SHA 40d84c0b0027dedb374bba52582648d82788238e21fac2f867f097b6156ea24c

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-5]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-005

- ANSWER: [capture](RFE-005/ANSWER/capture.json) SHA 8d43cf435ce2c879bcf7076a8e34139f1c186b36f5ae781a4d613c95bbd73858；[annotation](RFE-005/ANSWER/annotation-pending.json) SHA 09c4b44355f0f03aeed77a5b60c2fd7e8ac28d8a8fbf3956404b23ce806bee30

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-006

- ANSWER: [capture](RFE-006/ANSWER/capture.json) SHA 9b0ebca76e60e3f4acce47163181f62012aaecd5e61efcdcc5b9d479a4cfb136；[annotation](RFE-006/ANSWER/annotation-pending.json) SHA 529849ec1ea99126659cc7eed744e6a762e3dd4bc5414c44948984d05a417395

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-6]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-007

- ANSWER: [capture](RFE-007/ANSWER/capture.json) SHA 24e7acc3718dea898d4be5de717fe490d903c69a1492544780babcc6467b9e60；[annotation](RFE-007/ANSWER/annotation-pending.json) SHA 99c10dbdb4051ea77ae63b3701e72e8f822078012dab906456130eb44d4c5196

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-008

- ANSWER: [capture](RFE-008/ANSWER/capture.json) SHA 3959fb8eaa1fb699f9ae40c781913c9d0d62055e75ec260714c86b3cdd10307b；[annotation](RFE-008/ANSWER/annotation-pending.json) SHA 0293a5484308f410abb26b89ce24f013dec1469dc8fc112e879128c64bda339f

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-8]; observed=[handbook-2025-article-8, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [UNJUDGEABLE]: ANSWER:EXPECTATIONS_MET；ANSWER:REVIEW_MISSING_OR_UNANCHORED:executionSafety

## RFE-009

- ANSWER: [capture](RFE-009/ANSWER/capture.json) SHA 2819896f88e55e016bff3d5998d0aa57a5df06d9f0e6cd8c5419de8c8785f7cd；[annotation](RFE-009/ANSWER/annotation-pending.json) SHA d44cb01b7c8c356785826070b8c1b5160fcc8a287bbb8dedc5c9101b192106a7

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-2, handbook-2025-article-11]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-010

- ANSWER: [capture](RFE-010/ANSWER/capture.json) SHA c4fdf39d548f77e1476e1fdb3b3405df5968aa7618616cb4d2bb76cabd4f2428；[annotation](RFE-010/ANSWER/annotation-pending.json) SHA b01c71012b83725abf87bff93057385d4ad23e3ca717ca4e9170ab46b9d7dfd4

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-10]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-011

- ANSWER: [capture](RFE-011/ANSWER/capture.json) SHA c34ba018eadba467a3f219ed7b3ee1872245bab169d2c67b025a3d81841d1301；[annotation](RFE-011/ANSWER/annotation-pending.json) SHA d7a5b6514cca027b6862945e71ec8861b334f61eee881c2aa85ee723a85f7de6

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-11]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-012

- ANSWER: [capture](RFE-012/ANSWER/capture.json) SHA 01dcf4ba91e1eb8a22f428fc6b069e9289ffc5e8225a0c9efc051f3c17b86248；[annotation](RFE-012/ANSWER/annotation-pending.json) SHA 0449e46d4929703c011936a76212213b5ebe868a3320a162ed13bdfabb7764db

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-11]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-013

- ASK: [capture](RFE-013/ASK/capture.json) SHA 14efceb330c89b9d2388dc8cc13d6668fb44d66fe7a0d4f574b156f0e3c0529e；[annotation](RFE-013/ASK/annotation-pending.json) SHA 9c2029d7cba42743633ba5ac4f854f992de9852443e78086a5c152f1529b7b8d
- RESOLVE_PUBLIC: UNJUDGEABLE:NO_WAITING_USER

- retrievalRecall [UNJUDGEABLE]: ASK:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- factSupport [UNJUDGEABLE]: ASK:FACT_REVIEW_INCOMPLETE；ASK:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- citationIntegrity [UNJUDGEABLE]: ASK:CITATION_INVENTORY_MISSING；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- scopeAndApplicability [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- clarificationAndGap [UNJUDGEABLE]: ASK:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

- executionSafety [FAIL]: ASK:EXECUTION_FAILED_OR_STOPPED；ASK:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE；RESOLVE_PUBLIC:UNJUDGEABLE:NO_WAITING_USER

## RFE-014

- ANSWER: [capture](RFE-014/ANSWER/capture.json) SHA ab43b16c6ab93afd4208933d885296d0f860dfd2a4fb2bedbdb3729eda1e9c55；[annotation](RFE-014/ANSWER/annotation-pending.json) SHA ca58a01a2c19e8485a69f21192925ee63b60eb67488a899a3e3b559c04b539bb

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-015

- ANSWER: [capture](RFE-015/ANSWER/capture.json) SHA 3c18e34c0bf84bb0c00536139fa8fb95ab1be2e204930ed80ea6fc8774aafef2；[annotation](RFE-015/ANSWER/annotation-pending.json) SHA a4cd8f27e2d450284d1f38c497fa512bd91e63021f71d3843f77fcfbe03f2692

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-016

- ANSWER: [capture](RFE-016/ANSWER/capture.json) SHA 1667318dd38fa1dba108bae171a6ec96be351db9208c8517b14ca06df19037c1；[annotation](RFE-016/ANSWER/annotation-pending.json) SHA 942b0bb607e31645fd642c2964cf90144282cf9d183b7e1fa2bc2aaa585cfbd9

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-18]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-18, handbook-2025-article-6, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-017

- ANSWER: [capture](RFE-017/ANSWER/capture.json) SHA ecb96e69a011185cddc1082eb7dad45b588b7e6b3b758377727efed306b86be3；[annotation](RFE-017/ANSWER/annotation-pending.json) SHA 410b1b40d06112be0ed5e0908760bc85ac0fd2399c8aefef1584dd67e5de09e2

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-12]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-018

- ANSWER: [capture](RFE-018/ANSWER/capture.json) SHA b39decabb86b5b15ae814fd505f65b813ebc013c37d2fe7b8e386ebdc69b074e；[annotation](RFE-018/ANSWER/annotation-pending.json) SHA 139ca75c33f24d15bc3bdcd9ac31098726041c1a6f976bd0992937dbeeaf336d

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-9]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-12]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-019

- ANSWER: [capture](RFE-019/ANSWER/capture.json) SHA 05b5764225703a91eac79ecdcf5c498b58295f4e14fa558a409aae9aa3ee04e7；[annotation](RFE-019/ANSWER/annotation-pending.json) SHA b55c9e06de6080c0d9a9dd18b816a79708f8b4730047a1daacf8a27d81d87a2b

- retrievalRecall [PASS]: ANSWER:required=[handbook-2025-article-2]; observed=[handbook-2025-article-8, handbook-2025-article-9, handbook-2025-article-6, handbook-2025-article-18, handbook-2025-article-4, handbook-2025-article-5, handbook-2025-article-2, handbook-2025-article-12, handbook-2025-article-11, handbook-2025-article-10]

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:INVALID_MODEL_RESPONSE

## RFE-020

- ANSWER: [capture](RFE-020/ANSWER/capture.json) SHA 79662f76d3fd10023b0820eef3285b8780c2e8ea1adc5402e57f2f0700087cbe；[annotation](RFE-020/ANSWER/annotation-pending.json) SHA 67e3763ea16026761af602ea9d249e88e87b6b4d62f0a48dc95d6297b4254c79

- retrievalRecall [UNJUDGEABLE]: ANSWER:RETRIEVAL_OBSERVATION_MISSING

- factSupport [UNJUDGEABLE]: ANSWER:FACT_REVIEW_INCOMPLETE；ANSWER:REVIEW_MISSING_OR_UNANCHORED:unsupportedClaims

- citationIntegrity [UNJUDGEABLE]: ANSWER:CITATION_INVENTORY_MISSING

- scopeAndApplicability [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:scopeAndApplicability

- clarificationAndGap [UNJUDGEABLE]: ANSWER:REVIEW_MISSING_OR_UNANCHORED:clarificationAndGap

- executionSafety [FAIL]: ANSWER:EXECUTION_FAILED_OR_STOPPED；ANSWER:TERMINAL_ISSUE:NO_PROGRESS

语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：0。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。
