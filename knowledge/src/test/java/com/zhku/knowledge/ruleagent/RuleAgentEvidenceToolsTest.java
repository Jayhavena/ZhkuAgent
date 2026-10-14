package com.zhku.knowledge.ruleagent;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.*;
import com.zhku.knowledge.rag.*;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

class RuleAgentEvidenceToolsTest {
    RuleCheckApplicationService.CatalogEntry article4(String checksum,int end)throws Exception{
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var bundle=json.readTree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../docs/product/rule-check-2025-verbatim-import-draft.json")));
        var c=java.util.stream.StreamSupport.stream(bundle.path("chunks").spliterator(),false).filter(n->n.path("article").asInt()==4).findFirst().orElseThrow();
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"2025.9.2",checksum,0,end,c.path("content").asText());
        var fact=new RuleCheckRuleTemplate.Fact("old-category","旧分类解释",true,Map.of(),List.of(binding));
        return new RuleCheckApplicationService.CatalogEntry(new RuleCheckRuleTemplate("S02/GENERAL","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"synthetic-binding-test",LocalDate.of(2026,10,13),List.of(fact),List.of(),List.of(),List.of()),"本地绑定回归，不作为学生事实发布");
    }
    @Test void article4OverlayAcceptsSameDigestInUppercase()throws Exception{
        var entries=RuleArticle4Overlay.apply(java.nio.file.Path.of("..").toAbsolutePath().normalize(),List.of(article4("F363EF0CF8C318E24A299FF7CCCA3DACE5EBAF9B64BE65E038985DAED5F28552",78)));
        assertThat(entries).hasSize(2);assertThat(entries.getLast().template().facts()).hasSize(2);
        assertThat(entries.getLast().template().facts().getFirst().evidenceBindings().getFirst().startInclusive()).isEqualTo(26);
    }
    @Test void article4OverlayAcceptsLowercaseAndRejectsChangedDigestOrSpan()throws Exception{
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();String checksum="f363ef0cf8c318e24a299ff7ccca3dace5ebaf9b64be65e038985daed5f28552";
        assertThat(RuleArticle4Overlay.apply(root,List.of(article4(checksum,78)))).hasSize(2);
        var changed=article4("0".repeat(64),78);var badSpan=article4(checksum,77);
        assertThatThrownBy(()->RuleArticle4Overlay.apply(root,List.of(changed))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->RuleArticle4Overlay.apply(root,List.of(badSpan))).isInstanceOf(IllegalArgumentException.class);
    }
    final UUID source=UUID.randomUUID(),chunk=UUID.randomUUID(),task=UUID.randomUUID(),run=UUID.randomUUID();
    final Owner owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
    final String body="合成规则：允许核对。";
    String hash()throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));}
    RuleCheckApplicationService.CatalogEntry entry()throws Exception{
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"v1",hash(),0,body.length(),body);
        var fact=new RuleCheckRuleTemplate.Fact("fact","合成解释",true,Map.of(),List.of(binding));
        return new RuleCheckApplicationService.CatalogEntry(new RuleCheckRuleTemplate("rule","v1",RuleCheckRuleTemplate.ReviewState.APPROVED,"synthetic",LocalDate.of(2026,10,13),List.of(fact),List.of(),List.of(),List.of()),"合成范围");
    }
    RuleAgentEvidenceTools tools(java.util.function.Supplier<List<RuleCheckApplicationService.CatalogEntry>> entries,
            ChunkCandidateRetriever retriever,RuleCheckEvidenceVerifier.CurrentEvidenceReader reader,CitationAuditRepository audit){
        return new RuleAgentEvidenceTools(owner,task,new RuleCheckRuntimeSettings("profile","cut","scope","v1",20,5),entries,retriever,reader,audit,Clock.systemUTC());
    }
    RetrievedChunk candidate()throws Exception{return new RetrievedChunk(chunk,body,"synthetic","synthetic",1,"v1",hash(),source);}
    RuleCheckEvidenceSnapshot current()throws Exception{return new RuleCheckEvidenceSnapshot("current",source,chunk,"v1",hash(),body,true);}
    @Test void realPortSearchReturnsCandidateAndReadReturnsOnlyBoundFacts()throws Exception{
        var entry=entry();var candidate=candidate();var current=current();var audit=new ArrayList<CitationAuditRecord>();
        var tools=tools(()->List.of(entry),(q,n)->List.of(candidate),(s,c)->Optional.of(current),audit::add);
        var search=tools.search(owner,task,run,1,new Search("q1","合成规则"));
        assertThat(search.dataLevel()).isEqualTo(DataLevel.CANDIDATE);assertThat(search.factIds()).isEmpty();
        var result=tools.read(owner,task,run,2,new Read("q1",List.of(search.candidates().getFirst().handle())));
        assertThat(result.observation().dataLevel()).isEqualTo(DataLevel.VERIFIED_FACT);assertThat(result.facts()).hasSize(1);
        assertThat(result.observation().factIds()).containsExactly("rule:fact");assertThat(audit).isNotEmpty();
        assertThat(tools.original(owner,task,result.observation().evidence().getFirst().originalToken())).isPresent();
    }
    @Test void foreignTaskOrOwnerCannotUseHandle()throws Exception{
        var entry=entry();var candidate=candidate();var current=current();var tools=tools(()->List.of(entry),(q,n)->List.of(candidate),(s,c)->Optional.of(current),r->{});
        var handle=tools.search(owner,task,run,1,new Search("q","query")).candidates().getFirst().handle();
        assertThat(tools.read(owner,UUID.randomUUID(),run,2,new Read("q",List.of(handle))).observation().reasonCode()).isEqualTo(ReasonCode.MATERIAL_SCOPE_MISMATCH);
        assertThat(tools.read(new Owner(UUID.randomUUID(),owner.sessionNonce()),task,run,2,new Read("q",List.of(handle))).facts()).isEmpty();
        assertThat(tools.read(owner,task,run,2,new Read("q",List.of(UUID.randomUUID()))).observation().reasonCode()).isEqualTo(ReasonCode.EVIDENCE_CHANGED);
    }
    @Test void repeatedReadReusesEvidenceButStillAuditsAndRevalidates()throws Exception{
        var entry=entry();var candidate=candidate();var current=current();var audit=new ArrayList<CitationAuditRecord>();
        var available=new java.util.concurrent.atomic.AtomicBoolean(true);
        var tools=tools(()->List.of(entry),(q,n)->List.of(candidate),(s,c)->Optional.of(available.get()?current:new RuleCheckEvidenceSnapshot("changed",source,chunk,"v1",current.checksum(),body,false)),audit::add);
        var handle=tools.search(owner,task,run,1,new Search("q","query")).candidates().getFirst().handle();
        var first=tools.read(owner,task,run,2,new Read("q",List.of(handle))).observation().evidence().getFirst();
        for(int i=0;i<10;i++){
            var again=tools.read(owner,task,run,3,new Read("q",List.of(handle)));
            assertThat(again.observation().status()).isEqualTo(ObservationStatus.SUCCEEDED);
            assertThat(again.observation().evidence()).containsExactly(first);
        }
        assertThat(audit).hasSize(11);
        available.set(false);
        assertThat(tools.read(owner,task,run,4,new Read("q",List.of(handle))).observation().reasonCode()).isEqualTo(ReasonCode.EVIDENCE_CHANGED);
        assertThat(tools.original(owner,task,first.originalToken())).isEmpty();
    }
    @Test void mixedReadAddsOnlyNewProofsAndCapacityRejectsWholeSelection()throws Exception{
        var facts=new ArrayList<RuleCheckRuleTemplate.Fact>();var candidates=new ArrayList<RetrievedChunk>();
        var current=new HashMap<UUID,RuleCheckEvidenceSnapshot>();
        for(int i=0;i<9;i++){
            UUID id=UUID.randomUUID();var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,id,"v1",hash(),0,body.length(),body);
            facts.add(new RuleCheckRuleTemplate.Fact("f"+i,"合成事实"+i,true,Map.of(),List.of(binding)));
            candidates.add(new RetrievedChunk(id,body,"synthetic","synthetic",1,"v1",hash(),source));
            current.put(id,new RuleCheckEvidenceSnapshot("current-"+i,source,id,"v1",hash(),body,true));
        }
        var rule=new RuleCheckRuleTemplate("bulk","v1",RuleCheckRuleTemplate.ReviewState.APPROVED,"synthetic",LocalDate.of(2026,10,14),facts,List.of(),List.of(),List.of());
        var tools=tools(()->List.of(new RuleCheckApplicationService.CatalogEntry(rule,"合成容量测试")),(q,n)->candidates,(s,c)->Optional.ofNullable(current.get(c)),r->{});
        var broker=new RuleActionBroker(tools);var input=RuleAgentRunner.Input.single(owner,task,"合成问题");
        var found=broker.execute(new Search("q1","容量"),input,run,1).candidates();
        var first=broker.execute(new Read("q1",List.of(found.getFirst().handle())),input,run,2);
        assertThat(broker.progressed()).isTrue();
        var mixed=broker.execute(new Read("q1",found.subList(0,3).stream().map(Candidate::handle).toList()),input,run,3);
        assertThat(broker.progressed()).isTrue();assertThat(mixed.evidence()).contains(first.evidence().getFirst());
        assertThat(tools.read(owner,task,run,4,new Read("q1",found.subList(3,6).stream().map(Candidate::handle).toList())).observation().status()).isEqualTo(ObservationStatus.SUCCEEDED);
        assertThat(tools.read(owner,task,run,5,new Read("q1",found.subList(6,8).stream().map(Candidate::handle).toList())).observation().status()).isEqualTo(ObservationStatus.SUCCEEDED);
        var rejected=tools.read(owner,task,run,6,new Read("q1",List.of(found.getFirst().handle(),found.get(8).handle())));
        assertThat(rejected.observation().reasonCode()).isEqualTo(ReasonCode.BUDGET_EXCEEDED);
        assertThat(rejected.facts()).isEmpty();assertThat(rejected.observation().evidence()).isEmpty();
        assertThat(tools.read(owner,task,run,6,new Read("q1",List.of(found.getFirst().handle()))).observation().evidence()).containsExactly(first.evidence().getFirst());
        broker.execute(new Read("q1",List.of(found.getFirst().handle())),input,run,6);assertThat(broker.progressed()).isFalse();
    }
    @Test void noMatchAndUnavailableAreDifferentObservations()throws Exception{
        var entry=entry();var current=current();
        assertThat(tools(()->List.of(entry),(q,n)->List.of(),(s,c)->Optional.of(current),r->{}).search(owner,task,run,1,new Search("q","query")).status()).isEqualTo(ObservationStatus.NO_MATCH);
        assertThat(tools(()->List.of(entry),(q,n)->{throw new RetrieverUnavailableException("synthetic");},(s,c)->Optional.of(current),r->{}).search(owner,task,run,1,new Search("q","query")).status()).isEqualTo(ObservationStatus.UNAVAILABLE);
    }
    @Test void revokedChangedDirectoryAndReaderFailureCannotReuseCandidates()throws Exception{
        var entries=new ArrayList<>(List.of(entry()));var candidate=candidate();var available=new java.util.concurrent.atomic.AtomicBoolean(true);
        var tools=tools(()->List.copyOf(entries),(q,n)->List.of(candidate),(s,c)->{if(!available.get())return Optional.empty();try{return Optional.of(current());}catch(Exception e){throw new IllegalStateException(e);}},r->{});
        var handle=tools.search(owner,task,run,1,new Search("q","query")).candidates().getFirst().handle();available.set(false);
        assertThat(tools.read(owner,task,run,2,new Read("q",List.of(handle))).observation().reasonCode()).isEqualTo(ReasonCode.EVIDENCE_CHANGED);
        available.set(true);entries.clear();assertThat(tools.read(owner,task,run,2,new Read("q",List.of(handle))).facts()).isEmpty();
    }
    @Test void auditFailureReturnsNoPublishableData()throws Exception{
        var entry=entry();var candidate=candidate();var current=current();var tools=tools(()->List.of(entry),(q,n)->List.of(candidate),(s,c)->Optional.of(current),r->{throw new IllegalStateException("synthetic audit");});
        var handle=tools.search(owner,task,run,1,new Search("q","query")).candidates().getFirst().handle();
        var result=tools.read(owner,task,run,2,new Read("q",List.of(handle)));
        assertThat(result.observation().reasonCode()).isEqualTo(ReasonCode.AUDIT_UNAVAILABLE);assertThat(result.facts()).isEmpty();assertThat(result.observation().evidence()).isEmpty();
    }
    @Test void unrelatedOrDraftFactsAreNotEligible()throws Exception{
        var candidate=candidate();var current=current();var tools=tools(List::of,(q,n)->List.of(candidate),(s,c)->Optional.of(current),r->{});
        assertThat(tools.search(owner,task,run,1,new Search("q","query")).candidates()).isEmpty();
        var approved=entry();var rule=approved.template();
        var draft=new RuleCheckApplicationService.CatalogEntry(new RuleCheckRuleTemplate(rule.ruleId(),rule.referenceRevision(),RuleCheckRuleTemplate.ReviewState.DRAFT,null,null,rule.facts(),rule.conditions(),rule.evidenceGaps(),rule.conflicts()),"未审核解释");
        assertThat(tools(()->List.of(draft),(q,n)->List.of(candidate),(s,c)->Optional.of(current),r->{}).search(owner,task,run,1,new Search("q","query")).candidates()).isEmpty();
        var foreign=new RetrievedChunk(UUID.randomUUID(),body,"synthetic","synthetic",1,"v1",hash(),UUID.randomUUID());
        assertThat(tools(()->List.of(approved),(q,n)->List.of(foreign),(s,c)->Optional.of(current),r->{}).search(owner,task,run,1,new Search("q","query")).candidates()).isEmpty();
    }
    @Test void readerFaultIsUnavailableInsteadOfMissingPolicy()throws Exception{
        var entry=entry();var candidate=candidate();var current=current();var broken=new java.util.concurrent.atomic.AtomicBoolean(false);
        var tools=tools(()->List.of(entry),(q,n)->List.of(candidate),(s,c)->{if(broken.get())throw new IllegalStateException("synthetic reader failure");return Optional.of(current);},r->{});
        var handle=tools.search(owner,task,run,1,new Search("q","query")).candidates().getFirst().handle();broken.set(true);
        assertThat(tools.search(owner,task,run,2,new Search("q","query")).status()).isEqualTo(ObservationStatus.UNAVAILABLE);
        var result=tools.read(owner,task,run,2,new Read("q",List.of(handle)));
        assertThat(result.observation().status()).isEqualTo(ObservationStatus.UNAVAILABLE);assertThat(result.observation().reasonCode()).isEqualTo(ReasonCode.RETRIEVAL_UNAVAILABLE);
        assertThat(result.facts()).isEmpty();assertThat(result.observation().evidence()).isEmpty();
    }
    @Test void expiredHandleAndExpiredTaskCannotReadOrOpenOriginal()throws Exception{
        var entry=entry();var candidate=candidate();var current=current();var now=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-13T06:00:00Z"));
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        var tools=new RuleAgentEvidenceTools(owner,task,new RuleCheckRuntimeSettings("profile","cut","scope","v1",20,5),()->List.of(entry),(q,n)->List.of(candidate),(s,c)->Optional.of(current),r->{},clock);
        var handle=tools.search(owner,task,run,1,new Search("q","query")).candidates().getFirst().handle();
        var read=tools.read(owner,task,run,2,new Read("q",List.of(handle)));var token=read.observation().evidence().getFirst().originalToken();
        now.set(now.get().plusMillis(DEFAULT_BUDGET.waitingTtlMillis()));
        assertThat(tools.read(owner,task,run,3,new Read("q",List.of(handle))).observation().reasonCode()).isEqualTo(ReasonCode.EVIDENCE_CHANGED);
        now.set(Instant.parse("2026-10-13T06:00:00Z").plusMillis(DEFAULT_BUDGET.taskTtlMillis()));
        assertThat(tools.original(owner,task,token)).isEmpty();assertThat(tools.search(owner,task,run,3,new Search("q","query")).reasonCode()).isEqualTo(ReasonCode.MATERIAL_SCOPE_MISMATCH);
    }
}
