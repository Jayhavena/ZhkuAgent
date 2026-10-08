package com.zhku.knowledge.conversation;

import java.sql.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 用分阶段JDBC替身检查写事实及物理名额；不冒充真实数据库/断网测试。 */
@Timeout(15)
class OwnedConversationStoreFailureTest {
    @Test void commitAckFailureIsUnknownEvenWhenRollbackReturnsNormally() throws Exception {
        try (var fixture = new OwnedConversationStoreContractTest.Fixture()) {
            fixture.rows(OwnedConversationStoreContractTest.INITIAL);
            doThrow(new SQLException("synthetic secret SQL must not escape")).when(fixture.connection).commit();
            assertThatThrownBy(() -> fixture.store.create(OwnedConversationStoreContractTest.INITIAL))
                    .isInstanceOf(ConversationStoreFailure.class)
                    .hasMessage("CREATION_UNCONFIRMED").hasNoCause();
            verify(fixture.connection, times(1)).commit();
            verify(fixture.connection).rollback();
        }
    }

    @Test void preWriteConnectionFailureIsUnavailableWithoutRetry() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("synthetic password must not escape"));
        try (var access = new ConversationJdbcAccess(source)) {
            var store = new JdbcConversationStore(access, OwnedConversationStoreContractTest.ACTOR,
                    OwnedConversationStoreContractTest.CLOCK);
            assertThatThrownBy(() -> store.create(OwnedConversationStoreContractTest.INITIAL))
                    .isInstanceOf(ConversationStoreFailure.class).hasMessage("STORE_UNAVAILABLE").hasNoCause();
            verify(source, times(1)).getConnection();
        }
    }

    @Test void confirmedCommitSurvivesCloseFailureButSealsNewAdmission() throws Exception {
        try (var fixture = new OwnedConversationStoreContractTest.Fixture()) {
            fixture.rows(OwnedConversationStoreContractTest.INITIAL);
            doThrow(new SQLException("synthetic close failure")).when(fixture.connection).close();
            when(fixture.connection.isClosed()).thenReturn(true);
            assertThat(fixture.store.create(OwnedConversationStoreContractTest.INITIAL).outcome())
                    .isEqualTo(StoreResult.Outcome.CREATED);
            assertThat(fixture.access.status().resourceFailure()).isTrue();
            assertThatThrownBy(() -> fixture.store.find(OwnedConversationStoreContractTest.ID,
                    OwnedConversationStoreContractTest.CLOCK)).hasMessage("STORE_UNAVAILABLE");
        }
    }

    @Test void writeFailureWithConfirmedRollbackIsUnavailableAndFailedRollbackIsUnknown() throws Exception {
        for (boolean failedRollback : new boolean[] {false, true}) {
            var source = mock(DataSource.class);
            var connection = mock(Connection.class);
            when(source.getConnection()).thenReturn(connection);
            if (failedRollback) doThrow(new SQLException("rollback failure")).when(connection).rollback();
            try (var access = new ConversationJdbcAccess(source)) {
                assertThatThrownBy(() -> access.execute(new ConversationOperationBudget(), ConversationJdbcAccess.WriteKind.WRITE,
                        transaction -> { transaction.writeIssued(); throw new SQLException("write failure"); }))
                        .hasMessage(failedRollback ? "WRITE_UNCONFIRMED" : "STORE_UNAVAILABLE");
                verify(connection).rollback();
                verify(connection, never()).commit();
            }
        }
    }

    @Test void blockedConnectionsKeepBothSlotsAfterHostTimeoutAndClose() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var physical = ConcurrentHashMap.<Thread>newKeySet();
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(invocation -> {
            physical.add(Thread.currentThread());
            calls.incrementAndGet();
            entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            throw new SQLException("synthetic blocked connection");
        });
        var access = new ConversationJdbcAccess(source);
        var callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> unavailable(access));
            var second = callers.submit(() -> unavailable(access));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            long began = System.nanoTime();
            assertThat(unavailable(access)).isEqualTo(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofMillis(425));
            assertThat(first.get(2250, TimeUnit.MILLISECONDS)).isEqualTo(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
            assertThat(second.get(2250, TimeUnit.MILLISECONDS)).isEqualTo(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
            assertThat(access.status().inFlight()).isEqualTo(2);
            assertThat(unavailable(access)).isEqualTo(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
            assertThat(calls).hasValue(2);
            began = System.nanoTime();
            access.close(Duration.ofSeconds(2));
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofMillis(2250));
            assertThat(access.status().inFlight()).isEqualTo(2);
        } finally {
            release.countDown();
            access.close(Duration.ofSeconds(2));
            callers.shutdownNow();
            assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        for (var thread : physical) thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime())));
        assertThat(physical).hasSize(2);
        assertThat(physical.stream().anyMatch(Thread::isAlive)).isFalse();
        assertThat(access.status().inFlight()).isZero();
    }

    @Test void exhaustedRequestBudgetRefusesBeforeConnectionAndNeverRestartsDeadline() throws Exception {
        var source = mock(DataSource.class);
        try (var access = new ConversationJdbcAccess(source)) {
            var budget = new ConversationOperationBudget(Duration.ZERO);
            assertThatThrownBy(() -> access.execute(budget, ConversationJdbcAccess.WriteKind.NONE, tx -> null))
                    .hasMessage("STORE_UNAVAILABLE");
            verifyNoInteractions(source);
        }
    }

    @Test void exhaustedOperationCountFailsWithoutResettingRequestBudget() {
        var budget = new ConversationOperationBudget();
        for (int index = 0; index < 12; index++) budget.begin();
        assertThatThrownBy(budget::begin).hasMessage("STORE_UNAVAILABLE");
    }

    @Test void transactionSqlLimitFailsBeforeIssuingExtraStatement() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        try (var access = new ConversationJdbcAccess(source)) {
            assertThatThrownBy(() -> access.execute(new ConversationOperationBudget(), ConversationJdbcAccess.WriteKind.NONE,
                    transaction -> {
                        for (int index = 0; index < 6; index++) {
                            try (var query = transaction.prepare("SELECT 1")) { query.execute(); }
                        }
                        return null;
                    })).hasMessage("STORE_UNAVAILABLE");
            verify(connection, times(5)).prepareStatement("SELECT 1");
            verify(connection, never()).commit();
            verify(connection).rollback();
        }
    }

    @Test void interruptedCallerKeepsInterruptFlagAndDoesNotStartIo() throws Exception {
        var source = mock(DataSource.class);
        try (var access = new ConversationJdbcAccess(source)) {
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> access.execute(new ConversationOperationBudget(), ConversationJdbcAccess.WriteKind.NONE,
                        transaction -> null)).hasMessage("STORE_UNAVAILABLE");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                verifyNoInteractions(source);
            } finally { Thread.interrupted(); }
        }
    }

    private static ConversationStoreFailure.Code unavailable(ConversationJdbcAccess access) {
        try {
            access.execute(new ConversationOperationBudget(), ConversationJdbcAccess.WriteKind.NONE, transaction -> null);
            throw new AssertionError("应安全失败");
        } catch (ConversationStoreFailure expected) { return expected.code(); }
    }
}
