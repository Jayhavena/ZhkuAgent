package com.zhku.knowledge.rulecheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 新开发者边界合同，既不是新增人工参考，也不是实际模型/产品准确率。 */
class RuleCheckScopeLanguageTest {
    @Test void yearAliasCannotBeTakenFromInsideResearchVerbs() {
        var guard=new RuleCheckApplicabilityGuard();var undergrad=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        for(String q:List.of("请帮我调研一下全日制普通本科公共选修课的学时上限。","请调研一名本科学生如何安排公共选修。","请解释科研一般要求中的本科选修规则。")) {
            var a=guard.assess(q,undergrad,"2025.9.2");
            assertThat(a.requestedAudiences()).as(q).doesNotContain(RuleCheckApplicabilityGuard.Audience.POSTGRADUATE);assertThat(a.blocked()).isFalse();
        }
        for(String q:List.of("我是研一学生，选修要求是什么？","本科与研一学生的选修要求一样吗？","请给硕二同学解释选修要求。"))
            assertThat(guard.assess(q,undergrad,"2025.9.2").blocked()).as(q).isTrue();
    }
    @Test void postgraduateYearAliasesAreIndependentAudienceSignals() {
        var guard=new RuleCheckApplicabilityGuard();var undergrad=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        for(String alias:List.of("研一","研二","研三","研１","硕一","硕二","博一","博四")) {
            var result=guard.assess(alias+"学生的选修要求按什么办法？",undergrad,"2025.9.2");
            assertThat(result.requestedAudiences()).as(alias).containsExactly(RuleCheckApplicabilityGuard.Audience.POSTGRADUATE);
            assertThat(result.blocked()).isTrue();
        }
    }
    @Test void aliasExclusionCannotHideAnotherActivePostgraduateDemand() {
        var guard=new RuleCheckApplicabilityGuard();var undergrad=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        assertThat(guard.assess("我不是研一，只解释全日制普通本科的一般选修要求。",undergrad,"2025.9.2").blocked()).isFalse();
        assertThat(guard.assess("不问研一的选修要求，但请解释硕二的选修规定。",undergrad,"2025.9.2").blocked()).isTrue();
        assertThat(guard.assess("不问研一的选修要求，只解释全日制普通本科的一般选修要求。",undergrad,"2025.9.2").blocked()).isFalse();
    }
    @Test void quotedContentRequestedAgainCannotLoseItsScopeRisk() {
        var guard=new RuleCheckApplicabilityGuard();var coverage=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        var questions=List.of("“今天入口是否开放”只是引用；请回答这句引用。",
            "“研究生的选修要求”只是引用；请解释这段引用里的要求。",
            "“今天入口是否开放”只是例句；请回答该例句。",
            "“研究生的选修要求”仅作举例；请说明上面例子中的要求。",
            "“今天入口是否开放”只是例句；请直接作答。");
        assertThat(questions).allSatisfy(q->assertThat(guard.assess(q,coverage,"2025.9.2").blocked()).as(q).isTrue());
    }
    @Test void otherSituationsAreNotAThirdPersonApplication() {
        var guard=new RuleCheckApplicabilityGuard();var coverage=RuleCheckApplicabilityGuard.Coverage.unspecified();
        var general=guard.assess("我想了解一般规则中，其他可以申请选修学分认定的情形。",coverage,"2025.9.2");
        assertThat(general.personalRecordsRequired()).isFalse();assertThat(general.evidenceGaps()).isEmpty();
        assertThat(guard.assess("他可以申请选修学分认定吗？",coverage,"2025.9.2").personalRecordsRequired()).isTrue();
    }
    @Test void excludedRequestAloneCannotBecomeAnAnsweredGeneralTask() {
        var result=new RuleCheckApplicabilityGuard().assess("不确认个人资格。",RuleCheckApplicabilityGuard.Coverage.unspecified(),"2025.9.2");
        assertThat(result.blocked()).isTrue();assertThat(result.blockingReasons()).isNotEmpty();
    }
    @Test void explicitExclusionsAndImplicitRecordsPreserveActiveMixedDemands() throws Exception {
        var root=Path.of("..").toAbsolutePath().normalize();var file=root.resolve("docs/product/rule-check-c05-language-cases.json");
        byte[] before=Files.readAllBytes(file);var mapper=new ObjectMapper().findAndRegisterModules();var input=mapper.readTree(before);
        var guard=new RuleCheckApplicabilityGuard();var coverage=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        var rows=new ArrayList<Map<String,Object>>();var probes=new ArrayList<Map<String,Object>>();
        for(var c:input.path("cases")){
            var assessment=guard.assess(c.path("question").asText(),coverage,"2025.9.2");
            String actual=assessment.blocked()?"BLOCKED":assessment.evidenceGaps().isEmpty()?"GENERAL_ALLOWED":"GENERAL_ONLY";
            rows.add(Map.of("caseId",c.path("caseId").asText(),"family",c.path("family").asText(),"question",c.path("question").asText(),
                "expectedDisposition",c.path("expectedDisposition").asText(),"actualDisposition",actual,"assessment",assessment));
        }
        for(var c:input.path("observationOnlyProbes"))probes.add(Map.of("caseId",c.path("caseId").asText(),"question",c.path("question").asText(),"unverifiedRisk",c.path("unverifiedRisk").asText(),"assessment",guard.assess(c.path("question").asText(),coverage,"2025.9.2"),"graded",false));
        var directory=Path.of(Objects.requireNonNull(System.getProperty("zhku.rulecheck.evidenceDir")));var target=directory.resolve("c05-language-observations.json");
        Files.write(target,mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("datasetSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before)),
            "contractAuthority",input.path("authority").asText(),"cases",rows,"ungradedProbes",probes,"providerCalls",0,"humanSemanticGradeProduced",false)),StandardOpenOption.CREATE_NEW);
        assertThat(Files.readAllBytes(file)).isEqualTo(before);assertThat(rows).hasSize(36);
        assertThat(rows).allSatisfy(r->assertThat(r.get("actualDisposition")).as("%s: %s",r.get("caseId"),r.get("question")).isEqualTo(r.get("expectedDisposition")));
    }
}
