package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 实際资料目录准备与合成transport观察；未调用模型、未评价语义正确率。 */
class RuleCheck2025SelectionPreparationTest {
    private Path root;
    private Reviewed2025Trial trial;
    private RuleCheckDevelopmentInputs.Loaded inputs;
    @BeforeEach void start() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String target=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(target);
        var ds=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());trial.importAndBind(ds);
    }
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    @Test void preparesOnlyTwentyOriginalQuestionsAndFourUngradedProbesWithActualCatalogue() throws Exception {
        var requests=RuleCheckSelectionPreparation.build(root,trial,inputs);
        trial.save("selection-preparation-observation.json",Map.of("requests",requests,"providerCalls",0,"reportCasesConsumed",0));
        assertThat(requests).hasSize(24);
        var mapper=new ObjectMapper();var probes=mapper.readTree(root.resolve("docs/product/rule-check-c05-language-cases.json").toFile()).path("observationOnlyProbes");
        for(int i=0;i<requests.size();i++){
            var row=requests.get(i);var body=mapper.valueToTree(row.get("body"));
            assertThat(body.path("model").asText()).isEqualTo("qwen-plus");assertThat(body.path("enable_thinking").asBoolean(true)).isFalse();
            assertThat(body.path("stream").asBoolean(true)).isFalse();assertThat(body.path("max_tokens").asInt()).isEqualTo(128);
            assertThat(body.path("messages")).hasSize(2);assertThat(body.path("messages").get(0).path("content").asText()).contains("JSON","ruleId");
            var payload=mapper.readTree(body.path("messages").get(1).path("content").asText());
            assertThat(payload.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("question","options");
            String question=i<20?inputs.reference().path("records").get(i).path("question").asText():probes.get(i-20).path("question").asText();
            assertThat(payload.path("question").asText()).isEqualTo(question);
            assertThat(row.get("caseId")).isEqualTo(i<20?"Q%02d".formatted(i+1):"O%02d".formatted(i-19));
            assertThat(row.get("semanticGradeAvailable")).isEqualTo(false);
            assertThat(payload.path("options")).hasSize(16);
            for(int j=0;j<trial.catalogue.size();j++){
                var actual=payload.path("options").get(j);var entry=trial.catalogue.get(j);
                assertThat(actual.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("ruleId","questionScope");
                assertThat(actual.path("ruleId").asText()).isEqualTo(entry.template().ruleId());
                assertThat(actual.path("questionScope").asText()).isEqualTo(entry.questionScope());
            }
            assertThat(row.get("syntheticTransportOutcome")).isEqualTo("ABSTAINED");
        }
        trial.save("selection-outbound-package.json",Map.of("authority","PREPARED_NOT_AUTHORIZED_FOR_TRANSFER","requests",requests,
            "selectionContract","ACTUAL_BOUNDED_SELECTOR_REQUEST_WITH_SYNTHETIC_NULL_TRANSPORT","providerCalls",0,"reportCasesConsumed",0,"humanSemanticGradesProduced",false));
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
    @Test void duplicateCatalogueCannotCreateAnOutboundPackage() throws Exception {
        trial.catalogue.add(trial.catalogue.getFirst());
        assertThatThrownBy(()->RuleCheckSelectionPreparation.build(root,trial,inputs)).isInstanceOf(IllegalArgumentException.class);
    }
}
