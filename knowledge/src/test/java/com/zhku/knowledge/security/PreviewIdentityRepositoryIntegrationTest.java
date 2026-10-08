package com.zhku.knowledge.security;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.nio.CharBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真库身份验证，不代表框架登录、Cookie或HTTP权限已经完成。 */
class PreviewIdentityRepositoryIntegrationTest {
    private UUID id;
    private String login;
    private char[] password;

    @BeforeAll static void migrate() { ArchiveTestDatabase.migrate(); }
    @BeforeEach void account() {
        id = UUID.randomUUID();
        login = "user-" + UUID.randomUUID().toString().substring(0, 8);
        password = UUID.randomUUID().toString().replace("-", "").toCharArray();
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            insertIdentity(c, id, login, password);
        } catch (SQLException failure) { throw safeSqlFailure(failure); }
    }
    @AfterEach void remove() {
        Arrays.fill(password, '\0');
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("DELETE FROM preview_identity WHERE user_id=?")) {
            p.setObject(1, id);
            p.executeUpdate();
        } catch (SQLException failure) { throw safeSqlFailure(failure); }
    }

    @Test void authenticateErasesInputAndRemainsStableAcrossRepositoryRestart() {
        TrustedActor original;
        try (var repository = new PreviewIdentityRepository(ArchiveTestDatabase.dataSource())) {
            char[] input = password.clone();
            original = repository.authenticate(login, input).orElseThrow().actor();
            assertThat(Arrays.equals(input, new char[input.length])).isTrue();
            assertThat(original.userId()).isEqualTo(id);
            repository.requireCurrent(original);
        }
        try (var next = new PreviewIdentityRepository(ArchiveTestDatabase.dataSource())) {
            assertThat(next.authenticate(login, password.clone()).orElseThrow().actor()).isEqualTo(original);
        }
    }

    @Test void wrongPasswordAndMissingAccountBothReturnEmpty() {
        try (var repository = new PreviewIdentityRepository(ArchiveTestDatabase.dataSource())) {
            char[] incorrect = UUID.randomUUID().toString().replace("-", "").toCharArray();
            assertThat(repository.authenticate(login, incorrect)).isEmpty();
            assertThat(Arrays.equals(incorrect, new char[incorrect.length])).isTrue();
            assertThat(repository.authenticate("user-nonexistent", password.clone())).isEmpty();
        }
    }

    @Test void reenablingDoesNotReviveOldRevision() {
        try (var repository = new PreviewIdentityRepository(ArchiveTestDatabase.dataSource())) {
            var old = repository.authenticate(login, password.clone()).orElseThrow().actor();
            change("enabled=false,auth_revision=auth_revision+1");
            revoked(repository, old);
            assertThat(repository.authenticate(login, password.clone())).isEmpty();
            change("enabled=true,auth_revision=auth_revision+1");
            revoked(repository, old);
            var fresh = repository.authenticate(login, password.clone()).orElseThrow().actor();
            assertThat(fresh.authRevision()).isEqualTo(2);
            repository.requireCurrent(fresh);
        }
    }

    @Test void roleChangeRevokesOldSnapshotAndDoesNotGrantUserRole() {
        try (var repository = new PreviewIdentityRepository(ArchiveTestDatabase.dataSource())) {
            var old = repository.authenticate(login, password.clone()).orElseThrow().actor();
            change("role='MAINTAINER',auth_revision=auth_revision+1");
            revoked(repository, old);
            var current = repository.authenticate(login, password.clone()).orElseThrow().actor();
            assertThat(current.role()).isEqualTo(TrustedActor.Role.MAINTAINER);
            repository.requireCurrent(current);
        }
    }

    @Test void explicitBootstrapCreatesThreeAccountsAndErasesEveryInput() {
        cleanupBootstrap();
        char[][] inputs = freshPasswords();
        char[][] retained = Arrays.stream(inputs).map(char[]::clone).toArray(char[][]::new);
        try {
            PreviewIdentityBootstrapCli.initialize(ArchiveTestDatabase.dataSource(), inputs[0], inputs[1], inputs[2]);
            for (char[] input : inputs) assertThat(Arrays.equals(input, new char[input.length])).isTrue();
            try (var repository = new PreviewIdentityRepository(ArchiveTestDatabase.dataSource())) {
                var first = repository.authenticate("user-a", retained[0].clone()).orElseThrow().actor();
                var second = repository.authenticate("user-b", retained[1].clone()).orElseThrow().actor();
                var maintainer = repository.authenticate("maintainer", retained[2].clone()).orElseThrow().actor();
                assertThat(first.role()).isEqualTo(TrustedActor.Role.USER);
                assertThat(second.role()).isEqualTo(TrustedActor.Role.USER);
                assertThat(maintainer.role()).isEqualTo(TrustedActor.Role.MAINTAINER);
                assertThat(first.userId()).isNotEqualTo(second.userId()).isNotEqualTo(maintainer.userId());
            }
        } finally {
            for (char[] input : retained) Arrays.fill(input, '\0');
            cleanupBootstrap();
        }
    }

    @Test void bootstrapConflictRollsBackWholeBatchWithoutResettingExistingAccount() {
        cleanupBootstrap();
        char[][] inputs = freshPasswords();
        UUID existing = UUID.randomUUID();
        try {
            try (var connection = ArchiveTestDatabase.dataSource().getConnection()) {
                insertIdentity(connection, existing, "user-b", password);
            }
            assertThatThrownBy(() -> PreviewIdentityBootstrapCli.initialize(
                    ArchiveTestDatabase.dataSource(), inputs[0], inputs[1], inputs[2]))
                    .isInstanceOf(IllegalStateException.class).hasMessage("ACCOUNT_CONFLICT");
            try (var connection = ArchiveTestDatabase.dataSource().getConnection();
                    var query = connection.prepareStatement(
                            "SELECT count(*),min(user_id::text) FROM preview_identity WHERE login_name IN ('user-a','user-b','maintainer')")) {
                try (var row = query.executeQuery()) {
                    row.next();
                    assertThat(row.getInt(1)).isEqualTo(1);
                    assertThat(row.getString(2)).isEqualTo(existing.toString());
                }
            }
            for (char[] input : inputs) assertThat(Arrays.equals(input, new char[input.length])).isTrue();
        } catch (SQLException failure) { throw safeSqlFailure(failure); }
        finally { cleanupBootstrap(); }
    }

    @Test void bootstrapInvalidPasswordIsRejectedBeforeDatabaseAccessAndAllInputsAreErased() throws Exception {
        var source = mock(javax.sql.DataSource.class);
        char[][] inputs = freshPasswords();
        Arrays.fill(inputs[1], '\u4e2d');
        assertThatThrownBy(() -> PreviewIdentityBootstrapCli.initialize(source, inputs[0], inputs[1], inputs[2]))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_INPUT");
        verifyNoInteractions(source);
        for (char[] input : inputs) assertThat(Arrays.equals(input, new char[input.length])).isTrue();
    }

    @Test void bootstrapUnknownCommitIsNotRetriedOrReportedAsSuccess() throws Exception {
        var source = mock(javax.sql.DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(java.sql.PreparedStatement.class);
        var settings = mock(java.sql.Statement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(settings);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        doThrow(new SQLException("fixture", "08006")).when(connection).commit();
        char[][] inputs = freshPasswords();
        assertThatThrownBy(() -> PreviewIdentityBootstrapCli.initialize(source, inputs[0], inputs[1], inputs[2]))
                .isInstanceOf(IllegalStateException.class).hasMessage("INITIALIZATION_UNCONFIRMED")
                .hasNoCause();
        verify(source, times(1)).getConnection();
        verify(statement, times(3)).executeUpdate();
        verify(connection, times(1)).commit();
        verify(connection, times(1)).rollback();
        verify(connection, times(1)).close();
        for (char[] input : inputs) assertThat(Arrays.equals(input, new char[input.length])).isTrue();
    }

    private static char[][] freshPasswords() {
        return new char[][] { UUID.randomUUID().toString().toCharArray(),
                UUID.randomUUID().toString().toCharArray(), UUID.randomUUID().toString().toCharArray() };
    }
    private static void cleanupBootstrap() {
        try (var connection = ArchiveTestDatabase.dataSource().getConnection();
                var query = connection.prepareStatement(
                        "DELETE FROM preview_identity WHERE login_name IN ('user-a','user-b','maintainer')")) {
            query.executeUpdate();
        } catch (SQLException failure) { throw safeSqlFailure(failure); }
    }

    private void change(String fixedAssignment) {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("UPDATE preview_identity SET " + fixedAssignment + " WHERE user_id=?")) {
            p.setObject(1, id);
            assertThat(p.executeUpdate()).isEqualTo(1);
        } catch (SQLException failure) { throw safeSqlFailure(failure); }
    }
    private static void revoked(PreviewIdentityRepository repository, TrustedActor actor) {
        assertThatThrownBy(() -> repository.requireCurrent(actor)).isInstanceOf(PreviewIdentityRepository.Failure.class)
                .extracting(e -> ((PreviewIdentityRepository.Failure)e).code())
                .isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
    }
    static void insertIdentity(Connection connection, UUID id, String login, char[] password) throws SQLException {
        String hash = new BCryptPasswordEncoder().encode(CharBuffer.wrap(password));
        try (var p = connection.prepareStatement(
                "INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES (?,?,?,'USER')")) {
            p.setObject(1, id);
            p.setString(2, login);
            p.setString(3, hash);
            p.executeUpdate();
        }
    }
    static AssertionError safeSqlFailure(SQLException error) {
        // 驱动异常可能携带失败行中的hash；验收输出只保留有限SQLSTATE。
        return new AssertionError("身份测试数据库操作失败 SQLSTATE=" + error.getSQLState());
    }
}
