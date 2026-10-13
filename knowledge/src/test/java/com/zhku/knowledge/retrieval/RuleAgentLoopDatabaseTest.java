package com.zhku.knowledge.retrieval;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 决策使用可编程替身，真实SQL/审核原文/审计/runtime事件；不评分模型自然语言能力。 */
@Timeout(90)
class RuleAgentLoopDatabaseTest {
    record PublicationCase(String id,String question,Set<Integer> articles,boolean omitPublicArrangement,
                           TaskStatus observedStatus,int observedClaims,String requiredBehavior) {}

    @Test void controlledCataloguePublicationUsesIndependentFinishReview()throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        String target=System.getProperty("zhku.db.url");
        RuleCheckDatabaseTestTarget.verify(target);
        String user=System.getProperty("zhku.db.user","zhkuagent");
        var db=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());
        var cases=List.of(
            new PublicationCase("C01","专业选修课和公共选修课在修读安排上有什么不同？",Set.of(4),false,TaskStatus.RESOLVED,2,"两项修读事实合法发布，不得将主题未知当作资料不适用"),
            new PublicationCase("C02","专业选修课和公共选修课的课程类别与修读安排有哪些区别？",Set.of(4),false,TaskStatus.RESOLVED,2,"两项修读事实合法发布"),
            new PublicationCase("C03","专选和公选在选课修读上哪里不一样？",Set.of(4),false,TaskStatus.RESOLVED,2,"同含义表达应允许相同两项事实"),
            new PublicationCase("C04","专业选修课和公共选修课的课程类别与修读安排有哪些区别？",Set.of(4),true,TaskStatus.FAILED,1,"遗漏一侧修读安排不能声称完整覆盖"),
            new PublicationCase("C05","公共选修课通常授课多少学时，每学期最多多少学分？",Set.of(5,10),false,TaskStatus.RESOLVED,3,"两条规则联合覆盖HOURS和SEMESTER_CREDITS"),
            new PublicationCase("C06","公共选修课课程类别有哪些？",Set.of(18),false,TaskStatus.FAILED,0,"不得以条件已满足的重修事实回答课程分类")
        );
        var records=new ArrayList<Map<String,Object>>();
        var json=new ObjectMapper().findAndRegisterModules();
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
            catalogue.installOnIsolatedDatabase(db);
            var settings=catalogue.settings();
            var entries=RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));
            for(var test:cases) {
                var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
                UUID task=UUID.randomUUID();
                Clock clock=Clock.systemUTC();
                var tools=new RuleAgentEvidenceTools(owner,task,settings,()->entries,
                    JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                    new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),
                    new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock);
                var selectedIds=new ArrayList<String>();
                var closed=new java.util.concurrent.atomic.AtomicBoolean();
                RuleAgentRunner.DecisionPort port=new RuleAgentRunner.DecisionPort() {
                    public RuleAgentRunner.Reply decide(RuleAgentRunner.View view,Duration timeout) {
                        NextAction action;
                        if(view.observations().isEmpty()) {
                            action=new Search("q1",view.question());
                        } else if(view.facts().isEmpty()) {
                            // 诊断替身只从当次真实检索结果选材料；不固定UUID，不预置学生答案。
                            var handles=view.observations().getLast().candidates().stream()
                                .filter(c->test.articles().stream().anyMatch(a->c.excerpt().startsWith(articlePrefix(a))))
                                .map(Candidate::handle).toList();
                            assertThat(handles).hasSize(test.articles().size());
                            action=new Read("q1",handles);
                        } else {
                            var facts=view.facts().stream()
                                .filter(f->test.articles().contains(4)?f.ruleId().equals("T02/ARTICLE4"):true)
                                .filter(f->!test.id().equals("C05")||f.ruleId().endsWith("/GENERAL"))
                                .filter(f->!test.id().equals("C06")||f.id().equals("S10/RETAKE_ONLY:public-failed-no-retake"))
                                .filter(f->!test.omitPublicArrangement()||!f.id().endsWith("public-elective-self-selection"))
                                .map(RuleAgentEvidenceTools.VerifiedFact::id).toList();
                            selectedIds.clear();
                            selectedIds.addAll(facts);
                            action=new Finish(List.of(new Proposal("q1",facts,List.of(),null)));
                        }
                        try{return new RuleAgentRunner.Reply(json.writeValueAsString(action),false);}
                        catch(Exception invalid){throw new IllegalStateException("模拟动作编码失败");}
                    }
                    public void close(){closed.set(true);}
                };
                var input=new RuleAgentRunner.Input(owner,task,1,test.question(),
                    List.of(new RuleAgentRunner.Question("q1",test.question())),
                    test.id().equals("C06")?Map.of("courseType","PUBLIC"):Map.of());
                var context=AgentRunContext.create(test.question());
                RuleSemanticReviewPort reviewer=(safe,timeout)->finishReviewFixture(safe,test.articles());
                var outcome=new RuleAgentRunner(port,new RuleActionBroker(tools),clock,reviewer).run(context,input);
                var assessments=selectedIds.stream().map(id->entries.stream()
                    .filter(e->e.template().facts().stream().anyMatch(f->(e.template().ruleId()+":"+f.id()).equals(id)))
                    .findFirst().orElseThrow()).distinct()
                    .map(e->Map.of("ruleId",e.template().ruleId(),"declaredTopics",e.topicCoverage().topics(),
                        "assessment",new RuleCheckTopicGuard().assess(test.question(),e.topicCoverage()))).toList();
                var record=new LinkedHashMap<String,Object>();
                record.put("id",test.id());
                record.put("question",test.question());
                record.put("requiredBehavior",test.requiredBehavior());
                record.put("selectedFactIds",List.copyOf(selectedIds));
                record.put("topicAssessments",assessments);
                record.put("outcome",outcome);
                record.put("decisionPortClosed",closed.get());
                record.put("runtimeTerminated",context.snapshot().terminated());
                record.put("providerCalls",0);
                records.add(record);
                // 在断言前保存实际轨迹；复现断言通过不代表修复或目标行为通过。
                json.writerWithDefaultPrettyPrinter().writeValue(Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"publication-reproduction.json").toFile(),records);
                assertThat(outcome.snapshot().taskStatus()).as(test.id()).isEqualTo(test.observedStatus());
                assertThat(outcome.snapshot().result().items().getFirst().claims()).as(test.id()).hasSize(test.observedClaims());
                assertThat(outcome.events().get(0).observation().status()).isEqualTo(ObservationStatus.SUCCEEDED);
                assertThat(outcome.events().get(1).observation().dataLevel()).isEqualTo(DataLevel.VERIFIED_FACT);
                assertThat(closed.get()).isTrue();
                assertThat(context.snapshot().terminated()).isTrue();
                assertThat(outcome.snapshot().execution().modelAttemptCount()).isEqualTo(4);
                if(test.id().equals("C04")) {
                    assertThat(outcome.snapshot().result().coverage()).isEqualTo(Coverage.PARTIAL);
                    assertThat(outcome.snapshot().result().items().getFirst().issues()).anyMatch(i->i.reasonCode().name().equals("ANSWER_COVERAGE_INCOMPLETE"));
                } else if(test.id().equals("C06")) {
                    assertThat(outcome.snapshot().result().coverage()).isEqualTo(Coverage.NONE);
                    assertThat(outcome.snapshot().result().evidence()).isEmpty();
                } else {
                    assertThat(outcome.snapshot().result().coverage()).isEqualTo(Coverage.COMPLETE);
                }
            }
        }
    }

    /** 核验参考由实验提供；输入不含提议选择，事实只能取当次真实READ结果。 */
    private static RulePublicationContract.SemanticAssessment finishReviewFixture(RulePublicationContract.SemanticInput safe,Set<Integer> articles) {
        var requirements=new ArrayList<RulePublicationContract.Requirement>();
        var bindings=new ArrayList<RulePublicationContract.SemanticBinding>();
        var question=safe.questions().getFirst();
        var span=new RulePublicationContract.DemandSpan(question.id(),0,question.text().length());
        if(articles.contains(4)) {
            requirements.add(new RulePublicationContract.Requirement("r1",span,RulePublicationContract.Dimension.STUDY_ARRANGEMENT,RulePublicationContract.Subject.PROFESSIONAL_ELECTIVE,RulePublicationContract.Recognition.IDENTIFIED,List.of()));
            requirements.add(new RulePublicationContract.Requirement("r2",span,RulePublicationContract.Dimension.STUDY_ARRANGEMENT,RulePublicationContract.Subject.PUBLIC_ELECTIVE,RulePublicationContract.Recognition.IDENTIFIED,List.of()));
            safe.facts().stream().filter(f->f.id().startsWith("T02/ARTICLE4:")).forEach(f->bindings.add(new RulePublicationContract.SemanticBinding(f.id().endsWith("professional-elective-study-arrangement")?"r1":"r2",f.id(),RulePublicationContract.BindingRole.CORE)));
        } else if(articles.contains(5)) {
            requirements.add(new RulePublicationContract.Requirement("r1",span,RulePublicationContract.Dimension.HOURS_NORMAL,RulePublicationContract.Subject.PUBLIC_ELECTIVE,RulePublicationContract.Recognition.IDENTIFIED,List.of()));
            requirements.add(new RulePublicationContract.Requirement("r2",span,RulePublicationContract.Dimension.SEMESTER_CREDIT_LIMIT,RulePublicationContract.Subject.PUBLIC_ELECTIVE,RulePublicationContract.Recognition.IDENTIFIED,List.of()));
            for(var fact:safe.facts()) {
                if(fact.id().equals("S03/GENERAL:hours-principle"))bindings.add(new RulePublicationContract.SemanticBinding("r1",fact.id(),RulePublicationContract.BindingRole.CORE));
                if(fact.id().equals("S03/GENERAL:hours-upper-limit"))bindings.add(new RulePublicationContract.SemanticBinding("r1",fact.id(),RulePublicationContract.BindingRole.CONDITION_EXCEPTION));
                if(fact.id().equals("S07/GENERAL:public-semester-four-credits"))bindings.add(new RulePublicationContract.SemanticBinding("r2",fact.id(),RulePublicationContract.BindingRole.CORE));
            }
        } else {
            requirements.add(new RulePublicationContract.Requirement("r1",span,RulePublicationContract.Dimension.CATEGORY,RulePublicationContract.Subject.PUBLIC_ELECTIVE,RulePublicationContract.Recognition.IDENTIFIED,List.of()));
        }
        return new RulePublicationContract.SemanticAssessment(RulePublicationContract.REVISION,safe.inputFingerprint(),requirements,bindings,List.of());
    }
    private static String articlePrefix(int article) {
        return switch(article) {
            case 4->"第四条";
            case 5->"第五条";
            case 10->"第十条";
            case 18->"第十八条";
            default->throw new IllegalArgumentException("诊断未声明该条款");
        };
    }

    @Test void realToolsMixedEvidenceOperationAndRuntimeTermination()throws Exception{
        Path root=Path.of("..").toAbsolutePath().normalize();String target=System.getProperty("zhku.db.url");RuleCheckDatabaseTestTarget.verify(target);
        String user=System.getProperty("zhku.db.user","zhkuagent");var db=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());
        var dispatcher=AgentRunObservationDispatcher.createOwned();
        try(var catalogue=new Reviewed2025ReportCatalogue(root)){
            catalogue.installOnIsolatedDatabase(db);var settings=catalogue.settings();var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());UUID task=UUID.randomUUID();
            var tools=new RuleAgentEvidenceTools(owner,task,settings,()->{try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception e){throw new IllegalArgumentException("目录资格变化");}},JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(Clock.systemUTC()),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),Clock.systemUTC());
            String first="选修课程类别和修读安排有哪些区别",second="累计缺课正好30%满足不得考核的比例条件吗",question=first+"；"+second;
            var input=new RuleAgentRunner.Input(owner,task,1,question,List.of(new RuleAgentRunner.Question("q1",first),new RuleAgentRunner.Question("q2",second)),Map.of());
            var json=new ObjectMapper();var events=new CopyOnWriteArrayList<AgentRunEvent>();var ended=new CountDownLatch(1);
            var context=AgentRunContext.create(UUID.randomUUID(),question,e->{events.add(e);if(e.type()==AgentRunEvent.Type.RUN_TERMINATED)ended.countDown();},dispatcher);
            RuleAgentRunner.DecisionPort port=(view,timeout)->{
                NextAction action;
                if(view.observations().isEmpty())action=new Search("q1",view.question());
                else if(view.facts().isEmpty())action=new Read("q1",view.observations().getLast().candidates().stream().filter(c->c.excerpt().startsWith("第四条")||c.excerpt().startsWith("第十五条")).map(Candidate::handle).toList());
                else{var firstIds=view.facts().stream().filter(f->f.ruleId().equals("T02/ARTICLE4")).map(RuleAgentEvidenceTools.VerifiedFact::id).toList();var threshold=view.facts().stream().filter(f->f.ruleId().equals("A15/GENERAL")).findFirst().orElseThrow();int start=question.indexOf("30");var operation=new Operation(Comparison.GT,threshold.id()+":ABSENCE_PERCENT","30",new InputSpan(start,start+2,"30",ConditionSource.USER_DECLARED));action=new Finish(List.of(new Proposal("q1",firstIds,List.of(),null),new Proposal("q2",List.of(threshold.id()),List.of(),operation)));}
                try{return new RuleAgentRunner.Reply(json.writeValueAsString(action),false);}catch(Exception invalid){throw new IllegalStateException(invalid);}
            };
            var outcome=new RuleAgentRunner(port,new RuleActionBroker(tools),Clock.systemUTC()).run(context,input);
            assertThat(ended.await(2,TimeUnit.SECONDS)).isTrue();assertThat(outcome.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);assertThat(outcome.snapshot().result().coverage()).isEqualTo(Coverage.COMPLETE);
            assertThat(outcome.snapshot().result().items().getFirst().claims()).hasSize(2);assertThat(outcome.snapshot().result().items().getLast().claims().getFirst().text()).contains("不满足严格超过30%");
            assertThat(events).extracting(AgentRunEvent::type).contains(AgentRunEvent.Type.TOOL_CALL_PROPOSED,AgentRunEvent.Type.TOOL_CALL_RESULT,AgentRunEvent.Type.RUN_TERMINATED);
            json.findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValue(Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"actual-loop-flow.json").toFile(),Map.of("outcome",outcome,"runtimeEvents",events,"providerCalls",0,"fixedMechanicalTestNotAgentLanguageEval",true));
        }finally{dispatcher.close();}assertThat(dispatcher.status().liveThreadCount()).isZero();
    }
}
