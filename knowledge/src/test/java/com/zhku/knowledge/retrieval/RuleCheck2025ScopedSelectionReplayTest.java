package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 已采集真实响应回到实际应用/SQL/原文；回放不算新调用，也不算独立在线重复。 */
class RuleCheck2025ScopedSelectionReplayTest {
    static final String BASE="docs/evidence/rule-check-refactor/2026-10-10/package-01/";
    static final String PACKAGE_SHA="62d759d92163ea17aaa985bb1dae73b7a0208e6184df2b2bd58ffdec159bf168";
    private static void requireDevelopmentCase(String id,JsonNode reference,String controlId,JsonNode decision,String selectedId,JsonNode semantic){
        // ID控制是开发者工程契约，不冒充人工语义真值；必要事实映射独立依据已确认20题/B02。
        assertThat(decision.path("status").asText()).as(id).isEqualTo(reference.path("humanReview").path("acceptedStatus").asText());
        if(id.equals("Q02"))assertThat(selectedId).isNull();
        else assertThat(selectedId).as(id+"选题").isEqualTo(controlId);
        List<String> necessary=switch(id){
            case "Q01"->List.of("undergraduate-plan-obligation");
            case "Q03"->List.of("elective-category-distinction");
            case "Q04"->List.of("public-course-subcategories");
            case "Q06"->List.of("hours-principle");
            case "Q07"->List.of("hours-upper-limit");
            case "Q08"->List.of("campus-enrollment-general-range","campus-below-thirty");
            case "Q09"->List.of("campus-enrollment-general-range");
            case "Q10","Q13"->List.of("plan-selection-basis");
            case "Q11","Q12"->List.of("online-general-half");
            case "Q14"->List.of("online-selection-plan");
            case "Q15"->List.of("unenrolled-assessment-disjunction");
            case "Q17","Q18"->List.of("public-semester-four-credits");
            case "Q19"->List.of("no-same-or-similar-plan-course","no-repeat-passed-course");
            case "Q20"->List.of("schedule-previous-semester","schedule-order");
            case "Q02","Q05","Q16"->List.of();
            default->throw new IllegalArgumentException("未声明开发题必要事实");
        };
        assertThat(decision.path("confirmedFacts").findValuesAsText("id")).as(id+"必要事实").containsAll(necessary);
        if(id.equals("Q05")){
            assertThat(decision.path("confirmedFacts")).isEmpty();
            assertThat(decision.path("clarificationQuestions").findValuesAsText("key")).contains("courseType");
        }
        if(id.equals("Q02")||id.equals("Q16"))assertThat(decision.path("confirmedFacts")).isEmpty();
        // 对照已审核语义材料的原始fact ID/text，防止保留ID却改动限定、数值或否定。
        for(var fact:decision.path("confirmedFacts")){
            JsonNode reviewed=null;
            for(var rule:semantic.path("rules"))for(var candidate:rule.path("facts"))
                if(candidate.path("id").asText().equals(fact.path("id").asText()))reviewed=candidate;
            assertThat(reviewed).as(id+"审核事实").isNotNull();
            assertThat(fact.path("text").asText()).as(id+"事实正文").isEqualTo(reviewed.path("text").asText());
        }
    }
    private JsonNode priorCase()throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var prior=mapper.readTree(Files.readAllBytes(root.resolve("docs/evidence/rule-check-refactor/2026-10-07/package01-replay-final-20261010-01/package01-scoped-replay.json")));
        for(var row:prior.path("cases"))if(row.path("caseId").asText().equals("Q03"))return row;
        throw new IllegalArgumentException("缺少既有实际正控制");
    }
    @Test void keepingAnsweredStatusCannotHideDeletedNecessaryFact()throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();var inputs=RuleCheckDevelopmentInputs.load(root);var row=priorCase();
        JsonNode ref=inputs.reference().path("records").get(2);
        var decision=(com.fasterxml.jackson.databind.node.ObjectNode)row.path("actualPath").path("result").path("decision").deepCopy();
        var facts=(com.fasterxml.jackson.databind.node.ArrayNode)decision.path("confirmedFacts");
        for(int i=0;i<facts.size();i++)if(facts.get(i).path("id").asText().equals("elective-category-distinction")){facts.remove(i);break;}
        assertThat(decision.path("status").asText()).isEqualTo("ANSWERED");assertThat(facts).isNotEmpty();
        assertThatThrownBy(()->requireDevelopmentCase("Q03",ref,"S02/GENERAL",decision,"S02/GENERAL",null)).isInstanceOf(AssertionError.class);
    }
    @Test void keepingAnsweredStatusCannotHideWrongSelectedRule()throws Exception {
        var inputs=RuleCheckDevelopmentInputs.load(Path.of("..").toAbsolutePath().normalize());var row=priorCase();
        assertThatThrownBy(()->requireDevelopmentCase("Q03",inputs.reference().path("records").get(2),"S02/GENERAL",
            row.path("actualPath").path("result").path("decision"),"S03/GENERAL",null)).isInstanceOf(AssertionError.class);
    }
    @Test void verifiesActualScopedChoicesAndEvidenceInsteadOfStatusOnly()throws Exception {
        var fixture=new RuleCheck2025NarrowingValidationTest();fixture.start();
        try{
            var trial=fixture.trial;var inputs=fixture.inputs;Path base=fixture.root.resolve(BASE);
            assertThat(Reviewed2025Trial.sha(Files.readAllBytes(base.resolve("request-package.json")))).isEqualTo(PACKAGE_SHA);
            var pack=trial.read(base.resolve("request-package.json"));var summary=trial.read(base.resolve("provider-capture/summary.json"));
            var semantic=trial.read(fixture.root.resolve("docs/product/rule-check-2025-b02-semantic-review.json"));
            var auth=trial.read(base.resolve("external-transfer-authorization.json"));
            assertThat(auth.path("authority").asText()).isEqualTo("DIRECT_USER_MESSAGE");assertThat(auth.path("decision").asText()).isEqualTo("CONFIRMED");
            assertThat(auth.path("packageSha256").asText()).isEqualTo(PACKAGE_SHA);
            assertThat(summary.path("completedAllRequests").asBoolean()).isTrue();assertThat(summary.path("sourceUnchanged").asBoolean()).isTrue();
            assertThat(summary.path("records")).hasSize(20);assertThat(pack.path("localBlockedCases")).hasSize(4);
            var all=new ArrayList<JsonNode>();pack.path("requests").forEach(all::add);pack.path("localBlockedCases").forEach(all::add);
            var rows=new ArrayList<Map<String,Object>>();int matched=0,controlMatches=0,locations=0,audits=0;
            for(var item:all){
                String id=item.path("caseId").asText();boolean blocked=item.path("localBlocked").asBoolean();
                String question=blocked?item.path("question").asText():trial.mapper.readTree(item.path("body").path("messages").get(1).path("content").asText()).path("question").asText();
                JsonNode ref=null;for(var r:inputs.reference().path("records"))if(r.path("reviewId").asText().equals(id))ref=r;
                if(ref!=null)assertThat(question).isEqualTo(ref.path("question").asText());
                JsonNode observation=null;
                if(!blocked){
                    JsonNode identity=null;for(var r:summary.path("records"))if(r.path("caseId").asText().equals(id))identity=r;
                    assertThat(identity).isNotNull();assertThat(identity.path("path").asText()).isEqualTo(id+".json");
                    Path path=base.resolve("provider-capture/"+id+".json");assertThat(Reviewed2025Trial.sha(Files.readAllBytes(path))).isEqualTo(identity.path("sha256").asText());
                    observation=trial.read(path);assertThat(observation.path("status").asText()).isEqualTo("RESPONSE_RECEIVED");
                }
                final JsonNode observed=observation;var outcome=new BoundedRuleSelector.Outcome[1];
                try(var selector=new BoundedRuleSelector(request->{
                    try{
                        assertThat(blocked).isFalse();
                        assertThat(request.systemConstraints()).isEqualTo(item.path("body").path("messages").get(0).path("content").asText());
                        assertThat(trial.mapper.readTree(request.userPayload())).isEqualTo(trial.mapper.readTree(item.path("body").path("messages").get(1).path("content").asText()));
                        return observed.path("rawModelContent").asText();
                    }catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}
                },Duration.ofSeconds(5),1)){
                    var path=fixture.run(question,"PROVIDER_REPLAY_NO_FIXED_ID",true,false,(q,o)->{outcome[0]=selector.selectOutcome(q,o);return outcome[0].ruleId();});
                    var result=(RuleCheckScopedResult)path.get("result");
                    if(blocked){assertThat(selector.attempts()).isZero();assertThat(path.get("retrievalCalls")).isEqualTo(0);assertThat((List<?>)path.get("persistedAudits")).isEmpty();assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);}
                    else{assertThat(selector.attempts()).isEqualTo(1);assertThat(outcome[0].code().name()).isEqualTo(observed.path("protocolDiagnostic").asText());}
                    var row=new LinkedHashMap<String,Object>();row.put("caseId",id);row.put("question",question);row.put("localBlocked",blocked);row.put("selectorOutcome",outcome[0]);row.put("actualPath",path);
                    if(ref!=null){
                        boolean same=result.decision().status().name().equals(ref.path("humanReview").path("acceptedStatus").asText());if(same)matched++;
                        String control=inputs.controls().path("controls").path(id).asText();boolean sameId=outcome[0]!=null&&Objects.equals(control,outcome[0].ruleId());if(sameId)controlMatches++;
                        row.put("statusMatchesReference",same);row.put("matchesDeveloperIdControl",sameId);row.put("developerIdControl",control);row.put("humanSemanticGrade","NOT_SCORED");
                        requireDevelopmentCase(id,ref,control,trial.mapper.valueToTree(result.decision()),outcome[0]==null?null:outcome[0].ruleId(),semantic);
                    }else row.put("humanSemanticGrade","UNGRADED_PROBE");
                    if(id.equals("Q10")){assertThat(outcome[0].ruleId()).isEqualTo("S05/GENERAL");assertThat(result.decision().confirmedFacts()).anyMatch(f->f.id().equals("plan-selection-basis"));}
                    if(id.equals("Q12")){assertThat(result.scope().questionScope()).contains("第8条没有列出具体例外及审批办法。");assertThat(result.decision().confirmedFacts()).anyMatch(f->f.text().contains("一般不能"));}
                    if(List.of("Q02","Q16","O02","O04").contains(id))assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
                    if(List.of("Q09","Q13","Q18").contains(id))assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
                    locations+=((List<?>)path.get("actualOriginalLocations")).size();audits+=((List<?>)path.get("persistedAudits")).size();rows.add(row);
                }
            }
            trial.save("package01-scoped-replay.json",Map.of("cases",rows,"developmentStatusMatches",matched,"developmentCaseCount",20,
                "matchesDeveloperIdControl",controlMatches,"actualOriginalLocations",locations,"actualAuditReads",audits,
                "providerCallsInReplay",0,"reportCasesConsumed",0,"humanSemanticGradesProduced",false,"mode","ACTUAL_RESPONSE_OFFLINE_REPLAY"));
            trial.save("package01-required-fact-contract.json",Map.of("developmentCases",20,"externalDevelopmentIdChecks",19,
                "neededFactIdAndReviewedTextChecksPassed",true,"clarificationAndBlockedNoFactChecksPassed",true,
                "humanSemanticGradeProduced",false,"mappingAuthority","DEVELOPER_MECHANICAL_MAPPING_FROM_CONFIRMED_REFERENCE_AND_B02"));
            assertThat(matched).isEqualTo(20);RuleCheckDevelopmentInputs.verifyUnchanged(fixture.root,inputs);
        }finally{fixture.close();}
    }
}
