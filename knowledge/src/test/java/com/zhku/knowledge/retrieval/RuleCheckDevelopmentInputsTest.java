package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 在副本上测试准入拒绝；不改已批准材料，不连库。 */
class RuleCheckDevelopmentInputsTest {
    @TempDir Path temporary;
    private final ObjectMapper mapper=new ObjectMapper();
    private Path fixture() throws Exception {
        Path source=Path.of("..").toAbsolutePath().normalize();var paths=new LinkedHashSet<String>();
        String base="docs/evidence/rule-check-refactor/2026-10-09/c01-c02-eval-assets-01/";
        paths.add("docs/product/rule-check-c03-control-inputs.json");paths.add("docs/product/rule-check-2025-human-reference-development.json");
        for(String f:List.of("freeze-manifest.json","human-confirmation.json","review-request.json"))paths.add(base+f);
        var freeze=mapper.readTree(source.resolve(base+"freeze-manifest.json").toFile());
        for(JsonNode file:freeze.path("files"))paths.add(file.path("path").asText());
        var request=mapper.readTree(source.resolve(base+"review-request.json").toFile());
        for(JsonNode file:request.path("protectedInputs"))paths.add(file.path("path").asText());
        for(JsonNode file:request.path("presentedFiles"))paths.add(base+"presented-"+Path.of(file.path("path").asText()).getFileName());
        for(String relative:paths){Path target=temporary.resolve(relative);Files.createDirectories(target.getParent());Files.copy(source.resolve(relative),target);}
        return temporary;
    }
    private void change(Path root,String relative,java.util.function.Consumer<ObjectNode> edit) throws Exception {
        Path file=root.resolve(relative);var data=(ObjectNode)mapper.readTree(file.toFile());edit.accept(data);mapper.writeValue(file.toFile(),data);
    }
    @Test void actualConfirmedFixtureLoadsTwentyDevelopmentRecords() throws Exception {
        var loaded=RuleCheckDevelopmentInputs.load(fixture());assertThat(loaded.reference().path("records").size()).isEqualTo(20);
    }
    @Test void emptyFreezeManifestIsRejected() throws Exception {
        Path r=fixture();change(r,"docs/evidence/rule-check-refactor/2026-10-09/c01-c02-eval-assets-01/freeze-manifest.json",n->n.putArray("files"));
        assertThatThrownBy(()->RuleCheckDevelopmentInputs.load(r)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void wrongReferencePathCannotBecomeReportCollection() throws Exception {
        Path r=fixture();change(r,"docs/product/rule-check-c03-control-inputs.json",n->n.put("referencePath","docs/product/rule-check-2025-human-reference-report.json"));
        assertThatThrownBy(()->RuleCheckDevelopmentInputs.load(r)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void controlHashCannotReplaceActualFrozenDevelopmentIdentity() throws Exception {
        Path r=fixture();change(r,"docs/product/rule-check-c03-control-inputs.json",n->n.put("referenceSha256","0".repeat(64)));
        assertThatThrownBy(()->RuleCheckDevelopmentInputs.load(r)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingControlIdIsRejected() throws Exception {
        Path r=fixture();change(r,"docs/product/rule-check-c03-control-inputs.json",n->((ObjectNode)n.path("controls")).remove("Q20"));
        assertThatThrownBy(()->RuleCheckDevelopmentInputs.load(r)).isInstanceOf(IllegalArgumentException.class);
    }
}
