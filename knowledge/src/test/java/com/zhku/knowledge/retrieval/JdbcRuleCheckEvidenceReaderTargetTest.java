package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.JdbcRuleCheckEvidenceReader;
import com.zhku.knowledge.rulecheck.RuleCheckEvidenceSnapshot;
import com.zhku.knowledge.rulecheck.RuleCheckEvidenceSpan;
import com.zhku.knowledge.rulecheck.RuleCheckEvidenceVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class JdbcRuleCheckEvidenceReaderTargetTest {
    @Test void actualSetupRejectionCannotReconnectInCleanup() {
        for (String url:List.of("jdbc:postgresql://127.0.0.1:54329/zhkuagent", "jdbc:postgresql://remote:55439/zhkuagent_rulecheck_e2e")) {
            var lifecycle=new JdbcRuleCheckEvidenceReaderTest();
            assertThatThrownBy(()->lifecycle.setUpAt(url)).isInstanceOf(IllegalArgumentException.class);
            assertThatCode(lifecycle::tearDown).doesNotThrowAnyException();
        }
        var lifecycle=new JdbcRuleCheckEvidenceReaderTest();
        assertThatThrownBy(()->lifecycle.setUpAt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(lifecycle::tearDown).doesNotThrowAnyException();
    }

    @Test void missingServerConfigAndIdentityCannotConnect() {
        var calls=new AtomicInteger();var dataSource=failingDataSource(calls);
        assertThatThrownBy(()->new JdbcRuleCheckEvidenceReader(dataSource,"p","v",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JdbcRuleCheckEvidenceReader(dataSource," ","v","scope")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JdbcRuleCheckEvidenceReader(dataSource,"p"," ","scope")).isInstanceOf(IllegalArgumentException.class);
        var reader=new JdbcRuleCheckEvidenceReader(dataSource,"p","v","scope");
        assertThatThrownBy(()->reader.read(null,UUID.randomUUID())).isInstanceOf(NullPointerException.class);
        assertThat(calls).hasValue(0);
    }

    @Test void failedConnectionBecomesControlledVerificationRejection() {
        var calls=new AtomicInteger();var reader=new JdbcRuleCheckEvidenceReader(failingDataSource(calls),"p","v","scope");
        var row=new RuleCheckEvidenceSnapshot("ev",UUID.randomUUID(),UUID.randomUUID(),"v1",
                "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a","4",true);
        var result=new RuleCheckEvidenceVerifier(reader).verify(new RuleCheckEvidenceSpan("ev",0,1,"4"),List.of(row));
        assertThat(result.accepted()).isFalse();assertThat(result.excerpt()).isNull();
        assertThat(result.rejectionReason()).isEqualTo(RuleCheckEvidenceVerifier.RejectionReason.CURRENT_EVIDENCE_UNAVAILABLE);
        assertThat(calls).hasValue(1);
    }

    private static AbstractDataSource failingDataSource(AtomicInteger calls) {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {calls.incrementAndGet();throw new SQLException("连接中断");}
            @Override public Connection getConnection(String username,String password) throws SQLException {return getConnection();}
        };
    }
}
