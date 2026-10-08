package com.zhku.knowledge.controlled;

import com.zhku.knowledge.conversation.ConversationJdbcAccess;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import java.time.Clock;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** 3B唯一资源owner；默认关闭，绑定4A显式目标并在V20完成后创建。 */
@Configuration(proxyBeanMethods=false)
@Profile("zhku-controlled-preview")
@ConditionalOnProperty(name="zhku.controlled-preview.conversations.enabled",havingValue="true",matchIfMissing=false)
public class ControlledConversationConfiguration {
    static void validateEnvironment(Environment environment) {
        ControlledPreviewConfiguration.validateEnvironment(environment,true);
    }
    @Bean(destroyMethod="close") public ConversationJdbcAccess conversationJdbcAccess(DataSource controlledDataSource,
            Flyway controlledMigration,Environment environment) {
        validateEnvironment(environment);
        // 同一个DataSource已用于身份、归档和Flyway；不另建库、探测缺列后回退或退到Memory。
        if(controlledMigration.getConfiguration().getDataSource()!=controlledDataSource)
            throw new IllegalArgumentException("受控会话要求同目标V20已成功应用");
        var info=controlledMigration.info();
        var current=info.current();
        var required=MigrationVersion.fromVersion("20");
        // 后续迁移不能关闭既有会话，也不能凭最高版本跳过V20所建立的归属字段。
        boolean v20Applied=Arrays.stream(info.applied()).anyMatch(migration ->
                required.equals(migration.getVersion()) && migration.getState()==MigrationState.SUCCESS);
        if(current==null || current.getVersion()==null
                || current.getVersion().compareTo(required)<0 || !v20Applied)
            throw new IllegalArgumentException("受控会话要求同目标V20已成功应用");
        return new ConversationJdbcAccess(controlledDataSource);
    }
    @Bean public ControlledConversationFacade controlledConversationFacade(ConversationJdbcAccess access,
            PreviewIdentityRepository identities,Clock previewIdentityClock) {
        return new ControlledConversationFacade(access,identities,previewIdentityClock);
    }
}
