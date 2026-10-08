package com.zhku.knowledge.campusagent.submission;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampusRunSubmissionSchemaIntegrationTest {
    @Test void retainedKeyFindsOriginalUntilDatabaseExpiryAndCanBeReusedOnlyAfterAtomicPrune()throws Exception {
        try(var env=new CampusRunSubmissionStoreIntegrationTest.Env(null)) {
            var key=java.util.UUID.randomUUID();var id=java.util.UUID.randomUUID();
            env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(key,id,CampusRunSubmissionStoreIntegrationTest.FP));
            try(var c=env.source.getConnection();var p=c.prepareStatement("""
                UPDATE campus_agent_run_archive SET archive_status='INTERRUPTED',closed_at=clock_timestamp()-interval '1 minute',
                retained_until=clock_timestamp()+interval '30 seconds',interruption_reason='PREVIOUS_OWNER_RESULT_UNCONFIRMED' WHERE run_id=?
                """)){p.setObject(1,id);assertThat(p.executeUpdate()).isEqualTo(1);}
            assertThat(env.store.findSubmissionOwned(env.a,key).orElseThrow().runId()).isEqualTo(id);
            assertThat(env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(key,java.util.UUID.randomUUID(),CampusRunSubmissionStoreIntegrationTest.FP)).decision())
                    .isEqualTo(CampusRunSubmission.Decision.REPLAYED);
            try(var c=env.source.getConnection();var p=c.prepareStatement("UPDATE campus_agent_run_archive SET retained_until=clock_timestamp()-interval '1 second' WHERE run_id=?")) {
                p.setObject(1,id);assertThat(p.executeUpdate()).isEqualTo(1);
            }
            assertThat(env.store.findSubmissionOwned(env.a,key)).isEmpty();assertThat(env.count("campus_agent_run_submission")).isEqualTo(1);
            var next=java.util.UUID.randomUUID();var claim=env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(key,next,CampusRunSubmissionStoreIntegrationTest.FP));
            assertThat(claim.decision()).isEqualTo(CampusRunSubmission.Decision.CREATED);assertThat(claim.record().runId()).isEqualTo(next);
            assertThat(env.store.findOwned(env.a,id)).isEmpty();assertThat(env.count("campus_agent_run_submission")).isEqualTo(1);
        }
    }
    @BeforeAll static void migrate() { ArchiveTestDatabase.migrate(); }
    @Test void submissionTableContainsOnlyAtomicReferenceAndFingerprintMetadata() throws Exception {
        try(var c=ArchiveTestDatabase.dataSource().getConnection();var s=c.createStatement();
            var rows=s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='campus_agent_run_submission' ORDER BY ordinal_position")) {
            var columns=new java.util.ArrayList<String>(); while(rows.next())columns.add(rows.getString(1));
            assertThat(columns).containsExactly("owner_user_id","task_kind","submission_key","run_id",
                    "fingerprint_version","request_sha256","accepted_at");
        }
    }
    @Test void databaseRejectsDuplicateKeysMismatchedOwnershipAndAnyUpdate() throws Exception {
        try(var env=new CampusRunSubmissionStoreIntegrationTest.Env(null)) {
            var key=java.util.UUID.randomUUID();var id=java.util.UUID.randomUUID();
            env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(key,id,CampusRunSubmissionStoreIntegrationTest.FP));
            try(var c=env.source.getConnection()) {
                for(String sql:java.util.List.of(
                        "INSERT INTO campus_agent_run_submission SELECT * FROM campus_agent_run_submission WHERE run_id='"+id+"'",
                        "INSERT INTO campus_agent_run_submission(owner_user_id,task_kind,submission_key,run_id,fingerprint_version,request_sha256) VALUES('"+env.b.userId()+"','CAMPUS_CREDIT_SUMMARY','"+java.util.UUID.randomUUID()+"','"+id+"',1,'"+"a".repeat(64)+"')",
                        "UPDATE campus_agent_run_submission SET request_sha256='"+"b".repeat(64)+"' WHERE run_id='"+id+"'",
                        "DELETE FROM campus_agent_run_archive WHERE run_id='"+id+"'")) {
                    try(var s=c.createStatement()){assertThatThrownBy(()->s.executeUpdate(sql)).isInstanceOf(java.sql.SQLException.class);}
                }
            }
            assertThat(env.count("campus_agent_run_submission")).isEqualTo(1);
        }
    }
    @Test void pruneDeletesSameExpiredBatchAndFailureRollsBackBothTablesWhileActiveIsKept()throws Exception {
        try(var env=new CampusRunSubmissionStoreIntegrationTest.Env(null)) {
            var expired=java.util.UUID.randomUUID();var active=java.util.UUID.randomUUID();
            env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(java.util.UUID.randomUUID(),expired,CampusRunSubmissionStoreIntegrationTest.FP));
            env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(java.util.UUID.randomUUID(),active,CampusRunSubmissionStoreIntegrationTest.FP));
            try(var c=env.source.getConnection();var p=c.prepareStatement("""
                    UPDATE campus_agent_run_archive SET archive_status='INTERRUPTED',closed_at=clock_timestamp()-interval '8 days',
                    retained_until=clock_timestamp()-interval '1 day',interruption_reason='PREVIOUS_OWNER_RESULT_UNCONFIRMED' WHERE run_id=?
                    """)){p.setObject(1,expired);p.executeUpdate();}
            String name="test_prune_"+java.util.UUID.randomUUID().toString().replace("-","");
            try(var c=env.source.getConnection();var s=c.createStatement()) {
                s.execute("CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.run_id='"+expired+"'::uuid THEN RAISE EXCEPTION 'SYNTHETIC_PRUNE_FAILURE'; END IF; RETURN OLD; END; $$");
                s.execute("CREATE TRIGGER "+name+" BEFORE DELETE ON campus_agent_run_archive FOR EACH ROW EXECUTE FUNCTION "+name+"()");
                try {
                    assertThatThrownBy(()->env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),CampusRunSubmissionStoreIntegrationTest.FP)))
                            .isInstanceOf(com.zhku.knowledge.campusagent.archive.CampusRunArchive.Failure.class);
                    assertThat(env.count("campus_agent_run_submission")).isEqualTo(2);assertThat(env.count("campus_agent_run_archive")).isEqualTo(2);
                } finally {s.execute("DROP TRIGGER "+name+" ON campus_agent_run_archive");s.execute("DROP FUNCTION "+name+"()");}
            }
            env.store.close(java.time.Duration.ofSeconds(2));
            // 另一个真实owner重启：同事务关闭ADMITTED并只清理到期关联，没有补派发。
            var owner=new com.zhku.knowledge.campusagent.archive.CampusRunArchiveOwner(env.source,com.zhku.knowledge.campusagent.archive.CampusRunArchive.Limits.defaults());
            try {owner.start();assertThat(env.count("campus_agent_run_submission")).isEqualTo(1);assertThat(env.count("campus_agent_run_archive")).isEqualTo(1);
                try(var c=env.source.getConnection();var p=c.prepareStatement("SELECT archive_status FROM campus_agent_run_archive WHERE run_id=?")) {
                    p.setObject(1,active);try(var r=p.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("INTERRUPTED");}
                }
            }finally{owner.close(java.time.Duration.ofSeconds(2));}
        }
    }
}
