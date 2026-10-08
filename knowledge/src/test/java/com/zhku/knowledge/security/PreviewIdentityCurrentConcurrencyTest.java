package com.zhku.knowledge.security;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实身份执行器和受控 JDBC 屏障；不是实际数据库/供应商效果证明。 */
@Timeout(15)
class PreviewIdentityCurrentConcurrencyTest {
    private static final TrustedActor A = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
    private static final TrustedActor B = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);

    @Test void sameIdentityWaitsAndEveryCallerPerformsFreshQuery() throws Exception {
        try (var f = new Fixture(false)) {
            var first=f.call(A);var second=f.call(A);f.entered();
            var late=f.call(A);assertThatThrownBy(()->late.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(f.opened).hasValue(2);f.release.countDown();
            assertThat(first.get(2250,TimeUnit.MILLISECONDS)).isNull();assertThat(second.get(2250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(late.get(2250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(f.queries).hasValue(3);assertThat(f.maximumPhysical.get()).isLessThanOrEqualTo(2);
        }
    }

    @Test void distinctUsersAndRoleAreNotShared() throws Exception {
        try(var f=new Fixture(false)) {
            var first=f.call(A);var second=f.call(A);f.entered();
            var validB=f.call(B);var wrongRole=f.call(new TrustedActor(B.userId(),TrustedActor.Role.MAINTAINER,0));
            assertThatThrownBy(()->validB.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            f.release.countDown();first.get(2250,TimeUnit.MILLISECONDS);second.get(2250,TimeUnit.MILLISECONDS);
            assertThat(validB.get(2250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(wrongRole.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
            assertThat(f.queries).hasValue(4);
        }
    }

    @Test void lateJoinChecksChangedRevisionAndNeverCachesCompletedPermission() throws Exception {
        try(var f=new Fixture(false)) {
            var first=f.call(A);var second=f.call(A);f.entered();var late=f.call(A);
            assertThatThrownBy(()->late.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            f.rows.put(A.userId(),new Row("USER",1,true));f.release.countDown();
            first.get(2250,TimeUnit.MILLISECONDS);second.get(2250,TimeUnit.MILLISECONDS);
            assertThat(late.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
            assertThat(f.call(A).get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
            assertThat(f.call(new TrustedActor(A.userId(),TrustedActor.Role.USER,1)).get(2250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(f.queries).hasValue(5);
        }
    }

    @Test void disabledAccountCannotBorrowEarlierPermission() throws Exception {
        try(var f=new Fixture(false)) {
            var first=f.call(A);var second=f.call(A);f.entered();var late=f.call(A);
            assertThatThrownBy(()->late.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            f.rows.put(A.userId(),new Row("USER",0,false));f.release.countDown();
            first.get(2250,TimeUnit.MILLISECONDS);second.get(2250,TimeUnit.MILLISECONDS);
            assertThat(late.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
            assertThat(f.call(A).get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
            assertThat(f.queries).hasValue(4);
        }
    }

    @Test void cancellingOneWaitingCallerDoesNotInterruptOtherQueriesOrReleasePhysicalSlots() throws Exception {
        try(var f=new Fixture(false)) {
            var first=f.call(A);var second=f.call(A);f.entered();
            var started=new CountDownLatch(1);var exited=new CountDownLatch(1);var code=new AtomicReference<PreviewIdentityRepository.Code>();
            var caller=new Thread(()->{started.countDown();try{code.set(f.check(A));}finally{exited.countDown();}},"identity-test-cancel-waiter");
            caller.start();assertThat(started.await(425,TimeUnit.MILLISECONDS)).isTrue();
            assertThat(exited.await(100,TimeUnit.MILLISECONDS)).isFalse();caller.interrupt();
            assertThat(exited.await(425,TimeUnit.MILLISECONDS)).isTrue();caller.join(425);
            assertThat(code.get()).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(f.repository.status().inFlight()).isEqualTo(2);assertThat(f.opened).hasValue(2);
            var remaining=f.call(B);f.release.countDown();
            assertThat(first.get(2250,TimeUnit.MILLISECONDS)).isNull();assertThat(second.get(2250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(remaining.get(2250,TimeUnit.MILLISECONDS)).isNull();assertThat(f.queries).hasValue(3);
        }
    }

    @Test void waitingIsBoundedTo32AndPasswordsRemainFailFast() throws Exception {
        try(var f=new Fixture(false)) {
            f.call(A);f.call(A);f.entered();var waiting=new ArrayList<Future<PreviewIdentityRepository.Code>>();
            var ready=new CountDownLatch(32);var waitingThreads=ConcurrentHashMap.<Thread>newKeySet();
            for(int i=0;i<32;i++)waiting.add(f.callers.submit(()->{waitingThreads.add(Thread.currentThread());ready.countDown();return f.check(A);}));
            assertThat(ready.await(425,TimeUnit.MILLISECONDS)).isTrue();
            long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(425);
            while(waitingThreads.stream().anyMatch(t->t.getState()!=Thread.State.TIMED_WAITING)&&System.nanoTime()<until)Thread.sleep(1);
            assertThat(waiting).allMatch(x->!x.isDone());
            long began=System.nanoTime();assertThat(f.check(B)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(425));
            began=System.nanoTime();char[] password="Synthetic-valid-password".toCharArray();
            assertThatThrownBy(()->f.repository.authenticate("user-a",password)).isInstanceOf(PreviewIdentityRepository.Failure.class);
            assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(425));
            assertThat(password).containsOnly('\0');assertThat(f.opened).hasValue(2);
            f.release.countDown();for(var w:waiting)assertThat(w.get(2250,TimeUnit.MILLISECONDS)).isNull();
            assertThat(f.queries).hasValue(34);assertThat(f.maximumPhysical.get()).isLessThanOrEqualTo(2);
        }
    }

    @Test void closeWakesWaitingCallersButRetainsBlockedQuerySlotsUntilPhysicalExit() throws Exception {
        closeWithBlockedIo(false);
    }

    @Test void connectionCloseCannotBeMistakenForPhysicalRelease() throws Exception {
        closeWithBlockedIo(true);
    }

    private void closeWithBlockedIo(boolean blockClose) throws Exception {
        try(var f=new Fixture(blockClose)) {
            var first=f.call(A);var second=f.call(A);f.entered();var waiting=f.call(B);
            assertThatThrownBy(()->waiting.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            long began=System.nanoTime();f.repository.close(Duration.ZERO);
            assertThat(waiting.get(425,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(425));
            assertThat(first.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(second.get(2250,TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(f.repository.status().inFlight()).isEqualTo(2);assertThat(f.physical).hasValue(2);assertThat(f.opened).hasValue(2);
            assertThat(f.check(A)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            f.release.countDown();
        }
    }

    private record Row(String role,long revision,boolean enabled) { }
    private static final class Fixture implements AutoCloseable {
        final CountDownLatch held=new CountDownLatch(2),release=new CountDownLatch(1);
        final AtomicInteger opened=new AtomicInteger(),queries=new AtomicInteger(),physical=new AtomicInteger(),maximumPhysical=new AtomicInteger();
        final Map<UUID,Row> rows=new ConcurrentHashMap<>();
        final ExecutorService callers=Executors.newFixedThreadPool(34);
        final PreviewIdentityRepository repository;
        Fixture(boolean blockClose)throws Exception {
            rows.put(A.userId(),new Row("USER",0,true));rows.put(B.userId(),new Row("USER",0,true));
            var source=mock(DataSource.class);
            // Mockito装配不属于数据库行为，预先准备有限连接，避免装配耗时占用生产2秒预算。
            var connections=new ArrayList<Connection>();
            for(int i=1;i<=36;i++) {
                final int number=i;
                var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);var id=new AtomicReference<UUID>();
                var result=mock(ResultSet.class);var snapshot=new AtomicReference<Row>();
                when(result.next()).thenAnswer(x->snapshot.get()!=null);
                when(result.getString(1)).thenAnswer(x->snapshot.get().role());
                when(result.getLong(2)).thenAnswer(x->snapshot.get().revision());
                when(result.getBoolean(3)).thenAnswer(x->snapshot.get().enabled());
                when(connection.prepareStatement(anyString())).thenReturn(statement);
                doAnswer(x->{id.set(x.getArgument(1));return null;}).when(statement).setObject(eq(1),any());
                when(statement.executeQuery()).thenAnswer(x->{
                    queries.incrementAndGet();snapshot.set(rows.get(id.get()));
                    if(number<=2&&!blockClose){held.countDown();uncooperativeWait();}
                    return result;
                });
                doAnswer(x->{if(number<=2&&blockClose){held.countDown();uncooperativeWait();}physical.decrementAndGet();return null;}).when(connection).close();
                connections.add(connection);
            }
            when(source.getConnection()).thenAnswer(inv->{
                int number=opened.incrementAndGet();maximumPhysical.accumulateAndGet(physical.incrementAndGet(),Math::max);
                return connections.get(number-1);
            });repository=new PreviewIdentityRepository(source);
        }
        void uncooperativeWait(){while(release.getCount()!=0){try{release.await();}catch(InterruptedException ignored){}}}
        void entered()throws Exception {assertThat(held.await(2,TimeUnit.SECONDS)).isTrue();}
        PreviewIdentityRepository.Code check(TrustedActor actor){try{repository.requireCurrent(actor);return null;}catch(PreviewIdentityRepository.Failure e){return e.code();}}
        Future<PreviewIdentityRepository.Code> call(TrustedActor actor){return callers.submit(()->check(actor));}
        @Override public void close()throws Exception {
            release.countDown();repository.close(Duration.ofSeconds(2));callers.shutdownNow();assertThat(callers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(repository.status().liveThreads()!=0&&System.nanoTime()<until)Thread.sleep(1);
            assertThat(repository.status().liveThreads()).isZero();assertThat(repository.status().inFlight()).isZero();assertThat(physical).hasValue(0);
        }
    }
}
