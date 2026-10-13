package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static com.zhku.knowledge.rulecheck.RuleCheckTaskPlan.Action.*;
import static com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Topic.*;

/** 对已观察失败的工程回归；合成资料和替身计划不证明真实模型质量。 */
class RuleCheckCoverageRepairTest {
    private final UUID source=UUID.randomUUID();
    private final Map<UUID,RuleCheckEvidenceSnapshot> snapshots=new LinkedHashMap<>();
    private final List<RetrievedChunk> chunks=new ArrayList<>();
    private final List<RuleCheckApplicationService.CatalogEntry> entries=new ArrayList<>();
    private RuleCheckApplicationService.CatalogEntry add(String id,String text,RuleCheckTopicGuard.Topic topic) {
        UUID chunk=UUID.randomUUID();String checksum=sha(text);
        snapshots.put(chunk,new RuleCheckEvidenceSnapshot("fresh",source,chunk,"2025.9.2",checksum,text,true));
        chunks.add(new RetrievedChunk(chunk,text,"合成规则","合成原文位置",1,"2025.9.2",checksum,source));
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"2025.9.2",checksum,0,text.length(),text);
        var entry=new RuleCheckApplicationService.CatalogEntry(new RuleCheckRuleTemplate(id,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,
            "合成审核",LocalDate.of(2026,10,11),List.of(new RuleCheckRuleTemplate.Fact(id,text,true,Map.of(),List.of(binding))),List.of(),List.of(),List.of()),
            text,new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(topic));
        entries.add(entry);return entry;
    }
    private void addRetake() {
        var raw=add("S10/RETAKE_ONLY","合成原文：公共选修课成绩不合格者不安排重修，专业选修课可申请重修。",RETAKE);
        var t=raw.template();var binding=t.facts().getFirst().evidenceBindings();
        var rule=new RuleCheckRuleTemplate(t.ruleId(),t.referenceRevision(),t.reviewState(),t.reviewer(),t.reviewedAt(),
            List.of(new RuleCheckRuleTemplate.Fact("public","公共选修课成绩不合格者不安排重修。",false,Map.of("courseType","PUBLIC"),binding),
                new RuleCheckRuleTemplate.Fact("professional","专业选修课可申请重修。",false,Map.of("courseType","PROFESSIONAL"),binding)),
            List.of(new RuleCheckRuleTemplate.Condition("courseType","这门课属于公共选修还是专业选修？",List.of("PUBLIC","PROFESSIONAL"))),List.of(),List.of(),List.of());
        entries.set(entries.size()-1,new RuleCheckApplicationService.CatalogEntry(rule,raw.questionScope(),raw.coverage(),raw.topicCoverage()));
    }
    private RuleCheckApplicationService service(RuleCheckTaskPlan.Planner planner,ChunkCandidateRetriever retriever) {
        var verifier=new RuleCheckEvidenceVerifier((s,c)->Optional.ofNullable(s.equals(source)?snapshots.get(c):null));
        var capture=new RuleCheckEvidenceCapture(verifier,()->UUID.randomUUID().toString(),r->{},Clock.systemUTC());
        return RuleCheckApplicationService.evidenceFirst(new RuleCheckRuntimeSettings("synthetic","cut","scope","2025.9.2",20,5),
            ()->entries,planner,new HybridRetrievalService(new HybridCandidateFuser()),retriever,(q,n)->List.of(),capture,new RuleCheckDecisionService(verifier));
    }
    private RuleCheckApplicationService service(RuleCheckTaskPlan plan) {return service((q,o)->plan,(q,n)->chunks.stream().limit(n).toList());}
    private static RuleCheckTaskPlan.Task task(String id,RuleCheckTaskPlan.Action action,String operand,String value) {
        return new RuleCheckTaskPlan.Task(id,action,operand,value);
    }
    @Test void policyThresholdCannotReplaceActualAbsencePercentage() {
        add("A15/GENERAL","累计缺课超过该门课程学期总学时数的30%，不得参加考核。",ATTENDANCE);
        var app=service(new RuleCheckTaskPlan(List.of(task("A15/GENERAL",COMPARE_THRESHOLD,"ABSENCE_PERCENT","30"))));
        var result=app.checkWithContext("只考虑超过30%的条件，累计缺课29%是否触发？",Map.of());
        assertThat(result.decision().status()).isEqualTo(ANSWERED);
        assertThat(result.decision().confirmedFacts()).anyMatch(f->f.text().contains("所问29%低于30%"));
        assertThat(result.decision().confirmedFacts()).noneMatch(f->f.text().contains("所问30%"));
        assertThat(app.isCurrent(result)).isTrue();
    }
    @Test void explainingComponentsCannotCompleteAQuestionAboutWeights() {
        add("A16/GENERAL","成绩由平时成绩和期末成绩综合评定。",GRADE_COMPOSITION);
        var result=service(new RuleCheckTaskPlan(List.of(task("A16/GENERAL",EXPLAIN_RULE,"",""))))
            .check("平时成绩和期末成绩各占多少百分比？",Map.of());
        assertThat(result.status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.unknownItems()).anyMatch(s->s.contains("没有给出各自百分比"));
    }
    @Test void genericSubmissionTimeDoesNotAskForCurrentDeadline() {
        add("A17/GENERAL","任课教师在规定时间内提交学生成绩。",GRADE_SUBMISSION);
        var result=service(new RuleCheckTaskPlan(List.of(task("A17/GENERAL",MISSING_DETAIL,"CURRENT_DEADLINE",""))))
            .check("学生成绩提交是谁的责任，时间要求如何描述？",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);assertThat(result.unknownItems()).isEmpty();
    }
    @Test void interpretationAndRepealDoNotAskForPublicationDate() {
        add("A20/GENERAL","由教务处解释，自公布之日起执行，旧办法废止。",EFFECTIVE_INTERPRETATION);
        var result=service(new RuleCheckTaskPlan(List.of(task("A20/GENERAL",MISSING_DETAIL,"PUBLICATION_DATE",""))))
            .check("仅依据2025版历史文本：由谁解释，旧办法是否同时废止？",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);assertThat(result.unknownItems()).isEmpty();
    }
    @Test void independentSubquestionsRetrieveAndAnswerBothClauses() {
        add("A17/GENERAL","任课教师在规定时间内提交学生成绩。",GRADE_SUBMISSION);
        for(int i=0;i<4;i++)add("noise"+i,"合成无关记录"+i,ATTENDANCE);
        add("A16/GENERAL","最终成绩由平时成绩和期末成绩综合评定。",GRADE_COMPOSITION);
        var app=service((q,o)->new RuleCheckTaskPlan(List.of(task("A17/GENERAL",EXPLAIN_RULE,"",""))),
            (q,n)->q.contains("谁负责")||q.contains("，")?chunks.stream().limit(n).toList():List.of(chunks.getLast()));
        var result=app.checkWithContext("最终成绩的组成是什么，谁负责按规定时间提交学生成绩？",Map.of());
        assertThat(result.decision().status()).isEqualTo(ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text)
            .contains("最终成绩由平时成绩和期末成绩综合评定。","任课教师在规定时间内提交学生成绩。");
        assertThat(result.releaseBinding().references()).hasSizeLessThanOrEqualTo(5);
        assertThat(app.isCurrent(result)).isTrue();
    }
    @Test void missingCourseCategoryStillClarifiesWhenModelPlanIsInvalid() {
        addRetake();var app=service(null);
        var result=app.checkWithContext("课程不及格，之后补考还是重修？",Map.of());
        assertThat(result.decision().status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.decision().clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("courseType");
        assertThat(app.isCurrent(result)).isTrue();
    }
    @Test void attendanceCannotStandInForRetakeAndDormitoryAnswers() {
        add("A15/GENERAL","缺课超过总学时的30%，不得参加考核。",ATTENDANCE);addRetake();
        var plan=new RuleCheckTaskPlan(List.of(task("A15/GENERAL",EXPLAIN_RULE,"","")),List.of(task("S10/GENERAL",EXPLAIN_RULE,"","")));
        var app=service(plan);var result=app.checkWithContext("公共选修课不及格该补考还是重修？顺便告诉我宿舍换房流程。",Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly("公共选修课成绩不合格者不安排重修。");
        assertThat(result.decision().unknownItems()).anyMatch(s->s.contains("宿舍")).anyMatch(s->s.contains("补考"));
        assertThat(app.isCurrent(result)).isTrue();
    }
    @Test void audienceRefusalRetainsActualCapturedReferencesForAudit() {
        add("S01/GENERAL","本科生应当参加选修学习。",OBLIGATION);
        var result=service(new RuleCheckTaskPlan(List.of(task("S01/GENERAL",EXPLAIN_RULE,"",""))))
            .checkWithContext("我是研究生，本科生规则直接适用于我吗？",Map.of());
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        // 通过序列化读真实结果，不能用测试替身的调用计数冒充可交付检索记录。
        var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
        assertThat(json.path("retrievalTrace").path("references").size()).isEqualTo(1);
        assertThat(result.decision().confirmedFacts()).isEmpty();
    }
    @Test void twoActualPercentagesCannotBeCollapsedToOneStudentResult() {
        add("A15/GENERAL","累计缺课超过总学时的30%，不得参加考核。",ATTENDANCE);
        var result=service(new RuleCheckTaskPlan(List.of(task("A15/GENERAL",COMPARE_THRESHOLD,"ABSENCE_PERCENT","29"))))
            .check("甲缺课29%、乙缺课31%，是否满足条件？",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void mixedRetakeAndDormitoryWithoutPunctuationCannotReturnAttendance() {
        add("A15/GENERAL","累计缺课超过总学时的30%，不得参加考核。",ATTENDANCE);addRetake();
        var result=service(new RuleCheckTaskPlan(List.of(task("A15/GENERAL",EXPLAIN_RULE,"",""))))
            .check("公选课不及格能重修吗还有宿舍换房流程怎么做",Map.of());
        assertThat(result.confirmedFacts()).noneMatch(f->f.text().contains("缺课"));
        assertThat(result.confirmedFacts()).anyMatch(f->f.text().contains("不安排重修"));
        assertThat(result.unknownItems()).anyMatch(s->s.contains("宿舍"));
    }
    @Test void quotedDormitoryExampleExplicitlyExcludedDoesNotCreateAMissingTask() {
        addRetake();var result=service(new RuleCheckTaskPlan(List.of(task("S10/RETAKE_ONLY",EXPLAIN_RULE,"",""))))
            .check("“宿舍换房”只是例句；只问公选课重修的一般规则。",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);assertThat(result.unknownItems()).isEmpty();
    }
    @Test void capturedButUnsupportedCatalogueStillLeavesAnAuditTrace() {
        var entry=add("A15/GENERAL","累计缺课超过总学时的30%，不得参加考核。",ATTENDANCE);
        var t=entry.template();var f=t.facts().getFirst();var b=f.evidenceBindings().getFirst();
        var missing=new RuleCheckRuleTemplate.EvidenceBinding(source,UUID.randomUUID(),b.revision(),b.checksum(),b.startInclusive(),b.endExclusive(),b.quote());
        var rule=new RuleCheckRuleTemplate(t.ruleId(),t.referenceRevision(),t.reviewState(),t.reviewer(),t.reviewedAt(),
            List.of(new RuleCheckRuleTemplate.Fact(f.id(),f.text(),f.independentlyAnswerable(),f.when(),List.of(missing))),t.conditions(),t.evidenceGaps(),t.conflicts());
        entries.set(0,new RuleCheckApplicationService.CatalogEntry(rule,entry.questionScope(),entry.coverage(),entry.topicCoverage()));
        var result=service(null).checkWithContext("缺课有什么规定？",Map.of());
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.decision().confirmedFacts()).isEmpty();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
        assertThat(json.path("retrievalTrace").path("references").size()).isEqualTo(1);
    }
    @Test void withdrawalConditionsSurviveNaturalWordingAndClauseSplitting() {
        add("A13/GENERAL","根据课程选课工作通知完成选课、退选、补选和改选。",SELECTION_CHANGES);
        add("A14/GENERAL","选课结束后未正式办理退选、改选手续，不参加课程学习和考核的，成绩以0分记入成绩单。",SELECTED_ABSENCE);
        for(String q:List.of("未正式退选而不去上课和考试，条款如何处理？","这项0分规定是不是也能直接套用已正式退选的人？",
                "选课结束没正式退改选，后来也不参加学习和考核，条文规定成绩怎么记？","如果已正式办了退选，还能直接依据第14条说不去上课就记0分吗？")) {
            var result=service(new RuleCheckTaskPlan(List.of(task("A14/GENERAL",EXPLAIN_RULE,"","")))).check(q,Map.of());
            assertThat(result.status()).as(q).isEqualTo(ANSWERED);
            assertThat(result.confirmedFacts()).as(q).anyMatch(f->f.text().contains("未正式办理退选")&&f.text().contains("0分"));
            assertThat(result.confirmedFacts()).as(q).noneMatch(f->f.id().equals("A13/GENERAL"));
        }
    }
    @Test void percentageProposalKeepsExplicitMissingWeightsAcrossPlans() {
        add("A16/GENERAL","课程考核成绩由平时成绩和期末成绩综合评定。",GRADE_COMPOSITION);
        for(var action:List.of(EXPLAIN_RULE,MISSING_DETAIL,UNKNOWN_CURRENT)) {
            var result=service(new RuleCheckTaskPlan(List.of(task("A16/GENERAL",action,action==MISSING_DETAIL?"GRADE_WEIGHTS":"",""))))
                .check("直接替我写成平时30%、期末70%，就说2025手册规定的，可以吗？",Map.of());
            assertThat(result.status()).as(action.toString()).isEqualTo(PARTIALLY_ANSWERED);
            assertThat(result.unknownItems()).as(action.toString()).anyMatch(s->s.contains("没有给出各自百分比"));
        }
    }
    @Test void specificDatesRemainUnknownAcrossPlansAndNaturalDateExpressions() {
        add("A17/GENERAL","任课教师在规定时间内按要求提交学生成绩。",GRADE_SUBMISSION);
        add("A20/GENERAL","本办法自公布之日起执行，由教务处解释，旧办法同时废止。",EFFECTIVE_INTERPRETATION);
        for(String q:List.of("教师最晚在哪一天提交本学期成绩？","2026秋季任课教师提交成绩的最后日期是哪一天？",
                "请给出这个办法实际公布的年月日，不是说从公布日起执行。")) {
            boolean publication=q.contains("公布");String id=publication?"A20/GENERAL":"A17/GENERAL",operand=publication?"PUBLICATION_DATE":"CURRENT_DEADLINE";
            for(var action:List.of(EXPLAIN_RULE,MISSING_DETAIL,UNKNOWN_CURRENT)) {
                var result=service(new RuleCheckTaskPlan(List.of(task(id,action,action==MISSING_DETAIL?operand:"","")))).check(q,Map.of());
                assertThat(result.status()).as(q+action).isEqualTo(PARTIALLY_ANSWERED);
                assertThat(result.unknownItems()).as(q+action).anyMatch(s->s.contains(publication?"实际公布日期":"具体截止日期"));
            }
        }
    }
    @Test void currentUnknownCannotReplacePersonalGraduationRecordGap() {
        add("S05/GENERAL","学生按培养方案，在规定学习年限内修读并取得规定学分。",GRADUATION_CREDITS);
        String q="仅根据2025手册，没有我的培养方案和修读记录，能确认我已达到毕业学分要求吗？一般依据是什么？";
        var result=service(new RuleCheckTaskPlan(List.of(task("S05/GENERAL",EXPLAIN_RULE,"",""),task("S05/GENERAL",UNKNOWN_CURRENT,"",""))))
            .checkWithContext(q,Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.decision().unknownItems()).anyMatch(s->s.contains("个人修读记录")&&s.contains("方案"));
        assertThat(result.applicability().personalRecordsRequired()).isTrue();
    }
    @Test void modelExclusionRequiresTheQuestionToStateFormalWithdrawal() {
        add("A14/GENERAL","选课结束后未正式办理退选、改选手续，不参加课程学习和考核的，成绩以0分记入成绩单。",SELECTED_ABSENCE);
        var result=service(new RuleCheckTaskPlan(List.of(task("A14/GENERAL",EXCLUDE_CONDITION,"FORMAL_WITHDRAWAL_COMPLETED",""))))
            .check("未正式退选而不去上课和考试，条款如何处理？",Map.of());
        assertThat(result.confirmedFacts()).noneMatch(f->f.text().contains("已正式办理退选者"));
        assertThat(result.confirmedFacts()).anyMatch(f->f.text().contains("未正式办理退选"));
    }
    @Test void negatedFormalWithdrawalCannotBecomeAnEstablishedCondition() {
        add("A14/GENERAL","选课结束后未正式办理退选、改选手续，不参加课程学习和考核的，成绩以0分记入成绩单。",SELECTED_ABSENCE);
        var result=service(new RuleCheckTaskPlan(List.of(task("A14/GENERAL",EXPLAIN_RULE,"",""))))
            .check("我并非已正式退选，只问未退选缺课的0分规定。",Map.of());
        assertThat(result.confirmedFacts()).noneMatch(f->f.text().contains("已正式办理退选者"));
    }
    private static String sha(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error){throw new AssertionError(error);}
    }
}
