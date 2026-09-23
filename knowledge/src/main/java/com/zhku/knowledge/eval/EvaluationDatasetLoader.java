package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.routing.AnswerRoute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 版本化评测集与清单的加载器（阶段 10 批2）。
 *
 * <p>只做"读入 + 结构校验 + 交叉核对"，**不做任何评测执行**——执行（`EvaluationRunner`）
 * 是批3 的范围。把读入和执行分开，评测集的结构问题才能在批3 之前被测试锁死。</p>
 */
public final class EvaluationDatasetLoader {

    private final ObjectMapper objectMapper;

    public EvaluationDatasetLoader() {
        this(new ObjectMapper());
    }

    EvaluationDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 读取器不能为空");
    }

    public EvaluationDataset loadDataset(Path file) throws IOException {
        Objects.requireNonNull(file, "数据集路径不能为空");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("评测集文件不存在：" + file);
        }
        JsonNode root = objectMapper.readTree(file.toFile());
        List<EvaluationDataset.Case> cases = new ArrayList<>();
        for (JsonNode node : requireArray(root, "cases")) {
            cases.add(new EvaluationDataset.Case(
                    requiredText(node, "caseId"),
                    requiredText(node, "question"),
                    parseRoute(requiredText(node, "expectedRoute")),
                    requiredBoolean(node, "expectedHandoffRequired"),
                    requiredBoolean(node, "expectRefusal"),
                    readCitations(node.path("expectedCitations"))));
        }
        return new EvaluationDataset(
                requiredText(root, "datasetId"),
                requiredText(root, "datasetVersion"),
                requiredText(root, "corpusFingerprint"),
                List.copyOf(cases));
    }

    public EvaluationManifest loadManifest(Path file) throws IOException {
        Objects.requireNonNull(file, "清单路径不能为空");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("清单文件不存在：" + file);
        }
        JsonNode root = objectMapper.readTree(file.toFile());
        List<EvaluationManifest.Entry> entries = new ArrayList<>();
        for (JsonNode node : requireArray(root, "datasets")) {
            entries.add(new EvaluationManifest.Entry(
                    requiredText(node, "datasetId"),
                    requiredText(node, "file"),
                    requiredText(node, "datasetVersion"),
                    requiredInt(node, "caseCount")));
        }
        return new EvaluationManifest(
                requiredText(root, "manifestVersion"),
                requiredText(root, "corpusFingerprint"),
                List.copyOf(entries));
    }

    /** 清单登记与数据集本体的交叉核对：任何一处不一致都说明"改了数据忘了改清单"。 */
    public static void verifyAgainst(
            EvaluationManifest manifest,
            EvaluationManifest.Entry entry,
            EvaluationDataset dataset) {
        Objects.requireNonNull(manifest, "清单不能为空");
        Objects.requireNonNull(entry, "清单登记不能为空");
        Objects.requireNonNull(dataset, "数据集不能为空");
        if (!entry.datasetId().equals(dataset.datasetId())) {
            throw new IllegalArgumentException("清单与数据集的 datasetId 不一致："
                    + entry.datasetId() + " vs " + dataset.datasetId());
        }
        if (!entry.datasetVersion().equals(dataset.datasetVersion())) {
            throw new IllegalArgumentException("清单与数据集的 datasetVersion 不一致："
                    + entry.datasetVersion() + " vs " + dataset.datasetVersion());
        }
        if (entry.caseCount() != dataset.cases().size()) {
            throw new IllegalArgumentException("清单登记的用例数与数据集不一致："
                    + entry.caseCount() + " vs " + dataset.cases().size());
        }
        if (!manifest.corpusFingerprint().equals(dataset.corpusFingerprint())) {
            throw new IllegalArgumentException("清单与数据集的语料指纹不一致——"
                    + "评测数字必须连同同一条语料指纹一起报告");
        }
    }

    private List<EvaluationDataset.Citation> readCitations(JsonNode nodes) {
        List<EvaluationDataset.Citation> citations = new ArrayList<>();
        if (!nodes.isArray()) {
            return List.copyOf(citations);
        }
        for (JsonNode node : nodes) {
            Integer pageNumber = node.path("pageNumber").isInt()
                    ? node.path("pageNumber").asInt() : null;
            citations.add(new EvaluationDataset.Citation(
                    requiredText(node, "sourceLocator"), pageNumber));
        }
        return List.copyOf(citations);
    }

    private static AnswerRoute parseRoute(String routeName) {
        try {
            return AnswerRoute.valueOf(routeName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("评测集出现未知路由结论：" + routeName
                    + "（只允许既有五级路由，防拼写漂移）");
        }
    }

    private static JsonNode requireArray(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        if (field == null || !field.isArray()) {
            throw new IllegalArgumentException("评测集缺少数组字段：" + fieldName);
        }
        return field;
    }

    private static String requiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            throw new IllegalArgumentException("评测集缺少文本字段：" + fieldName);
        }
        return field.asText().strip();
    }

    private static int requiredInt(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.canConvertToInt()) {
            throw new IllegalArgumentException("评测集缺少整数字段：" + fieldName);
        }
        return field.asInt();
    }

    private static boolean requiredBoolean(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isBoolean()) {
            throw new IllegalArgumentException("评测集缺少布尔字段：" + fieldName);
        }
        return field.asBoolean();
    }
}
