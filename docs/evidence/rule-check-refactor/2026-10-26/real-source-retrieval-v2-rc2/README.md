# Real corpus retrieval evaluation v2.0.0-rc2

Status: CANDIDATE_PENDING_INDEPENDENT_REVIEW. The asset validator can pass without implying independent review, production readiness, or a retrieval algorithm winner.

## Scope of this candidate

- Parent v2.0.0-rc1 remains frozen and unchanged.
- A fresh logical clone was restored from the untouched isolated source container zhkuagent-eval-v1 into the dedicated database zhkuagent_eval_v2_rc2, container zhkuagent-eval-v2-rc2, endpoint 127.0.0.1:55442.
- Exactly three knowledge_source.source_url fields were populated with the official 2025 handbook publication page. The URL is a publication page, not a direct PDF attachment URL.
- Eight chunk snapshot rows inherit that URL through their source_id. No knowledge_chunk row was updated; chunk content and checksums remain unchanged.
- The repair SQL has database-name and row-state guards. It refuses to run unless connected to zhkuagent_eval_v2_rc2 and all three expected published 2025 source rows still have NULL source_url.

## Reproducibility fingerprints

- Retrieval corpus fingerprint (unchanged): 4fd00ba1c8154fa7f4389cde6b64fe7276b5ce36ae05a6c87224ee9d465980f0
- Source metadata fingerprint before repair: 216febd1c1042b4eb08acdce63a8ec2ed3e9d2d1d2df93e8f2b1a7e0c35630b9
- Source metadata fingerprint after repair: 5d6012b4c8a252fb28662578ac8852916e782b81eed2a552625d5d2159b331a8
- Source URL state SHA-256 before repair: 27af38f18757ef76def4dcc28cc9caabfe9d7842c527817970da19bc5d40b7ca
- Source URL state SHA-256 after repair: cc99c9df75655ed90f7a203966ad21826443f25bd23320f4cdff6ceb170454f4
- Source rows: 6 total; 3 source_url values changed; 0 other source URL values changed.
- Chunk rows: 17 total; 8 eligible and 9 excluded; 8 joined sourceUrl values now point to the official 2025 publication page.

The retrieval corpus fingerprint intentionally covers ranking-relevant chunk/source fields but excludes source_url. Therefore it remains unchanged. The separately recorded source metadata fingerprint and the corpus-snapshot file hash bind the source URL repair.

## Validation

From this directory, run:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\validate-v2-rc2.ps1

The validator checks the original rc1 parent hashes, the unchanged retrieval corpus fingerprint, source URL mappings, source metadata fingerprint, all 18 cases, all gold bindings, and every artifact hash in manifest.json.

## What this does not claim

- The lexical/vector/RRF comparison was subsequently executed on this rc2 candidate; metrics and ranking differences are recorded in retrieval-comparison-report.md.
- Candidate preparation made no model or Embedding API calls. The subsequent comparison generated 18 query vectors in three DashScope batch requests (8, 8, 2; 306 input tokens); only evaluation questions were sent. Retrieval replay then used the saved vectors and made zero API calls.
- The copied evaluation database records latest successful migration version 17. No schema migrations were applied as part of this metadata-only repair.
- The local 2025 PDF SHA-256 and clause mappings are supported by the copied external audit ledger. The database still does not store the PDF SHA-256 itself.
- Byte equality between the local PDF and the official online attachment remains NOT_VERIFIED.
- The local 2026 handbook OCR comparison found no substantive difference in the compared articles, but the official attachment identity remains unverified; current-academic-year applicability is therefore not promoted to a certified status.
- Strict locator accuracy remains disabled because known multi-article and page/locator issues remain.
- The set has only eight eligible chunks and is a small development diagnostic set, not an estimate of production-wide retrieval quality.

## Three-way retrieval comparison

The comparison ran on the frozen eight-chunk eligible pool with candidateLimitPerPath=8, resultLimitK=5 and RRF k=60. The existing project retrievers and RRF service were executed, not a synthetic scoring fixture.

| Algorithm | Positive cases | Macro Recall@5 | MRR | Complete evidence set @5 |
| --- | ---: | ---: | ---: | ---: |
| LEXICAL | 15 | 1.000000 | 0.966667 | 1.000000 |
| VECTOR | 15 | 1.000000 | 0.966667 | 1.000000 |
| RRF_HYBRID | 15 | 1.000000 | 1.000000 | 1.000000 |

Scorer status: SCORED_GATES_PASS. Forbidden chunk leaks, unknown chunks, checksum mismatches, source-binding mismatches, scope violations and authorization deny-list hits were all zero. Scope gates were run separately in PUBLIC_ELECTIVE mode.

Interpretation: RRF has a higher MRR on these 15 development questions, while Recall@5 and complete-evidence-set rate are already 1.0 for all three paths. This is a small AI-drafted diagnostic set over eight eligible chunks, not a production-wide quality estimate. Nine excluded chunks were not indexed, so the zero authorization deny-list count does not by itself prove rejection when an excluded chunk is present in a candidate pool.

The query vector cache contains 18 unique questions. It was generated in three DashScope requests; only evaluation question text was sent, and no chunk body or excluded content was sent. The retrieval replay used the saved cache, called no model/Embedding API, and ran all database queries in read-only transactions. The vector profile remains BUILDING and was not activated.

For an additional replay without overwriting the recorded result, run:

    powershell -NoProfile -ExecutionPolicy Bypass -File .\run-retrieval-comparison.ps1

The script writes replay artifacts to a new temporary directory under the current user's TEMP folder and removes its temporary JUnit source from the worktree when finished. It requires the isolated rc2 database to be running and ZHKU_DB_PASSWORD in the process environment.
## Artifacts

- cases.json: 15 positive retrieval cases, one authorization exclusion gate, and two scope-contract gates.
- corpus-snapshot.json: 17 chunk metadata rows; excluded chunk bodies remain omitted.
- source-url-repair.sql: guarded SQL for a fresh isolated candidate database only.
- source-url-repair.json: observed before/after hashes and row-level repair evidence.
- database-verification.json: live query evidence for source URLs, unchanged chunk identity/checksum set, and the identical eight-chunk lexical/vector index pool.
- query-embeddings.json: the 18 cached query vectors from text-embedding-v4 (1024 dimensions), bound to exact question text hashes.
- Rc2RetrievalEvaluationHarnessTest.java: temporary JUnit harness source that invokes the project lexical/vector retrievers and RRF service; it is not left in application test sources.
- retrieval-results-v2-rc2.json: actual Top 5 results for all three algorithms and both scope modes.
- retrieval-trace-v2-rc2.json: actual Top 8 lexical/vector rankings and Top 5 RRF rankings with RRF scores.
- scored-results-v2-rc2.json: metrics and independent eligibility/scope/source-binding gate totals.
- retrieval-comparison-report.md: result interpretation, ranking differences, and limitations.
- run-retrieval-comparison.ps1: isolated, read-only replay using cached query vectors; replay does not call the Embedding API.
- source-audit-pre-repair.json: immutable historical audit ledger showing the state before URL repair.
- 2026-handbook-comparison.md: prior local OCR comparison and its limitations.
- score-v2-results.ps1: unchanged deterministic scorer; scored-results-v2-rc2.json contains the actual three-way comparison metrics.
