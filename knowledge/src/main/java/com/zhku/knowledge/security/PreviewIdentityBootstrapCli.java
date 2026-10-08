package com.zhku.knowledge.security;

import com.zhku.knowledge.controlled.ControlledPreviewConfiguration;
import java.nio.CharBuffer;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 显式初始化入口；不随服务启动自动建账号。 */
public final class PreviewIdentityBootstrapCli {
    private PreviewIdentityBootstrapCli() { }

    static void initialize(DataSource source, char[] userA, char[] userB, char[] maintainer) {
        char[][] passwords = { userA, userB, maintainer };
        boolean committing = false;
        try {
            if (source == null) throw new IllegalArgumentException("INVALID_INPUT");
            for (char[] password : passwords) {
                if (password == null || password.length < 16 || password.length > 64)
                    throw new IllegalArgumentException("INVALID_INPUT");
                for (char value : password) if (value < 32 || value > 126)
                    throw new IllegalArgumentException("INVALID_INPUT");
            }
            String[] hashes = Arrays.stream(passwords)
                    .map(value -> new BCryptPasswordEncoder().encode(CharBuffer.wrap(value))).toArray(String[]::new);
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (var settings = connection.createStatement()) {
                        settings.setQueryTimeout(1);
                        settings.execute("SET LOCAL lock_timeout='500ms'");
                        settings.execute("SET LOCAL statement_timeout='1s'");
                    }
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES (?,?,?,?)")) {
                        insert.setQueryTimeout(1);
                        String[] logins = { "user-a", "user-b", "maintainer" };
                        for (int i = 0; i < logins.length; i++) {
                            insert.setObject(1, UUID.randomUUID());
                            insert.setString(2, logins[i]);
                            insert.setString(3, hashes[i]);
                            insert.setString(4, i == 2 ? "MAINTAINER" : "USER");
                            if (insert.executeUpdate() != 1) throw new SQLException("", "08006");
                        }
                    }
                    // 三账号只尝试一次原子提交；ACK丢失不能据rollback成功推断未创建再重放。
                    committing = true;
                    connection.commit();
                } catch (SQLException failure) {
                    try { connection.rollback(); } catch (SQLException ignored) { }
                    throw failure;
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(committing ? "INITIALIZATION_UNCONFIRMED"
                    : "23505".equals(failure.getSQLState()) ? "ACCOUNT_CONFLICT" : "STORAGE_UNAVAILABLE");
        } finally {
            for (char[] password : passwords) if (password != null) Arrays.fill(password, '\0');
        }
    }

    public static void main(String[] args) {
        char[][] inputs = new char[3][];
        int exit = 1;
        String code = "INVALID_INPUT";
        try {
            if (args.length != 0) throw new IllegalArgumentException("INVALID_INPUT");
            var properties = new LinkedHashMap<String, Object>();
            properties.put("zhku.controlled-preview.enabled", "true");
            properties.put("zhku.campus-agent.enabled", "true");
            properties.put("zhku.campus-agent.archive.enabled", "true");
            properties.put("server.address", "127.0.0.1");
            properties.put("zhku.db.url", required("ZHKU_DB_URL"));
            properties.put("zhku.db.username", required("ZHKU_DB_USERNAME"));
            var environment = new StandardEnvironment();
            environment.setActiveProfiles("zhku-controlled-preview");
            environment.getPropertySources().addFirst(new MapPropertySource("bootstrap-target", properties));
            ControlledPreviewConfiguration.validateEnvironment(environment);
            var source = new DriverManagerDataSource((String) properties.get("zhku.db.url"),
                    (String) properties.get("zhku.db.username"), required("ZHKU_DB_PASSWORD"));
            var timeouts = new Properties();
            timeouts.setProperty("connectTimeout", "1"); timeouts.setProperty("socketTimeout", "2");
            timeouts.setProperty("cancelSignalTimeout", "1");
            source.setConnectionProperties(timeouts);
            String[] keys = { "ZHKU_PREVIEW_USER_A_PASSWORD", "ZHKU_PREVIEW_USER_B_PASSWORD", "ZHKU_PREVIEW_MAINTAINER_PASSWORD" };
            for (int i = 0; i < keys.length; i++) inputs[i] = required(keys[i]).toCharArray();
            // V19须事先完成；CLI不会迁移、停用、改密或把冲突账号重置成新的身份。
            initialize(source, inputs[0], inputs[1], inputs[2]);
            code = "INITIALIZED";
            exit = 0;
        } catch (IllegalStateException failure) {
            if (Arrays.asList("ACCOUNT_CONFLICT", "INITIALIZATION_UNCONFIRMED", "STORAGE_UNAVAILABLE").contains(failure.getMessage()))
                code = failure.getMessage();
        } catch (RuntimeException invalid) { /* 不打印可能含环境值/SQL行/hash的异常。 */ }
        finally { for (char[] input : inputs) if (input != null) Arrays.fill(input, '\0'); }
        System.out.println(code);
        if (exit != 0) System.exit(exit);
    }

    private static String required(String key) {
        if (System.getProperty(key) != null) throw new IllegalArgumentException("INVALID_INPUT");
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("INVALID_INPUT");
        return value;
    }
}
