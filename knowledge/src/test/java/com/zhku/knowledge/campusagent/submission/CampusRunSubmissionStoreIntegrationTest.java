package com.zhku.knowledge.campusagent.submission;

import com.zhku.knowledge.campusagent.archive.*;
import com.zhku.knowledge.security.TrustedActor;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.campusagent.submission.CampusRunSubmission.*;

public class CampusRunSubmissionStoreIntegrationTest {
    @BeforeAll static void migrate() { ArchiveTestDatabase.migrate(); }
    public static final Fingerprint FP = new Fingerprint(1,"a".repeat(64));
    @Test void oneTransactionCreatesThenReplayHasNoDispatchCapabilityAndOtherUserCannotRead() throws Exception {
        try(var env=new Env(null)) {
            var key=UUID.randomUUID(); var id=UUID.randomUUID();
            var created=env.store.admitSubmissionOwned(env.a,new Command(key,id,FP));
            var replay=env.store.admitSubmissionOwned(env.a,new Command(key,UUID.randomUUID(),FP));
            assertThat(created.decision()).isEqualTo(Decision.CREATED);
            assertThat(created.admission().runId()).isEqualTo(id);
            assertThat(replay.decision()).isEqualTo(Decision.REPLAYED); assertThat(replay.admission()).isNull();
            assertThat(replay.record()).isEqualTo(created.record());
            assertThat(env.store.findSubmissionOwned(env.b,key)).isEmpty();
            var other=env.store.admitSubmissionOwned(env.b,new Command(key,UUID.randomUUID(),FP));
            assertThat(other.record().runId()).isNotEqualTo(id);
            assertThat(env.count("campus_agent_run_submission")).isEqualTo(2);
            assertThat(env.count("campus_agent_run_archive")).isEqualTo(2);
            assertThat(env.store.find(id)).isEmpty();
            assertThatThrownBy(()->env.store.admitSubmissionOwned(env.a,new Command(key,UUID.randomUUID(),
                    new Fingerprint(1,"b".repeat(64))))) .isInstanceOf(Failure.class)
                    .extracting(e->((Failure)e).code()).isEqualTo(Code.SUBMISSION_CONTENT_CONFLICT);
        }
    }
    @Test void commitBeforeFailureAndLostAckRemainTruthfulAndNeverAcquireAdmissionViaRead() throws Exception {
        for(boolean after: new boolean[]{false,true}) {
            var armed=new AtomicBoolean(false);
            try(var env=new Env(ds->fault(ds,armed,after))) {
                armed.set(true);var key=UUID.randomUUID();
                assertThatThrownBy(()->env.store.admitSubmissionOwned(env.a,new Command(key,UUID.randomUUID(),FP)))
                        .isInstanceOf(CampusRunArchive.Failure.class);
                assertThat(env.store.isAccepting()).isFalse();
                assertThat(env.count("campus_agent_run_submission")).isEqualTo(after?1:0);
                assertThat(env.count("campus_agent_run_archive")).isEqualTo(after?1:0);
                assertThat(env.store.findSubmissionOwned(env.a,key).isPresent()).isEqualTo(after);
            }
        }
    }
    /** 测试侧真实提交代理；ACK丢失先commit，再抛异常，不能用Mock响应替代这个窗口。 */
    public static DataSource fault(DataSource source,AtomicBoolean armed,boolean after) {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(proxy,m,args)->{
            Object result=invoke(source,m,args);
            if(!m.getName().equals("getConnection"))return result;
            var c=(Connection)result;
            var write=new AtomicBoolean();
            return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,cm,ca)->{
                if(cm.getName().equals("prepareStatement")&&((String)ca[0]).stripLeading().startsWith("INSERT INTO campus_agent_run_archive"))write.set(true);
                if(cm.getName().equals("commit")&&write.get()&&armed.compareAndSet(true,false)) {
                    if(after)invoke(c,cm,ca);
                    throw new java.sql.SQLTransientConnectionException("SYNTHETIC_COMMIT_CONFIRMATION_LOSS");
                }
                return invoke(c,cm,ca);
            });
        });
    }
    private static Object invoke(Object object,java.lang.reflect.Method method,Object[] args)throws Throwable {
        try{return method.invoke(object,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
    public static final class Env implements AutoCloseable {
        public final DataSource source;
        public final CampusRunArchiveOwner owner;
        public final JdbcCampusRunArchiveStore store;
        public final TrustedActor a=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
        public final TrustedActor b=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
        public Env(java.util.function.Function<DataSource,DataSource> decorator)throws Exception {
            ArchiveTestDatabase.migrate();source=ArchiveTestDatabase.dataSource();
            owner=new CampusRunArchiveOwner(source,CampusRunArchive.Limits.defaults());owner.start();
            try(var c=source.getConnection();var p=c.prepareStatement("INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES(?,?,?,'USER')")) {
                for(var actor:java.util.List.of(a,b)) {
                    p.setObject(1,actor.userId());p.setString(2,"t"+UUID.randomUUID().toString().replace("-","").substring(0,20));
                    p.setString(3,"$2a$10$"+"a".repeat(53));p.addBatch();
                } p.executeBatch();
            }
            store=new JdbcCampusRunArchiveStore(decorator==null?source:decorator.apply(source),owner,
                    CampusRunArchive.Limits.defaults(),new CampusRunHistoryVisibility());
        }
        public int count(String table)throws Exception {
            if(!java.util.Set.of("campus_agent_run_submission","campus_agent_run_archive").contains(table))throw new IllegalArgumentException();
            try(var c=source.getConnection();var p=c.prepareStatement("SELECT count(*) FROM "+table+" WHERE owner_user_id IN (?,?)")) {
                p.setObject(1,a.userId());p.setObject(2,b.userId());try(var r=p.executeQuery()){r.next();return r.getInt(1);}
            }
        }
        @Override public void close()throws Exception {
            store.close(Duration.ofSeconds(2));
            try(var c=source.getConnection()) {c.setAutoCommit(false);
                for(String table:java.util.List.of("campus_agent_run_submission","campus_agent_run_archive","preview_identity")) {
                    try(var p=c.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("preview_identity")?"user_id":"owner_user_id")+" IN (?,?)")){
                        p.setObject(1,a.userId());p.setObject(2,b.userId());p.executeUpdate();
                    }
                }c.commit();
            }
        }
    }
}
