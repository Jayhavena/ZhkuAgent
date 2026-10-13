package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.zhku.knowledge.rulecheck.RuleCheckEvidenceVerifier.RejectionReason.*;
import static org.assertj.core.api.Assertions.assertThat;

class JdbcRuleCheckEvidenceReaderTest {
    // 合成资料仅存在专用临时库；字段STUDENT_VISIBLE不代表真实学生发布授权。
    private static final String TEXT="合成条款：上限4学分。例外另行审核。";
    private final UUID source=UUID.randomUUID(), chunk=UUID.randomUUID(), run=UUID.randomUUID();
    private final String profile="excerpt-"+UUID.randomUUID();
    private DataSource dataSource;
    private boolean targetVerified;
    private RuleCheckEvidenceSnapshot captured;
    private JdbcRuleCheckEvidenceReader reader;

    @BeforeEach void setUp() throws SQLException {setUpAt(System.getProperty("zhku.db.url"));}

    void setUpAt(String target) throws SQLException {
        targetVerified=false;
        // 会创建ACTIVE，必须在迁移/连接前拒绝原库；不能只靠推荐使用runner。
        RuleCheckDatabaseTestTarget.verify(target);
        targetVerified=true;
        String password=ZhkuDatabaseTestConfig.databasePassword();
        String user=System.getProperty("zhku.db.user","zhkuagent");
        dataSource=new DriverManagerDataSource(target,user,password);
        ZhkuFlywayTestSupport.migrate(target,user,password);
        update("""
                INSERT INTO knowledge_source (id,title,source_type,source_version,publication_status,
                    published_at,content_checksum,visibility_scope,publication_scope,review_notice,
                    sharing_rights_confirmed,document_family,knowledge_scope)
                VALUES (?,'合成原文核验夹具','STUDENT_HANDBOOK','v1','PUBLISHED',CURRENT_TIMESTAMP,?,
                    'STUDENT_VISIBLE','本地验证','非真实审核资料',TRUE,'STUDENT_HANDBOOK','PUBLIC_ELECTIVE')
                """,source,sha(TEXT));
        update("""
                INSERT INTO knowledge_chunk (id,source_id,chunk_index,section_title,source_locator,page_number,content,content_checksum)
                VALUES (?,?,0,'合成条款','合成页1',1,?,?)
                """,chunk,source,TEXT,sha(TEXT));
        update("INSERT INTO embedding_profile (profile_id,provider,model,dimensions,status,activated_at) VALUES (?,'fixture','none',3,'ACTIVE',CURRENT_TIMESTAMP)",profile);
        update("INSERT INTO retrieval_index_run (id,profile_id,chunking_version,status,finished_at,indexed_entries) VALUES (?,?,'span-v1','COMPLETED',CURRENT_TIMESTAMP,1)",run,profile);
        update("INSERT INTO retrieval_index_entry (id,run_id,profile_id,chunk_id,source_version,chunking_version,content_checksum) VALUES (?,?,?,?,'v1','span-v1',?)",UUID.randomUUID(),run,profile,chunk,sha(TEXT));
        captured=new RuleCheckEvidenceSnapshot("ev-1",source,chunk,"v1",sha(TEXT),TEXT,true);
        reader=new JdbcRuleCheckEvidenceReader(dataSource,profile,"span-v1","PUBLIC_ELECTIVE");
    }

    @AfterEach void tearDown() throws SQLException {
        // JUnit在setup拒绝后仍cleanup，不能重新连接已被拒绝的目标。
        if (!targetVerified || dataSource==null) {return;}
        update("DELETE FROM retrieval_index_entry WHERE profile_id=?",profile);
        update("DELETE FROM retrieval_index_run WHERE profile_id=?",profile);
        update("DELETE FROM embedding_profile WHERE profile_id=?",profile);
        update("DELETE FROM knowledge_chunk WHERE source_id=?",source);
        update("DELETE FROM knowledge_source WHERE id=?",source);
    }

