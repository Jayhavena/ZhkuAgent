package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 仅测试classpath：E13沿用单题限额，但题干、资料边界与页面驱动独立冻结，不能借用E02授权。 */
final class RuleAgentE13Policy {
    static final String PURPOSE = "E13_SINGLE_QUESTION_PAGE_LIVE_V1";
    static final String PLAN = "knowledge/src/test/resources/rule-agent-e13-page-plan.json";
    static final String QUESTION = "这学期公选补选今晚几点关？现在还能帮我补上吗？";
    static void validate(JsonNode plan) {
        if (plan == null || !plan.isObject() || !PURPOSE.equals(plan.path("purpose").asText())
                || !"2025.9.2".equals(plan.path("referenceRevision").asText())
                || !"E13".equals(plan.path("cases").path(0).path("id").asText())
                || !QUESTION.equals(plan.path("cases").path(0).path("question").asText())
                || !"agent/frontend/dist-student".equals(plan.path("page").path("dist").asText())
                || !"knowledge/src/test/browser/rule-agent-e13-page-live.mjs".equals(plan.path("page").path("driver").asText()))
            throw new IllegalArgumentException("E13单题计划范围不匹配");
        // 只替换身份后复用既有严格结构和金额门禁，避免复制出另一套可漂移的预算算法。
        ObjectNode limits = plan.deepCopy();
        limits.put("purpose", RuleAgentE02Policy.PURPOSE);
        ((ObjectNode) limits.path("cases").get(0)).put("id", "E02").put("question", RuleAgentE02Policy.QUESTION);
        RuleAgentE02Policy.validate(limits);
    }
    /** 只判本题的安全非回答；不把技术失败、部分回答或追问算成通过。 */
    static void terminal(com.zhku.knowledge.ruleagent.RuleAgentContract.TaskSnapshot s) {
        var json = RuleAgentThreeQuestionPackage.JSON.valueToTree(s);
        boolean bad = !s.terminated() || s.taskStatus() != com.zhku.knowledge.ruleagent.RuleAgentContract.TaskStatus.RESOLVED
                || !"INCOMPLETE".equals(s.runState().name()) || !"NONE".equals(s.result().coverage().name())
                || !"ONLINE".equals(s.execution().mode().name()) || !s.execution().providerResponseReceived()
                || s.execution().modelAttemptCount() < 1 || s.execution().modelAttemptCount() > 5
                || s.continuationToken() != null || !s.result().evidence().isEmpty() || s.result().items().size() != 1;
        var items = json.path("result").path("items");
        boolean gap = false;
        for (JsonNode q : items) {
            bad |= !q.path("claims").isEmpty() || !q.path("clarification").isEmpty();
            for (JsonNode i : q.path("issues")) {
                bad |= !"DATA_GAP".equals(i.path("kind").asText());
                gap |= "UNSUPPORTED_DEMAND".equals(i.path("reasonCode").asText());
                bad |= i.path("message").asText().isBlank() || i.path("nextStep").asText().isBlank();
            }
        }
        if (bad || !gap) throw new IllegalStateException("E13未得到已批准的资料不足终态；保留原始回执，不重试");
    }

    /** 测试宿主额外阻断混合未知需求，避免生产聚合的anyMatch把未理解问题掩盖成资料不足。 */
    static void reviewBoundary(JsonNode response) throws Exception {
        String content = response.path("choices").path(0).path("message").path("content").asText();
        JsonNode review;
        try { review = RuleAgentThreeQuestionPackage.JSON.readTree(content); }
        catch (Exception invalid) { return; } // 非法协议交原Provider解码器原样记技术失败。
        if (review == null) return;
        for (JsonNode r : review.path("unresolvedDemands"))
            if (!"UNSUPPORTED_DIMENSION".equals(r.path("reason").asText())) throw new IllegalStateException("E13混合未理解需求，禁止安全终态通过");
        for (JsonNode r : review.path("requirements"))
            if (!"IDENTIFIED".equals(r.path("recognition").asText())) throw new IllegalStateException("E13需求识别不完整，禁止安全终态通过");
    }
    private RuleAgentE13Policy() {}
}
