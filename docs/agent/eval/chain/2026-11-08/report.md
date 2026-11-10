# Agent Eval Chain Report

- suite: `agent-eval-chain-v1`
- generatedAt: 2026-11-08T09:28:34.195303100Z
- regression: PASS
- cases: 6 (matched 6, mismatched 0)
- verdicts: PASS 4, FAIL 0, NOT_EVALUABLE 2

| Case | Block | 来源 | 期望 | 实际 | 匹配 | 明细 |
| --- | --- | --- | --- | --- | --- | --- |
| T05-69-CLARIFY | live-t05-69 | docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request/live-04994adbb93a4932bed3aa7b1b8b9047 | PASS | PASS | yes | EXPECTATIONS_MET |
| T05-69-RESOLVE | live-t05-69 | docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request/live-04994adbb93a4932bed3aa7b1b8b9047 | PASS | PASS | yes | EXPECTATIONS_MET |
| E13-0842-FAIL-CLOSED | e13-failure-fixtures | knowledge/src/test/resources/agent-eval-chain/fixtures/e13-20261029T0842.json | PASS | PASS | yes | EXPECTATIONS_MET |
| E13-1016-NO-OBSERVATION | e13-failure-fixtures | knowledge/src/test/resources/agent-eval-chain/fixtures/e13-20261029T1016.json | NOT_EVALUABLE | NOT_EVALUABLE | yes | TOOL_OBSERVATION_MISSING |
| T05-46-FAILED-RUN | failed-probe-t05-46 | docs/evidence/rule-agent-provider/2026-10-15/t05-46-p02-request/live-538e1c90a76c4a10b7347d323c8a06aa | PASS | PASS | yes | EXPECTATIONS_MET |
| T05-INCOMPLETE-EVIDENCE | incomplete-probe-t05 | docs/evidence/rule-agent-provider/2026-10-14/t05-live-1c7d94d4c9e94870bb299b35b38b5c53 | NOT_EVALUABLE | NOT_EVALUABLE | yes | COLLECTION_INCOMPLETE |

## 证据块

- `live-t05-69` (EVIDENCE_BUNDLE): COMPLETE, runs=2
- `e13-failure-fixtures` (FIXTURE_CORPUS): COMMITTED_FIXTURES, runs=0
- `failed-probe-t05-46` (EVIDENCE_BUNDLE): PARTIAL_TERMINAL_FALLBACK, reasons=TERMINAL_SNAPSHOT_NOT_PUBLISHED, runs=1
- `incomplete-probe-t05` (EVIDENCE_BUNDLE): INCOMPLETE, reasons=TERMINAL_SNAPSHOT_MISSING, runs=0
