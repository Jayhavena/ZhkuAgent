package com.zhku.knowledge.eval.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 链路报告：既记录 Grader 判定，也记录“期望是否被打中”。
 *
 * <p>两者必须分开看：判定 PASS 不等于回归通过（可能期望的就是 NOT_EVALUABLE），
 * 回归通过也不等于产品合格，只是“链路行为与已审核契约一致”。
 */
public record EvalChainReport(String suiteId, String generatedAt, List<BlockReport> blocks,
        List<CaseRecord> cases, boolean regressionPassed) {

    public EvalChainReport {
        blocks = List.copyOf(blocks);
        cases = List.copyOf(cases);
    }

    public record BlockReport(String blockId, String kind, String evidenceDir, String collectionStatus,
            List<String> collectionReasons, List<String> collectedRuns) {
        public BlockReport {
            collectionReasons = List.copyOf(collectionReasons);
            collectedRuns = List.copyOf(collectedRuns);
        }
    }

    public record CaseRecord(String caseId, String title, String blockId, String source,
            String expectedVerdict, String verdict, boolean matched, List<String> details,
            List<String> expectedDetails, String actualTaskStatus, String actualCoverage,
            List<String> actionTypes, String note) {
        public CaseRecord {
            details = List.copyOf(details);
            expectedDetails = List.copyOf(expectedDetails);
            actionTypes = List.copyOf(actionTypes);
        }
    }

    public long matched() {
        return cases.stream().filter(CaseRecord::matched).count();
    }

    public long mismatched() {
        return cases.size() - matched();
    }

    public long verdictCount(String verdict) {
        return cases.stream().filter(c -> verdict.equals(c.verdict())).count();
    }

    public void write(Path directory) throws IOException {
        Files.createDirectories(directory);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("suiteId", suiteId);
        root.put("generatedAt", generatedAt);
        root.put("regressionPassed", regressionPassed);
        root.put("cases", cases.size());
        root.put("matched", matched());
        root.put("mismatched", mismatched());
        root.put("passed", verdictCount("PASS"));
        root.put("failed", verdictCount("FAIL"));
        root.put("notEvaluable", verdictCount("NOT_EVALUABLE"));
        ArrayNode blockArray = root.putArray("blocks");
        for (BlockReport block : blocks) {
            ObjectNode node = blockArray.addObject();
            node.put("blockId", block.blockId());
            node.put("kind", block.kind());
            node.put("evidenceDir", block.evidenceDir());
            node.put("collectionStatus", block.collectionStatus());
            ArrayNode reasons = node.putArray("collectionReasons");
            block.collectionReasons().forEach(reasons::add);
            ArrayNode runs = node.putArray("collectedRuns");
            block.collectedRuns().forEach(runs::add);
        }
        ArrayNode caseArray = root.putArray("results");
        for (CaseRecord record : cases) {
            ObjectNode node = caseArray.addObject();
            node.put("caseId", record.caseId());
            node.put("title", record.title());
            node.put("blockId", record.blockId());
            node.put("source", record.source());
            node.put("expectedVerdict", record.expectedVerdict());
            node.put("verdict", record.verdict());
            node.put("matched", record.matched());
            node.put("actualTaskStatus", record.actualTaskStatus());
            node.put("actualCoverage", record.actualCoverage());
            node.put("note", record.note());
            ArrayNode details = node.putArray("details");
            record.details().forEach(details::add);
            ArrayNode expectedDetails = node.putArray("expectedDetails");
            record.expectedDetails().forEach(expectedDetails::add);
            ArrayNode actions = node.putArray("actionTypes");
            record.actionTypes().forEach(actions::add);
        }
        Files.writeString(directory.resolve("report.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("report.md"), markdown(), StandardCharsets.UTF_8);
    }

    public String markdown() {
        var out = new StringBuilder();
        out.append("# Agent Eval Chain Report\n\n");
        out.append("- suite: `").append(suiteId).append("`\n");
        out.append("- generatedAt: ").append(generatedAt).append('\n');
        out.append("- regression: ").append(regressionPassed ? "PASS" : "FAIL").append('\n');
        out.append("- cases: ").append(cases.size()).append(" (matched ").append(matched())
                .append(", mismatched ").append(mismatched()).append(")\n");
        out.append("- verdicts: PASS ").append(verdictCount("PASS"))
                .append(", FAIL ").append(verdictCount("FAIL"))
                .append(", NOT_EVALUABLE ").append(verdictCount("NOT_EVALUABLE")).append("\n\n");
        out.append("| Case | Block | 来源 | 期望 | 实际 | 匹配 | 明细 |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (CaseRecord record : cases) {
            out.append("| ").append(record.caseId())
                    .append(" | ").append(record.blockId())
                    .append(" | ").append(shorten(record.source()))
                    .append(" | ").append(record.expectedVerdict())
                    .append(" | ").append(record.verdict())
                    .append(" | ").append(record.matched() ? "yes" : "NO")
                    .append(" | ").append(String.join(", ", record.details()))
                    .append(" |\n");
        }
        out.append("\n## 证据块\n\n");
        for (BlockReport block : blocks) {
            out.append("- `").append(block.blockId()).append("` (").append(block.kind()).append(")")
                    .append(": ").append(block.collectionStatus());
            if (!block.collectionReasons().isEmpty()) {
                out.append(", reasons=").append(String.join("/", block.collectionReasons()));
            }
            out.append(", runs=").append(block.collectedRuns().size()).append('\n');
        }
        return out.toString();
    }

    private static String shorten(String source) {
        return source == null ? "" : source.replace('|', '/');
    }
}
