package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.archive.CampusRunArchiveStore;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.TrustedActor;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Manager与存储替身的契约；不把显式fixture actor说成可信框架登录。 */
@Timeout(15)
class CampusCreditAgentOwnershipTest {
    private final TrustedActor a = actor(TrustedActor.Role.USER);
    private final TrustedActor b = actor(TrustedActor.Role.USER);
    private static TrustedActor actor(TrustedActor.Role role) { return new TrustedActor(UUID.randomUUID(), role, 0); }
    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("公选课学分", List.of());
    }
    private static CampusCreditAgentService service(AtomicInteger models) {
        return new CampusCreditAgentService(prompt -> {
            models.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("请补充课程"))));
        }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
            throw new AssertionError("无政策工具调用");
        }, 3);
    }
    private static CampusRunArchiveStore archive() {
        var archive = mock(CampusRunArchiveStore.class);
        when(archive.isAccepting()).thenReturn(true);
        when(archive.admitOwned(any(), any())).thenAnswer(call -> new CampusRunArchive.Admission(
                call.getArgument(0), new CampusRunArchive.OwnerToken(UUID.randomUUID(), 1), 0));
        when(archive.admit(any())).thenAnswer(call -> new CampusRunArchive.Admission(
                call.getArgument(0), new CampusRunArchive.OwnerToken(UUID.randomUUID(), 1), 0));
        when(archive.findOwned(any(), any())).thenReturn(Optional.of(mock(CampusRunArchive.Read.class)));
        when(archive.finalizeRun(any(), any(), any())).thenReturn(CampusRunArchive.Finalization.CONFIRMED);
        return archive;
    }
    @Test void explicitActorControlsAdmissionRegardlessOfThreadLocalAndAnonymousMethodsCannotReadIt() {
        var models = new AtomicInteger();
        var archive = archive();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(models), Runnable::run, timer, Duration.ofSeconds(30), archive);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(a, null, List.of()));
            var run = manager.startOwned(request(), b);
            assertThat(run.terminated()).isTrue();
            assertThat(manager.findOwned(b, run.runId()).runId()).isEqualTo(run.runId());
            verify(archive).admitOwned(run.runId(), b);
            verify(archive, never()).admit(any());
            assertThatThrownBy(() -> manager.find(run.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> manager.cancel(run.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> manager.findOwned(a, run.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> manager.cancelOwned(a, run.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThat(models).hasValue(1);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void absentOwnedStoreAndMaintainerAreRejectedBeforeAnyDispatch() {
        var models = new AtomicInteger();
        var archive = archive();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var unowned = new CampusCreditAgentManager(service(models), Runnable::run, timer, Duration.ofSeconds(30));
            assertThatThrownBy(() -> unowned.startOwned(request(), a)).isInstanceOf(CampusRunArchive.Failure.class);
            var manager = new CampusCreditAgentManager(service(models), Runnable::run, timer, Duration.ofSeconds(30), archive);
            assertThatThrownBy(() -> manager.startOwned(request(), actor(TrustedActor.Role.MAINTAINER)))
                    .isInstanceOf(PreviewIdentityRepository.Failure.class)
                    .extracting(e -> ((PreviewIdentityRepository.Failure)e).code())
                    .isEqualTo(PreviewIdentityRepository.Code.ROLE_DENIED);
            verify(archive, never()).admitOwned(any(), any());
            assertThat(models).hasValue(0);
        }
    }
    @Test void unknownAdmissionNeverSchedulesOrDispatches() {
        var models = new AtomicInteger();
        var archive = archive();
        doThrow(new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_ADMISSION_UNCONFIRMED))
                .when(archive).admitOwned(any(), any());
        var timer = mock(ScheduledExecutorService.class);
        var worker = mock(java.util.concurrent.Executor.class);
        var manager = new CampusCreditAgentManager(service(models), worker, timer, Duration.ofSeconds(30), archive);
        assertThatThrownBy(() -> manager.startOwned(request(), a)).isInstanceOf(CampusRunArchive.Failure.class);
        verifyNoInteractions(worker, timer);
        assertThat(models).hasValue(0);
    }
    @Test void ownershipIsStableWhenOneWorkerHandlesTwoActorsAndOwnedCannotReadAnonymousRun() {
        var models = new AtomicInteger();
        var archive = archive();
        var queued = new ArrayDeque<Runnable>();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(models), queued::add, timer, Duration.ofSeconds(30), archive);
            var first = manager.startOwned(request(), a);
            var second = manager.startOwned(request(), b);
            queued.remove().run();
            queued.remove().run();
            assertThat(manager.findOwned(a, first.runId()).terminated()).isTrue();
            assertThat(manager.findOwned(b, second.runId()).terminated()).isTrue();
            assertThatThrownBy(() -> manager.findOwned(b, first.runId())).isInstanceOf(IllegalArgumentException.class);
            var anonymous = manager.start(request());
            assertThatThrownBy(() -> manager.findOwned(a, anonymous.runId())).isInstanceOf(IllegalArgumentException.class);
            queued.remove().run();
            assertThat(models).hasValue(3);
        }
    }
    @Test void unauthorizedCancellationDoesNotStopBlockedModelAndAuthorizationFailureHasNoFallback() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var archive = archive();
        var service = new CampusCreditAgentService(prompt -> {
            calls.incrementAndGet(); entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("补充课程"))));
        }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
            throw new AssertionError("工具计数为0");
        }, 3);
        var worker = Executors.newSingleThreadExecutor();
        var timer = Executors.newSingleThreadScheduledExecutor();
        try {
            var manager = new CampusCreditAgentManager(service, worker, timer, Duration.ofSeconds(30), archive);
            var run = manager.startOwned(request(), a);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> manager.cancelOwned(b, run.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThat(manager.findOwned(a, run.runId()).state()).isEqualTo(AgentRunState.RUNNING);
            when(archive.findOwned(a, run.runId())).thenThrow(new CampusRunArchive.Failure(
                    CampusRunArchive.Code.ARCHIVE_READ_UNAVAILABLE));
            assertThatThrownBy(() -> manager.cancelOwned(a, run.runId())).isInstanceOf(CampusRunArchive.Failure.class);
            doReturn(Optional.of(mock(CampusRunArchive.Read.class))).when(archive).findOwned(a, run.runId());
            assertThat(manager.findOwned(a, run.runId()).state()).isEqualTo(AgentRunState.RUNNING);
            assertThat(calls).hasValue(1);
        } finally {
            release.countDown(); timer.shutdownNow(); worker.shutdown();
            assertThat(worker.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(timer.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
