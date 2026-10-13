package com.zhku.knowledge.retrieval;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 真实已审核原文及实际SQL；测试问句固定，不评分Agent自然语言能力。 */
@Timeout(90)
class RuleAgentEvidenceDatabaseTest {
    @Test void actualSearchReadOverlayAndWithdrawalUseProductionPorts()throws Exception{
        Path root=Path.of("..").toAbsolutePath().normalize();String target=System.getProperty("zhku.db.url");RuleCheckDatabaseTestTarget.verify(target);
        String user=System.getProperty("zhku.db.user","zhkuagent");var db=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());
        try(var catalogue=new Reviewed2025ReportCatalogue(root)){
            catalogue.installOnIsolatedDatabase(db);
            new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValue(Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"catalogue-binding-diagnostic.json").toFile(),catalogue.reviewedCatalogue(root));
            assertThat(RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root))).isNotEmpty();
            var settings=catalogue.settings();var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());UUID task=UUID.randomUUID(),run=UUID.randomUUID();
            var tools=new RuleAgentEvidenceTools(owner,task,settings,()->{try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception e){throw new IllegalArgumentException("目录资格变化");}},
                JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(Clock.systemUTC()),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),Clock.systemUTC());
            var search=tools.search(owner,task,run,1,new Search("q1","专业选修课和公共选修课的修读安排"));assertThat(search.status()).isEqualTo(ObservationStatus.SUCCEEDED);
            var candidate=search.candidates().stream().filter(c->c.excerpt().startsWith("第四条")).findFirst().orElseThrow();
            var read=tools.read(owner,task,run,2,new Read("q1",List.of(candidate.handle())));assertThat(read.observation().dataLevel()).isEqualTo(DataLevel.VERIFIED_FACT);
            assertThat(read.facts()).extracting(f->f.definition().text()).contains("专业选修课列入教学计划，要求在规定学期内修读。","公共选修课包括校内公选课和网络公选课，学生自行选课修读。");
            var quote=read.observation().evidence().getFirst();assertThat(tools.original(owner,task,quote.originalToken())).isPresent();
            assertThat(tools.read(owner,UUID.randomUUID(),run,3,new Read("q1",List.of(candidate.handle()))).facts()).isEmpty();
            try(var c=db.getConnection();var p=c.prepareStatement("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?")){p.setObject(1,quote.sourceId());p.executeUpdate();}
            assertThat(tools.original(owner,task,quote.originalToken())).isEmpty();var revoked=tools.read(owner,task,run,3,new Read("q1",List.of(candidate.handle())));
            assertThat(revoked.facts()).isEmpty();assertThat(revoked.observation().reasonCode()).isEqualTo(ReasonCode.EVIDENCE_CHANGED);
            new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValue(Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"actual-tool-flow.json").toFile(),Map.of("search",search,"read",read,"afterWithdrawal",revoked,"providerCalls",0,"fixedToolTestNotAgentEval",true));
        }
    }
}
