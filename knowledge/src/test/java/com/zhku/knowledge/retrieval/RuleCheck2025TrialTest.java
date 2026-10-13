package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import javax.sql.DataSource;
import java.util.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 真实2025文本的显式专用库验收；固定选题及词面查询不冒充真实模型/用户评测。 */
class RuleCheck2025TrialTest {
    private Reviewed2025Trial trial;
    private DataSource dataSource;
    @BeforeEach void setUp() throws Exception {
        trial=new Reviewed2025Trial(Path.of("..").toAbsolutePath().normalize());
        String target=System.getProperty("zhku.db.url");
        RuleCheckDatabaseTestTarget.verify(target);
        String user=System.getProperty("zhku.db.user","zhkuagent");
        dataSource=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());
    }
    @AfterEach void cleanUp() throws Exception {if(trial!=null)trial.close();}
    private void start() throws Exception {trial.importAndBind(dataSource);}

    @Test void reviewedVerbatimPackageHasTenActualDatabaseBindings() throws Exception {
        start();assertThat(trial.bindings).hasSize(10);assertThat(trial.catalogue).hasSize(16);
        for(var b:trial.bindings.values()) {
            var current=trial.reader().read(trial.source,b.chunkId()).orElseThrow();
            assertThat(current.sourceId()).isEqualTo(b.sourceId());assertThat(current.text()).isEqualTo(b.text());
            assertThat(current.revision()).isEqualTo(Reviewed2025Trial.REVISION);assertThat(current.checksum()).isEqualToIgnoringCase(b.checksum());
        }
        try(var c=dataSource.getConnection();var p=c.prepareStatement("SELECT count(*),count(embedding),count(DISTINCT run_id) FROM retrieval_index_entry WHERE profile_id=?")) {
            p.setString(1,trial.profile);try(var rows=p.executeQuery()) {rows.next();assertThat(rows.getInt(1)).isEqualTo(10);assertThat(rows.getInt(2)).isZero();assertThat(rows.getInt(3)).isEqualTo(1);}
        }
        trial.save("actual-binding.json",Map.of("lifetime","ACTUAL_TEMPORARY_DB_ROWS_REMOVED_AFTER_TEST", "sourceId",trial.source,"profileId",trial.profile,"runId",trial.run,
                "indexMode","LEXICAL_ONLY_EMBEDDINGS_NULL_NO_PROVIDER", "bindings",trial.bindings.values(),"catalogue",trial.catalogue,
                "originalFileSha256",trial.bundle.path("sourceFileSha256").asText(),"sourceChecksumMeaning","发布器生成条款集合checksum；不是整本PDF的checksum"));
    }

    @Test void tenArticleLookupsUseActualRetrievalAuditAndOriginalLocation() throws Exception {
        start();var cases=new ArrayList<Object>();
        for(int i=0;i<10;i++) {
            var rule=trial.semantics.path("rules").get(i);int article=rule.path("article").asInt();
            String id=rule.path("reviewItemId").asText()+(article==18?"/COMBINED":"/GENERAL");
            // 条款号是显式词面基线；没有改写自然语言问题，也没有读取20题来硬编码选题。
            String query=trial.bindings.get(article).text().substring(0,3);
            var service=trial.service(id);var result=service.checkWithContext(query,article==18?Map.of("courseType","PUBLIC"):Map.of());
            assertThat(result.decision().status()).isEqualTo(ANSWERED);assertThat(service.isCurrent(result)).isTrue();
            var originals=new ArrayList<Object>();
            for(var fact:result.decision().confirmedFacts())for(var quote:fact.evidence()) {
                var b=trial.bindings.get(article);assertThat(quote.sourceId()).isEqualTo(trial.source);assertThat(quote.chunkId()).isEqualTo(b.chunkId());
                assertThat(quote.text()).isEqualTo(b.text());assertThat(service.isReferenceApproved(quote)).isTrue();
                var located=trial.originals().locate(quote);assertThat(located.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
                assertThat(located.original().sourceLocator()).isEqualTo(b.locator());assertThat(located.original().recordedPageNumber()).isEqualTo(b.physicalPage());
                assertThat(located.original().sourceLocator()).contains("印刷第"+b.printedPage()+"页");assertThat(located.original().safeSourceUrl()).isNull();
                originals.add(located);
                try(var c=dataSource.getConnection();var p=c.prepareStatement("SELECT e.chunk_id,e.run_id FROM citation_audit a JOIN retrieval_index_entry e ON a.index_entry_id=e.id WHERE a.citation_id=?")) {
                    p.setString(1,quote.citationId());try(var rows=p.executeQuery()) {assertThat(rows.next()).isTrue();assertThat(rows.getObject(1,UUID.class)).isEqualTo(b.chunkId());assertThat(rows.getObject(2,UUID.class)).isEqualTo(trial.run);assertThat(rows.next()).isFalse();}
                }
            }
            cases.add(Map.of("article",article,"query",query,"selectionMode","EXPLICIT_TEST_CATALOGUE_ID_NOT_SEMANTIC_ROUTING", "result",result,"originals",originals));
        }
        trial.save("actual-flow.json",cases);
    }

    @Test void actualArticle18ClarifiesAndSeparatesRetakeDeferredAndCombinedScopes() throws Exception {
        start();var combined=trial.service("S10/COMBINED");var unknown=combined.checkWithContext("缓考",Map.of());
        assertThat(unknown.decision().status()).isEqualTo(NEED_CLARIFICATION);assertThat(unknown.continuation()).isNotNull();
        var publicBranch=combined.resume(unknown.continuation(),Map.of("courseType","PUBLIC"));
        assertThat(publicBranch.decision().status()).isEqualTo(ANSWERED);assertThat(publicBranch.decision().confirmedFacts()).hasSize(2);
        var professional=combined.resume(unknown.continuation(),Map.of("courseType","PROFESSIONAL"));
        assertThat(professional.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(professional.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly("专业选修课成绩不合格者可申请重修学习。");
        assertThat(professional.decision().unknownItems()).contains("专业选修课缓考依据未取得");
        var retake=trial.service("S10/RETAKE_ONLY").checkWithContext("重修",Map.of("courseType","PROFESSIONAL"));
        assertThat(retake.decision().status()).isEqualTo(ANSWERED);assertThat(retake.decision().unknownItems()).isEmpty();
        var deferred=trial.service("S10/DEFERRED_EXAM_ONLY").checkWithContext("缓考",Map.of("courseType","PROFESSIONAL"));
        assertThat(deferred.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(deferred.decision().confirmedFacts()).isEmpty();
        trial.save("actual-branch-flow.json",Map.of("selectionMode","EXPLICIT_TEST_CATALOGUE_ID", "unknown",unknown,"public",publicBranch,"professionalCombined",professional,"retakeOnly",retake,"deferredOnly",deferred));
    }

    @Test void personalAndCurrentScopesCannotTurnGeneralRulesIntoWholeTaskAnswers() throws Exception {
        start();var cases=new LinkedHashMap<String,Object>();
        for(var pair:List.of(List.of("S04/ACTUAL_CLASS_OPENING","低于"),List.of("S05/PERSONAL_CREDIT_DEFICIT","网络"),List.of("S07/PERSONAL_SELECTION_ELIGIBILITY","每学期"))) {
            var scoped=trial.service(pair.get(0)).checkWithContext(pair.get(1),Map.of());assertThat(scoped.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
            var general=trial.service(pair.get(0).substring(0,3)+"/GENERAL").checkWithContext(pair.get(1),Map.of());assertThat(general.decision().status()).isEqualTo(ANSWERED);
            cases.put(pair.get(0),Map.of("specific",scoped,"general",general));
        }
        var current=trial.service("S06/CURRENT_ENTRY_OR_TIME").checkWithContext("网上选课",Map.of());
        assertThat(current.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(current.decision().confirmedFacts()).isEmpty();
        cases.put("S06/CURRENT_ENTRY_OR_TIME",current);trial.save("actual-intent-flow.json",cases);
    }

    @Test void withdrawalInvalidatesActualAnswerAndPreviouslyLocatedOriginal() throws Exception {
        start();var service=trial.service("S07/GENERAL");var before=service.checkWithContext("每学期",Map.of());
        assertThat(before.decision().status()).isEqualTo(ANSWERED);var quote=before.decision().confirmedFacts().getFirst().evidence().getFirst();
        assertThat(trial.originals().locate(quote).code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
        trial.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",trial.source);
        assertThat(service.isCurrent(before)).isFalse();var original=trial.originals().locate(quote);
        assertThat(original.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.UNAVAILABLE);assertThat(original.original()).isNull();
        var after=service.checkWithContext("每学期",Map.of());assertThat(after.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        trial.save("actual-withdrawal.json",Map.of("before",before,"originalAfter",original,"answerAfter",after,"previousAnswerCurrent",false));
    }

    @Test void versionBodyAndCatalogueDriftCannotReuseActualBindings() throws Exception {
        start();var service=trial.service("S07/GENERAL");var before=service.checkWithContext("每学期",Map.of());
        assertThat(before.decision().status()).isEqualTo(ANSWERED);var quote=before.decision().confirmedFacts().getFirst().evidence().getFirst();
        trial.update("UPDATE knowledge_source SET source_version='2026-unreviewed' WHERE id=?",trial.source);
        assertThat(service.isCurrent(before)).isFalse();assertThat(trial.originals().locate(quote).original()).isNull();
        trial.update("UPDATE knowledge_source SET source_version=? WHERE id=?",Reviewed2025Trial.REVISION,trial.source);
        trial.update("UPDATE knowledge_chunk SET content=content||'未经审核的附加内容' WHERE id=?",quote.chunkId());
        assertThat(service.isCurrent(before)).isFalse();assertThat(trial.originals().locate(quote).original()).isNull();
        trial.update("UPDATE knowledge_chunk SET content=? WHERE id=?",trial.bindings.get(10).text(),quote.chunkId());
        assertThat(service.isCurrent(before)).isTrue();trial.catalogue.clear();
        assertThat(service.isCurrent(before)).isFalse();assertThat(service.isReferenceApproved(quote)).isFalse();
        trial.save("actual-drift.json",Map.of("versionChangeRejected",true,"staleHashBodyChangeRejected",true,"restoredOriginalCurrent",true,"catalogueWithdrawalRejected",true));
    }

    @Test void lostSharingQualificationIsRejectedBeforeBusinessImport() throws Exception {
        ((ObjectNode)trial.approval).put("sharingRightsConfirmed",false);
        assertThatThrownBy(this::start).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("资格");assertNoSource();
    }
    @Test void changedSemanticFactCannotBeApprovedJustBecauseCitationIsValid() throws Exception {
        ((ObjectNode)trial.semantics.path("rules").get(6).path("facts").get(0)).put("text","每学期上限40学分。");
        assertThatThrownBy(this::start).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("主张发生变化");assertNoSource();
    }
    @Test void changedUseNoticeCannotBorrowPreviousSharingApproval() throws Exception {
        ((ObjectNode)trial.approval.path("allowedUseIfConfirmed")).put("sourceNotice","本年度适用，已获教务官方背书。");
        assertThatThrownBy(this::start).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("许可范围");assertNoSource();
    }
    @Test void changedPhysicalPageOrArticleLocatorCannotBorrowBodyApproval() throws Exception {
        ((ObjectNode)trial.bundle.path("chunks").get(6)).put("sourceLocator","PDF物理第1页／印刷第65页，第18条");
        assertThatThrownBy(this::start).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("定位");assertNoSource();
    }
    private void assertNoSource() throws Exception {
        try(var c=dataSource.getConnection();var p=c.prepareStatement("SELECT count(*) FROM knowledge_source WHERE id=?")) {
            p.setObject(1,trial.source);try(var rows=p.executeQuery()){rows.next();assertThat(rows.getInt(1)).isZero();}
        }
    }
}
