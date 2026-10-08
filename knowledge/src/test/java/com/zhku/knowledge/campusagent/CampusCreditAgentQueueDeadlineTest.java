package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.knowledge.campusagent.archive.*;
import com.zhku.knowledge.campusagent.submission.*;
import com.zhku.knowledge.security.TrustedActor;
import com.zhku.knowledge.tools.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实2/16线程池、固定10/30秒单调时钟屏障；归档替身不证明数据库事务。 */
@Timeout(15)
class CampusCreditAgentQueueDeadlineTest {
    @Test void twoPerUserAndEighteenPerInstanceAreHeldUntilPhysicalCompletionButSameKeyReplays()throws Exception {
        try(var f=new Fixture()) {
            var keys=new ArrayList<UUID>();keys.add(UUID.randomUUID());keys.add(UUID.randomUUID());
            var first=f.submit(f.users.get(0),keys.get(0));f.submit(f.users.get(0),keys.get(1));f.entered();
            quota(()->f.submit(f.users.get(0),UUID.randomUUID()),"USER_IN_FLIGHT_LIMIT");
            assertThat(f.submit(f.users.get(0),keys.get(0)).claim().record().runId()).isEqualTo(first.claim().record().runId());
            for(int u=1;u<9;u++){f.submit(f.users.get(u),UUID.randomUUID());f.submit(f.users.get(u),UUID.randomUUID());}
            assertThat(f.worker.getQueue()).hasSize(16);quota(()->f.submit(f.users.get(9),UUID.randomUUID()),"INSTANCE_CAPACITY_EXCEEDED");
            assertThat(f.records).hasSize(18);assertThat(f.worker.getTaskCount()).isEqualTo(18);assertThat(f.calls).hasValue(2);
            f.manager.cancelOwned(f.users.get(0),first.claim().record().runId());
            assertThat(f.manager.findOwned(f.users.get(0),first.claim().record().runId()).terminated()).isFalse();
            quota(()->f.submit(f.users.get(0),UUID.randomUUID()),"USER_IN_FLIGHT_LIMIT");assertThat(f.budget.inFlight()).isEqualTo(18);
            f.release.countDown();f.awaitAll();assertThat(f.budget.inFlight()).isZero();
            f.submit(f.users.get(0),UUID.randomUUID());f.awaitAll();assertThat(f.budget.inFlight()).isZero();
        }
    }
    @Test void queuedCancellationRemovesActualTaskAndFinalizesExactlyOnceWithoutModelCall()throws Exception {
        try(var f=new Fixture()) {
            f.submit(f.users.get(0),UUID.randomUUID());f.submit(f.users.get(0),UUID.randomUUID());f.entered();
            var queued=f.submit(f.users.get(1),UUID.randomUUID());var id=queued.claim().record().runId();
            long began=System.nanoTime();f.manager.cancelOwned(f.users.get(1),id);
            assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(200));
            await(()->f.manager.findOwned(f.users.get(1),id).terminated(),425);
            assertThat(f.worker.getQueue()).isEmpty();assertThat(f.calls).hasValue(2);assertThat(f.finalized.get(id)).hasValue(1);
            assertThat(f.manager.findOwned(f.users.get(1),id).stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
            assertThat(f.budget.inFlight()).isEqualTo(2);f.timer.fire(Duration.ofSeconds(10));assertThat(f.finalized.get(id)).hasValue(1);
        }
    }
    @Test void queueTenSecondsAndTotalThirtyShareOriginAndNeverReleaseBlockedExecutionEarly()throws Exception {
        try(var f=new Fixture()) {
            var running=f.submit(f.users.get(0),UUID.randomUUID());f.submit(f.users.get(0),UUID.randomUUID());f.entered();
            var queued=f.submit(f.users.get(1),UUID.randomUUID());var id=queued.claim().record().runId();
            f.now.set(TimeUnit.SECONDS.toNanos(10));f.timer.fire(Duration.ofSeconds(10));
            await(()->f.manager.findOwned(f.users.get(1),id).terminated(),425);
            assertThat(f.worker.getQueue()).isEmpty();assertThat(f.calls).hasValue(2);
            assertThat(f.manager.findOwned(f.users.get(1),id).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
            assertThat(f.budget.inFlight()).isEqualTo(2);
            f.now.set(TimeUnit.SECONDS.toNanos(30));f.timer.fire(Duration.ofSeconds(30));
            assertThat(f.manager.findOwned(f.users.get(0),running.claim().record().runId()).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
            assertThat(f.manager.findOwned(f.users.get(0),running.claim().record().runId()).terminated()).isFalse();
            assertThat(f.worker.getActiveCount()).isEqualTo(2);assertThat(f.budget.inFlight()).isEqualTo(2);
            f.release.countDown();f.awaitAll();assertThat(f.calls).hasValue(2);assertThat(f.budget.inFlight()).isZero();
        }
    }
    @Test void lateTimerCannotAllowExpiredQueuedWorkToStartModel()throws Exception {
        try(var f=new Fixture()) {
            f.submit(f.users.get(0),UUID.randomUUID());f.submit(f.users.get(0),UUID.randomUUID());f.entered();
            var queued=f.submit(f.users.get(1),UUID.randomUUID());
            f.now.set(TimeUnit.SECONDS.toNanos(30));f.release.countDown();f.awaitAll();
            assertThat(f.calls).hasValue(2);
            assertThat(f.manager.findOwned(f.users.get(1),queued.claim().record().runId()).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
        }
    }
    @Test void dequeueAndExpiryRaceNeverDoubleFinalizeOrStartExpiredModel()throws Exception {
        for(int sample=0;sample<40;sample++)try(var f=new Fixture()) {
            f.submit(f.users.get(0),UUID.randomUUID());f.submit(f.users.get(0),UUID.randomUUID());f.entered();
            var queued=f.submit(f.users.get(1),UUID.randomUUID());var id=queued.claim().record().runId();
            var go=new CountDownLatch(1);f.now.set(TimeUnit.SECONDS.toNanos(10));
            try(var racers=Executors.newFixedThreadPool(2)) {
                var expiry=racers.submit(()->{go.await();f.timer.fire(Duration.ofSeconds(10));return null;});
                var dequeue=racers.submit(()->{go.await();f.release.countDown();return null;});go.countDown();
                expiry.get(2,TimeUnit.SECONDS);dequeue.get(2,TimeUnit.SECONDS);
            }
            f.awaitAll();assertThat(f.calls).hasValue(2);assertThat(f.finalized.get(id)).hasValue(1);
            assertThat(f.manager.findOwned(f.users.get(1),id).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
        }
    }
    @Test void expiryBeforeExecuteAcceptsQueueIsRemovedAfterActualEnqueue()throws Exception {
        var before=new CountDownLatch(1);var enqueue=new CountDownLatch(1);var executions=new AtomicInteger();
        var worker=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16)) {
            @Override public void execute(Runnable task) {
                if(executions.incrementAndGet()==3){before.countDown();try{assertThat(enqueue.await(2,TimeUnit.SECONDS)).isTrue();}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new RejectedExecutionException();}}
                super.execute(task);
            }
        };
        try(var f=new Fixture(null,null,worker);var submitter=Executors.newSingleThreadExecutor()) {
            try {
                f.submit(f.users.get(0),UUID.randomUUID());f.submit(f.users.get(0),UUID.randomUUID());f.entered();
                var key=UUID.randomUUID();var future=submitter.submit(()->f.submit(f.users.get(1),key));
                assertThat(before.await(2,TimeUnit.SECONDS)).isTrue();f.now.set(TimeUnit.SECONDS.toNanos(10));f.timer.fire(Duration.ofSeconds(10));
                enqueue.countDown();var submitted=future.get(2,TimeUnit.SECONDS);
                await(()->f.manager.findOwned(f.users.get(1),submitted.claim().record().runId()).terminated(),425);
                assertThat(worker.getQueue()).isEmpty();assertThat(f.calls).hasValue(2);
            }finally{enqueue.countDown();}
        }
    }

    @Test @Timeout(45) void realTimerWiresTenSecondQueueAndThirtySecondStopWithoutClaimingPhysicalExit()throws Exception {
        var timer=new ScheduledThreadPoolExecutor(1);timer.setRemoveOnCancelPolicy(true);
        try(var f=new Fixture(timer,System::nanoTime)) {
            var first=f.submit(f.users.get(0),UUID.randomUUID());f.submit(f.users.get(0),UUID.randomUUID());f.entered();
            var queued=f.submit(f.users.get(1),UUID.randomUUID());long queuedAt=System.nanoTime();
            await(()->f.manager.findOwned(f.users.get(1),queued.claim().record().runId()).terminated(),10500);
            assertThat(Duration.ofNanos(System.nanoTime()-queuedAt)).isBetween(Duration.ofMillis(9500),Duration.ofMillis(10500));
            assertThat(f.calls).hasValue(2);assertThat(f.budget.inFlight()).isEqualTo(2);assertThat(f.worker.getQueue()).isEmpty();
            await(()->f.manager.findOwned(f.users.get(0),first.claim().record().runId()).stopReason()==AgentStopReason.RUN_TIMEOUT,20500);
            assertThat(f.manager.findOwned(f.users.get(0),first.claim().record().runId()).terminated()).isFalse();
            assertThat(f.worker.getActiveCount()).isEqualTo(2);assertThat(f.budget.inFlight()).isEqualTo(2);
            f.release.countDown();f.awaitAll();assertThat(f.budget.inFlight()).isZero();
            System.out.println("3C-A7 realQueueDeadline10s=true totalStop30s=true blockedPhysical2=true actualExitAfterRelease=true");
        }finally{timer.shutdownNow();assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
    }
    private static void quota(Runnable r,String code){assertThatThrownBy(r::run).isInstanceOf(CampusRunSubmission.Failure.class)
            .extracting(e->((CampusRunSubmission.Failure)e).code().name()).isEqualTo(code);}
    private static void await(java.util.function.BooleanSupplier p,long ms)throws Exception {
        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(!p.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(1);assertThat(p.getAsBoolean()).isTrue();
    }
    private static final class Fixture implements AutoCloseable {
        final AtomicLong now=new AtomicLong();final AtomicInteger calls=new AtomicInteger();
        final CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
        final CampusAdmissionBudget budget=new CampusAdmissionBudget(2,18);
        final ThreadPoolExecutor worker;
        final Timer timer=new Timer();final CampusRunArchiveStore archive=mock(CampusRunArchiveStore.class);
        final List<TrustedActor> users=new ArrayList<>();final Map<String,CampusRunSubmission.Record> records=new ConcurrentHashMap<>();
        final Map<UUID,AtomicInteger> finalized=new ConcurrentHashMap<>();final CampusCreditAgentManager manager;
        Fixture(){this(null,null);}
        Fixture(ScheduledExecutorService realTimer,java.util.function.LongSupplier realClock) {
            this(realTimer,realClock,new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16)));
        }
        Fixture(ScheduledExecutorService realTimer,java.util.function.LongSupplier realClock,ThreadPoolExecutor worker) {
            this.worker=worker;
            for(int i=0;i<10;i++)users.add(new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0));
            var owner=new CampusRunArchive.OwnerToken(UUID.randomUUID(),1);when(archive.isAccepting()).thenReturn(true);
            when(archive.findSubmissionOwned(any(),any())).thenAnswer(i->Optional.ofNullable(records.get(i.getArgument(0,TrustedActor.class).userId()+":"+i.getArgument(1))));
            when(archive.findOwned(any(),any())).thenReturn(Optional.of(mock(CampusRunArchive.Read.class)));
            when(archive.admitSubmissionOwned(any(),any())).thenAnswer(i->{var a=i.getArgument(0,TrustedActor.class);var c=i.getArgument(1,CampusRunSubmission.Command.class);
                var r=new CampusRunSubmission.Record(a.userId(),c.submissionKey(),c.proposedRunId(),c.fingerprint(),Instant.EPOCH);
                records.put(a.userId()+":"+c.submissionKey(),r);return new CampusRunSubmission.Claim(CampusRunSubmission.Decision.CREATED,r,new CampusRunArchive.Admission(r.runId(),owner,0));});
            when(archive.finalizeRun(any(),any(),nullable(CampusRunArchive.PayloadCandidate.class))).thenAnswer(i->{finalized.computeIfAbsent(i.getArgument(0,CampusRunArchive.Admission.class).runId(),id->new AtomicInteger()).incrementAndGet();return CampusRunArchive.Finalization.CONFIRMED;});
            ChatModel model=p->{calls.incrementAndGet();entered.countDown();while(release.getCount()!=0){try{release.await();}catch(InterruptedException ignored){}}
                return new ChatResponse(List.of(new Generation(new AssistantMessage("补充课程"))));};
            var service=new CampusCreditAgentService(model,new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->{throw new AssertionError("空课程不查询政策");},3);
            manager=new CampusCreditAgentManager(service,worker,realTimer==null?timer:realTimer,Duration.ofSeconds(30),archive,budget,
                    Duration.ofSeconds(10),realClock==null?now::get:realClock);
        }
        CampusCreditAgentManager.Submitted submit(TrustedActor a,UUID key){var request=new CampusCreditAgentService.Request("合成问题",List.of());return manager.submitOwned(a,key,request,CampusRequestFingerprint.of(request));}
        void entered()throws Exception {assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();}
        void awaitAll()throws Exception {await(()->records.values().stream().allMatch(r->manager.findOwned(users.stream().filter(u->u.userId().equals(r.userId())).findFirst().orElseThrow(),r.runId()).terminated())&&budget.inFlight()==0,2000);}
        @Override public void close()throws Exception {release.countDown();worker.shutdown();assertThat(worker.awaitTermination(2,TimeUnit.SECONDS)).isTrue();manager.close(Duration.ZERO);timer.shutdownNow();assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
    }
    private static final class Timer extends ScheduledThreadPoolExecutor {
        record Entry(long delay,Runnable callback) { }
        final List<Entry> entries=new CopyOnWriteArrayList<>();
        Timer(){super(1);setRemoveOnCancelPolicy(true);}
        @Override public ScheduledFuture<?> schedule(Runnable r,long delay,TimeUnit unit){
            if(delay==0)return super.schedule(r,0,TimeUnit.NANOSECONDS);
            entries.add(new Entry(unit.toNanos(delay),r));return super.schedule(r,1,TimeUnit.DAYS);
        }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> c,long delay,TimeUnit unit){
            entries.add(new Entry(unit.toNanos(delay),()->{try{c.call();}catch(Exception e){throw new AssertionError(e);}}));return super.schedule(c,1,TimeUnit.DAYS);
        }
        void fire(Duration delay){entries.stream().filter(e->e.delay()==delay.toNanos()).forEach(e->e.callback().run());}
    }
}
