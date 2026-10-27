package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 仅测试classpath：E02 单题页面 Live 计划的离线结构闸门。
 * 只做字段/金额一致性核对，不读凭证、不联网、不创建 Provider；费用是否可强制扣除仍由宿主与启动器二次守卫。
 */
final class RuleAgentE02Policy {
    static final String PURPOSE = "E02_SINGLE_QUESTION_PAGE_LIVE_V1";
    static final String PLAN = "knowledge/src/test/resources/rule-agent-e02-page-plan.json";
    static final String QUESTION = "有人说公选必须上满16学时，24学时的安排肯定违规，是这样吗？";
    static final String HARD_BUDGET_RMB = "4.02";
    static final String WORST_CASE_RESERVED_RMB = "4.013959";
    static final int MAXIMUM_PROVIDER_CALLS = 5;
    static final int MAXIMUM_TASKS = 1;
    static final int MAXIMUM_RUNS = 1;
    static final int MAXIMUM_OUTPUT_TOKENS = 1024;
    static final long MAXIMUM_INPUT_TOKENS = 1000000L;
    static final int MAXIMUM_REQUEST_BYTES = 65536;
    static final String ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    static final String MODEL = "qwen3.8-flash";
    private static final Set<String> TOP = Set.of("schemaVersion", "purpose", "authorizationStatus", "endpoint", "model",
            "referenceRevision", "maximumTasks", "maximumRuns", "maximumProviderCalls", "maximumOutputTokensPerCall",
            "maximumInputTokensPerCallConservative", "maximumRequestBytes", "automaticRetries", "money", "stopConditions",
            "page", "cases");
    private static final Set<String> MONEY = Set.of("currency", "hardBudget", "inputPerMillion", "outputPerMillion",
            "maximumBilledOutputTokensPerCallConservative", "worstCaseReserved", "estimatedTypicalRange", "tariffCheckedDate",
            "tariffValidThrough", "tariffSource", "evidenceFile", "billing");
    private static final Set<String> PAGE = Set.of("entry", "dist", "driver", "oneSinglePageTask", "javaPostAllowed", "browserRequired");
    private static final Set<String> CASE = Set.of("id", "question", "maximumRuns", "maximumCalls", "expected",
            "necessaryEvidence", "acceptableRefusal", "forbidden", "safetyPass", "productPass");
    private static final List<String> REQUIRED_CASE_TEXT = List.of("expected", "acceptableRefusal", "safetyPass", "productPass");
    private static final List<String> REQUIRED_CASE_LIST = List.of("necessaryEvidence", "forbidden");

    private RuleAgentE02Policy() {}

    /** 任何字段增删、题干改写、题量/调用数/金额漂移都在这里拒绝；拒绝信息不带问题或金额细节。 */
    static void validate(JsonNode plan) {
        if (plan == null || !plan.isObject() || !fields(plan, TOP)) throw reject();
        if (!integer(plan.path("schemaVersion"), 1) || !PURPOSE.equals(plan.path("purpose").asText())
                || !"NOT_AUTHORIZED".equals(plan.path("authorizationStatus").asText())
                || !ENDPOINT.equals(plan.path("endpoint").asText()) || !MODEL.equals(plan.path("model").asText())
                || !integer(plan.path("maximumTasks"), MAXIMUM_TASKS) || !integer(plan.path("maximumRuns"), MAXIMUM_RUNS)
                || !integer(plan.path("maximumProviderCalls"), MAXIMUM_PROVIDER_CALLS)
                || !integer(plan.path("maximumOutputTokensPerCall"), MAXIMUM_OUTPUT_TOKENS)
                || !integer(plan.path("maximumRequestBytes"), MAXIMUM_REQUEST_BYTES)
                || plan.path("maximumInputTokensPerCallConservative").asLong() != MAXIMUM_INPUT_TOKENS
                || !integer(plan.path("automaticRetries"), 0)) throw reject();
        // 单题只允许一次运行：追问需要在同一题上消耗第二个run，本计划没有该额度。
        if (!plan.path("stopConditions").isArray() || plan.path("stopConditions").isEmpty()) throw reject();
        money(plan.path("money"), plan);
        page(plan.path("page"));
        cases(plan.path("cases"));
    }

