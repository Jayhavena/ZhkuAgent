package com.zhku.knowledge.eval.chain;

import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;
import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 链路编排：证据采集 → 夹具投影 → 现有 Grader 判定 → 报告。
 *
 * <p>这里刻意不引入新的判定逻辑，所有 PASS/FAIL/NOT_EVALUABLE 都来自
 * {@link RuleAgentTraceEvaluator}；本类只负责“喂什么”和“和期望比对”。
 */
public final class EvalChainRunner {
    private EvalChainRunner() {}

    public static EvalChainReport run(Path repoRoot, Path suiteFile, Path workDirectory)
            throws IOException {
        Path root = repoRoot.toAbsolutePath().normalize();
        Path work = workDirectory.toAbsolutePath().normalize();
        Files.createDirectories(work);
        EvalChainSuite suite = EvalChainSuite.load(suiteFile);
        var blocks = new ArrayList<EvalChainReport.BlockReport>();
        var records = new ArrayList<EvalChainReport.CaseRecord>();

        for (EvalChainSuite.Block block : suite.blocks()) {
            if (EvalChainSuite.EVIDENCE_BUNDLE.equals(block.kind())) {
                runEvidenceBlock(root, work, block, blocks, records);
            } else {
                runFixtureBlock(root, block, blocks, records);
            }
        }

        boolean passed = !records.isEmpty() && records.stream().allMatch(EvalChainReport.CaseRecord::matched);
        return new EvalChainReport(suite.suiteId(), Instant.now().toString(), blocks, records, passed);
    }

    private static void runEvidenceBlock(Path root, Path work, EvalChainSuite.Block block,
            List<EvalChainReport.BlockReport> blocks, List<EvalChainReport.CaseRecord> records)
            throws IOException {
        Path evidence = root.resolve(block.evidenceDir());
        EvalEvidenceCollector.Collection collection = EvalEvidenceCollector.collect(evidence,
                block.blockId(), work.resolve("collected").resolve(block.blockId()), root);
        blocks.add(new EvalChainReport.BlockReport(block.blockId(), block.kind(), block.evidenceDir(),
                collection.status().name(), collection.reasons(),
                collection.runs().stream().map(r -> r.runId().toString()).toList()));

        if (collection.status() == EvalEvidenceCollector.Status.INCOMPLETE) {
            // 证据不完整时不能“跳过”用例：要么按 bundleCase 断言 NOT_EVALUABLE，要么直接失败。
            if (block.bundleCase() != null) {
                String detail = "COLLECTION_" + collection.status();
                var result = new RuleAgentTraceEvaluator.CaseResult(block.bundleCase().caseId(),
                        RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE, List.of(detail), null, null, List.of());
                records.add(record(block, block.bundleCase().caseId(), block.bundleCase().title(),
                        block.evidenceDir(), block.bundleCase().expectedVerdict(),
                        block.bundleCase().expectedDetails(), result,
                        "采集状态=" + collection.status() + " 原因=" + String.join("/", collection.reasons())));
            }
            for (EvalChainSuite.CaseDefinition definition : block.cases()) {
                records.add(record(block, definition, block.evidenceDir(), definition.expectedVerdict(),
                        definition.expectedDetails(),
                        new RuleAgentTraceEvaluator.CaseResult(definition.caseId(),
                                RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE,
                                List.of("COLLECTION_" + collection.status()), null, null, List.of()),
                        "证据块整体不可判定，未对该 run 作任何断言"));
            }
            return;
        }

        for (EvalChainSuite.CaseDefinition definition : block.cases()) {
            if (definition.runId() == null) {
                records.add(record(block, definition, block.evidenceDir(), definition.expectedVerdict(),
                        definition.expectedDetails(),
                        new RuleAgentTraceEvaluator.CaseResult(definition.caseId(),
                                RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE,
                                List.of("CASE_RUN_ID_MISSING"), null, null, List.of()),
                        "证据块用例必须声明 runId"));
                continue;
            }
            UUID runId = EvalChainSuite.parseRunId(definition.runId());
            var collected = collection.runs().stream()
                    .filter(run -> run.runId().equals(runId)).findFirst().orElse(null);
            if (collected == null) {
                // 缺 run 时仍走 Grader，让报告里的结论来自同一个判定入口。
                var missing = RuleAgentTraceEvaluator.evaluate(definition.toExpectation(), null);
                records.add(record(block, definition, block.evidenceDir(), definition.expectedVerdict(),
                        definition.expectedDetails(), missing, "证据目录中未采集到该 run"));
                continue;
            }
            EvalTraceFixture fixture = EvalTraceFixture.load(collected.fixture(), root);
            RuleAgentRunTrace trace = fixture.toTrace();
            RuleAgentTraceEvaluator.CaseResult result;
            String note = "采集投影=" + collected.projection();
            if (collection.status() == EvalEvidenceCollector.Status.PARTIAL_TERMINAL_FALLBACK
                    && !"FAILED".equals(trace.taskStatus())) {
                // 终态快照未发布时，非失败终态可能是运行中断的中间态，不能当作成功证据。
                result = new RuleAgentTraceEvaluator.CaseResult(definition.caseId(),
                        RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE,
                        List.of("TERMINAL_SNAPSHOT_UNPUBLISHED"), trace.taskStatus(),
                        trace.result().coverage(), List.of());
                note += "；终态未发布，按不可判定处理";
            } else {
                result = RuleAgentTraceEvaluator.evaluate(definition.toExpectation(), trace);
            }
            records.add(record(block, definition, block.evidenceDir(), definition.expectedVerdict(),
                    definition.expectedDetails(), result, note));
        }
    }

    private static void runFixtureBlock(Path root, EvalChainSuite.Block block,
            List<EvalChainReport.BlockReport> blocks, List<EvalChainReport.CaseRecord> records)
            throws IOException {
        blocks.add(new EvalChainReport.BlockReport(block.blockId(), block.kind(), null, "COMMITTED_FIXTURES",
                List.of(), List.of()));
        for (EvalChainSuite.CaseDefinition definition : block.cases()) {
            if (definition.fixture() == null) {
                throw new IllegalArgumentException("夹具用例缺少 fixture: " + definition.caseId());
            }
            EvalTraceFixture fixture = EvalTraceFixture.load(root.resolve(definition.fixture()), root);
            var result = RuleAgentTraceEvaluator.evaluate(definition.toExpectation(), fixture.toTrace());
            records.add(record(block, definition, definition.fixture(), definition.expectedVerdict(),
                    definition.expectedDetails(), result, "投影=" + fixture.projection()));
        }
    }

    private static EvalChainReport.CaseRecord record(EvalChainSuite.Block block,
            EvalChainSuite.CaseDefinition definition, String source, String expectedVerdict,
            List<String> expectedDetails, RuleAgentTraceEvaluator.CaseResult result, String note) {
        return record(block, definition.caseId(), definition.title(), source, expectedVerdict,
                expectedDetails, result, note);
    }

    private static EvalChainReport.CaseRecord record(EvalChainSuite.Block block, String caseId,
            String title, String source, String expectedVerdict, List<String> expectedDetails,
            RuleAgentTraceEvaluator.CaseResult result, String note) {
        boolean matched = expectedVerdict.equals(result.verdict().name())
                && result.details().containsAll(expectedDetails);
        return new EvalChainReport.CaseRecord(caseId, title, block.blockId(), source, expectedVerdict,
                result.verdict().name(), matched, result.details(), expectedDetails,
                result.actualTaskStatus(), result.actualCoverage(), result.actionTypes(), note);
    }
}
