package com.zhku.knowledge.serviceguide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 读"已审核"的办事清单策展 JSON。
 *
 * <p>与 `ReviewedKnowledgeJsonReader` 同构：**只读人工审核过的清单文件**，不读原始 PDF，
 * 也不接受任何未经审核的文本——审核状态、渠道来源定位、步骤连续性都在这里强制校验，
 * 使"渠道只能来自审核资料"在入口处就无法绕过。</p>
 */
public final class ReviewedServiceGuideJsonReader {

    private static final String APPROVED_STATUS = "审核通过";

    private final ObjectMapper objectMapper;

    public ReviewedServiceGuideJsonReader() {
        this(new ObjectMapper());
    }

    ReviewedServiceGuideJsonReader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 读取器不能为空");
    }

    public ServiceGuide read(Path curationFile) throws IOException {
        Objects.requireNonNull(curationFile, "策展文件路径不能为空");
        if (!Files.isRegularFile(curationFile)) {
            throw new IllegalArgumentException("策展文件不存在或不是普通文件：" + curationFile);
        }
        JsonNode root = objectMapper.readTree(curationFile.toFile());

        String reviewStatus = requiredText(root, "review_status");
        if (!APPROVED_STATUS.equals(reviewStatus)) {
            throw new IllegalStateException("只有审核通过的办事清单才能导入");
        }
        boolean studentVisible = requiredBoolean(root, "student_visible");
        String reviewNotice = requiredText(root, "review_notice");
        Instant reviewedAt = Instant.parse(requiredText(root, "reviewed_at"));
        Instant validUntil = optionalInstant(root, "valid_until");

        return new ServiceGuide(
                requiredText(root, "guide_identifier"),
                requiredText(root, "guide_version"),
                requiredText(root, "title"),
                requiredText(root, "applicable_scope"),
                readSteps(root.path("steps")),
                readMaterials(root.path("materials")),
                readChannels(root.path("channels")),
                readRiskNotices(root.path("risk_notices")),
                optionalText(root, "source_rule_reference"),
                optionalText(root, "source_notice_reference"),
                reviewNotice,
                studentVisible,
                reviewedAt,
                validUntil);
    }

    private List<GuideStep> readSteps(JsonNode nodes) {
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException("办事清单必须包含至少一个步骤");
        }
        List<GuideStep> steps = new ArrayList<>();
        for (JsonNode node : nodes) {
            steps.add(new GuideStep(
                    requiredInteger(node, "order"),
                    requiredText(node, "instruction"),
                    optionalBoolean(node, "requires_identity_check")));
        }
        return List.copyOf(steps);
    }

    private List<ServiceGuide.Material> readMaterials(JsonNode nodes) {
        List<ServiceGuide.Material> materials = new ArrayList<>();
        if (!nodes.isArray()) {
            return List.copyOf(materials);
        }
        for (JsonNode node : nodes) {
            materials.add(new ServiceGuide.Material(
                    requiredInteger(node, "order"),
                    requiredText(node, "name"),
                    optionalText(node, "note"),
                    optionalBooleanDefaultTrue(node, "required")));
        }
        return List.copyOf(materials);
    }

    private List<OfficialChannel> readChannels(JsonNode nodes) {
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException("办事清单必须包含至少一条官方渠道");
        }
        List<OfficialChannel> channels = new ArrayList<>();
        for (JsonNode node : nodes) {
            channels.add(new OfficialChannel(
                    requiredInteger(node, "order"),
                    OfficialChannel.ChannelType.valueOf(requiredText(node, "channel_type")),
                    requiredText(node, "name"),
                    requiredText(node, "value"),
                    // 来源定位必填：没有它就无法核对这条渠道是否出自审核资料。
                    requiredText(node, "source_locator"),
                    Instant.parse(requiredText(node, "verified_at"))));
        }
        return List.copyOf(channels);
    }

    private List<ServiceGuide.RiskNotice> readRiskNotices(JsonNode nodes) {
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException("办事清单必须包含至少一条风险提示");
        }
        List<ServiceGuide.RiskNotice> notices = new ArrayList<>();
        for (JsonNode node : nodes) {
            notices.add(new ServiceGuide.RiskNotice(
                    requiredInteger(node, "order"),
                    requiredText(node, "text")));
        }
        return List.copyOf(notices);
    }

    private static String requiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            throw new IllegalArgumentException("办事清单策展缺少文本字段：" + fieldName);
        }
        return field.asText().strip();
    }

    private static int requiredInteger(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.canConvertToInt()) {
            throw new IllegalArgumentException("办事清单策展缺少整数字段：" + fieldName);
        }
        return field.asInt();
    }

    private static boolean requiredBoolean(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isBoolean()) {
            throw new IllegalArgumentException("办事清单策展缺少布尔字段：" + fieldName);
        }
        return field.asBoolean();
    }

    private static boolean optionalBoolean(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && field.isBoolean() && field.asBoolean();
    }

    private static boolean optionalBooleanDefaultTrue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null || !field.isBoolean() || field.asBoolean();
    }

    private static String optionalText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (!field.isTextual()) {
            throw new IllegalArgumentException("办事清单策展字段格式错误：" + fieldName);
        }
        return field.asText();
    }

    private static Instant optionalInstant(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
