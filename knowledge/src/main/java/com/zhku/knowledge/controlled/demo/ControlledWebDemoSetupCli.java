package com.zhku.knowledge.controlled.demo;

import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 显式离线准备入口；不会被受控Application扫描为Bean或HTTP接口。 */
public final class ControlledWebDemoSetupCli {
    public static final UUID CREDIT_SOURCE = UUID.fromString("75764be7-970a-44ac-8fc3-4530fc06c612");
    public static final UUID RETAKE_SOURCE = UUID.fromString("c3a8f94e-84a0-4291-837e-90662078b34c");
    private static final List<String> LOGINS = List.of("user-a", "user-b", "maintainer");
    private static final String CREDIT_TEXT = "每学期公共选修课最高不得超过 4 学分";
    private static final String RETAKE_TEXT = "专业选修课成绩不合格者可申请重修学习。公共选修课不办理缓考，成绩不合格者不安排重修。";
    private ControlledWebDemoSetupCli() { }
    public record Prepared(Map<String, UUID> accounts, UUID creditSource, UUID retakeSource) { }

    public static Prepared prepare(DataSource source, Map<String, char[]> passwords) {
        validateTarget(source);
        validatePasswords(passwords);
        var encoder = new BCryptPasswordEncoder(10);
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            boolean committing = false;
            try {
                // 同库准备串行，账号与资料一次提交；SQL失败只回滚，不补写或重置既有身份。
                try (var lock = connection.createStatement()) {
                    lock.setQueryTimeout(2);
                    lock.execute("SET LOCAL lock_timeout='1s'");
                    lock.execute("SELECT pg_advisory_xact_lock(742031206)");
                }
                int existing;
                try (var query = connection.createStatement()) {
                    query.setQueryTimeout(2);
                    try (var rows = query.executeQuery("SELECT count(*) FROM preview_identity")) { rows.next(); existing = rows.getInt(1); }
                }
                if (existing != 0 && existing != 3) throw rejected();
                var accounts = new LinkedHashMap<String, UUID>();
                for (String login : LOGINS) {
                    String role = login.equals("maintainer") ? "MAINTAINER" : "USER";
                    UUID id;
                    try (var query = connection.prepareStatement("SELECT user_id,password_hash,role,enabled,auth_revision FROM preview_identity WHERE login_name=?")) {
                        query.setString(1, login); query.setQueryTimeout(2);
                        try (var row = query.executeQuery()) {
                            if (row.next()) {
                                if (existing != 3 || !role.equals(row.getString(3)) || !row.getBoolean(4)
                                        || row.getLong(5) != 0 || !encoder.matches(CharBuffer.wrap(passwords.get(login)), row.getString(2))) throw rejected();
                                id = row.getObject(1, UUID.class);
                            } else {
                                if (existing != 0) throw rejected();
                                id = UUID.randomUUID();
                                try (var insert = connection.prepareStatement("INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES(?,?,?,?)")) {
                                    insert.setObject(1, id); insert.setString(2, login);
                                    insert.setString(3, encoder.encode(CharBuffer.wrap(passwords.get(login))));
                                    insert.setString(4, role); insert.setQueryTimeout(2); insert.executeUpdate();
                                }
                            }
                        }
                    }
                    accounts.put(login, id);
                }
                policy(connection, CREDIT_SOURCE, "合成演示学分规则", "PUBLIC_ELECTIVE_CREDIT_LIMIT", CREDIT_TEXT, existing == 3);
                policy(connection, RETAKE_SOURCE, "合成演示重修规则", "ELECTIVE_RETAKE", RETAKE_TEXT, existing == 3);
                committing = true;
                connection.commit();
                return new Prepared(Map.copyOf(accounts), CREDIT_SOURCE, RETAKE_SOURCE);
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                // commit确认丢失可能已经持久化，不能把rollback当未写入证明，更不能自动重试准备。
                if (committing) throw new IllegalStateException("DEMO_SETUP_UNCONFIRMED");
                throw rejected();
            }
        } catch (SQLException failure) { throw rejected(); }
    }

    private static void policy(Connection connection, UUID id, String title, String scope, String text, boolean repeat) throws SQLException {
        String checksum = checksum(text);
        try (var query = connection.prepareStatement("""
                SELECT title,source_version,document_family,knowledge_scope,content_checksum
                FROM knowledge_source WHERE id=?
                """)) {
            query.setObject(1, id); query.setQueryTimeout(2);
            try (var row = query.executeQuery()) {
                if (row.next()) {
                    // 重跑不恢复分享许可，亦不接管同ID的外来资料；仅确认合成内容仍是本工具写入的版本。
                    if (!repeat || !title.equals(row.getString(1)) || !"web-demo-v1".equals(row.getString(2))
                            || !"STUDENT_HANDBOOK".equals(row.getString(3)) || !scope.equals(row.getString(4))
                            || !checksum.equals(row.getString(5))) throw rejected();
                    try (var chunk = connection.prepareStatement("SELECT content,content_checksum FROM knowledge_chunk WHERE source_id=?")) {
                        chunk.setObject(1, id); chunk.setQueryTimeout(2);
                        try (var rows = chunk.executeQuery()) {
                            if (!rows.next() || !text.equals(rows.getString(1)) || !checksum.equals(rows.getString(2)) || rows.next()) throw rejected();
                        }
                    }
                    return;
                }
                if (repeat) throw rejected();
            }
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO knowledge_source(id,title,source_type,source_version,publication_status,published_at,
                  content_checksum,visibility_scope,publication_scope,sharing_rights_confirmed,document_family,knowledge_scope,review_notice)
                VALUES(?,?,'PDF','web-demo-v1','PUBLISHED',clock_timestamp(),?,'STUDENT_VISIBLE',
                  '合成本科生',true,'STUDENT_HANDBOOK',?,'仅为技术演示合成规则，不能用于实际选课决策。')
                """)) {
            insert.setObject(1, id); insert.setString(2, title); insert.setString(3, checksum); insert.setString(4, scope);
            insert.setQueryTimeout(2); insert.executeUpdate();
        }
        try (var insert = connection.prepareStatement("INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,content,content_checksum) VALUES(?,?,0,'合成演示第1条',?,?)")) {
            insert.setObject(1, UUID.randomUUID()); insert.setObject(2, id); insert.setString(3, text); insert.setString(4, checksum);
            insert.setQueryTimeout(2); insert.executeUpdate();
        }
    }

    public static void validateTarget(DataSource source) {
        // 先验证显式URL再getConnection；拒绝别名、54329及URI凭据，不能“连上后再看是不是演示库”。
        try {
            if (!(source instanceof DriverManagerDataSource jdbc) || jdbc.getUrl() == null || !jdbc.getUrl().startsWith("jdbc:postgresql://")) throw rejected();
            URI target = URI.create(jdbc.getUrl().substring(5));
            if (!"127.0.0.1".equals(target.getHost()) || target.getPort() < 1024 || target.getPort() > 65535
                    || target.getPort() == 54329 || !"/zhkuagent_run_archive_e2e".equals(target.getPath())
                    || target.getUserInfo() != null || target.getQuery() != null || target.getFragment() != null
                    || !"zhkuagent".equals(jdbc.getUsername())) throw rejected();
        } catch (RuntimeException invalid) { throw rejected(); }
    }

    private static void validatePasswords(Map<String, char[]> passwords) {
        if (passwords == null || !passwords.keySet().equals(Set.copyOf(LOGINS))) throw rejected();
        for (char[] value : passwords.values()) {
            if (value == null || value.length < 16 || value.length > 64) throw rejected();
            for (char character : value) if (character < 32 || character > 126) throw rejected();
        }
    }
    private static IllegalArgumentException rejected() { return new IllegalArgumentException("专用演示目标、凭据或已有合成数据不匹配，未重置任何身份"); }
    private static String checksum(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256不可用"); }
    }

    public static void main(String[] args) {
        var passwords = new LinkedHashMap<String, char[]>();
        try {
            if (args.length != 0) throw rejected();
            for (String login : LOGINS) {
                String name = "ZHKU_DEMO_" + login.toUpperCase(Locale.ROOT).replace('-', '_') + "_PASSWORD";
                String value = System.getenv(name);
                if (value == null) throw rejected();
                passwords.put(login, value.toCharArray());
            }
            validatePasswords(passwords);
            String password = System.getenv("ZHKU_DB_PASSWORD");
            if (password == null || password.isBlank()) throw rejected();
            var source = new DriverManagerDataSource(System.getenv("ZHKU_DB_URL"), "zhkuagent", password);
            validateTarget(source);
            var settings = new Properties(); settings.setProperty("connectTimeout", "1"); settings.setProperty("socketTimeout", "2");
            source.setConnectionProperties(settings);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").outOfOrder(false).load().migrate();
            prepare(source, passwords);
            System.out.println("DEMO_SETUP_READY accounts=user-a,user-b,maintainer syntheticOnly=true");
        } catch (RuntimeException failure) {
            // 外部SQL/认证异常可能附带敏感值；CLI只输出固定类别，不打印异常栈或cause。
            System.err.println("DEMO_SETUP_UNCONFIRMED".equals(failure.getMessage()) ? "DEMO_SETUP_UNCONFIRMED" : "DEMO_SETUP_REJECTED"); System.exit(1);
        } finally { passwords.values().forEach(value -> Arrays.fill(value, '\0')); }
    }
}