    private static void money(JsonNode money, JsonNode plan) {
        if (!money.isObject() || !fields(money, MONEY)) throw reject();
        if (!"CNY".equals(money.path("currency").asText())) throw reject();
        BigDecimal perCall = perCallReserve(money, plan);
        BigDecimal worst = perCall.multiply(BigDecimal.valueOf(MAXIMUM_PROVIDER_CALLS));
        if (worst.compareTo(new BigDecimal(WORST_CASE_RESERVED_RMB)) != 0
                || new BigDecimal(money.path("worstCaseReserved").asText()).compareTo(worst) != 0) throw reject();
        BigDecimal hard = new BigDecimal(money.path("hardBudget").asText());
        // 硬帽必须真的覆盖最坏预留，且不得低于本轮重新核价后的上限。
        if (hard.compareTo(worst) < 0 || hard.compareTo(new BigDecimal(HARD_BUDGET_RMB)) != 0) throw reject();
        if (new BigDecimal(money.path("inputPerMillion").asText()).compareTo(new BigDecimal("0.8")) != 0
                || new BigDecimal(money.path("outputPerMillion").asText()).compareTo(new BigDecimal("2.7")) != 0
                || money.path("maximumBilledOutputTokensPerCallConservative").asLong() != MAXIMUM_OUTPUT_TOKENS + 10) throw reject();
        for (String name : List.of("tariffCheckedDate", "tariffValidThrough", "tariffSource", "evidenceFile", "billing", "estimatedTypicalRange"))
            if (money.path(name).asText().isBlank()) throw reject();
    }

    /** 与 Package 的 perCallReserve 同式：整段输入上下文按百万计价，输出按文档许可的10 token误差上浮。 */
    static BigDecimal perCallReserve(JsonNode money, JsonNode plan) {
        return new BigDecimal(money.path("inputPerMillion").asText())
                .multiply(BigDecimal.valueOf(plan.path("maximumInputTokensPerCallConservative").asLong()))
                .add(new BigDecimal(money.path("outputPerMillion").asText())
                        .multiply(BigDecimal.valueOf(money.path("maximumBilledOutputTokensPerCallConservative").asLong())))
                .divide(BigDecimal.valueOf(1000000));
    }

    private static void page(JsonNode page) {
        if (!page.isObject() || !fields(page, PAGE)) throw reject();
        if (!"/rules".equals(page.path("entry").asText()) || !page.path("oneSinglePageTask").asBoolean(false)
                || page.path("javaPostAllowed").asBoolean(true) || !page.path("browserRequired").asBoolean(false)
                || page.path("dist").asText().isBlank() || page.path("driver").asText().isBlank()) throw reject();
    }

    private static void cases(JsonNode cases) {
        if (!cases.isArray() || cases.size() != MAXIMUM_TASKS) throw reject();
        JsonNode item = cases.get(0);
        if (!item.isObject() || !fields(item, CASE)) throw reject();
        if (!"E02".equals(item.path("id").asText()) || !QUESTION.equals(item.path("question").asText())
                || !integer(item.path("maximumRuns"), MAXIMUM_RUNS) || !integer(item.path("maximumCalls"), MAXIMUM_PROVIDER_CALLS))
            throw reject();
        // 评分契约必须冻结；缺一项就无法在执行前判断“安全但不完成”。
        for (String name : REQUIRED_CASE_TEXT) if (item.path(name).asText().isBlank()) throw reject();
        for (String name : REQUIRED_CASE_LIST) if (!item.path(name).isArray() || item.path(name).isEmpty()) throw reject();
    }

    private static boolean fields(JsonNode node, Set<String> expected) {
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(expected);
    }

    private static boolean integer(JsonNode node, int expected) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == expected;
    }

    private static IllegalArgumentException reject() {
        // 不带题干/金额/路径：失败摘要可能进入日志或回执。
        return new IllegalArgumentException("E02单题计划结构或金额不一致，停止待重新审包");
    }
}
