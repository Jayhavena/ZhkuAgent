package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rag.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class JdbcRuleCheckCitationAuditTargetTest {
    private final AtomicInteger connections = new AtomicInteger();
    private final AbstractDataSource dataSource = new AbstractDataSource() {
        public Connection getConnection() throws SQLException { connections.incrementAndGet(); throw new SQLException("unavailable"); }
        public Connection getConnection(String u,String p) throws SQLException { return getConnection(); }
    };
    private CitationAuditRecord valid() {
        return new CitationAuditRecord("a", UUID.randomUUID(), "RULE_CHECK_CAPTURE", Instant.now(), UUID.randomUUID(),
                "v1", "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a", "4");
    }
    @Test void rejectedDatabaseSetupCannotReconnectDuringCleanup() {
        for (String url : List.of("jdbc:postgresql://127.0.0.1:54329/zhkuagent", "jdbc:postgresql://remote:55459/zhkuagent_rulecheck_e2e")) {
            var test = new JdbcRuleCheckCitationAuditTest();
            assertThatThrownBy(()->test.setUpAt(url)).isInstanceOf(IllegalArgumentException.class);
            assertThatCode(test::tearDown).doesNotThrowAnyException();
        }
        var test = new JdbcRuleCheckCitationAuditTest();
        assertThatThrownBy(()->test.setUpAt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(test::tearDown).doesNotThrowAnyException();
    }
    @Test void missingFixedScopeConfigCannotConnect() {
        assertThatThrownBy(()->new JdbcRuleCheckCitationAuditRepository(dataSource, " ", "v", "scope")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JdbcRuleCheckCitationAuditRepository(dataSource, "p", null, "scope")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JdbcRuleCheckCitationAuditRepository(dataSource, "p", "v", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(connections).hasValue(0);
    }
    @Test void legacyRepositoryCannotAcceptNewCaptureRoute() {
        var legacy = new JdbcCitationAuditRepository(dataSource);
        assertThatThrownBy(()->legacy.record(valid())).isInstanceOf(IllegalArgumentException.class);
        assertThat(connections).hasValue(0);
    }
    @Test void legacyRecordWithoutCapturedBindingCannotConnect() {
        var audit = new JdbcRuleCheckCitationAuditRepository(dataSource, "p", "v", "scope");
        assertThatThrownBy(()->audit.record(new CitationAuditRecord("a",UUID.randomUUID(),"RULE_CHECK_CAPTURE",Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(connections).hasValue(0);
    }
    @Test void wrongRouteAndMalformedBindingCannotConnect() {
        var audit = new JdbcRuleCheckCitationAuditRepository(dataSource, "p", "v", "scope");
        var row=valid();
        assertThatThrownBy(()->audit.record(new CitationAuditRecord("a",row.chunkId(),"RETRIEVAL_AUGMENTED",row.issuedAt(),
                row.sourceId(),row.sourceRevision(),row.contentChecksum(),row.content()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->audit.record(new CitationAuditRecord("a",row.chunkId(),row.route(),row.issuedAt(),
                row.sourceId(),row.sourceRevision(),"bad-hash",row.content()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->audit.record(new CitationAuditRecord("a",row.chunkId(),row.route(),row.issuedAt(),
                row.sourceId()," ",row.contentChecksum(),row.content()))).isInstanceOf(IllegalArgumentException.class);
        assertThat(connections).hasValue(0);
    }
    @Test void databaseFailureCannotBecomeSuccessfulAudit() {
        var audit = new JdbcRuleCheckCitationAuditRepository(dataSource, "p", "v", "scope");
        assertThatThrownBy(()->audit.record(valid())).isInstanceOf(IllegalStateException.class);
        assertThat(connections).hasValue(1);
    }
}
