package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * 真实审核资产的运行包检查单独执行，避免核心CI依赖仓库外手册。
 * 不用跳过掩盖缺资产：显式提供zhku.t06.handbookPdf后本类仍须完整通过。
 */
class RuleAgentProbePackageAssetsTest {
    final AtomicInteger calls = new AtomicInteger();
    RuleAgentOnlineFactory factory;
    @Test void prepareP01PacketFromCurrentAuthoritiesWithoutOpeningPorts()throws Exception{
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        var method=RuleAgentOnlineProbeHost.class.getDeclaredMethod("packageNode",java.nio.file.Path.class);
        method.setAccessible(true);
        var pack=(com.fasterxml.jackson.databind.node.ObjectNode)method.invoke(null,root);
        pack.put("purpose","T05_P01_READ_REFERENCE_HARDENING_FULL_FLOW_NOT_PRODUCT_QUALITY_EVAL");
        pack.put("maximumProviderCalls",4);
        pack.put("authorizationStatus","NOT_AUTHORIZED");
        pack.putArray("cases").addObject().put("id","P01").put("question","专业选修课和公共选修课在修读安排上有什么不同？");
        pack.put("remainingBudgetRule","每run3次决策加1次独立核验，共4次/45秒；单次10秒/1024输出token，无自动重试。新检索使旧编号失效。");
        pack.put("dynamicFeedback","完整当前候选摘要、c编号及宿主集合代号；实际UUID映射仅留宿主。已读取的审核原文、事实、条件及独立核验能力/来源指纹不变。不发送owner/session/token/凭证/参考答案/整本PDF。");
        pack.put("executionReadiness","PACKAGE_ONLY; current old-batch launcher must reject this packet; require P01-only preflight and new explicit authorization before any call");
        assertThat(pack.path("protocolRevision").asText()).isEqualTo("RULE_AGENT_ACTION_CONTRACT_06");
        assertThat(pack.path("automaticRetries").asInt()).isZero();
        assertThat(factory).isNull();
        assertThat(calls).hasValue(0);
        var output=java.nio.file.Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"p01-request-package.json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),pack);
    }

    @Test void currentP01PacketMatchesActualReviewedAssetsWithoutCalls()throws Exception{
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        var method=RuleAgentOnlineProbeHost.class.getDeclaredMethod("packageNode",java.nio.file.Path.class);
        method.setAccessible(true);
        var current=(JsonNode)method.invoke(null,root);
        RuleAgentOnlineProbeHost.validatePackage(current);
        assertThat(current.path("remainingBudgetRule").asText()).contains("110秒","20秒").doesNotContain("45秒","单次10秒");
        assertThat(current.path("executionPolicyRevision").asText()).isEqualTo("STATE_AWARE_P02_CONTINUATION_01");
        assertThat(calls).hasValue(0);
        assertThat(factory).isNull();
    }

    @Test void p02PacketContainsTwoRunInteractionAndKeepsPerRunBudget() throws Exception {
        var m=RuleAgentOnlineProbeHost.class.getDeclaredMethod("packageNode",java.nio.file.Path.class);m.setAccessible(true);
        var p=(JsonNode)m.invoke(null,java.nio.file.Path.of("..").toAbsolutePath().normalize());
        assertThat(p.path("maximumProviderCalls").asInt()).isEqualTo(10);
        assertThat(p.path("perRunCalls").asInt()).isEqualTo(5);
        assertThat(p.path("cases").size()).isEqualTo(1);
        assertThat(p.path("cases").get(0).path("id").asText()).isEqualTo("P02");
        assertThat(p.path("cases").get(0).path("reply").asText()).isEqualTo("PUBLIC");
    }
}
