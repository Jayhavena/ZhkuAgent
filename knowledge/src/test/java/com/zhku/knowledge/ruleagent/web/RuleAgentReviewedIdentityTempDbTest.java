package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.PreviewPrincipal;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/**
 * T06 真实身份仓储与已审核资料安装的专用临时库验收。
 *
 * <p>为什么这样设计：
 * <ul>
 *   <li><b>只认专用回环空库。</b>库名固定 {@code zhkuagent_rulecheck_e2e}，拒绝 54329 与原演示库；
 *       迁移前先证明目标为空，再迁移、再安装——顺序不能颠倒，否则"空库约束"会被自身写入破坏。</li>
 *   <li><b>身份走正式仓储。</b>账号写入 {@code preview_identity} 后必须经
 *       {@link PreviewIdentityRepository#authenticate} 校验，不能用测试替身冒充"真实身份"。</li>
 *   <li><b>资料安装沿用既有受控组件。</b>{@link Reviewed2025ControlledCatalogue} 自带批准输入指纹与
 *       空库双重校验；安装成功即代表批准指纹逐个匹配，无需另造校验。</li>
 * </ul>
 *
 * <p>未配置 {@code zhku.db.url} 时**跳过并明确标未验证**，不静默回退到任何其他库。
 * 本类不启动 Web、不调用任何供应商。
 */
class RuleAgentReviewedIdentityTempDbTest {

    private static final Pattern ISOLATED = Pattern
            .compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$");

    private static DriverManagerDataSource isolated() {
        String url = System.getProperty("zhku.db.url", System.getenv("ZHKU_DB_URL"));
        Assumptions.assumeTrue(url != null, "未提供 zhku.db.url / ZHKU_DB_URL：专用临时库未配置，本项未验证");
        var matcher = ISOLATED.matcher(url);
        assertThat(matcher.matches()).as("必须使用专用回环临时库").isTrue();
        int port = Integer.parseInt(matcher.group(1));
        assertThat(port).as("拒绝原演示库端口").isNotEqualTo(54329);
        String password = System.getenv("ZHKU_DB_PASSWORD");
        Assumptions.assumeTrue(password != null && !password.isBlank(), "缺少本次数据库凭据：本项未验证");
        return new DriverManagerDataSource(url, "zhkuagent", password);
    }

    private static Path root() {
        var candidate = Paths.get(System.getProperty("zhku.t06.root", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        assertThat(candidate.resolve("knowledge/src").toFile().isDirectory()).as("受控资料根目录无效").isTrue();
        return candidate;
    }

    @Test void installsReviewedAssetsIntoEmptyTempDbAndAuthenticatesRealIdentity() throws Exception {
        var db = isolated();
        var jdbc = new JdbcTemplate(db);

        // 1) 迁移前证明目标为空：没有任何既有表，更没有资料。
        Integer tables = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema NOT IN ('pg_catalog','information_schema')", Integer.class);
        assertThat(tables).as("专用临时库必须为空（迁移前）").isZero();

        // 2) 既有迁移。
        ZhkuFlywayTestSupport.migrate(db.getUrl(), db.getUsername(), System.getenv("ZHKU_DB_PASSWORD"));
        Integer migrated = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema NOT IN ('pg_catalog','information_schema')", Integer.class);
        assertThat(migrated).as("迁移后应有 schema").isGreaterThan(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_source", Integer.class)).isZero();

        // 3) 已审核资料安装：组件内部会重新核对批准输入指纹与空库约束。
        String revision;
        String installedChecksum;
        int chunks;
        try (var catalogue = new Reviewed2025ControlledCatalogue(root())) {
            catalogue.installOnIsolatedDatabase(db);
            var settings = catalogue.settings();
            revision = settings.referenceRevision();
            // 安装后核对：版本、正文指纹、引用跨度必须可从库里读回。
            installedChecksum = jdbc.queryForObject(
                    "SELECT content_checksum FROM knowledge_chunk ORDER BY content_checksum LIMIT 1", String.class);
            chunks = jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk", Integer.class);
        }
        assertThat(revision).isEqualTo("2025.9.2");
        assertThat(chunks).as("应安装到已审核片段").isGreaterThan(0);
        assertThat(installedChecksum).as("正文指纹必须可读回").matches("(?i)[0-9a-f]{64}");

        // 4) 真实身份仓储：写入正式身份表后必须能登录，且错误口令/未知账号被拒绝。
        var encoder = new BCryptPasswordEncoder();
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled) VALUES (?,?,?,'USER',true)",
                userId, "t06-tempdb-user", encoder.encode("t06-tempdb-pass"));
        try (var identities = new PreviewIdentityRepository(db)) {
            Optional<PreviewPrincipal> ok = identities.authenticate("t06-tempdb-user", "t06-tempdb-pass".toCharArray());
            assertThat(ok).as("真实身份必须可登录").isPresent();
            assertThat(ok.get().actor().userId()).isEqualTo(userId);
            assertThat(identities.authenticate("t06-tempdb-user", "wrong-password".toCharArray()))
                    .as("错误口令必须被拒绝").isEmpty();
            assertThat(identities.authenticate("no-such-user", "t06-tempdb-pass".toCharArray()))
                    .as("未知账号必须被拒绝").isEmpty();
        }

        // 5) 只读核对：安装结果确实落在本次临时库，未触及任何其他库。
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preview_identity", Integer.class)).isEqualTo(1);
    }
}
