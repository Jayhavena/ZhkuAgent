package com.zhku.knowledge.campusagent.archive;

import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.TrustedActor;
import java.lang.reflect.*;
import java.nio.CharBuffer;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;

/** 专用真库归属/过滤测试；提交ACK丢失用连接代理注入，不冒充真实网络故障。 */
@Timeout(20)
class CampusRunOwnershipIntegrationTest {
    private final TrustedActor a = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
    private final TrustedActor b = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
    private final List<UUID> ids = new ArrayList<>();
    private CampusRunArchiveOwner owner;
    private JdbcCampusRunArchiveStore store;
    @BeforeAll static void migrate() { ArchiveTestDatabase.migrate(); }
    @BeforeEach void open() {
        try (var connection = ArchiveTestDatabase.dataSource().getConnection()) {
            account(connection, a); account(connection, b);
        } catch (SQLException failure) { throw safeSql(failure); }
        owner = new CampusRunArchiveOwner(ArchiveTestDatabase.dataSource(), Limits.defaults());
        owner.start();
        store = new JdbcCampusRunArchiveStore(ArchiveTestDatabase.dataSource(), owner, Limits.defaults(),
                new CampusRunHistoryVisibility());
    }
    @AfterEach void cleanup() {
        if (store != null) store.close(Duration.ofSeconds(2));
        try (var connection = ArchiveTestDatabase.dataSource().getConnection();
                var remove = connection.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
            for (UUID id : ids) { remove.setObject(1, id); remove.executeUpdate(); }
            try (var accounts = connection.prepareStatement("DELETE FROM preview_identity WHERE user_id IN (?,?)")) {
                accounts.setObject(1, a.userId()); accounts.setObject(2, b.userId()); accounts.executeUpdate();
            }
        } catch (SQLException failure) { throw safeSql(failure); }
    }
    private UUID nextId() { UUID id=UUID.randomUUID(); ids.add(id); return id; }
    @Test void admissionAtomicallyIncludesOwnerAndOtherAndAnonymousQueriesCannotFindIt() {
        UUID id=nextId();
        var admission=store.admitOwned(id,a);
        assertThat(admission.runId()).isEqualTo(id);
        assertThat(ownerOf(id)).isEqualTo(a.userId());
        assertThat(store.findOwned(a,id)).isPresent();
        assertThat(store.findOwned(b,id)).isEmpty();
        assertThat(store.findOwned(b,UUID.randomUUID())).isEmpty();
        assertThat(store.find(id)).isEmpty();
        assertThat(store.list(100,null).items()).extracting(CampusRunArchive::runId).doesNotContain(id);
    }
    @Test void ownedCannotReadNullOwnerAndOldAnonymousBehaviorIsPreserved() {
        UUID id=nextId(); store.admit(id);
        assertThat(ownerOf(id)).isNull();
        assertThat(store.find(id)).isPresent();
        assertThat(store.findOwned(a,id)).isEmpty();
        assertThat(store.listOwned(a,100,null).items()).isEmpty();
    }
    @Test void paginationFiltersOwnersBeforeLimitAndHistoryUsesOwnedBoundary() {
        UUID first=nextId(), second=nextId(), other=nextId(), anonymous=nextId();
        store.admitOwned(first,a); store.admitOwned(other,b); store.admit(anonymous); store.admitOwned(second,a);
        var history=new CampusRunHistoryService(store);
        var page=history.listOwned(a,1,null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNotNull();
        var next=history.listOwned(a,1,page.nextCursor());
        assertThat(next.items()).hasSize(1);
        assertThat(next.nextCursor()).isNull();
        assertThat(List.of(page.items().getFirst().runId(),next.items().getFirst().runId()))
                .containsExactlyInAnyOrder(first,second);
        assertThat(history.findOwned(a,first).runId()).isEqualTo(first);
        assertThatThrownBy(()->history.findOwned(b,first)).isInstanceOf(Failure.class)
                .extracting(e->((Failure)e).code()).isEqualTo(Code.ARCHIVE_NOT_FOUND);
    }
    @Test void revokedRevisionDeniesOwnedAdmissionAndReadsWithoutClaimingOldRecords() {
        UUID existing=nextId(), rejected=nextId(); store.admitOwned(existing,a);
        try (var connection=ArchiveTestDatabase.dataSource().getConnection();
                var update=connection.prepareStatement("UPDATE preview_identity SET enabled=false,auth_revision=auth_revision+1 WHERE user_id=?")) {
            update.setObject(1,a.userId()); update.executeUpdate();
        } catch(SQLException failure){throw safeSql(failure);}
        for (Runnable action:List.<Runnable>of(()->store.admitOwned(rejected,a),()->store.findOwned(a,existing),
                ()->store.listOwned(a,1,null))) {
            assertThatThrownBy(action::run).isInstanceOf(PreviewIdentityRepository.Failure.class)
                    .extracting(e->((PreviewIdentityRepository.Failure)e).code()).isEqualTo(PreviewIdentityRepository.Code.AUTH_REVOKED);
        }
        assertThat(rowCount(rejected)).isZero();
        assertThat(ownerOf(existing)).isEqualTo(a.userId());
        assertThat(store.isAccepting()).isTrue();
    }
    @Test void maintainerRoleIsRejectedBeforeAnyRunIsInserted() {
        UUID id=nextId();
        var maintainer=new TrustedActor(a.userId(),TrustedActor.Role.MAINTAINER,0);
        assertThatThrownBy(()->store.admitOwned(id,maintainer)).isInstanceOf(PreviewIdentityRepository.Failure.class)
                .extracting(e->((PreviewIdentityRepository.Failure)e).code()).isEqualTo(PreviewIdentityRepository.Code.ROLE_DENIED);
        assertThat(rowCount(id)).isZero();
    }
    @Test void committedAdmissionWithLostAckKeepsOwnerButNeverRetriesOrClaimsConfirmation() {
        var commits=new AtomicInteger();
        var injected=commitAckLost(ArchiveTestDatabase.dataSource(),commits);
        var failing=new JdbcCampusRunArchiveStore(injected,owner,Limits.defaults(),new CampusRunHistoryVisibility());
        UUID id=nextId();
        try {
            assertThatThrownBy(()->failing.admitOwned(id,a)).isInstanceOf(Failure.class)
                    .extracting(e->((Failure)e).code()).isEqualTo(Code.ARCHIVE_ADMISSION_UNCONFIRMED);
            assertThat(commits).hasValue(1);
            assertThat(rowCount(id)).isEqualTo(1);
            assertThat(ownerOf(id)).isEqualTo(a.userId());
            assertThat(failing.isAccepting()).isFalse();
        } finally { failing.close(Duration.ofSeconds(2)); }
    }
    @Test void concurrentDifferentUsersCannotOversellFinalSlot() throws Exception {
        long existing;
        try(var connection=ArchiveTestDatabase.dataSource().getConnection(); var statement=connection.createStatement();
                var row=statement.executeQuery("SELECT count(*) FROM campus_agent_run_archive")){
            row.next(); existing=row.getLong(1);
        }
        var limits=new Limits(Duration.ofSeconds(2),Duration.ofSeconds(5),Duration.ofSeconds(5),Duration.ofDays(7),(int)existing+1);
        var first=new JdbcCampusRunArchiveStore(ArchiveTestDatabase.dataSource(),owner,limits,new CampusRunHistoryVisibility());
        var second=new JdbcCampusRunArchiveStore(ArchiveTestDatabase.dataSource(),owner,limits,new CampusRunHistoryVisibility());
        var entered=new CountDownLatch(2); var go=new CountDownLatch(1);
        var callers=Executors.newFixedThreadPool(2);
        UUID left=nextId(),right=nextId();
        try {
            var l=callers.submit(()->compete(first,left,a,entered,go));
            var r=callers.submit(()->compete(second,right,b,entered,go));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(List.of(l.get(10,TimeUnit.SECONDS),r.get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(rowCount(left)+rowCount(right)).isEqualTo(1);
            if(rowCount(left)==1) assertThat(ownerOf(left)).isEqualTo(a.userId());
            if(rowCount(right)==1) assertThat(ownerOf(right)).isEqualTo(b.userId());
        } finally {
            go.countDown(); callers.shutdownNow();
            assertThat(callers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
            first.close(Duration.ofSeconds(2)); second.close(Duration.ofSeconds(2));
        }
    }
    private static boolean compete(JdbcCampusRunArchiveStore store,UUID id,TrustedActor actor,
            CountDownLatch entered,CountDownLatch go)throws InterruptedException{
        entered.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new AssertionError("竞争屏障未释放");
        try {store.admitOwned(id,actor);return true;}
        catch(Failure failure){if(failure.code()==Code.ARCHIVE_CAPACITY_EXCEEDED)return false; throw failure;}
    }
    private UUID ownerOf(UUID id){
        try(var connection=ArchiveTestDatabase.dataSource().getConnection();
                var query=connection.prepareStatement("SELECT owner_user_id FROM campus_agent_run_archive WHERE run_id=?")){
            query.setObject(1,id); try(var row=query.executeQuery()){return row.next()?row.getObject(1,UUID.class):null;}
        }catch(SQLException failure){throw safeSql(failure);}
    }
    private int rowCount(UUID id){
        try(var connection=ArchiveTestDatabase.dataSource().getConnection();
                var query=connection.prepareStatement("SELECT count(*) FROM campus_agent_run_archive WHERE run_id=?")){
            query.setObject(1,id); try(var row=query.executeQuery()){row.next();return row.getInt(1);}
        }catch(SQLException failure){throw safeSql(failure);}
    }
    private static void account(Connection connection,TrustedActor actor)throws SQLException{
        char[] transientPassword=UUID.randomUUID().toString().toCharArray();
        String hash;
        try{hash=new BCryptPasswordEncoder().encode(CharBuffer.wrap(transientPassword));}
        finally{Arrays.fill(transientPassword,'\0');}
        try(var insert=connection.prepareStatement("INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES (?,?,?,'USER')")){
            insert.setObject(1,actor.userId());insert.setString(2,"user-"+actor.userId().toString().substring(0,8));insert.setString(3,hash);insert.executeUpdate();
        }
    }
    private static DataSource commitAckLost(DataSource delegate,AtomicInteger commits){
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(proxy,method,args)->{
            try{
                Object value=method.invoke(delegate,args);
                if(method.getName().equals("getConnection")){
                    var connection=(Connection)value;
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                        try{
                            Object result=m.invoke(connection,a);
                            if(m.getName().equals("commit")){commits.incrementAndGet();throw new SQLException("", "08006");}
                            return result;
                        }catch(InvocationTargetException failure){throw failure.getCause();}
                    });
                }
                return value;
            }catch(InvocationTargetException failure){throw failure.getCause();}
        });
    }
    private static AssertionError safeSql(SQLException failure){return new AssertionError("归属测试数据库失败 SQLSTATE="+failure.getSQLState());}
}
