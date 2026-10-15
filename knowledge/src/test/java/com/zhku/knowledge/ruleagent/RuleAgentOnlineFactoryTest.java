package com.zhku.knowledge.ruleagent;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;
import com.zhku.agent.agent.runtime.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
@Timeout(15)
class RuleAgentOnlineFactoryTest {
    HttpServer server;
    ExecutorService workers;
    RuleAgentOnlineFactory factory;
    final AtomicInteger calls=new AtomicInteger();
    final List<RuleAgentProvider.Trace> traces=new CopyOnWriteArrayList<>();
    final RuleAgentRunnerTest fixture=new RuleAgentRunnerTest();
    final UUID staleReplacementChunk=UUID.randomUUID();
    boolean conditional;
    boolean invalidReviewFingerprint;
    final List<RuleAgentProvider.Exchange> exchanges=new CopyOnWriteArrayList<>();
    boolean studyArrangement;
    Runnable onOriginalRead=()->{};
    int selectedCount=1;
    String readSelection="valid";
    String savedSet;
    int searches;
    int candidateCopies=1;
    volatile JsonNode lastDecisionView;
    volatile int lastRequestBytes;
    final CountDownLatch reviewStarted=new CountDownLatch(1);
    final CountDownLatch releaseReview=new CountDownLatch(1);
    volatile boolean holdReview;
    RuleAgentEvidenceTools mappedTools(Owner owner,UUID task)throws Exception{
        if(studyArrangement)return article4Tools(owner,task);
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(fixture.source,fixture.chunk,"2025.9.2",fixture.hash(),0,fixture.text.length(),fixture.text);
        String ruleId=conditional?"S10/RETAKE_ONLY":"S02/GENERAL";
        var facts=conditional?List.of(
            new RuleCheckRuleTemplate.Fact("public-failed-no-retake","合成公选重修说明",true,Map.of("courseType","PUBLIC"),List.of(binding)),
            new RuleCheckRuleTemplate.Fact("professional-failed-retake-application","合成专选重修说明",true,Map.of("courseType","PROFESSIONAL"),List.of(binding))):
            List.of(new RuleCheckRuleTemplate.Fact("elective-category-distinction","合成分类说明",true,Map.of(),List.of(binding)));
        var conditions=conditional?List.of(new RuleCheckRuleTemplate.Condition("courseType","请选择合成课程类别",List.of("PUBLIC","PROFESSIONAL"))):List.<RuleCheckRuleTemplate.Condition>of();
        if(readSelection.equals("stale")){
            var expanded=new ArrayList<>(facts);
            expanded.add(new RuleCheckRuleTemplate.Fact("replacement","合成新检索来源",true,Map.of(),List.of(new RuleCheckRuleTemplate.EvidenceBinding(fixture.source,staleReplacementChunk,"2025.9.2",fixture.hash(),0,fixture.text.length(),fixture.text))));
            facts=expanded;
        }
        var rule=new RuleCheckRuleTemplate(ruleId,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"SYNTHETIC_TEST",LocalDate.of(2026,10,14),facts,conditions,List.of(),List.of());
        var entry=new RuleCheckApplicationService.CatalogEntry(rule,"合成范围",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
        var candidate=new RetrievedChunk(fixture.chunk,fixture.text,"synthetic","synthetic",1,"2025.9.2",fixture.hash(),fixture.source);
        var current=new RuleCheckEvidenceSnapshot("current",fixture.source,fixture.chunk,"2025.9.2",fixture.hash(),fixture.text,true);
        var replacement=new RetrievedChunk(staleReplacementChunk,fixture.text,"synthetic","synthetic",1,"2025.9.2",fixture.hash(),fixture.source);
        var replacementCurrent=new RuleCheckEvidenceSnapshot("replacement",fixture.source,staleReplacementChunk,"2025.9.2",fixture.hash(),fixture.text,true);
        // 旧集合反例要先取得真正的新来源；否则已被无进展守卫提前停止，测不到集合失效校验。
        return new RuleAgentEvidenceTools(owner,task,new RuleCheckRuntimeSettings("p","c","s","2025.9.2",20,5),()->List.of(entry),(q,n)->Collections.nCopies(candidateCopies,readSelection.equals("stale")&&searches>0?replacement:candidate),(s,c)->Optional.of(c.equals(staleReplacementChunk)?replacementCurrent:current),r->{},Clock.systemUTC());
    }
    RuleAgentEvidenceTools article4Tools(Owner owner,UUID task)throws Exception{
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        var bundle=new ObjectMapper().readTree(java.nio.file.Files.readAllBytes(root.resolve("docs/product/rule-check-2025-verbatim-import-draft.json")));
        var row=java.util.stream.StreamSupport.stream(bundle.path("chunks").spliterator(),false).filter(c->c.path("article").asInt()==4).findFirst().orElseThrow();
        String text=row.path("content").asText();
        String checksum=row.path("contentChecksum").asText();
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(fixture.source,fixture.chunk,"2025.9.2",checksum,0,text.length(),text);
        var baseFact=new RuleCheckRuleTemplate.Fact("binding-only","仅供原文绑定",true,Map.of(),List.of(binding));
        var template=new RuleCheckRuleTemplate("S02/GENERAL","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"LOCAL_BINDING_TEST",LocalDate.of(2026,10,14),List.of(baseFact),List.of(),List.of(),List.of());
        var entry=new RuleCheckApplicationService.CatalogEntry(template,"本地绑定测试",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
        var entries=RuleArticle4Overlay.apply(root,List.of(entry));
        var candidate=new RetrievedChunk(fixture.chunk,text,"local-reviewed-article4","local-reviewed-article4",1,"2025.9.2",checksum,fixture.source);
        var current=new RuleCheckEvidenceSnapshot("current",fixture.source,fixture.chunk,"2025.9.2",checksum,text,true);
        return new RuleAgentEvidenceTools(owner,task,new RuleCheckRuntimeSettings("p","c","s","2025.9.2",20,5),()->entries,(q,n)->Collections.nCopies(candidateCopies,candidate),(s,c)->{onOriginalRead.run();return Optional.of(current);},r->{},Clock.systemUTC());
    }
    Object review(JsonNode safe){
        var requirements=new ArrayList<RulePublicationContract.Requirement>();
        var bindings=new ArrayList<RulePublicationContract.SemanticBinding>();
        var q=safe.path("questions").get(0);
        for(var fact:safe.path("facts")){
            if(studyArrangement&&!fact.path("id").asText().startsWith("T02/ARTICLE4:"))continue;
            var when=fact.path("when");
            if(when.has("courseType")&&safe.path("userConditions").has("courseType")&&!when.path("courseType").equals(safe.path("userConditions").path("courseType")))continue;
            String id="r"+requirements.size();
            var cap=safe.path("capabilities").path(fact.path("id").asText());
            requirements.add(new RulePublicationContract.Requirement(id,new RulePublicationContract.DemandSpan(q.path("id").asText(),0,q.path("text").asText().length()),
                RulePublicationContract.Dimension.valueOf(cap.path("dimension").asText()),RulePublicationContract.Subject.valueOf(cap.path("subject").asText()),RulePublicationContract.Recognition.IDENTIFIED,
                when.has("courseType")&&!safe.path("userConditions").has("courseType")?List.of("courseType"):List.of()));
            bindings.add(new RulePublicationContract.SemanticBinding(id,fact.path("id").asText(),RulePublicationContract.BindingRole.CORE));
        }
        return new RulePublicationContract.SemanticAssessment(RulePublicationContract.REVISION,invalidReviewFingerprint?"b".repeat(64):safe.path("inputFingerprint").asText(),requirements,bindings,List.of());
    }
    void start(int quota)throws Exception{
        workers=Executors.newFixedThreadPool(2);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(workers);
        server.createContext("/chat/completions",e->{
            try{
                calls.incrementAndGet();
                var json=new ObjectMapper();
                byte[] rawBody=e.getRequestBody().readAllBytes();
                // 工具定义会显著增大请求体；记录真实字节数以核对是否仍在上限内。
                lastRequestBytes=rawBody.length;
                var request=json.readTree(rawBody);
                var view=json.readTree(request.path("messages").path(1).path("content").asText());
                if(!view.has("input"))lastDecisionView=view;
                Object action;
                if(view.has("input")){
                    reviewStarted.countDown();
                    if(holdReview)releaseReview.await(2,TimeUnit.SECONDS);
                    action=review(view.path("input"));
                }
                else if(view.path("observations").isEmpty())action=new Search("q1",studyArrangement?view.path("question").asText():"课程类别");
                else if(view.path("facts").isEmpty()){
                    var set=view.path("candidateSetId").asText("old-protocol");
                    var candidates=view.path("currentCandidates");
                    String ref=candidates.isEmpty()?"c1":candidates.get(0).path("handle").asText();
                    Object selections=switch(readSelection){
                        case "unknown"->List.of("c999");
                        case "duplicate"->List.of(ref,ref);
                        case "excess"->List.of(ref,ref,ref,ref);
                        case "empty"->List.of();
                        case "all"->new ObjectMapper().convertValue(candidates,JsonNode.class).findValues("handle").stream().map(JsonNode::asText).toList();
                        case "four"->java.util.stream.IntStream.range(0,4).mapToObj(i->candidates.get(i).path("handle").asText()).toList();
                        case "not-array"->Map.of("private-body",ref);
                        case "legal-count"->java.util.stream.IntStream.range(0,selectedCount).mapToObj(i->candidates.get(i).path("handle").asText()).toList();
                        case "uuid"->List.of(fixture.chunk.toString());
                        case "object"->List.of(Map.of("handle",ref));
                        case "number"->List.of(1);
                        case "invalid"->List.of("private-free-body");
                        case "three"->List.of("c1","c2","c3");
                        default->List.of(ref);
                    };
                    if(readSelection.equals("stale")&&searches++==0){
                        savedSet=set;
                        action=new Search("q1","课程类别重新检索");
                    }else{
                        if(readSelection.equals("cross-task")||readSelection.equals("stale"))set=savedSet;
                        action=Map.of("type","READ_VERIFIED_RULE","subquestionId","q1","candidateSetId",set,"handles",selections);
                        if(readSelection.equals("save"))savedSet=set;
                    }
                }
                else if(conditional&&!view.path("userConditions").has("courseType")){
                    var ids=new ArrayList<String>();view.path("facts").forEach(f->ids.add(f.path("id").asText()));
                    action=new Ask("q1","courseType","合成必要条件",ids);
                }else{
                    var ids=new ArrayList<String>();
                    view.path("facts").forEach(f->{if((!studyArrangement||f.path("id").asText().startsWith("T02/ARTICLE4:"))&&(!conditional||f.path("when").path("courseType").equals(view.path("userConditions").path("courseType"))))ids.add(f.path("id").asText());});
                    action=new Finish(List.of(new Proposal("q1",ids,List.of(),null)));
                }
                // 决策与核验都以 JSON 文本返回，但载荷合同不同；决策侧额外受 response_format 的Schema约束。
                byte[] bytes=json.writeValueAsBytes(Map.of("usage",Map.of("prompt_tokens",25,"completion_tokens",12),"choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",json.writeValueAsString(action))))));
                e.sendResponseHeaders(200,bytes.length);
                e.getResponseBody().write(bytes);
            }
            catch(Exception ignored){
            }
            finally{
                e.close();
            }
        });
        server.start();
        factory=new RuleAgentOnlineFactory(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),()->"local-test-placeholder",(owner,task)->{try{return mappedTools(owner,task);}catch(Exception invalid){throw new IllegalStateException("合成资料初始化失败");}},traces::add,exchanges::add,quota,Clock.systemUTC());
    }
    @Test void failedReviewKeepsActualFactoryExchangeAndAskProposal()throws Exception{
        conditional=true;invalidReviewFingerprint=true;start(10);
        var input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"我这门选修课没过，能不能重修？");
        var result=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input.question()),input);
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);assertThat(calls).hasValue(4);
        assertThat(factory.remainingCalls()).isEqualTo(6);assertThat(factory.resourcesClosed()).isTrue();
        assertThat(traces.getLast().validation().code().name()).isEqualTo("REVIEW_INPUT_MISMATCH");
        assertThat(result.events()).anySatisfy(e->{assertThat(e.action()).isEqualTo("ASK_NECESSARY_CONDITION");assertThat(e.reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);});
        assertThat(result.events()).anySatisfy(e->{assertThat(e.action()).isEqualTo("PUBLICATION_REVIEW");assertThat(e.reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);});
        assertThat(exchanges).hasSize(11);
        var proposal=exchanges.stream().filter(e->e.attempt()==3&&e.direction()==RuleAgentProvider.Direction.PROPOSED_ACTION).findFirst().orElseThrow();
        assertThat(new ObjectMapper().readTree(proposal.body()).path("type").asText()).isEqualTo("ASK_NECESSARY_CONDITION");
        var json=new ObjectMapper();assertThat(json.writeValueAsString(exchanges)).doesNotContain("local-test-placeholder","Bearer ");
        String output=System.getProperty("zhku.ruleagent.diagnostics.output");
        if(output!=null){var dir=java.nio.file.Path.of(output);java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("factory-failed-review.json"),json.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("synthetic",true,"exchanges",exchanges,"outcome",result,"traces",traces)));}
    }
    @AfterEach void cleanup()throws Exception{
        releaseReview.countDown();
        if(factory!=null){
            factory.close();
            assertThat(factory.resourcesClosed()).isTrue();
        }
        if(server!=null)server.stop(0);
        if(workers!=null){
            workers.shutdownNow();
            assertThat(workers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
        }
    }
    RuleAgentRunner.Input input(){
        return RuleAgentRunner.Input.single(fixture.owner,fixture.task,"选修课程类别有哪些？");
    }
    @Test void localProviderAndActualDomainToolsFollowObservations()throws Exception{
        start(4);
        var work=factory.open(fixture.owner,fixture.task);
        var result=work.run(AgentRunContext.create(input().question()),input());
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(result.events()).extracting(RuleAgentRunner.Event::action).containsExactly("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","FINISH_CHECK","PUBLICATION_REVIEW");
        assertThat(result.events().get(1).observation().dataLevel()).isEqualTo(DataLevel.VERIFIED_FACT);
        assertThat(calls).hasValue(4);
        assertThat(result.snapshot().execution().providerResponseReceived()).isFalse();
        assertThat(factory.resourcesClosed()).isTrue();
        assertThat(work.revalidate(result.snapshot().result())).isTrue();
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        assertThat(traces.getLast().outputTokens()).isEqualTo(12);
    }
    @Test void finalFreshReadCrossingRunDeadlineCannotPublishSuccess()throws Exception{
        studyArrangement=true;start(5);factory.close();
        var now=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-15T00:00:00Z"));var began=now.get();
        Clock fake=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        onOriginalRead=()->{if(reviewStarted.getCount()==0)now.set(began.plusMillis(110001));};
        factory=new RuleAgentOnlineFactory(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),()->"local-test-placeholder",(owner,task)->{
            try{return mappedTools(owner,task);}catch(Exception invalid){throw new IllegalStateException(invalid);}
        },traces::add,5,fake);
        var input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"专业选修课和公共选修课在修读安排上有什么不同？");
        var result=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input.question()),input);
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.STOPPED);
        assertThat(result.snapshot().result().items().getFirst().claims()).isEmpty();
        assertThat(result.snapshot().result().items().getFirst().issues()).extracting(Issue::reasonCode).contains(ReasonCode.BUDGET_EXCEEDED);
        assertThat(factory.resourcesClosed()).isTrue();assertThat(calls).hasValue(4);
    }
    @Test void actualDecisionViewCarriesReadStateCapacityAndReviewReserve()throws Exception{
        start(5);
        var result=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input().question()),input());
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);

        var state=lastDecisionView.path("executionState");
        assertThat(state.path("reservedReviewMillis").asInt()).isEqualTo(20000);
        assertThat(state.path("reservedPublicationMillis").asInt()).isEqualTo(5000);
        assertThat(state.path("remainingEvidenceFragments").asInt()).isEqualTo(7);
        assertThat(state.path("runMillisRemaining").asLong()).isPositive();
        assertThat(lastDecisionView.path("currentCandidates").get(0).path("alreadyRead").asBoolean()).isTrue();
        assertThat(state.path("allowedActions").toString()).contains("FINISH_CHECK").doesNotContain("SEARCH_REVIEWED_RULES");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"unknown","duplicate","excess","uuid","object","number","invalid","stale"})
    void invalidWireSelectionCannotReadOrPublish(String selection)throws Exception{
        readSelection=selection;
        start(4);
        var result=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input().question()),input());
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.events()).noneMatch(e->e.action().equals("READ_VERIFIED_RULE"));
        assertThat(result.snapshot().result().items().getFirst().claims()).isEmpty();
        assertThat(factory.resourcesClosed()).isTrue();
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        assertThat(traces.getLast().outputTokens()).isEqualTo(12);
        var validation=new ObjectMapper().valueToTree(traces.getLast()).path("validation");
        assertThat(validation.path("readSelection").path("failureCategory").isTextual()).isTrue();
        var details=validation.path("readSelection");
        if(!selection.equals("excess")){
            assertThat(details.path("elementIndex").asInt(-1)).isEqualTo(selection.equals("duplicate")?1:0);
            assertThat(details.path("jsonType").asText()).isEqualTo(selection.equals("object")?"OBJECT":selection.equals("number")?"NUMBER":"STRING");
            if(Set.of("object","number").contains(selection))assertThat(details.path("stringLength").isNull()).isTrue();
        }
        if(!Set.of("duplicate","excess").contains(selection))assertThat(validation.path("readSelection").path("currentCandidate").asBoolean()).isFalse();
        assertThat(validation.toString()).doesNotContain(fixture.chunk.toString(),"private-free-body","candidateSetId");
    }
    @Test void candidateReferenceCannotCrossTasks()throws Exception{
        readSelection="save";
        start(8);
        var first=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input().question()),input());
        assertThat(first.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        readSelection="cross-task";
        var other=RuleAgentRunner.Input.single(fixture.owner,UUID.randomUUID(),input().question());
        var second=factory.open(fixture.owner,other.taskId()).run(AgentRunContext.create(other.question()),other);
        assertThat(second.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(second.events()).noneMatch(e->e.action().equals("READ_VERIFIED_RULE"));
        assertThat(second.snapshot().result().items().getFirst().claims()).isEmpty();
    }
    @Test void finishedRunReferenceCannotBeReusedInSameTask()throws Exception{
        readSelection="save";
        start(8);
        var work=factory.open(fixture.owner,fixture.task);
        assertThat(work.run(AgentRunContext.create(input().question()),input()).snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        readSelection="cross-task";
        var second=work.run(AgentRunContext.create(input().question()),input());
        assertThat(second.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(second.events()).noneMatch(e->e.action().equals("READ_VERIFIED_RULE"));
        assertThat(second.snapshot().result().items().getFirst().claims()).isEmpty();
        assertThat(factory.resourcesClosed()).isTrue();
    }
    @Test void allCandidatesRemainVisibleAndThreeUniqueReferencesRead()throws Exception{
        candidateCopies=4;
        readSelection="three";
        start(4);
        var result=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input().question()),input());
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(result.events().getFirst().observation().candidates()).hasSize(4);
        assertThat(result.events().get(1).observation().dataLevel()).isEqualTo(DataLevel.VERIFIED_FACT);
        assertThat(lastDecisionView.path("currentCandidates").size()).isEqualTo(4);
        var refs=new ArrayList<String>();
        lastDecisionView.path("currentCandidates").forEach(c->refs.add(c.path("handle").asText()));
        assertThat(refs).containsExactly("c1","c2","c3","c4");
        for(var candidate:result.events().getFirst().observation().candidates())assertThat(lastDecisionView.toString()).doesNotContain(candidate.handle().toString());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"empty,READ_HANDLE_COUNT_BELOW_MIN","all,READ_HANDLE_COUNT_ABOVE_MAX","not-array,READ_HANDLE_ARRAY_REQUIRED","four,READ_HANDLE_COUNT_ABOVE_MAX"})
    void countFailuresAreDistinctBeforeReadWithRealReviewedArticle4(String selection,String category)throws Exception{
        studyArrangement=true;
        candidateCopies=10;
        readSelection=selection;
        start(4);
        var input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"专业选修课和公共选修课在修读安排上有什么不同？");
        var outcome=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input.question()),input);
        assertThat(outcome.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(outcome.events()).extracting(RuleAgentRunner.Event::action).containsExactly("SEARCH_REVIEWED_RULES");
        assertThat(outcome.events().getFirst().observation().candidates()).hasSize(10);
        assertThat(outcome.snapshot().result().items().getFirst().claims()).isEmpty();
        assertThat(calls).hasValue(2);
        var validation=new ObjectMapper().valueToTree(traces.getLast()).path("validation");
        assertThat(validation.path("code").asText()).isEqualTo("READ_HANDLE_COUNT");
        assertThat(validation.path("readSelection").path("failureCategory").asText()).isEqualTo(category);
        assertThat(validation.path("readSelection").size()).isEqualTo(5);
        assertThat(validation.toString()).doesNotContain("private-body",fixture.chunk.toString(),"c1","candidateSetId");
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        assertThat(traces.getLast().outputTokens()).isEqualTo(12);
        assertThat(factory.resourcesClosed()).isTrue();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,2,3})
    void legalCountsPublishBothReviewedStudyFactsWithoutTruncatingCandidates(int count)throws Exception{
        studyArrangement=true;
        candidateCopies=10;
        selectedCount=count;
        readSelection="legal-count";
        start(4);
        var input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"专业选修课和公共选修课在修读安排上有什么不同？");
        var outcome=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input.question()),input);
        assertThat(outcome.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(outcome.events()).extracting(RuleAgentRunner.Event::action).containsExactly("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","FINISH_CHECK","PUBLICATION_REVIEW");
        assertThat(outcome.snapshot().result().items().getFirst().claims()).extracting(Claim::factId).containsExactlyInAnyOrder("T02/ARTICLE4:professional-elective-study-arrangement","T02/ARTICLE4:public-elective-self-selection");
        assertThat(lastDecisionView.path("currentCandidates").size()).isEqualTo(10);
        assertThat(calls).hasValue(4);
        assertThat(factory.resourcesClosed()).isTrue();
        // 结构化输出Schema会增大请求体；必须仍在上限内，否则真实运行会在宿主侧以BUDGET_EXCEEDED终止。
        assertThat(lastRequestBytes).isLessThan(DEFAULT_BUDGET.maxRequestBytes());
        java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"decision-request-budget.json"),
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("selectedCount",count,"candidateCopies",candidateCopies,
                "maximumRequestBytes",DEFAULT_BUDGET.maxRequestBytes(),"measuredRequestBytes",lastRequestBytes,
                "headroomBytes",DEFAULT_BUDGET.maxRequestBytes()-lastRequestBytes,"structuredOutputIncluded",true,"toolsAbsent",true)),
            java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }
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
        assertThat(pack.path("protocolRevision").asText()).isEqualTo("RULE_AGENT_ACTION_CONTRACT_05");
        assertThat(pack.path("automaticRetries").asInt()).isZero();
        assertThat(factory).isNull();
        assertThat(calls).hasValue(0);
        var output=java.nio.file.Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"p01-request-package.json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),pack);
    }
    @Test void realTaskStoreTokenContinuesThroughOnlineFactoryAndSharesBatchQuota()throws Exception{
        conditional=true;
        start(8);
        var queue=new ArrayDeque<Runnable>();
        try(var store=new RuleAgentTaskStore(queue::add,factory,Clock.systemUTC(),RuleAgentTaskStore.DEFAULT_LIMITS)){
            var first=store.create(fixture.owner,new CreateTask("这门选修没过，能重修吗？",UUID.randomUUID()));
            queue.remove().run();
            var waiting=store.snapshot(fixture.owner,first.taskId());
            assertThat(waiting.taskStatus()).isEqualTo(TaskStatus.WAITING_USER);
            var message=new Message("PUBLIC",waiting.continuationToken(),waiting.version(),UUID.randomUUID());
            var accepted=store.message(fixture.owner,first.taskId(),message);
            assertThat(store.message(fixture.owner,first.taskId(),message).runId()).isEqualTo(accepted.runId());
            assertThat(queue).hasSize(1);
            queue.remove().run();
            var result=store.snapshot(fixture.owner,first.taskId());
            assertThat(result.taskStatus()).isEqualTo(TaskStatus.RESOLVED);
            assertThat(result.taskId()).isEqualTo(waiting.taskId());
            assertThat(result.runId()).isNotEqualTo(waiting.runId());
            assertThat(result.result().items().getFirst().clarification()).isEmpty();
            assertThat(store.history(fixture.owner,first.taskId())).hasSize(2);
            assertThat(calls).hasValue(8);
            assertThat(factory.remainingCalls()).isZero();
            assertThat(factory.resourcesClosed()).isTrue();
            assertThat(traces.stream().filter(t->t.purpose()==RuleAgentProvider.Purpose.PUBLICATION_REVIEW&&t.phase()==RuleAgentProvider.Phase.RECEIVED).count()).isEqualTo(2);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"actual-factory-continuation.json").toFile(),Map.of("waiting",waiting,"resolved",result,"traces",traces,"providerCalls",0,"localHttpCalls",calls.get(),"factoryResourcesClosed",factory.resourcesClosed()));
        }
    }
    @Test void cancelDuringFactoryReviewDoesNotPublishAndReleasesPorts()throws Exception{
        start(4);holdReview=true;
        var executor=Executors.newSingleThreadExecutor();
        try(var store=new RuleAgentTaskStore(executor,factory,Clock.systemUTC(),RuleAgentTaskStore.DEFAULT_LIMITS)){
            var first=store.create(fixture.owner,new CreateTask(input().question(),UUID.randomUUID()));
            assertThat(reviewStarted.await(2,TimeUnit.SECONDS)).isTrue();
            var active=store.snapshot(fixture.owner,first.taskId());
            store.cancel(fixture.owner,first.taskId(),new Cancel(active.runId(),active.version(),UUID.randomUUID()));
            releaseReview.countDown();
            executor.submit(()->{}).get(3,TimeUnit.SECONDS);
            var stopped=store.snapshot(fixture.owner,first.taskId());
            assertThat(stopped.taskStatus()).isEqualTo(TaskStatus.STOPPED);
            assertThat(stopped.result().items().getFirst().claims()).isEmpty();
            assertThat(factory.resourcesClosed()).isTrue();
        }finally{releaseReview.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void quotaIsSharedAcrossNewRunsAndNeverReset()throws Exception{
        start(2);
        var work=factory.open(fixture.owner,fixture.task);
        var first=work.run(AgentRunContext.create(input().question()),input());
        assertThat(first.snapshot().taskStatus()).isEqualTo(TaskStatus.STOPPED);
        work.run(AgentRunContext.create(input().question()),input());
        assertThat(calls).hasValue(2);
        assertThat(factory.remainingCalls()).isZero();
        assertThat(traces.stream().filter(t->t.diagnostics().dispatchedMillis()!=null&&t.phase()==RuleAgentProvider.Phase.RECEIVED).count()).isEqualTo(2);
        assertThat(traces.stream().filter(t->t.validation().code()==RuleAgentProtocol.Code.BUDGET_UNAVAILABLE).count()).isEqualTo(2);
    }
    @Test void probeStartupRequiresSpecificPackageApproval(){
        var env=new HashMap<>(Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","probe-approved","ZHKU_RULE_AGENT_PROBE_CASE","P01"));
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
        env.put("ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64));
        env.put("ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64));
        env.put("ZHKU_RULE_AGENT_PROBE_CASE","P02_CONTINUATION_01");
        var result=RuleAgentOnlineProbeHost.environmentCheck(env);
        assertThat(result.get("maximumProviderCalls")).isEqualTo(2*DEFAULT_BUDGET.maxModelAttemptsPerRun());
        assertThat(result.get("providerCalls")).isEqualTo(0);
        assertThat(new ObjectMapper().valueToTree(result).toString()).doesNotContain("local-test-placeholder");
    }
    @Test void selectiveManualProbeCannotRepeatSuccessfulCaseOrResetFullQuota(){
        var env=new HashMap<>(Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_CASE","P02_CONTINUATION_01"));
        assertThat(RuleAgentOnlineProbeHost.environmentCheck(env).get("maximumProviderCalls")).isEqualTo(2*DEFAULT_BUDGET.maxModelAttemptsPerRun());
        // 旧批次身份不能在新候选下重放：含已失败关闭的 P01_CLOSEOUT_07，授权与批次号同批作废。
        env.put("ZHKU_RULE_AGENT_PROBE_CASE","P01_CLOSEOUT_07");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
        env.put("ZHKU_RULE_AGENT_PROBE_CASE","P02");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void diagnosticRequestUsesFreshPackageLocation()throws Exception{
        var field=RuleAgentOnlineProbeHost.class.getDeclaredField("PACKAGE");field.setAccessible(true);
        assertThat(field.get(null)).isEqualTo("docs/evidence/rule-agent-provider/2026-10-15/t05-51-p02-diagnostic-request/request-package.json");
    }
    @Test void diagnosticPacketRejectsMissingOrDisabledControlledExchangePolicy()throws Throwable{
        var valid=packet();validate(valid);
        var missing=valid.deepCopy();missing.remove("localExchangeRecording");
        assertThatThrownBy(()->validate(missing)).isInstanceOf(IllegalArgumentException.class);
        var disabled=valid.deepCopy();disabled.put("localExchangeRecording","DISABLED");
        assertThatThrownBy(()->validate(disabled)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void revisedProbeCannotSilentlyUseOldAllCaseAuthorization(){
        var env=Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64));
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void retiredDiagnosticBatchCannotReuseAnApproval(){
        var env=Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_CASE","P01_DIAGNOSTIC_01");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void retiredDiagnosticPolicyCannotPassPackageValidation()throws Throwable{
        var old=packet();old.put("executionPolicyRevision","STATE_AWARE_DIAGNOSTIC_01");
        assertThatThrownBy(()->validate(old)).isInstanceOf(IllegalArgumentException.class);
    }
    com.fasterxml.jackson.databind.node.ObjectNode packet(){
        var p=new ObjectMapper().createObjectNode();
        p.put("endpoint",RuleAgentProvider.BEIJING.toString());
        p.put("model",RuleAgentProvider.MODEL);
        p.put("budgetRevision",BUDGET_REVISION);
        p.put("executionPolicyRevision","STATE_AWARE_P02_CONTINUATION_01");
        p.put("localExchangeRecording","OPT_IN_PROBE_JSON_REQUEST_RESPONSE_PROPOSED_ACTION; NO_HTTP_HEADERS; API_KEY_REDACTED; MALFORMED_JSON_OMITTED; ORIGINAL_SHA_AND_REDACTION_FLAG");
        p.put("maximumProviderCalls",2*DEFAULT_BUDGET.maxModelAttemptsPerRun());
        p.put("maximumTaskRunsThisBatch",2);p.put("ownedProcessMillis",235000);
        p.put("perRunCalls",DEFAULT_BUDGET.maxModelAttemptsPerRun());
        p.put("timeoutMillis",DEFAULT_BUDGET.maxModelMillis());
        p.put("runTimeoutMillis",DEFAULT_BUDGET.maxRunMillis());
        p.put("maximumOutputTokens",1024);
        p.put("automaticRetries",0);
        p.put("systemPrompt",RuleAgentProvider.SYSTEM);
        p.put("protocolRevision",RuleAgentProtocol.REVISION);
        p.set("actionContract",RuleAgentProtocol.description());
        p.set("actionSchema",RuleAgentProtocol.decisionSchema(List.of()));
        p.set("remainingBudgetMaximum",RuleAgentProtocol.remainingBudget(RulePublicationContract.MAX_DECISION_CALLS,DEFAULT_BUDGET.maxToolStepsPerRun()));
        p.put("publicationReviewProtocolRevision",RulePublicationContract.REVISION);
        p.set("publicationReviewContract",RulePublicationProtocol.description());
        p.set("publicationReviewPolicy",new ObjectMapper().valueToTree(RulePublicationContract.policy()));
        try{var field=RuleAgentProvider.class.getDeclaredField("REVIEW_SYSTEM");field.setAccessible(true);p.put("reviewSystemPrompt",(String)field.get(null));}catch(Exception invalid){throw new IllegalStateException("核验提示读取失败");}
        p.set("budget",new ObjectMapper().valueToTree(DEFAULT_BUDGET));
        var cases=p.putArray("cases");
        cases.addObject().put("id","P02").put("question","我这门选修课没过，能不能重修？").put("reply","PUBLIC");
        return p;
    }
    void validate(JsonNode p)throws Throwable{
        try{
            RuleAgentOnlineProbeHost.class.getDeclaredMethod("validatePackage",JsonNode.class).invoke(null,p);
        }
        catch(java.lang.reflect.InvocationTargetException e){
            throw e.getCause();
        }
    }
    @Test void revisedPacketBindsCurrentBudgetAndRejectsOldBudgetOrExpandedScope()throws Throwable{
        validate(packet());
        for(String field:List.of("timeoutMillis","runTimeoutMillis","maximumProviderCalls","automaticRetries")){
            var p=packet();
            p.put(field,field.equals("timeoutMillis")?5000:1);
            assertThatThrownBy(()->validate(p)).isInstanceOf(IllegalArgumentException.class);
        }
        var wrong=packet();
        wrong.putArray("cases").addObject().put("id","P02").put("question","old successful case");
        assertThatThrownBy(()->validate(wrong)).isInstanceOf(IllegalArgumentException.class);
        var budget=packet();
        ((com.fasterxml.jackson.databind.node.ObjectNode)budget.path("budget")).put("maxReadHandles",4);
        assertThatThrownBy(()->validate(budget)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void packetRejectsEditedActionContractAndDynamicBudget()throws Throwable {
        var edited=packet();
        ((com.fasterxml.jackson.databind.node.ObjectNode)edited.path("actionContract")).put("unexpected","changed");
        assertThatThrownBy(()->validate(edited)).isInstanceOf(IllegalArgumentException.class);
        var budget=packet();
        ((com.fasterxml.jackson.databind.node.ObjectNode)budget.path("remainingBudgetMaximum")).put("toolSteps",99);
        assertThatThrownBy(()->validate(budget)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void packetRejectsMissingOrOldProtocolIdentity()throws Throwable {
        var old=packet();
        old.remove("protocolRevision");
        assertThatThrownBy(()->validate(old)).isInstanceOf(IllegalArgumentException.class);
        var prompt=packet();
        prompt.put("systemPrompt","historical prompt");
        assertThatThrownBy(()->validate(prompt)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void packetRejectsMissingReviewAndOldDecisionBudget()throws Throwable{
        var missing=packet();missing.remove("publicationReviewContract");
        assertThatThrownBy(()->validate(missing)).isInstanceOf(IllegalArgumentException.class);
        var old=packet();old.set("remainingBudgetMaximum",RuleAgentProtocol.remainingBudget(3,6));
        assertThatThrownBy(()->validate(old)).isInstanceOf(IllegalArgumentException.class);
        var edited=packet();edited.put("reviewSystemPrompt","old review");
        assertThatThrownBy(()->validate(edited)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void oldCombinedCaseAndExpandedPacketAreRejected()throws Throwable{
        var env=Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_CASE","P01_P02");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
        var old=packet();
        old.put("maximumProviderCalls",12);
        old.withArray("cases").addObject().put("id","P02").put("question","我这门选修课没过，能不能重修？").put("reply","PUBLIC");
        assertThatThrownBy(()->validate(old)).isInstanceOf(IllegalArgumentException.class);
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
    @Test void p01ProbeRequiresSameRunReviewAndPublicationRatherThanTwoFactIds()throws Exception{
        studyArrangement=true;start(5);
        var input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"专业选修课和公共选修课在修读安排上有什么不同？");
        var actual=factory.open(fixture.owner,fixture.task).run(AgentRunContext.create(input.question()),input);
        assertThat(actual.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(RuleAgentOnlineProbeHost.p01Passed(actual)).isFalse();
        var json=new ObjectMapper();var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(actual.snapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("execution")).put("providerResponseReceived",true).put("answerSource","LIVE_AGENT");
        var snapshot=RuleAgentJson.readSnapshot(node.toString());
        assertThat(RuleAgentOnlineProbeHost.p01Passed(new RuleAgentRunner.Outcome(snapshot,actual.events()))).isTrue();
        var withoutReview=actual.events().stream().filter(e->!e.action().equals("PUBLICATION_REVIEW")).toList();
        assertThat(RuleAgentOnlineProbeHost.p01Passed(new RuleAgentRunner.Outcome(snapshot,withoutReview))).isFalse();
    }
    @Test void runtimeReadyBatchRejectsClosedTransportAuthorization() throws Throwable {
        var env=new HashMap<>(Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_CASE","P02_CONTINUATION_01"));
        assertThat(RuleAgentOnlineProbeHost.environmentCheck(env).get("providerCalls")).isEqualTo(0);
        env.put("ZHKU_RULE_AGENT_PROBE_CASE","P01_TRANSPORT_CLOSEOUT_01");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
        var old=packet();old.put("executionPolicyRevision","STATE_AWARE_TRANSPORT_CLOSEOUT_01");
        assertThatThrownBy(()->validate(old)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);assertThat(factory).isNull();
    }
    @Test void buildIdentityPinsArtifactAndSourceAndRejectsMissingJar(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        String source="knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java";
        for(String file:List.of("knowledge/target/knowledge-0.1.0-rc.1-controlled.jar",source,".gitattributes","knowledge/scripts/start-rule-agent-probe.cmd","knowledge/scripts/start-rule-agent-probe.ps1","scripts/Invoke-ZhkuOwnedProcess.ps1")) {
            java.nio.file.Files.createDirectories(root.resolve(file).getParent());
            java.nio.file.Files.writeString(root.resolve(file),"SYNTHETIC_LOCAL_IDENTITY_TEST");
        }
        var method=RuleAgentOnlineProbeHost.class.getDeclaredMethod("buildIdentity",java.nio.file.Path.class);method.setAccessible(true);
        var first=(JsonNode)method.invoke(null,root);
        var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(root.resolve("knowledge/target/knowledge-0.1.0-rc.1-controlled.jar"))));
        assertThat(first.path("jarSha256").asText()).isEqualTo(hash);
        java.nio.file.Files.writeString(root.resolve(source),"SYNTHETIC_CHANGED_SOURCE");
        var changed=(JsonNode)method.invoke(null,root);
        assertThat(changed.path("sourceSha256").path(source)).isNotEqualTo(first.path("sourceSha256").path(source));
        java.nio.file.Files.delete(root.resolve("knowledge/target/knowledge-0.1.0-rc.1-controlled.jar"));
        assertThatThrownBy(()->method.invoke(null,root)).isInstanceOf(java.lang.reflect.InvocationTargetException.class).hasCauseInstanceOf(java.nio.file.NoSuchFileException.class);
        assertThat(calls).hasValue(0);assertThat(factory).isNull();
    }
    boolean p02Check(String method,RuleAgentRunner.Outcome... values) throws Exception {
        var types=java.util.Collections.nCopies(values.length,RuleAgentRunner.Outcome.class).toArray(Class<?>[]::new);
        var m=RuleAgentOnlineProbeHost.class.getDeclaredMethod(method,types);m.setAccessible(true);return (Boolean)m.invoke(null,(Object[])values);
    }
    RuleAgentRunner.Outcome markedLive(RuleAgentRunner.Outcome o) throws Exception {
        var json=new ObjectMapper();var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(o.snapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("execution")).put("providerResponseReceived",true).put("answerSource","LIVE_AGENT");
        return new RuleAgentRunner.Outcome(RuleAgentJson.readSnapshot(node.toString()),o.events());
    }
    @Test void p02BatchHasSharedTenCallQuotaAndRejectsP01() {
        var env=new HashMap<>(Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_CASE","P02_CONTINUATION_01"));
        assertThat(RuleAgentOnlineProbeHost.environmentCheck(env).get("maximumProviderCalls")).isEqualTo(10);
        env.put("ZHKU_RULE_AGENT_PROBE_CASE","P01_RUNTIME_READY_01");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
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
    @Test void p02SameTaskResumeIsIdempotentAndRequiresBothLiveReviews() throws Exception {
        conditional=true;start(10);var queue=new ArrayDeque<Runnable>();var seen=new HashMap<UUID,RuleAgentRunner.Outcome>();
        RuleAgentTaskStore.Factory recording=(owner,task)->{var work=factory.open(owner,task);return new RuleAgentTaskStore.Work(){
            public RuleAgentRunner.Outcome run(AgentRunContext c,RuleAgentRunner.Input i){var o=work.run(c,i);seen.put(c.runId(),o);return o;}
            public boolean revalidate(ResultEnvelope r){return work.revalidate(r);}
        };};
        try(var store=new RuleAgentTaskStore(queue::add,recording,Clock.systemUTC(),RuleAgentTaskStore.DEFAULT_LIMITS)) {
            var first=store.create(fixture.owner,new CreateTask("我这门选修课没过，能不能重修？",UUID.randomUUID()));queue.remove().run();
            var waiting=store.snapshot(fixture.owner,first.taskId());
            assertThat(waiting.taskStatus()).isEqualTo(TaskStatus.WAITING_USER);
            assertThat(waiting.result().items().getFirst().claims()).isEmpty();assertThat(calls).hasValue(4);
            var wrong=new Message("PUBLIC",UUID.randomUUID(),waiting.version(),UUID.randomUUID());
            assertThatThrownBy(()->store.message(fixture.owner,first.taskId(),wrong)).isInstanceOf(RuleAgentTaskStore.Failure.class);
            assertThat(queue).isEmpty();assertThat(calls).hasValue(4);
            var message=new Message("PUBLIC",waiting.continuationToken(),waiting.version(),UUID.randomUUID());
            var next=store.message(fixture.owner,first.taskId(),message);
            assertThat(store.message(fixture.owner,first.taskId(),message).runId()).isEqualTo(next.runId());assertThat(queue).hasSize(1);queue.remove().run();
            var resolved=store.snapshot(fixture.owner,first.taskId());
            assertThat(resolved.taskStatus()).isEqualTo(TaskStatus.RESOLVED);
            assertThat(resolved.taskId()).isEqualTo(waiting.taskId());assertThat(resolved.runId()).isNotEqualTo(waiting.runId());
            assertThat(store.message(fixture.owner,first.taskId(),message).runId()).isEqualTo(next.runId());assertThat(queue).isEmpty();assertThat(calls).hasValue(8);
            assertThatThrownBy(()->store.message(fixture.owner,first.taskId(),new Message("PUBLIC",waiting.continuationToken(),waiting.version(),UUID.randomUUID()))).isInstanceOf(RuleAgentTaskStore.Failure.class);
            assertThat(calls).hasValue(8);assertThat(factory.remainingCalls()).isEqualTo(2);
            var before=seen.get(waiting.runId());var after=seen.get(resolved.runId());
            assertThat(RuleAgentOnlineProbeHost.publishedMatches(before,waiting)).isTrue();assertThat(RuleAgentOnlineProbeHost.publishedMatches(after,resolved)).isTrue();
            assertThat(p02Check("p02Passed",before,after)).isFalse();
            var onlineBefore=markedLive(before);var onlineAfter=markedLive(after);
            assertThat(p02Check("p02WaitingPassed",onlineBefore)).isTrue();assertThat(p02Check("p02Passed",onlineBefore,onlineAfter)).isTrue();
            var noReview=new RuleAgentRunner.Outcome(onlineAfter.snapshot(),onlineAfter.events().stream().filter(v->!v.action().equals("PUBLICATION_REVIEW")).toList());
            assertThat(p02Check("p02Passed",onlineBefore,noReview)).isFalse();
            var json=new ObjectMapper();var changed=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(onlineAfter.snapshot());changed.put("taskId",UUID.randomUUID().toString());
            assertThat(p02Check("p02Passed",onlineBefore,new RuleAgentRunner.Outcome(RuleAgentJson.readSnapshot(changed.toString()),onlineAfter.events()))).isFalse();
            json.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"p02-local-continuation.json").toFile(),Map.of("waiting",waiting,"resolved",resolved,"outcomes",List.of(before,after),"localHttpCalls",calls.get(),"realProviderCalls",0,"remainingSharedQuota",factory.remainingCalls(),"duplicateSubmissionExtraCalls",0,"staleTokenExtraCalls",0,"gateLiveFlagsOnlySyntheticUnitFixture",true));
        }
    }
    @Test void failedSecondOutcomeWriteCannotRemainSuccessful(@org.junit.jupiter.api.io.TempDir java.nio.file.Path evidence) throws Exception {
        conditional=true;start(10);var queue=new ArrayDeque<Runnable>();var saved=new HashMap<UUID,RuleAgentRunner.Outcome>();var original=new HashMap<UUID,RuleAgentRunner.Outcome>();
        RuleAgentTaskStore.Factory recording=(owner,task)->{var work=factory.open(owner,task);return new RuleAgentTaskStore.Work(){
            public RuleAgentRunner.Outcome run(AgentRunContext c,RuleAgentRunner.Input i){
                var o=work.run(c,i);original.put(c.runId(),o);
                if(!saved.isEmpty())try{java.nio.file.Files.createDirectory(evidence.resolve(c.runId()+"-outcome.json"));}catch(Exception failure){throw new IllegalStateException(failure);}
                RuleAgentOnlineProbeHost.recordOutcome(evidence,saved,o);return o;
            }
            public boolean revalidate(ResultEnvelope result){return work.revalidate(result);}
        };};
        try(var store=new RuleAgentTaskStore(queue::add,recording,Clock.systemUTC(),RuleAgentTaskStore.DEFAULT_LIMITS)) {
            var first=store.create(fixture.owner,new CreateTask("我这门选修课没过，能不能重修？",UUID.randomUUID()));queue.remove().run();
            var waiting=store.snapshot(fixture.owner,first.taskId());assertThat(waiting.taskStatus()).isEqualTo(TaskStatus.WAITING_USER);
            var next=store.message(fixture.owner,first.taskId(),new Message("PUBLIC",waiting.continuationToken(),waiting.version(),UUID.randomUUID()));queue.remove().run();
            var published=store.snapshot(fixture.owner,first.taskId());assertThat(published.taskStatus()).isEqualTo(TaskStatus.ACTIVE);
            assertThat(original.get(next.runId()).snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
            assertThat(saved).doesNotContainKey(next.runId());assertThat(calls).hasValue(8);
            var m=RuleAgentOnlineProbeHost.class.getDeclaredMethod("publishedMatches",RuleAgentRunner.Outcome.class,TaskSnapshot.class);m.setAccessible(true);assertThat((Boolean)m.invoke(null,original.get(next.runId()),published)).isFalse();
        }
    }
}
