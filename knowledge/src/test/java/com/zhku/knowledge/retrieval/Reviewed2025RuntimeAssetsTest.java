package com.zhku.knowledge.retrieval;
import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
class Reviewed2025RuntimeAssetsTest {
 @Test void zeroCallPackageBindsTopicRuntimeAsset() throws Exception {
  var method=RuleAgentOnlineProbeHost.class.getDeclaredMethod("packageNode",Path.class);method.setAccessible(true);
  var pack=(JsonNode)method.invoke(null,Path.of("..").toAbsolutePath().normalize());
  assertThat(pack.path("runtimeAssets").isArray()).isTrue();
  assertThat(pack.path("runtimeAssets").toString()).contains("docs/product/rule-check-c05-topic-catalogue.json","LOCAL_DRAFT_NOT_AUTHORIZED_FOR_TRANSFER_OR_PUBLICATION");
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"missing","duplicate","unknown-topic","status","semantic-hash","rule-id","scope"})
 void invalidTopicAssetRejectedBeforeDatabase(String change,@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
  Path original=Path.of("..").toAbsolutePath().normalize();
  try(var trial=new Reviewed2025ControlledCatalogue(original)) {
   String topic="docs/product/rule-check-c05-topic-catalogue.json",semantic="docs/product/rule-check-2025-b02-semantic-review.json";
   Files.createDirectories(root.resolve("docs/product"));Files.copy(original.resolve(semantic),root.resolve(semantic));
   if(!change.equals("missing")) {
    var json=new ObjectMapper();var draft=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(original.resolve(topic)));
    var first=(com.fasterxml.jackson.databind.node.ObjectNode)draft.path("entries").get(0);
    switch(change) {
     case "duplicate" -> ((com.fasterxml.jackson.databind.node.ArrayNode)draft.path("entries")).add(first.deepCopy());
     case "unknown-topic" -> first.putArray("topics").add("UNREVIEWED");
     case "status" -> draft.put("status","APPROVED");
     case "semantic-hash" -> draft.put("sourceSemanticSha256","0".repeat(64));
     case "rule-id" -> first.put("ruleId","S99/GENERAL");
     case "scope" -> first.put("questionScope","");
    }
    Files.write(root.resolve(topic),json.writeValueAsBytes(draft));
   }
   assertThatThrownBy(()->Reviewed2025TopicCatalogue.validateAsset(root,trial)).isInstanceOf(Exception.class);
   assertThat(trial.dataSource).isNull();
   assertThat(trial.catalogue).isEmpty();
  }
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"docs/product/rule-check-c05-topic-catalogue.json","docs/evidence/rule-agent-evidence/2026-10-13/t02-01/human-confirmation.json","docs/evidence/rule-check-refactor/2026-10-09/b03-student-qualification-01/presented-review.md"})
 void packagePreflightRejectsMissingActualDependency(String missing,@org.junit.jupiter.api.io.TempDir Path temporary) throws Exception {
  Path original=Path.of("..").toAbsolutePath().normalize(),root=temporary.resolve("checkout");Files.createDirectories(root);
  try(var trial=new Reviewed2025ControlledCatalogue(original)) {
   for(var asset:trial.runtimeAssetManifest()) {
    String relative=asset.get("path");if(relative.equals(missing))continue;
    Path target=root.resolve(relative).normalize();Files.createDirectories(target.getParent());Files.copy(original.resolve(relative).normalize(),target);
   }
  }
  String capability="knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java";
  Files.createDirectories(root.resolve(capability).getParent());Files.copy(original.resolve(capability),root.resolve(capability));
  var method=RuleAgentOnlineProbeHost.class.getDeclaredMethod("packageNode",Path.class);method.setAccessible(true);
  assertThatThrownBy(()->method.invoke(null,root)).isInstanceOf(java.lang.reflect.InvocationTargetException.class).hasCauseInstanceOf(NoSuchFileException.class);
 }
}
