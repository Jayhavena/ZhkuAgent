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
