package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.rulecheck.RuleCheckVerifiedExcerpt;
import com.zhku.knowledge.security.TrustedActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;

/** 不连接数据库；合成excerpt只验证Session凭据约束，不证明来源真实或学生展示许可。 */
class RuleCheckCitationStoreTest {
    final MutableClock clock=new MutableClock();final RuleCheckCitationStore store=new RuleCheckCitationStore(clock);
    final TrustedActor actor=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
    final RuleCheckVerifiedExcerpt excerpt=quote("C1","合成原文");
    static RuleCheckVerifiedExcerpt quote(String id,String text) {return new RuleCheckVerifiedExcerpt(id,UUID.randomUUID(),UUID.randomUUID(),"2025.9.2","a".repeat(64),0,text.length(),text);}
    String put(MockHttpSession session) {var handles=store.publish(session,actor,store.begin(session,actor),List.of(excerpt));assertThat(handles).containsKey(excerpt);return handles.get(excerpt);}
    void fails(Runnable action) {assertThatThrownBy(action::run).isInstanceOf(RuleCheckCitationStore.Failure.class).hasMessage("引用凭据已失效").hasNoCause();}
    @Test void publishesOpaqueHandleAndOnlyOneSessionSlot() {
        var session=new MockHttpSession();String token=put(session);assertThat(token).isNotNull();assertThat(UUID.fromString(token).toString()).isEqualTo(token);
        var ticket=store.resolve(session,actor,token);assertThat(ticket.excerpt()).isEqualTo(excerpt);assertThat(ticket.token()).isEqualTo(token);
        assertThat(Collections.list(session.getAttributeNames())).hasSize(1);assertThat(token).isNotEqualTo(excerpt.citationId());
    }
    @Test void emptyCompletedRoundCannotBePublishedAgain() {
        var s=new MockHttpSession();String generation=store.begin(s,actor);assertThat(store.publish(s,actor,generation,List.of())).isEmpty();
        fails(()->store.publish(s,actor,generation,List.of(excerpt)));
    }
    @Test void exactDuplicateReferencesShareOneHandleAndImmutableOutput() {
        var s=new MockHttpSession();var handles=store.publish(s,actor,store.begin(s,actor),List.of(excerpt,excerpt));assertThat(handles).hasSize(1);
        assertThatThrownBy(()->handles.clear()).isInstanceOf(UnsupportedOperationException.class);assertThat(store.resolve(s,actor,handles.get(excerpt)).excerpt()).isEqualTo(excerpt);
    }
    @Test void conflictingSameAliasCannotPublishAnyReference() {
        var s=new MockHttpSession();String g=store.begin(s,actor);var changed=new RuleCheckVerifiedExcerpt(excerpt.citationId(),excerpt.sourceId(),excerpt.chunkId(),excerpt.revision(),excerpt.checksum(),0,2,"不同");
        assertThatThrownBy(()->store.publish(s,actor,g,List.of(excerpt,changed))).isInstanceOf(IllegalArgumentException.class);
        fails(()->store.resolve(s,actor,UUID.randomUUID().toString()));
    }
    @Test void inputCountIncludesDuplicatesBeforeDeduplication() {
        var s=new MockHttpSession();String g=store.begin(s,actor);
        assertThatThrownBy(()->store.publish(s,actor,g,Collections.nCopies(33,excerpt))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void individualUtf16AndUtf8QuoteBudgetsAreBothEnforced() {
        for(String text:List.of("a".repeat(4097),"中".repeat(2731))) {
            var s=new MockHttpSession();String g=store.begin(s,actor);assertThatThrownBy(()->store.publish(s,actor,g,List.of(quote("large",text)))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void aggregateQuoteBytesCannotGrowWithoutBound() {
        var quotes=new ArrayList<RuleCheckVerifiedExcerpt>();for(int i=0;i<5;i++)quotes.add(quote("C"+i,"😀".repeat(2048)));
        var s=new MockHttpSession();String g=store.begin(s,actor);assertThatThrownBy(()->store.publish(s,actor,g,quotes)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void exactCountAndTextBudgetBoundariesRemainUsable() {
        var s=new MockHttpSession();var quotes=new ArrayList<RuleCheckVerifiedExcerpt>();for(int i=0;i<4;i++)quotes.add(quote("C"+i,"😀".repeat(2048)));
        assertThat(store.publish(s,actor,store.begin(s,actor),quotes)).hasSize(4);
        quotes.clear();for(int i=0;i<32;i++)quotes.add(quote("C"+i,"合成"));assertThat(store.publish(s,actor,store.begin(s,actor),quotes)).hasSize(32);
    }
    @Test void malformedReferenceFieldsAndUnicodeCannotEnterStore() {
        var invalid=Arrays.asList(null,new RuleCheckVerifiedExcerpt(null,excerpt.sourceId(),excerpt.chunkId(),excerpt.revision(),excerpt.checksum(),0,4,"合成原文"),
            new RuleCheckVerifiedExcerpt("C1",null,excerpt.chunkId(),excerpt.revision(),excerpt.checksum(),0,4,"合成原文"),
            new RuleCheckVerifiedExcerpt("C1",excerpt.sourceId(),null,excerpt.revision(),excerpt.checksum(),0,4,"合成原文"),
            new RuleCheckVerifiedExcerpt("C1",excerpt.sourceId(),excerpt.chunkId(),"x".repeat(129),excerpt.checksum(),0,4,"合成原文"),
            new RuleCheckVerifiedExcerpt("C1",excerpt.sourceId(),excerpt.chunkId(),excerpt.revision(),"bad",0,4,"合成原文"),
            new RuleCheckVerifiedExcerpt("C1",excerpt.sourceId(),excerpt.chunkId(),excerpt.revision(),excerpt.checksum(),-1,3,"合成原文"),
            new RuleCheckVerifiedExcerpt("C1",excerpt.sourceId(),excerpt.chunkId(),excerpt.revision(),excerpt.checksum(),0,3,"合成原文"),quote("C1"," "),quote("C1","\uD800"),quote("\uDC00","合成"));
        for(var q:invalid){var s=new MockHttpSession();String g=store.begin(s,actor);assertThatThrownBy(()->store.publish(s,actor,g,Collections.singletonList(q))).isInstanceOf(IllegalArgumentException.class);}
    }
    @Test void anotherSessionAndMalformedOrUnknownHandlesCannotReadOrEraseCurrentReference() {
        var s=new MockHttpSession();String token=put(s);fails(()->store.resolve(new MockHttpSession(),actor,token));
        for(String bad:new String[]{null,"C1","not-a-uuid",UUID.randomUUID().toString()})fails(()->store.resolve(s,actor,bad));
        assertThat(store.resolve(s,actor,token).excerpt()).isEqualTo(excerpt);
    }
    @Test void fullActorIdentityAndRevisionMustMatchWithoutClearingNewOwnersRound() {
        var s=new MockHttpSession();String token=put(s);
        for(var other:List.of(new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0),new TrustedActor(actor.userId(),TrustedActor.Role.USER,1),new TrustedActor(actor.userId(),TrustedActor.Role.MAINTAINER,0)))fails(()->store.resolve(s,other,token));
        assertThat(store.resolve(s,actor,token).excerpt()).isEqualTo(excerpt);
    }
    @Test void missingSessionOrNonStudentCannotStartOrPublish() {
        fails(()->store.begin(null,actor));fails(()->store.begin(new MockHttpSession(),null));
        fails(()->store.begin(new MockHttpSession(),new TrustedActor(actor.userId(),TrustedActor.Role.MAINTAINER,0)));
        fails(()->store.publish(null,actor,UUID.randomUUID().toString(),List.of(excerpt)));
    }
    @Test void readsDoNotSlideExpiryAndExactFiveMinuteBoundaryRejects() {
        var s=new MockHttpSession();String token=put(s);clock.now=Instant.EPOCH.plusSeconds(299);assertThat(store.resolve(s,actor,token)).isNotNull();
        clock.now=Instant.EPOCH.plusSeconds(300);fails(()->store.resolve(s,actor,token));assertThat(Collections.list(s.getAttributeNames())).isEmpty();
    }
    @Test void slowPublishExpiresFromRoundStartRatherThanPublicationTime() {
        var s=new MockHttpSession();String g=store.begin(s,actor);clock.now=Instant.EPOCH.plusSeconds(300);fails(()->store.publish(s,actor,g,List.of(excerpt)));
    }
    @Test void validationThatCrossesDeadlineCannotPublishCompletedHandles() {
        var s=new MockHttpSession();String g=store.begin(s,actor);
        var advancing=new AbstractList<RuleCheckVerifiedExcerpt>() {
            public int size(){return 1;}public RuleCheckVerifiedExcerpt get(int index){clock.now=Instant.EPOCH.plusSeconds(300);return excerpt;}
        };
        fails(()->store.publish(s,actor,g,advancing));assertThat(Collections.list(s.getAttributeNames())).isEmpty();
    }
    @Test void newQuestionInvalidatesOldHandlesAndLatePublicationCannotOverwriteNewRound() {
        var s=new MockHttpSession();String oldToken=put(s),oldGeneration=store.resolve(s,actor,oldToken).generation();String next=store.begin(s,actor);
        fails(()->store.resolve(s,actor,oldToken));fails(()->store.publish(s,actor,oldGeneration,List.of(excerpt)));
        var handles=store.publish(s,actor,next,List.of(excerpt));assertThat(store.resolve(s,actor,handles.get(excerpt)).generation()).isEqualTo(next);
    }
    @Test void lateClearOnlyClearsOwnGeneration() {
        var s=new MockHttpSession();String old=store.begin(s,actor),next=store.begin(s,actor);store.clearOwn(s,actor,old);
        String token=store.publish(s,actor,next,List.of(excerpt)).get(excerpt);assertThat(store.resolve(s,actor,token)).isNotNull();
        store.clearOwn(s,actor,next);fails(()->store.resolve(s,actor,token));
    }
    @Test void postIoRecheckRejectsGenerationExpiryAndForgedTicket() {
        var s=new MockHttpSession();String token=put(s);var ticket=store.resolve(s,actor,token);store.requireCurrent(s,actor,ticket);
        var other=quote("C1","伪造正文");fails(()->store.requireCurrent(s,actor,new RuleCheckCitationStore.Ticket(ticket.generation(),token,other)));
        store.begin(s,actor);fails(()->store.requireCurrent(s,actor,ticket));
        var another=new MockHttpSession();String next=put(another);var saved=store.resolve(another,actor,next);clock.now=Instant.EPOCH.plusSeconds(300);fails(()->store.requireCurrent(another,actor,saved));
    }
    @Test void invalidatedSessionCannotPublishResolveOrRestoreReferences() {
        var s=new MockHttpSession();String token=put(s);var ticket=store.resolve(s,actor,token);s.invalidate();
        fails(()->store.resolve(s,actor,token));fails(()->store.requireCurrent(s,actor,ticket));fails(()->store.begin(s,actor));
        assertThatCode(()->store.clearOwn(s,actor,ticket.generation())).doesNotThrowAnyException();
    }
    static final class MutableClock extends Clock {
        Instant now=Instant.EPOCH;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
}
