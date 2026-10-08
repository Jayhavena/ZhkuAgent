package com.zhku.knowledge.controlled.demo;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** 专用临时真库验证准备事务；不运行供应商或服务器。 */
class ControlledWebDemoSetupTest {
    private static DataSource source;
    private JdbcTemplate jdbc;
    private Map<String, char[]> passwords;
    @BeforeAll static void migrate() { ArchiveTestDatabase.migrate(); source = ArchiveTestDatabase.dataSource(); }
    @BeforeEach void before() {
        jdbc = new JdbcTemplate(source);
        clear();
        passwords = new LinkedHashMap<>();
        for (String login : new String[] {"user-a", "user-b", "maintainer"})
            passwords.put(login, ("Synthetic-" + UUID.randomUUID()).toCharArray());
    }
    @AfterEach void after() { clear(); passwords.values().forEach(value -> java.util.Arrays.fill(value, '\0')); }
    private void clear() {
        jdbc.update("DELETE FROM knowledge_chunk WHERE source_id IN (?,?)", ControlledWebDemoSetupCli.CREDIT_SOURCE, ControlledWebDemoSetupCli.RETAKE_SOURCE);
        jdbc.update("DELETE FROM knowledge_source WHERE id IN (?,?)", ControlledWebDemoSetupCli.CREDIT_SOURCE, ControlledWebDemoSetupCli.RETAKE_SOURCE);
        jdbc.update("DELETE FROM preview_identity WHERE login_name IN ('user-a','user-b','maintainer')");
    }
    @Test void firstPreparationStoresThreeHashedAccountsAndTwoSyntheticSources() {
        var prepared = ControlledWebDemoSetupCli.prepare(source, passwords);
        assertThat(prepared.accounts()).containsOnlyKeys("user-a", "user-b", "maintainer");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preview_identity", Integer.class)).isEqualTo(3);
        for (String login : passwords.keySet()) {
            var row = jdbc.queryForMap("SELECT password_hash,role,enabled,auth_revision FROM preview_identity WHERE login_name=?", login);
            // 对断言仅传boolean，失败消息不能把hash或密码带入XML。
            assertThat(new BCryptPasswordEncoder().matches(new String(passwords.get(login)), (String) row.get("password_hash"))).isTrue();
            assertThat(row.get("role")).isEqualTo(login.equals("maintainer") ? "MAINTAINER" : "USER");
            assertThat(row.get("enabled")).isEqualTo(true);
            assertThat(row.get("auth_revision")).isEqualTo(0L);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk WHERE source_id IN (?,?)", Integer.class,
                prepared.creditSource(), prepared.retakeSource())).isEqualTo(2);
        String text = prepared.toString();
        for (char[] password : passwords.values()) assertThat(text.contains(new String(password))).isFalse();
        assertThat(text).doesNotContain("$2a$", "$2b$");
    }
    @Test void repeatKeepsIdentityAndWithdrawnSourceInsteadOfResetting() {
        var first = ControlledWebDemoSetupCli.prepare(source, passwords);
        jdbc.update("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?", first.creditSource());
        var second = ControlledWebDemoSetupCli.prepare(source, passwords);
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT visibility_scope FROM knowledge_source WHERE id=?", String.class, first.creditSource())).isEqualTo("INTERNAL_ONLY");
    }
    @Test void differentPasswordRejectsWithoutReplacingIdentity() {
        var first = ControlledWebDemoSetupCli.prepare(source, passwords);
        passwords.put("user-a", ("Synthetic-" + UUID.randomUUID()).toCharArray());
        assertThatThrownBy(() -> ControlledWebDemoSetupCli.prepare(source, passwords)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT user_id FROM preview_identity WHERE login_name='user-a'", UUID.class)).isEqualTo(first.accounts().get("user-a"));
    }
    @Test void accountCollisionDoesNotLeaveOtherAccountsOrPolicies() {
        jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES(?,'user-b',?,'MAINTAINER')",
                UUID.randomUUID(), new BCryptPasswordEncoder(10).encode(new String(passwords.get("user-b"))));
        assertThatThrownBy(() -> ControlledWebDemoSetupCli.prepare(source, passwords)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preview_identity", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_source WHERE id IN (?,?)", Integer.class,
                ControlledWebDemoSetupCli.CREDIT_SOURCE, ControlledWebDemoSetupCli.RETAKE_SOURCE)).isZero();
    }
    @Test void sourceCollisionRollsBackAllAccounts() {
        jdbc.update("INSERT INTO knowledge_source(id,title,source_type,source_version,content_checksum) VALUES(?,'非本轮资料','PDF','other',?)",
                ControlledWebDemoSetupCli.CREDIT_SOURCE, "0".repeat(64));
        assertThatThrownBy(() -> ControlledWebDemoSetupCli.prepare(source, passwords)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preview_identity", Integer.class)).isZero();
    }
    @Test void nonDedicatedTargetRejectedBeforeConnection() {
        var unsafe = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:54329/zhkuagent", "zhkuagent", "unused");
        assertThatThrownBy(() -> ControlledWebDemoSetupCli.prepare(unsafe, passwords)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingOrInvalidPasswordRejectedWithoutWrites() {
        passwords.put("user-b", "short".toCharArray());
        assertThatThrownBy(() -> ControlledWebDemoSetupCli.prepare(source, passwords)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preview_identity", Integer.class)).isZero();
    }
}
