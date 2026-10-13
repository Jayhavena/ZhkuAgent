package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 来源拟题而非16题参考；验证补充装配、实际身份以及资料撤回边界。 */
class RuleCheck2025ReportAssemblyTest {
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private Reviewed2025ReportCatalogue trial;
    private DriverManagerDataSource ds;
    @BeforeEach void start() throws Exception {
        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(url);
        ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        trial=new Reviewed2025ReportCatalogue(root);
    }
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    @Test void assemblesTwentyActualClausesAndWithdrawnSupplementCannotBeQuoted() throws Exception {
        trial.installOnIsolatedDatabase(ds);
        assertThat(trial.bindings).hasSize(20);assertThat(trial.reviewedCatalogue(root)).hasSize(26);
        for(int article:List.of(1,3,7,13,14,15,16,17,19,20)) {
            var binding=trial.bindings.get(article);
            assertThat(trial.reader().read(binding.sourceId(),binding.chunkId())).isPresent();
            var entry=trial.reviewedCatalogue(root).stream().filter(e->e.template().ruleId().equals("A%02d/GENERAL".formatted(article))).findFirst().orElseThrow();
            assertThat(entry.template().facts()).hasSize(1);
            assertThat(entry.template().facts().getFirst().text()).isEqualTo(binding.text());
            assertThat(entry.template().facts().getFirst().evidenceBindings().getFirst().chunkId()).isEqualTo(binding.chunkId());
        }
        var binding=trial.bindings.get(15);
        var excerpt=new RuleCheckVerifiedExcerpt("report-source-test",binding.sourceId(),binding.chunkId(),Reviewed2025ControlledCatalogue.REVISION,
                binding.checksum(),0,binding.text().length(),binding.text());
        assertThat(trial.originals().locate(excerpt).code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
        trial.update("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false WHERE id=?",trial.source);
        assertThat(trial.reader().read(binding.sourceId(),binding.chunkId())).isEmpty();
        assertThat(trial.originals().locate(excerpt).code()).isNotEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
    }
    @Test void changingSupplementPermissionBeforeWriteCannotTouchDatabase() throws Exception {
        ((ObjectNode)trial.supplement).put("humanQualificationConfirmed",false);
        assertThatThrownBy(()->trial.importAndBind(ds)).isInstanceOf(IllegalArgumentException.class);
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM knowledge_source")) {
            r.next();assertThat(r.getInt(1)).isZero();
        }
    }
    @Test void attendanceCannotEnterGeneralTeachingHoursCandidates() throws Exception {
        trial.installOnIsolatedDatabase(ds);
        var seen=new java.util.concurrent.atomic.AtomicReference<List<RuleCheckApplicationService.RuleOption>>();
        var selector=new RuleCheckApplicationService.RuleSelector() {
            public boolean requiresScopedCandidates(){return true;}
            public String select(String q,List<RuleCheckApplicationService.RuleOption> options){seen.set(options);return null;}
        };
        var app=RuleCheckRuntimeFactory.createControlled2025(ds,trial.settings(),()->{
            try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
        },selector,()->UUID.randomUUID().toString(),java.time.Clock.systemUTC());
        app.check("公共选修课通常安排多少学时？",Map.of());
        assertThat(seen.get()).isNotNull().noneMatch(option->option.ruleId().equals("A15/GENERAL"));
    }
}
