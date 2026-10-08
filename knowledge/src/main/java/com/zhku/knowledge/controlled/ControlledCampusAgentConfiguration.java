package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.campusagent.CampusAdmissionBudget;
import com.zhku.knowledge.campusagent.archive.*;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.rag.RagProviderProfileResolver;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
import com.zhku.knowledge.tools.modelcall.CourseCreditModelTransport;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import com.zhku.knowledge.web.ZhkuLocalPreviewConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** 受控入口只复用校园构造链；身份留在应用/存储边界，领域策略与两个循环保持原样。 */
@Configuration(proxyBeanMethods = false)
@Profile("zhku-controlled-preview")
@Import(ZhkuLocalPreviewConfiguration.class)
public class ControlledCampusAgentConfiguration {
    static RagProviderProfile validateProductionModel(Environment environment) {
        try {
            String name = environment.getProperty("zhku.rag.active-profile");
            if (name == null || !name.matches("[a-z][a-z0-9-]{0,31}")) throw new IllegalArgumentException();
            var properties = new HashMap<String, String>();
            for (String suffix : List.of("base-url", "api-key-env-var", "chat-model", "embedding-model",
                    "embedding-dimensions", "timeout-seconds", "index-profile")) {
                String key = "zhku.rag.profiles." + name + "." + suffix;
                String value = environment.getProperty(key);
                if (value != null) properties.put(key, value);
            }
            // 只读取profile元数据；不启用RAG、不创建embedding/retriever，也不改既有Factory契约。
            var profile = new RagProviderProfileResolver(properties).profile(name);
            URI endpoint = URI.create(profile.baseUrl());
            if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null)
                throw new IllegalArgumentException();
            if (System.getenv("DASHSCOPE_API_KEY") == null || System.getenv("DASHSCOPE_API_KEY").isBlank())
                throw new IllegalArgumentException();
            ApiKeyResolver.systemEnvironment().requireApiKey(profile);
            return profile;
        } catch (RuntimeException invalid) {
            // profile解析异常可能含原始端点/属性；启动只保留有限消息，不传cause。
            throw new IllegalArgumentException("受控模型配置或环境凭据未通过启动核验");
        }
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ChatModel.class)
    CourseCreditModelTransport controlledCampusModelTransport(Environment environment) {
        return CourseCreditChatModelFactory.open(validateProductionModel(environment), ApiKeyResolver.systemEnvironment());
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(ChatModel.class)
    ChatModel controlledCampusChatModel(CourseCreditModelTransport controlledCampusModelTransport) {
        // open句柄是唯一关闭者；ChatModel是视图，不重复关闭transport。
        return controlledCampusModelTransport.model();
    }

    @Bean CampusRunArchive.Limits controlledCampusArchiveLimits(Environment environment) {
        var defaults = CampusRunArchive.Limits.defaults();
        try {
            return new CampusRunArchive.Limits(defaults.operation(), defaults.startup(), defaults.drain(),
                    Duration.ofDays(Integer.parseInt(environment.getProperty("zhku.campus-agent.archive.retention-days", "7"))),
                    Integer.parseInt(environment.getProperty("zhku.campus-agent.archive.max-records", "10000")));
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("归档保留天数或容量超出预览边界"); }
    }

    @Bean(destroyMethod = "")
    CampusRunArchiveOwner controlledCampusArchiveOwner(DataSource controlledDataSource,
            Flyway controlledMigration, CampusRunArchive.Limits limits) {
        // 先迁移，构造尚不取锁；Store成功创建后是唯一关闭者。
        return new CampusRunArchiveOwner(controlledDataSource, limits);
    }

    @Bean(destroyMethod = "close")
    CampusRunArchiveStore controlledCampusArchiveStore(DataSource controlledDataSource, CampusRunArchiveOwner owner,
            CampusRunArchive.Limits limits) {
        long deadline = System.nanoTime() + limits.startup().toNanos();
        try {
            owner.start();
            return new JdbcCampusRunArchiveStore(controlledDataSource, owner, limits, new CampusRunHistoryVisibility());
        } catch (RuntimeException failure) {
            owner.close(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
            throw failure;
        }
    }

    @Bean CampusRunHistoryService controlledCampusHistory(CampusRunArchiveStore store) {
        return new CampusRunHistoryService(store);
    }

    @Bean(destroyMethod = "shutdown") ExecutorService controlledCampusWorker() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16),
                Thread.ofPlatform().name("zhku-campus-agent-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdown") ScheduledExecutorService controlledCampusTimer() {
        var timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("zhku-campus-agent-timeout").factory());
        // 排空期间迟到完成会取消deadline；shutdown后的取消项也必须立即移除，
        // 否则timer仍等原30秒到期，不能把Future.cancel当宿主资源已退出。
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    @Bean(destroyMethod = "close")
    CampusCreditAgentManager controlledCampusManager(ChatModel model, LocalPreviewConversationFacade policyFacade,
            ExecutorService controlledCampusWorker, ScheduledExecutorService controlledCampusTimer,
            CampusRunArchiveStore store, ConfigurableBeanFactory factory) {
        if (factory.containsBean("controlledCampusModelTransport"))
            factory.registerDependentBean("controlledCampusModelTransport", "controlledCampusWorker");
        // Manager先排空/封写，worker再关准入，transport最后等待；Store之后才释放宿主锁。
        factory.registerDependentBean("controlledCampusArchiveStore", "controlledCampusManager");
        var service = new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
                    var session = policyFacade.createConversation();
                    return policyFacade.sendMessage(session.sessionId(), question);
                }, 3);
        return new CampusCreditAgentManager(service, controlledCampusWorker, controlledCampusTimer,
                Duration.ofSeconds(30), store, new CampusAdmissionBudget(2,18),Duration.ofSeconds(10));
    }

    @Bean CampusRunAccessService controlledCampusAccess(CampusCreditAgentManager manager,
            CampusRunArchiveStore store, PreviewIdentityRepository identities) {
        return new CampusRunAccessService(manager, store, identities);
    }
}
