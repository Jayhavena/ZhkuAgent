package com.zhku.knowledge.retrieval;
import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
class Reviewed2025RuntimeAssetsTest {
 @Test void externalHandbookHasStableLogicalKeyWithoutCrossDriveRelativize() {
  Path root=Path.of("checkout").toAbsolutePath(),external=root.resolveSibling("outside").resolve("handbook.pdf");
  assertThat(Reviewed2025ControlledCatalogue.assetKey(root,external)).isEqualTo("@external/student-handbook.pdf");
  assertThat(Reviewed2025ControlledCatalogue.assetKey(root,root.resolve("docs/file.json"))).isEqualTo("docs/file.json");
  if(System.getProperty("os.name").startsWith("Windows"))
   assertThat(Reviewed2025ControlledCatalogue.assetKey(Path.of("C:/repo"),Path.of("D:/handbook.pdf"))).isEqualTo("@external/student-handbook.pdf");
 }
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
  String previous=System.getProperty("zhku.t06.handbookPdf");
  System.setProperty("zhku.t06.handbookPdf",Reviewed2025ControlledCatalogue.handbookPdf(original).toString());
  try {
  try(var trial=new Reviewed2025ControlledCatalogue(original)) {
   for(var asset:trial.runtimeAssetManifest()) {
    String relative=asset.get("path");if(relative.startsWith("@external/"))continue;
    Path target=root.resolve(relative).normalize();Files.createDirectories(target.getParent());Files.copy(original.resolve(relative).normalize(),target);
   }
  }
  String capability="knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java";
  Files.createDirectories(root.resolve(capability).getParent());Files.copy(original.resolve(capability),root.resolve(capability));
  var method=RuleAgentOnlineProbeHost.class.getDeclaredMethod("packageNode",Path.class);method.setAccessible(true);
  // 先证明完整副本能准备包，再删目标文件；否则PDF缺失也会让这个负例空洞通过。
  assertThat(method.invoke(null,root)).isNotNull();
  Files.delete(root.resolve(missing));
  assertThatThrownBy(()->method.invoke(null,root)).isInstanceOf(java.lang.reflect.InvocationTargetException.class)
      .hasCauseInstanceOf(NoSuchFileException.class).satisfies(error->
          assertThat(error.getCause().getMessage()).contains(root.resolve(missing).toString()));
  } finally {if(previous==null)System.clearProperty("zhku.t06.handbookPdf");else System.setProperty("zhku.t06.handbookPdf",previous);}
 }
}
