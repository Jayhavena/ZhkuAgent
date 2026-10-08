package com.zhku.knowledge.controlled;

import com.zhku.knowledge.conversation.*;
import com.zhku.knowledge.conversation.elective.*;
import com.zhku.knowledge.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际Session/来源SQL与四层领域链；不是HTTP或供应商验收。 */
@Timeout(30)
class ControlledConversationFacadeTest {
    @Test void rebuiltFacadeContinuesOnlyThePersistedRetakeClarification() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            assertThat(f.facade.sendMessage(f.a, state.sessionId(), 0, "选修课挂科了可以重修吗？").type())
                    .isEqualTo(ElectiveRetakeReplyType.NEED_CLARIFICATION);
            var pending = f.facade.findConversation(f.a, state.sessionId());
            assertThat(pending.pendingIntent()).isEqualTo(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE);
            assertThat(pending.revision()).isEqualTo(1);
            var rebuilt = new ControlledConversationFacade(f.access, f.identities, f.clock);
            clearInvocations(f.access);
            var answer = rebuilt.sendMessage(f.a, state.sessionId(), 1, "专业选修课");
            var budgets=org.mockito.ArgumentCaptor.forClass(ConversationOperationBudget.class);
            verify(f.access,atLeastOnce()).execute(budgets.capture(),any(),any());
            assertThat(budgets.getAllValues()).hasSizeLessThanOrEqualTo(12)
                    .allSatisfy(budget->assertThat(budget).isSameAs(budgets.getAllValues().get(0)));
            assertThat(answer.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
            assertThat(answer.answer().sourceId()).isEqualTo(f.retake);
            // 原规则答复把来源快照放在answer中；citation列表保留原Facade语义，不扩接口。
            assertThat(answer.answer().sourceVersion()).isEqualTo("synthetic-retake-v1");
            assertThat(answer.answer().sourceLocator()).isEqualTo("合成重修第1条");
            assertThat(answer.citations()).isEmpty();
            assertThat(answer.guideCard()).isNull();
            var cleared = rebuilt.findConversation(f.a, state.sessionId());
            assertThat(cleared.pendingIntent()).isNull(); assertThat(cleared.revision()).isEqualTo(2);
        }
    }
    @Test void otherUserAnonymousRowsAndUnknownIdsNeverEnterImplicitCreation() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            assertThatThrownBy(() -> f.facade.sendMessage(f.b, state.sessionId(), 0, "选修课挂科重修"))
                    .hasMessage("SESSION_NOT_FOUND");
            UUID unknown = UUID.randomUUID();
            assertThatThrownBy(() -> f.facade.sendMessage(f.a, unknown, 0, "选修课挂科重修"))
                    .hasMessage("SESSION_NOT_FOUND");
            new JdbcConversationStore(f.data.source()).create(ConversationState.active(unknown, f.clock.instant().plusSeconds(600)));
            assertThatThrownBy(() -> f.facade.findConversation(f.a, unknown)).hasMessage("SESSION_NOT_FOUND");
            sql(f, "DELETE FROM conversation_session WHERE session_id=?", unknown);
            assertThat(f.facade.findConversation(f.a, state.sessionId()).revision()).isZero();
        }
    }
    @Test void readCancelAndRejectedWritesNeverExtendTtlOrReviveState() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a); f.clock.now = f.clock.now.plusSeconds(20);
            assertThat(f.facade.findConversation(f.a, state.sessionId()).expiresAt()).isEqualTo(state.expiresAt());
            var cancelled = f.facade.cancelConversation(f.a, state.sessionId(), 0);
            assertThat(cancelled.status()).isEqualTo(ConversationStatus.CANCELLED);
            assertThat(cancelled.expiresAt()).isEqualTo(state.expiresAt());
            assertThat(f.facade.cancelConversation(f.a, state.sessionId(), 0)).isEqualTo(cancelled);
            assertThat(f.facade.findConversation(f.a, state.sessionId())).isEqualTo(cancelled);
            assertThatThrownBy(() -> f.facade.sendMessage(f.a, state.sessionId(), 0, "专业选修课"))
                    .hasMessage("SESSION_CANCELLED");
            f.clock.now = state.expiresAt();
            assertThatThrownBy(() -> f.facade.findConversation(f.a, state.sessionId())).hasMessage("SESSION_EXPIRED");
        }
    }
    @Test void staleRevisionAndInvalidMessageFailBeforeAnyMutation() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            assertThatThrownBy(() -> f.facade.sendMessage(f.a, state.sessionId(), 1, "选修课挂科重修"))
                    .hasMessage("SESSION_STATE_CHANGED");
            for (String text : List.of(" ", "字".repeat(501)))
                assertThatThrownBy(() -> f.facade.sendMessage(f.a, state.sessionId(), 0, text)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> f.facade.cancelConversation(f.a, state.sessionId(), -1)).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.facade.findConversation(f.a, state.sessionId()).revision()).isZero();
        }
    }
    @Test void unsupportedCreditNoticeAdjustmentAndGeneralQuestionsCannotAcquireCapabilities() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            for (String question : List.of("公选课每学期最高多少学分？", "这学期什么时候选课？", "怎么退课？", "介绍校园")) {
                var answer = f.facade.sendMessage(f.a, state.sessionId(), 0, question);
                assertThat(answer.answer()).isNull(); assertThat(answer.citations()).isEmpty();
                assertThat(answer.handoffRequired()).isTrue(); assertThat(answer.guideCard()).isNull();
            }
            assertThat(f.facade.findConversation(f.a, state.sessionId()).revision()).isZero();
        }
    }
    @Test void withdrawnOriginalCitationReturnsNoBodyWithoutUndoingConfirmedClear() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            f.facade.sendMessage(f.a, state.sessionId(), 0, "选修课挂科重修");
            afterEvidence(f, () -> sql(f, "UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false WHERE id=?", f.retake));
            var answer = f.facade.sendMessage(f.a, state.sessionId(), 1, "专业选修课");
            assertThat(answer.type()).isEqualTo(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE);
            assertThat(answer.answer()).isNull(); assertThat(answer.citations()).isEmpty(); assertThat(answer.citationIds()).isEmpty();
            assertThat(answer.handoffRequired()).isTrue();
            var cleared = f.facade.findConversation(f.a, state.sessionId());
            assertThat(cleared.pendingIntent()).isNull(); assertThat(cleared.revision()).isEqualTo(2);
        }
    }
    @Test void cancellationDuringEvidenceDiscardsAnswerAndDoesNotOverwriteNewRevision() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            afterEvidence(f, () -> sql(f, "UPDATE conversation_session SET status='CANCELLED',revision=revision+1 WHERE session_id=?", state.sessionId()));
            assertThatThrownBy(() -> f.facade.sendMessage(f.a, state.sessionId(), 0, "专业选修课挂科重修"))
                    .hasMessage("SESSION_CANCELLED");
            assertThat(f.facade.findConversation(f.a, state.sessionId()).revision()).isEqualTo(1);
        }
    }
    @Test void revocationDuringEvidenceDiscardsAnswerAndOldActorCannotReturn() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            afterEvidence(f, () -> sql(f, "UPDATE preview_identity SET enabled=false,auth_revision=auth_revision+1 WHERE user_id=?", f.a.userId()));
            assertThatThrownBy(() -> f.facade.sendMessage(f.a, state.sessionId(), 0, "专业选修课挂科重修"))
                    .hasMessage("AUTH_REVOKED");
        }
    }
    @Test void maintainerAndUnavailableDatabaseFailWithoutMemoryFallback() throws Exception {
        try (var f = new Fixture()) {
            var maintainer = new TrustedActor(f.data.maintainer().id(), TrustedActor.Role.MAINTAINER, 0);
            assertThatThrownBy(() -> f.facade.createConversation(maintainer)).hasMessage("ROLE_DENIED");
            f.access.close();
            assertThatThrownBy(() -> f.facade.createConversation(f.a)).hasMessage("STORE_UNAVAILABLE");
        }
    }
    @Test void exactOriginalVersionLocatorScopeAndRetakeFamilyAreRechecked() throws Exception {
        try (var f = new Fixture()) {
            var finder = new JdbcElectiveRetakeEvidenceFinder(f.access,new ConversationOperationBudget());
            var evidence = finder.find(CourseType.PROFESSIONAL_ELECTIVE).orElseThrow();
            var answer = new ElectiveRetakeAnswer("结论","依据",evidence.sourceId(),evidence.sourceTitle(),evidence.sourceVersion(),
                    evidence.sourceLocator(),evidence.applicableScope());
            assertThat(finder.isCurrentlyVisible(answer)).isTrue();
            for (String[] change : List.of(new String[]{"source_version","changed-v2","synthetic-retake-v1"},
                    new String[]{"publication_scope","其他范围","合成本科生"},
                    new String[]{"document_family","OTHER","STUDENT_HANDBOOK"},
                    new String[]{"knowledge_scope","PUBLIC_ELECTIVE_CREDIT_LIMIT","ELECTIVE_RETAKE"})) {
                sql(f,"UPDATE knowledge_source SET "+change[0]+"=? WHERE id=?",change[1],f.retake);
                assertThat(new JdbcElectiveRetakeEvidenceFinder(f.access,new ConversationOperationBudget()).isCurrentlyVisible(answer)).isFalse();
                sql(f,"UPDATE knowledge_source SET "+change[0]+"=? WHERE id=?",change[2],f.retake);
            }
            sql(f,"UPDATE knowledge_chunk SET source_locator='其他条' WHERE source_id=?",f.retake);
            assertThat(new JdbcElectiveRetakeEvidenceFinder(f.access,new ConversationOperationBudget()).isCurrentlyVisible(answer)).isFalse();
        }
    }
    @Test void midRequestRevisionCompetitionAndExpiryDiscardGeneratedAnswer() throws Exception {
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            afterEvidence(f, () -> sql(f,"UPDATE conversation_session SET revision=revision+1 WHERE session_id=?",state.sessionId()));
            assertThatThrownBy(() -> f.facade.sendMessage(f.a,state.sessionId(),0,"专业选修课挂科重修"))
                    .hasMessage("SESSION_STATE_CHANGED");
        }
        try (var f = new Fixture()) {
            var state = f.facade.createConversation(f.a);
            afterEvidence(f, () -> f.clock.now=state.expiresAt());
            assertThatThrownBy(() -> f.facade.sendMessage(f.a,state.sessionId(),0,"专业选修课挂科重修"))
                    .hasMessage("SESSION_EXPIRED");
        }
    }
    @Test void serverUuidCollisionUsesOneCandidateAndNeverReplacesOrReturnsConflictState() throws Exception {
        try(var f=new Fixture()) {
            var state=f.facade.createConversation(f.a);
            f.facade.sendMessage(f.a,state.sessionId(),0,"选修课挂科重修");
            var pending=f.facade.findConversation(f.a,state.sessionId());
            for(var actor:List.of(f.a,f.b)) {
                try(var ids=mockStatic(UUID.class,CALLS_REAL_METHODS)) {
                    ids.when(UUID::randomUUID).thenReturn(state.sessionId());
                    assertThatThrownBy(()->f.facade.createConversation(actor)).hasMessage("SESSION_CREATE_CONFLICT");
                    ids.verify(UUID::randomUUID,times(1));
                }
                assertThat(f.facade.findConversation(f.a,state.sessionId())).isEqualTo(pending);
            }
        }
    }
    @FunctionalInterface interface Action { void run() throws Exception; }
    static void afterEvidence(Fixture f, Action action) {
        var once = new AtomicBoolean();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (result instanceof Optional<?> optional && optional.orElse(null) instanceof ElectiveRetakeEvidence && once.compareAndSet(false, true))
                action.run();
            return result;
        }).when(f.access).execute(any(), any(), any());
    }
    static void sql(Fixture f, String sql, Object... arguments) throws Exception {
        try (var connection = f.data.source().getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(1); for (int i=0;i<arguments.length;i++) statement.setObject(i+1, arguments[i]); statement.executeUpdate();
        }
    }
    static final class MutableClock extends Clock {
        Instant now = Instant.now();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId ignored) { return this; }
        public Instant instant() { return now; }
    }
    static final class Fixture implements AutoCloseable {
        final ControlledCampusTestFixtures.Data data = ControlledCampusTestFixtures.seed();
        final UUID retake = UUID.randomUUID();
        final MutableClock clock = new MutableClock();
        final TrustedActor a = new TrustedActor(data.a().id(), TrustedActor.Role.USER, 0);
        final TrustedActor b = new TrustedActor(data.b().id(), TrustedActor.Role.USER, 0);
        final ConversationJdbcAccess access = spy(new ConversationJdbcAccess(data.source()));
        final PreviewIdentityRepository identities = new PreviewIdentityRepository(data.source());
        final ControlledConversationFacade facade = new ControlledConversationFacade(access, identities, clock);
        Fixture() throws Exception { seedRetake(data.source(), retake); }
        public void close() throws Exception {
            access.close(Duration.ofSeconds(2)); identities.close(Duration.ofSeconds(2));
            assertThat(access.status().inFlight()).isZero(); assertThat(access.status().liveThreads()).isZero();
            cleanupConversations(data, retake); ControlledCampusTestFixtures.cleanup(data);
        }
    }
    static void seedRetake(javax.sql.DataSource source, UUID id) throws Exception {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_source(id,title,source_type,source_version,publication_status,published_at,content_checksum,
                    visibility_scope,publication_scope,sharing_rights_confirmed,document_family,knowledge_scope)
                    VALUES(?,'合成重修规则','PDF','synthetic-retake-v1','PUBLISHED',clock_timestamp(),?,'STUDENT_VISIBLE',
                    '合成本科生',true,'STUDENT_HANDBOOK','ELECTIVE_RETAKE')
                    """)) {
                statement.setObject(1,id); statement.setString(2,"0".repeat(64)); statement.setQueryTimeout(1); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,content,content_checksum)
                    VALUES(?,?,0,'合成重修第1条','专业选修课成绩不合格者可申请重修学习。公共选修课不办理缓考，成绩不合格者不安排重修。',?)
                    """)) {
                statement.setObject(1,UUID.randomUUID()); statement.setObject(2,id); statement.setString(3,"0".repeat(64));
                statement.setQueryTimeout(1); statement.executeUpdate();
            }
            connection.commit();
        }
    }
    static void cleanupConversations(ControlledCampusTestFixtures.Data data, UUID retake) throws Exception {
        try (var connection = data.source().getConnection()) {
            for (var account : List.of(data.a(),data.b(),data.maintainer()))
                try (var statement = connection.prepareStatement("DELETE FROM conversation_session WHERE owner_user_id=?")) {
                    statement.setObject(1,account.id()); statement.setQueryTimeout(1); statement.executeUpdate();
                }
            for (String sql : List.of("DELETE FROM knowledge_chunk WHERE source_id=?", "DELETE FROM knowledge_source WHERE id=?"))
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setObject(1,retake); statement.setQueryTimeout(1); statement.executeUpdate();
                }
        }
    }
}
