package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;

/** 合成审核/reader；最终组件复查不等于HTTP最终交付或来源语义真实性。 */
class RuleCheckReleaseGuardTest {
    final UUID source=UUID.randomUUID(),chunk=UUID.randomUUID();final String text="合成条款：上限4。",hash=sha(text);
    final RuleCheckRuntimeSettings settings=new RuleCheckRuntimeSettings("fixture","cut","SCOPE","v1",20,5);
    final AtomicReference<List<RuleCheckApplicationService.CatalogEntry>> catalogue=new AtomicReference<>();
    final AtomicReference<RuleCheckEvidenceSnapshot> current=new AtomicReference<>(new RuleCheckEvidenceSnapshot("current",source,chunk,"v1",hash,text,true));
    final AtomicInteger selects=new AtomicInteger(),retrievals=new AtomicInteger(),audits=new AtomicInteger(),reads=new AtomicInteger();
    final Map<UUID,RuleCheckEvidenceSnapshot> otherCurrent=new HashMap<>();
    final List<RetrievedChunk> extraRetrieved=new ArrayList<>();
    Runnable onRead=()->{};boolean readerFailure;
    EvidenceBinding binding(){return new EvidenceBinding(source,chunk,"v1",hash,0,text.length(),text);}
    Fact fact(String id,boolean independent,Map<String,String> when){return new Fact(id,"合成审核事实"+id,independent,when,List.of(binding()));}
    RuleCheckRuleTemplate rule(List<Fact> facts,List<Condition> conditions,List<String> gaps,List<Conflict> conflicts){return new RuleCheckRuleTemplate("fixture-rule","v1",ReviewState.APPROVED,"合成责任",LocalDate.of(2026,10,8),facts,conditions,gaps,conflicts);}
    RuleCheckRuleTemplate ordinary(){return rule(List.of(fact("a",true,Map.of())),List.of(),List.of(),List.of());}
    RuleCheckApplicationService app(RuleCheckRuleTemplate rule) {
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(rule,"合成查询范围")));
        var verifier=new RuleCheckEvidenceVerifier((s,c)->{reads.incrementAndGet();onRead.run();if(readerFailure)throw new IllegalStateException("synthetic private failure");return Optional.ofNullable(otherCurrent.getOrDefault(c,current.get()));});
        var capture=new RuleCheckEvidenceCapture(verifier,()->"C-"+UUID.randomUUID(),row->audits.incrementAndGet(),Clock.systemUTC());
        return new RuleCheckApplicationService(settings,catalogue::get,(q,o)->{selects.incrementAndGet();return rule.ruleId();},
            new HybridRetrievalService(new HybridCandidateFuser()),(q,l)->{retrievals.incrementAndGet();var chunks=new ArrayList<>(extraRetrieved);chunks.add(new RetrievedChunk(chunk,text,"合成","合成页",1,"v1",hash,source));return chunks;},
            (q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
    }
    RuleCheckScopedResult ready(RuleCheckApplicationService app) {
        var result=app.checkWithContext("合成条款",Map.of());assertThat(result.releaseBinding()).isNotNull();return result;
    }
    @Test void stableResultRechecksFreshEvidenceWithoutReselectRetrievalOrAudit() {
        var app=app(ordinary());var result=ready(app);int before=reads.get();assertThat(app.isCurrent(result)).isTrue();
        assertThat(reads.get()).isGreaterThan(before);assertThat(selects).hasValue(1);assertThat(retrievals).hasValue(1);assertThat(audits).hasValue(1);
        assertThat(result.releaseBinding().references()).hasSize(1);assertThat(result.releaseBinding().references().getFirst().citationId()).startsWith("C-");
        assertThatThrownBy(()->result.releaseBinding().references().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void withdrawnEvidenceAfterDecisionCannotReleaseOldFacts() {
        var app=app(ordinary());var result=ready(app);current.set(new RuleCheckEvidenceSnapshot("current",source,chunk,"v1",hash,text,false));
        assertThat(app.isCurrent(result)).isFalse();assertThat(result.decision().status()).isEqualTo(ANSWERED);
    }
    @Test void currentPersistentIdentityRevisionHashBodyAndFailuresAreRechecked() {
        var app=app(ordinary());var result=ready(app);var original=current.get();
        for(var changed:List.of(new RuleCheckEvidenceSnapshot("current",UUID.randomUUID(),chunk,"v1",hash,text,true),
            new RuleCheckEvidenceSnapshot("current",source,UUID.randomUUID(),"v1",hash,text,true),new RuleCheckEvidenceSnapshot("current",source,chunk,"v2",hash,text,true),
            new RuleCheckEvidenceSnapshot("current",source,chunk,"v1",sha("合成上限40"),"合成上限40",true),new RuleCheckEvidenceSnapshot("current",source,chunk,"v1",hash,"合成上限40",true))) {
            current.set(changed);assertThat(app.isCurrent(result)).isFalse();
        }
        current.set(null);assertThat(app.isCurrent(result)).isFalse();current.set(original);readerFailure=true;
        assertThatCode(()->assertThat(app.isCurrent(result)).isFalse()).doesNotThrowAnyException();
    }
    @Test void ruleRetirementAndApprovedScopeChangeRejectBeforeEvidenceReads() {
        var app=app(ordinary());var result=ready(app);var entry=catalogue.get().getFirst();int before=reads.get();catalogue.set(List.of());
        assertThat(app.isCurrent(result)).isFalse();assertThat(reads).hasValue(before);
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(entry.template(),"变化范围")));assertThat(app.isCurrent(result)).isFalse();
    }
    @Test void approvedFactReviewResponsibilityAndConditionChangesInvalidateBinding() {
        var app=app(ordinary());var result=ready(app);var rule=ordinary();
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(rule(List.of(fact("changed",true,Map.of())),List.of(),List.of(),List.of()),"合成查询范围")));
        assertThat(app.isCurrent(result)).isFalse();
        var changed=new RuleCheckRuleTemplate(rule.ruleId(),rule.referenceRevision(),rule.reviewState(),"另一合成责任",rule.reviewedAt(),rule.facts(),rule.conditions(),rule.evidenceGaps(),rule.conflicts());
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(changed,"合成查询范围")));assertThat(app.isCurrent(result)).isFalse();
    }
    @Test void catalogueChangeDuringFreshReadsCannotReleaseOldRule() {
        var app=app(ordinary());var result=ready(app);onRead=()->catalogue.set(List.of());assertThat(app.isCurrent(result)).isFalse();
    }
    @Test void fourScopedStatesReplayExactlyIncludingPendingConditionWithoutQuotes() {
        var condition=new Condition("type","合成类型",List.of("ordinary","other"));
        var rules=List.of(ordinary(),rule(List.of(fact("a",true,Map.of())),List.of(),List.of("合成缺口"),List.of()),
            rule(List.of(fact("a",false,Map.of("type","ordinary"))),List.of(condition),List.of(),List.of()),
            rule(List.of(),List.of(),List.of(),List.of(new Conflict("合成冲突",List.of(fact("a",true,Map.of()),fact("b",true,Map.of()))))));
        var expected=List.of(ANSWERED,PARTIALLY_ANSWERED,NEED_CLARIFICATION,CONFLICTING_EVIDENCE);
        for(int i=0;i<rules.size();i++){var app=app(rules.get(i));var result=ready(app);assertThat(result.decision().status()).isEqualTo(expected.get(i));assertThat(app.isCurrent(result)).isTrue();}
    }
    @Test void pendingConditionIsRefusedWhenItsNecessaryEvidenceWasWithdrawn() {
        var condition=new Condition("type","合成类型",List.of("ordinary","other"));
        var app=app(rule(List.of(fact("a",false,Map.of("type","ordinary"))),List.of(condition),List.of(),List.of()));var result=ready(app);
        assertThat(result.decision().confirmedFacts()).isEmpty();current.set(null);assertThat(app.isCurrent(result)).isFalse();
    }
    @Test void publicCompatibilityConstructorDoesNotAuthorizeAnswerRelease() {
        var app=app(ordinary());var original=app.checkWithContext("合成条款",Map.of());
        assertThat(app.isCurrent(new RuleCheckScopedResult(original.decision(),original.scope(),original.continuation()))).isFalse();assertThat(app.isCurrent(null)).isFalse();
    }
    @Test void alteredFactsScopeAndContinuationCannotPassReplay() {
        var app=app(ordinary());var original=ready(app);var fact=original.decision().confirmedFacts().getFirst();
        var forged=new RuleCheckDecision(ANSWERED,"v1",List.of(new RuleCheckDecision.SupportedFact(fact.id(),"伪造40",fact.evidence())),List.of(),List.of(),List.of());
        assertThat(app.isCurrent(new RuleCheckScopedResult(forged,original.scope(),null,original.releaseBinding()))).isFalse();
        assertThat(app.isCurrent(new RuleCheckScopedResult(original.decision(),null,null,original.releaseBinding()))).isFalse();
        var condition=new Condition("type","类型",List.of("A"));var continuation=new RuleCheckScopedResult.Continuation(settings,catalogue.get().getFirst(),"合成条款",Map.of(),condition);
        assertThat(app.isCurrent(new RuleCheckScopedResult(original.decision(),original.scope(),continuation,original.releaseBinding()))).isFalse();
    }
    @Test void mismatchedSettingsAndMissingOrDuplicatedReferenceMetadataFailClosed() {
        var app=app(ordinary());var result=ready(app);var b=result.releaseBinding();var wrong=new RuleCheckRuntimeSettings("other","cut","SCOPE","v1",20,5);
        int before=reads.get();
        for(var binding:List.of(new RuleCheckScopedResult.ReleaseBinding(wrong,b.entry(),b.question(),b.confirmedConditions(),b.references()),
            new RuleCheckScopedResult.ReleaseBinding(settings,b.entry(),b.question(),b.confirmedConditions(),List.of()),
            new RuleCheckScopedResult.ReleaseBinding(settings,b.entry(),b.question(),b.confirmedConditions(),Collections.nCopies(2,b.references().getFirst())),
            new RuleCheckScopedResult.ReleaseBinding(settings,b.entry(),b.question(),b.confirmedConditions(),Collections.nCopies(6,b.references().getFirst()))))
            assertThat(app.isCurrent(new RuleCheckScopedResult(result.decision(),result.scope(),result.continuation(),binding))).isFalse();
        assertThat(reads).hasValue(before);
    }
    @Test void recheckDoesNotFetchNewEvidenceAndUpgradePreviouslyPartialDecision() {
        UUID otherSource=UUID.randomUUID(),otherChunk=UUID.randomUUID();String otherText="合成缺失条款";
        var missing=new EvidenceBinding(otherSource,otherChunk,"v1",sha(otherText),0,otherText.length(),otherText);
        var app=app(rule(List.of(fact("known",true,Map.of()),new Fact("missing","合成待证事实",false,Map.of(),List.of(missing))),List.of(),List.of(),List.of()));
        var result=ready(app);assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        otherCurrent.put(otherChunk,new RuleCheckEvidenceSnapshot("other",otherSource,otherChunk,"v1",sha(otherText),otherText,true));
        assertThat(app.isCurrent(result)).isTrue();assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(selects).hasValue(1);assertThat(retrievals).hasValue(1);assertThat(audits).hasValue(1);
    }
    @Test void unclaimedCapturedChunkStillRequiresActualBodyHashAndWellFormedUnicode() {
        UUID otherSource=UUID.randomUUID(),otherChunk=UUID.randomUUID();String otherText="合成未使用片段",otherHash=sha(otherText);
        otherCurrent.put(otherChunk,new RuleCheckEvidenceSnapshot("other",otherSource,otherChunk,"v1",otherHash,otherText,true));
        extraRetrieved.add(new RetrievedChunk(otherChunk,otherText,"合成","合成页",1,"v1",otherHash,otherSource));
        var app=app(ordinary());var result=ready(app);assertThat(result.releaseBinding().references()).hasSize(2);
        assertThat(result.decision().confirmedFacts()).hasSize(1);assertThat(app.isCurrent(result)).isTrue();
        otherCurrent.put(otherChunk,new RuleCheckEvidenceSnapshot("other",otherSource,otherChunk,"v1",otherHash,"合成未使用片段变更",true));
        assertThat(app.isCurrent(result)).isFalse();
        otherCurrent.put(otherChunk,new RuleCheckEvidenceSnapshot("other",otherSource,otherChunk,"v1",otherHash,"\uD800",true));
        assertThat(app.isCurrent(result)).isFalse();
    }
    @Test void evidenceFreeRefusalCanReleaseWithoutCallingUnavailableCatalogue() {
        var app=app(ordinary());catalogue.set(List.of());var result=app.checkWithContext("合成条款",Map.of());assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.releaseBinding()).isNull();assertThat(app.isCurrent(result)).isTrue();
    }
    @Test void originalMembershipUsesApprovedExactBindingNotClientFlagsOrAlias() {
        var app=app(ordinary());var result=ready(app);var q=result.decision().confirmedFacts().getFirst().evidence().getFirst();assertThat(app.isReferenceApproved(q)).isTrue();
        var changed=new RuleCheckVerifiedExcerpt(q.citationId(),q.sourceId(),q.chunkId(),q.revision(),q.checksum(),0,2,"伪造");assertThat(app.isReferenceApproved(changed)).isFalse();
        catalogue.set(List.of());assertThat(app.isReferenceApproved(q)).isFalse();assertThat(app.isReferenceApproved(null)).isFalse();
    }
    @Test void originalConflictSideBindingsAreAlsoCurrentCatalogueMembers() {
        var app=app(rule(List.of(),List.of(),List.of(),List.of(new Conflict("合成冲突",List.of(fact("a",true,Map.of()),fact("b",true,Map.of()))))));
        var result=ready(app);assertThat(app.isReferenceApproved(result.decision().conflicts().getFirst().sides().getFirst().evidence().getFirst())).isTrue();
    }
    static String sha(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
}