    @Test void currentReadAndVerifierReturnActualServerText() {
        assertThat(reader.read(source,chunk)).isPresent();
        var current=reader.read(source,chunk).orElseThrow();
        assertThat(current.sourceId()).isEqualTo(source);assertThat(current.chunkId()).isEqualTo(chunk);
        assertThat(current.text()).isEqualTo(TEXT);assertThat(current.revision()).isEqualTo("v1");
        var result=verify();assertThat(result.accepted()).isTrue();assertThat(result.excerpt().text()).isEqualTo("上限4学分。");
    }
    @Test void locatedReadBindsCurrentMetadataToTheSameQualifiedChunk() throws Exception {
        update("UPDATE knowledge_source SET source_url='https://official.example/manual.pdf' WHERE id=?",source);
        var result=reader.readLocated(source,chunk);assertThat(result).isPresent();
        var row=result.orElseThrow();assertThat(row.snapshot().text()).isEqualTo(TEXT);assertThat(row.snapshot().revision()).isEqualTo("v1");
        assertThat(row.title()).isEqualTo("合成原文核验夹具");assertThat(row.sourceUrl()).isEqualTo("https://official.example/manual.pdf");
        assertThat(row.sourceLocator()).isEqualTo("合成页1");assertThat(row.recordedPageNumber()).isEqualTo(1);assertThat(row.sectionTitle()).isEqualTo("合成条款");
    }
    @Test void originalLookupRechecksWithdrawalVisibilityAndSharing() throws Exception {
        var service=new RuleCheckOriginalEvidenceService(reader::readLocated,"v1",java.util.Set.of());
        var quote=new RuleCheckVerifiedExcerpt("round",source,chunk,"v1",sha(TEXT),0,TEXT.length(),TEXT);
        assertThat(service.locate(quote).code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
        // 撤回/失去分享资格必须同步转内部，不能构造被现有数据库约束禁止的学生可见行。
        for(String sql:List.of("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",
                "UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?","UPDATE knowledge_source SET sharing_rights_confirmed=false,visibility_scope='INTERNAL_ONLY' WHERE id=?")) {
            update(sql,source);var result=service.locate(quote);assertThat(result.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.UNAVAILABLE);assertThat(result.original()).isNull();
            update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE',sharing_rights_confirmed=true WHERE id=?",source);
        }
    }
    @Test void unpublishedSourceCannotReadOrReopenCapturedOriginal() throws SQLException {
        var service=new RuleCheckOriginalEvidenceService(reader::readLocated,"v1",java.util.Set.of());
        var quote=new RuleCheckVerifiedExcerpt("round",source,chunk,"v1",sha(TEXT),0,TEXT.length(),TEXT);
        assertThat(service.locate(quote).code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
        // 未发布必须转内部以满足库约束；历史已核验引文不能绕过当前发布状态。
        for(String status:List.of("DRAFT","REVIEWING")) {
            update("UPDATE knowledge_source SET publication_status=?,visibility_scope='INTERNAL_ONLY' WHERE id=?",status,source);
            assertUnavailable();assertThat(reader.readLocated(source,chunk)).isEmpty();
            var result=service.locate(quote);
            assertThat(result.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.UNAVAILABLE);
            assertThat(result.original()).isNull();
        }
    }
    @Test void oversizedCurrentChunkIsRejectedBeforeReturningDatabaseText() throws Exception {
        String huge="x".repeat(65537);update("UPDATE knowledge_chunk SET content=?,content_checksum=? WHERE id=?",huge,sha(huge),chunk);
        update("UPDATE retrieval_index_entry SET content_checksum=? WHERE chunk_id=?",sha(huge),chunk);
        assertThat(reader.read(source,chunk)).isEmpty();assertThat(reader.readLocated(source,chunk)).isEmpty();
    }
    @Test void unlimitedTextMetadataIsSuppressedInSqlWithoutChangingEvidenceText() throws Exception {
        update("UPDATE knowledge_source SET source_url=? WHERE id=?","x".repeat(4097),source);
        update("UPDATE knowledge_chunk SET section_title=? WHERE id=?","x".repeat(8193),chunk);
        var row=reader.readLocated(source,chunk).orElse(null);assertThat(row).isNotNull();
        assertThat(row.sourceUrl()).isNull();assertThat(row.sectionTitle()).isNull();assertThat(row.snapshot().text()).isEqualTo(TEXT);
    }

    @Test void sourceAndChunkMustBothMatch() {
        assertThat(reader.read(UUID.randomUUID(),chunk)).isEmpty();
        assertThat(reader.read(source,UUID.randomUUID())).isEmpty();
    }

    @Test void withdrawalBetweenReadsBlocksOldQuote() throws SQLException {
        assertThat(reader.read(source,chunk)).isPresent();
        update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",source);
        assertUnavailable();
    }

    @Test void internalVisibilityBlocksOldQuote() throws SQLException {
        update("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?",source);assertUnavailable();
    }

    @Test void unconfirmedSharingAndScopeAreRejected() throws SQLException {
        update("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=FALSE WHERE id=?",source);assertUnavailable();
    }

    @Test void scopeDriftIsRejected() throws SQLException {
        update("UPDATE knowledge_source SET knowledge_scope='OTHER' WHERE id=?",source);assertUnavailable();
    }

    @Test void buildingAndRetiredProfilesCannotRead() throws SQLException {
        for (String status:List.of("BUILDING","RETIRED")) {
            update("UPDATE embedding_profile SET status=? WHERE profile_id=?",status,profile);assertUnavailable();
        }
    }

    @Test void failedOrIncompleteRunCannotRead() throws SQLException {
        for (String status:List.of("RUNNING","FAILED","ROLLED_BACK")) {
            update("UPDATE retrieval_index_run SET status=? WHERE id=?",status,run);assertUnavailable();
        }
    }

    @Test void sourceRevisionWithoutReindexingCannotRead() throws SQLException {
        update("UPDATE knowledge_source SET source_version='v2' WHERE id=?",source);assertUnavailable();
    }

    @Test void reindexedRevisionStillCannotValidateCapturedVersion() throws SQLException {
        update("UPDATE knowledge_source SET source_version='v2' WHERE id=?",source);
        update("UPDATE retrieval_index_entry SET source_version='v2' WHERE profile_id=?",profile);
        assertChanged();
    }

    @Test void storedChecksumDriftCannotRead() throws SQLException {
        update("UPDATE knowledge_chunk SET content_checksum=? WHERE id=?","0".repeat(64),chunk);assertUnavailable();
    }

    @Test void bodyChangeWithStaleStoredHashIsDetectedByVerifier() throws SQLException {
        update("UPDATE knowledge_chunk SET content=? WHERE id=?",TEXT+"新增例外。",chunk);assertChanged();
    }

    @Test void bodyAndBothHashesChangedStillRejectCapturedEvidence() throws SQLException {
        String changed=TEXT+"新增例外。";
        update("UPDATE knowledge_chunk SET content=?,content_checksum=? WHERE id=?",changed,sha(changed),chunk);
        update("UPDATE retrieval_index_entry SET content_checksum=? WHERE profile_id=?",sha(changed),profile);assertChanged();
    }

    @Test void configuredChunkingVersionDoesNotFallBack() throws SQLException {
        update("UPDATE retrieval_index_run SET chunking_version='span-v2' WHERE id=?",run);assertUnavailable();
        update("UPDATE retrieval_index_entry SET chunking_version='span-v2' WHERE profile_id=?",profile);assertUnavailable();
    }

    @Test void configValuesAreBoundParameters() {
        var injected=new JdbcRuleCheckEvidenceReader(dataSource,profile,"span-v1","PUBLIC_ELECTIVE' OR TRUE --");
        assertThat(injected.read(source,chunk)).isEmpty();
    }

    private RuleCheckEvidenceVerifier.VerificationResult verify() {
        String quote="上限4学分。";int start=TEXT.indexOf(quote);
        return new RuleCheckEvidenceVerifier(reader).verify(new RuleCheckEvidenceSpan("ev-1",start,start+quote.length(),quote),List.of(captured));
    }
    private void assertUnavailable() {
        assertThat(reader.read(source,chunk)).isEmpty();var result=verify();
        assertThat(result.accepted()).isFalse();assertThat(result.excerpt()).isNull();assertThat(result.rejectionReason()).isEqualTo(CURRENT_EVIDENCE_UNAVAILABLE);
    }
    private void assertChanged() {
        assertThat(reader.read(source,chunk)).isPresent();var result=verify();
        assertThat(result.accepted()).isFalse();assertThat(result.excerpt()).isNull();assertThat(result.rejectionReason()).isEqualTo(CURRENT_EVIDENCE_CHANGED);
    }
    private void update(String sql,Object...values) throws SQLException {
        try(var connection=dataSource.getConnection();var statement=connection.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++){statement.setObject(i+1,values[i]);}statement.executeUpdate();
        }
    }
    private static String sha(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException failure){throw new AssertionError(failure);}
    }
}
