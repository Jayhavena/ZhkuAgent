package com.zhku.knowledge.web;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.campusagent.*;
import com.zhku.knowledge.campusagent.archive.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampusRunArchiveConfigurationIntegrationTest {
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger MODELS = new AtomicInteger();

    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    private ApplicationContextRunner runner() {
        assertThat(ZhkuDatabaseTestConfig.databasePassword()).isNotBlank();
        return new ApplicationContextRunner()
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("zhku-local-preview", "m3a-archive-fixture"))
                .withPropertyValues("zhku.campus-agent.enabled=true", "zhku.campus-agent.archive.enabled=true",
                        "server.address=127.0.0.1",
                        "zhku.db.url=" + System.getProperty("zhku.db.url"), "zhku.db.user=zhkuagent")
                .withUserConfiguration(CampusRunArchiveConfiguration.class, Fixture.class);
    }

    @Test
    void actualManagerFactoryUsesArchiveAndClosesBeforeStorageReleasesOwner() throws Exception {
        EVENTS.clear();
        MODELS.set(0);
        var id = new AtomicReference<UUID>();
        try {
            runner().run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(CampusRunArchiveStore.class)
                        .hasSingleBean(CampusRunArchiveOwner.class);
                assertThat(c.getBeanFactory().getDependentBeans("zhkuCampusRunArchiveStore"))
                        .contains("zhkuCampusCreditAgentManager");
                var manager = c.getBean(CampusCreditAgentManager.class);
                var accepted = manager.start(new CampusCreditAgentService.Request("合成追问", List.of()));
                id.set(accepted.runId());
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (!manager.find(id.get()).terminated() && System.nanoTime() < deadline)
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                assertThat(manager.find(id.get()).terminated()).isTrue();
                var read = c.getBean(CampusRunArchiveStore.class).find(id.get()).orElseThrow();
                assertThat(read.archive().archiveStatus()).isEqualTo(ArchiveStatus.FINALIZED);
                assertThat(read.archive().execution().state().name()).isEqualTo("INCOMPLETE");
                assertThat(MODELS.get()).isEqualTo(1);
            });
            assertThat(EVENTS).containsSubsequence("STOP_ADMISSION", "SEAL_WRITES", "CLOSE_BEGIN", "CLOSE_END");
            assertLockReleased();
        } finally {
            if (id.get() != null)
                delete(id.get());
        }
    }

    @Test
    void competingContextFailsBeforeItCanClaimNewEpoch() {
        runner().run(first -> {
            assertThat(first).hasNotFailed();
            var token = first.getBean(CampusRunArchiveOwner.class).token();
            runner().run(second -> {
                assertThat(second).hasFailed();
                assertThat(second.getStartupFailure()).hasRootCauseInstanceOf(Failure.class);
            });
            assertThat(first.getBean(CampusRunArchiveOwner.class).token()).isEqualTo(token);
            assertThat(first.getBean(CampusRunArchiveOwner.class).isAccepting()).isTrue();
        });
    }

    private static void assertLockReleased() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        do {
            try (var connection = ArchiveTestDatabase.dataSource().getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery(
                            "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND classid=1514687317::oid AND objid=1129464146::oid AND objsubid=2")) {
                rows.next();
                if (rows.getInt(1) == 0)
                    return;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("归档锁未收敛");
    }

    private static void delete(UUID id) throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
            p.setObject(1, id);
            p.executeUpdate();
        }
    }

    // 最小ContextRunner不注册Boot测试扫描排除器；专用profile让夹具只能在本测试显式装配。
    @TestConfiguration(proxyBeanMethods = false)
    @Profile("m3a-archive-fixture")
    static class Fixture {
        @Bean
        static BeanPostProcessor tracing() {
            return new BeanPostProcessor() {
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return name.equals("zhkuCampusRunArchiveStore") ? new TracingStore((CampusRunArchiveStore) bean)
                            : bean;
                }
            };
        }

        @Bean
        ChatModel model() {
            return p -> {
                MODELS.incrementAndGet();
                return new ChatResponse(List.of(new Generation(new AssistantMessage("补充课程"))));
            };
        }

        @Bean
        LocalPreviewConversationFacade facade() {
            return mock(LocalPreviewConversationFacade.class);
        }

        @Bean(destroyMethod = "shutdown")
        ExecutorService worker() {
            return new CampusAgentPreviewConfiguration().zhkuCampusAgentWorker();
        }

        @Bean(destroyMethod = "shutdown")
        ScheduledExecutorService timer() {
            return new CampusAgentPreviewConfiguration().zhkuCampusAgentTimer();
        }

        @Bean(name = "zhkuCampusCreditAgentManager", destroyMethod = "close")
        CampusCreditAgentManager manager(ChatModel model, LocalPreviewConversationFacade facade, ExecutorService worker,
                ScheduledExecutorService timer,
                ObjectProvider<CampusRunArchiveStore> provider, ConfigurableBeanFactory factory) {
            // 调用真实Manager Bean工厂，不调用真实ChatModel工厂，不把重建Manager说成进程重启。
            return new CampusAgentPreviewConfiguration().zhkuCampusCreditAgentManager(model, facade, worker, timer,
                    provider, factory);
        }
    }

    private record TracingStore(CampusRunArchiveStore delegate) implements CampusRunArchiveStore {
        public boolean isAccepting() {
            return delegate.isAccepting();
        }

        public Admission admit(UUID id) {
            return delegate.admit(id);
        }

        public Finalization finalizeRun(Admission a, FinalFacts f, PayloadCandidate p) {
            return delegate.finalizeRun(a, f, p);
        }

        public Optional<Read> find(UUID id) {
            return delegate.find(id);
        }

        public Page list(int limit, Cursor cursor) {
            return delegate.list(limit, cursor);
        }

        public void stopAdmission() {
            EVENTS.add("STOP_ADMISSION");
            delegate.stopAdmission();
        }

        public void sealWrites() {
            EVENTS.add("SEAL_WRITES");
            delegate.sealWrites();
        }

        public void close(Duration remaining) {
            EVENTS.add("CLOSE_BEGIN");
            delegate.close(remaining);
            EVENTS.add("CLOSE_END");
        }
    }
}
