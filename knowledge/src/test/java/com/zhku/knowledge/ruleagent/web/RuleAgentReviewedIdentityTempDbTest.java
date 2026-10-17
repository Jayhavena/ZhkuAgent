package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.PreviewPrincipal;
import com.zhku.knowledge.security.TrustedActor;
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
        //
        // 口令必须满足 PreviewIdentityRepository.validPassword 的正式约束：16–64 位、全部为可打印 ASCII。
        // 上一轮用 15 位口令（"t06-tempdb-pass"）导致输入在数据库查询与 BCrypt 校验之前就被拒绝，
        // 返回 Optional.empty()——那是夹具问题，不是仓储缺陷；此处不再放宽规则，只修正输入。
        // 另：正式仓储会在 finally 中清空调用方传入的 char[]，所以每次调用必须重新生成数组。
        var encoder = new BCryptPasswordEncoder();
        String correctPassword = "t06-tempdb-correct-pass";
        String wrongPassword = "t06-tempdb-wrong-password";
        String unknownAccountPassword = "t06-tempdb-unknown-pass";
        assertThat(List.of(correctPassword, wrongPassword, unknownAccountPassword))
                .as("夹具口令必须符合 16–64 位可打印 ASCII 的正式约束")
                .allSatisfy(value -> assertThat(value.length()).isBetween(16, 64));
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled) VALUES (?,?,?,'USER',true)",
                userId, "t06-tempdb-user", encoder.encode(correctPassword));
        var identities = new PreviewIdentityRepository(db);
        char[] first = correctPassword.toCharArray();
        Optional<PreviewPrincipal> ok = identities.authenticate("t06-tempdb-user", first);
        assertThat(first).as("正式仓储必须清空调用方传入的口令数组").containsOnly('\0');
        assertThat(ok).as("格式合格的正确口令必须返回主体").isPresent();
        var actor = ok.get().actor();
        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.role()).isEqualTo(TrustedActor.Role.USER);
        assertThat(actor.authRevision()).isEqualTo(0);

        // 格式合格、内容错误：必须走到 BCrypt 校验后被拒，而不是被长度规则提前拒绝。
        assertThat(identities.authenticate("t06-tempdb-user", wrongPassword.toCharArray()))
                .as("格式合格但内容错误的口令必须被拒绝").isEmpty();
        // 格式合格的未知账号。
        assertThat(identities.authenticate("t06-tempdb-unknown", unknownAccountPassword.toCharArray()))
                .as("格式合格的未知账号必须被拒绝").isEmpty();
        // 不合格输入（15 位）的拒绝已由 PreviewIdentityRepositoryIntegrationTest
        // 与 PreviewIdentityBudgetTest 覆盖，此处不重复堆砌。

        // 5) 仓储退出可核对：关闭后不得有在途请求或存活工作线程。
        identities.close();
        var exit = identities.status();
        assertThat(exit.closed()).as("关闭后状态必须为 closed").isTrue();
        assertThat(exit.inFlight()).as("不得有在途请求").isZero();
        assertThat(exit.liveThreads()).as("不得有存活工作线程").isZero();

        // 6) 只读核对：安装结果确实落在本次临时库，未触及任何其他库。
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preview_identity", Integer.class)).isEqualTo(1);
    }
}
