package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.security.TrustedActor;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.*;

class RuleCheckClarificationStoreTest {
    final MutableClock clock=new MutableClock();final RuleCheckClarificationStore store=new RuleCheckClarificationStore(clock);
    final TrustedActor actor=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
    RuleCheckScopedResult.Continuation context() {
        var condition=new Condition("type","类型",List.of("ordinary","other"));
        var rule=new RuleCheckRuleTemplate("synthetic","v1",ReviewState.APPROVED,"合成责任",LocalDate.of(2026,10,8),List.of(),List.of(condition),List.of(),List.of());
        return new RuleCheckScopedResult.Continuation(new RuleCheckRuntimeSettings("fixture","cut","scope","v1",20,5),
            new RuleCheckApplicationService.CatalogEntry(rule,"合成查询范围"),"原问题",Map.of(),condition);
    }
    RuleCheckScopedResult.Continuation take(MockHttpSession session,String token) {
        var value=new AtomicReference<RuleCheckScopedResult.Continuation>();
        assertThatCode(()->value.set(store.consume(session,actor,token,"原问题",Map.of("type","ordinary")))).doesNotThrowAnyException();return value.get();
    }
    @Test void storesOneOpaqueContextAndConsumesOnlyOnce() {
        var session=new MockHttpSession();var original=context();var token=store.put(session,actor,original);
        assertThat(UUID.fromString(token)).isNotNull();assertThat(Collections.list(session.getAttributeNames())).hasSize(1);
        assertThat(take(session,token)).isEqualTo(original);assertThat(Collections.list(session.getAttributeNames())).isEmpty();
        assertThatThrownBy(()->store.consume(session,actor,token,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
    }
    @Test void newQuestionReplacesPreviousSingleContext() {
        var session=new MockHttpSession();var old=store.put(session,actor,context());var next=store.put(session,actor,context());assertThat(next).isNotEqualTo(old);
        assertThatThrownBy(()->store.consume(session,actor,old,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        assertThat(take(session,next)).isNotNull();
    }
    @Test void expiresAtExactFiveMinuteBoundaryWithoutSlidingRefresh() {
        var first=new MockHttpSession();var token=store.put(first,actor,context());clock.now=Instant.EPOCH.plusSeconds(299);assertThat(take(first,token)).isNotNull();
        clock.now=Instant.EPOCH;var expired=new MockHttpSession();token=store.put(expired,actor,context());clock.now=Instant.EPOCH.plusSeconds(300);var finalToken=token;
        assertThatThrownBy(()->store.consume(expired,actor,finalToken,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        assertThat(Collections.list(expired.getAttributeNames())).isEmpty();
    }
    @Test void invalidConditionCanBeCorrectedWithoutConsumingOrExtendingExpiry() {
        var session=new MockHttpSession();var token=store.put(session,actor,context());clock.now=Instant.EPOCH.plusSeconds(250);
        for(var delta:List.of(Map.<String,String>of(),Map.of("wrong","ordinary"),Map.of("type","invalid"),Map.of("type","ordinary","other","value")))
            assertThatThrownBy(()->store.consume(session,actor,token,"原问题",delta)).isInstanceOf(IllegalArgumentException.class);
        assertThat(take(session,token)).isNotNull();
        clock.now=Instant.EPOCH;var finalToken=store.put(session,actor,context());clock.now=Instant.EPOCH.plusSeconds(299);
        assertThatThrownBy(()->store.consume(session,actor,finalToken,"另一个问题",Map.of("type","ordinary"))).isInstanceOf(IllegalArgumentException.class);
        clock.now=Instant.EPOCH.plusSeconds(300);
        assertThatThrownBy(()->store.consume(session,actor,finalToken,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
    }
    @Test void differentActorOrRevisionCannotReuseStoredContext() {
        for(var other:List.of(new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0),new TrustedActor(actor.userId(),TrustedActor.Role.USER,1),new TrustedActor(actor.userId(),TrustedActor.Role.MAINTAINER,0))) {
            var session=new MockHttpSession();var token=store.put(session,actor,context());
            assertThatThrownBy(()->store.consume(session,other,token,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
            assertThat(Collections.list(session.getAttributeNames())).isEmpty();
        }
    }
    @Test void wrongTokenDoesNotDestroyCorrectOwnersCurrentQuestion() {
        var session=new MockHttpSession();var token=store.put(session,actor,context());
        assertThatThrownBy(()->store.consume(session,actor,UUID.randomUUID().toString(),"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        assertThat(take(session,token)).isNotNull();
    }
    @Test void clearAndInvalidatedSessionCannotRestoreContext() {
        var session=new MockHttpSession();var token=store.put(session,actor,context());assertThat(Collections.list(session.getAttributeNames())).hasSize(1);store.clear(session);
        assertThatThrownBy(()->store.consume(session,actor,token,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        var finalToken=store.put(session,actor,context());session.invalidate();
        assertThatThrownBy(()->store.consume(session,actor,finalToken,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
    }
    String finished(MockHttpSession session,String generation,RuleCheckScopedResult.Continuation context) {
        var result=new AtomicReference<String>();assertThatCode(()->result.set(store.finish(session,actor,generation,context))).doesNotThrowAnyException();return result.get();
    }
    @Test void earlierSlowCompletionCannotOverwriteNewerQuestion() {
        var session=new MockHttpSession();var old=store.begin(session,actor);var current=store.begin(session,actor);
        var token=finished(session,current,context());
        assertThatThrownBy(()->store.finish(session,actor,old,context())).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        assertThatThrownBy(()->store.finish(session,actor,old,null)).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        assertThat(take(session,token)).isNotNull();
    }
    @Test void consumingAndStartingNextGenerationIsAtomicAndCannotReplayOldToken() {
        var session=new MockHttpSession();var token=store.put(session,actor,context());
        var ticket=new AtomicReference<RuleCheckClarificationStore.Ticket>();
        assertThatCode(()->ticket.set(store.consumeAndBegin(session,actor,token,"原问题",Map.of("type","ordinary")))).doesNotThrowAnyException();
        assertThat(ticket.get().context()).isEqualTo(context());
        assertThatThrownBy(()->store.consumeAndBegin(session,actor,token,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        assertThat(finished(session,ticket.get().generation(),null)).isNull();
        assertThat(Collections.list(session.getAttributeNames())).isEmpty();
    }
    @Test void startingNewQuestionInvalidatesOldTokenBeforeWorkCompletes() {
        var session=new MockHttpSession();var token=store.put(session,actor,context());var generation=store.begin(session,actor);
        assertThatThrownBy(()->store.consume(session,actor,token,"原问题",Map.of("type","ordinary"))).isInstanceOf(RuleCheckClarificationStore.Failure.class);
        clock.now=Instant.EPOCH.plusSeconds(300);
        assertThatThrownBy(()->store.finish(session,actor,generation,context())).isInstanceOf(RuleCheckClarificationStore.Failure.class);
    }
    static final class MutableClock extends Clock {
        Instant now=Instant.EPOCH;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
}
