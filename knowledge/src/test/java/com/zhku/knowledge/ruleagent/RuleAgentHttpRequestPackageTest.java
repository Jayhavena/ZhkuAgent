package com.zhku.knowledge.ruleagent;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class RuleAgentHttpRequestPackageTest {
 @TempDir Path root;
 ObjectNode pack(){return RuleAgentHttpRequestPackage.JSON.createObjectNode().put("purpose",RuleAgentHttpRequestPackage.PURPOSE).put("authorizationStatus","NOT_AUTHORIZED");}
 String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 @Test void createsOneNewImmutablePackageWithoutCredentials()throws Exception{
  Path directory=root.resolve("new");var expected=pack();String sha=RuleAgentHttpRequestPackage.writeNew(directory,expected);
  assertThat(sha).isEqualTo(hash(Files.readAllBytes(directory.resolve("request-package.json"))));
  assertThat(RuleAgentHttpRequestPackage.verifyBytes(directory,sha,expected)).isEqualTo(expected);
 }
 @Test void existingDirectoryIsRejectedWithoutOverwriting()throws Exception{
  Path directory=root.resolve("new");RuleAgentHttpRequestPackage.writeNew(directory,pack());byte[] before=Files.readAllBytes(directory.resolve("request-package.json"));
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.writeNew(directory,pack().put("changed",true))).isInstanceOf(FileAlreadyExistsException.class);
  assertThat(Files.readAllBytes(directory.resolve("request-package.json"))).isEqualTo(before);
 }
 @Test void alteredPackageBytesAreRejected()throws Exception{
  Path directory=root.resolve("new");String sha=RuleAgentHttpRequestPackage.writeNew(directory,pack());Files.writeString(directory.resolve("request-package.json"),"{}");
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.verifyBytes(directory,sha,pack())).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void changedCurrentInputsAreRejected()throws Exception{
  Path directory=root.resolve("new");String sha=RuleAgentHttpRequestPackage.writeNew(directory,pack());
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.verifyBytes(directory,sha,pack().put("currentSourceChanged",true))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void closedHistoricalPurposeCannotBecomeNewAuthorization()throws Exception{
  Path directory=root.resolve("old");var old=pack().put("purpose","T05_P02_SAME_TASK_DIAGNOSTIC_NOT_PRODUCT_QUALITY_EVAL");String sha=RuleAgentHttpRequestPackage.writeNew(directory,old);
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.verifyBytes(directory,sha,old)).isInstanceOf(IllegalArgumentException.class);
 }
 @ParameterizedTest @ValueSource(strings={"source.java","knowledge/target/knowledge-0.1.0-rc.1-controlled.jar","dist/assets/app.js","classes/App.class","dependency.jar"})
 void everyExecutionInputAndStudentBuildIsBound(String change)throws Exception{
  var files=List.of("source.java","knowledge/target/knowledge-0.1.0-rc.1-controlled.jar","dist/index.html","dist/assets/app.js","classes/App.class","dependency.jar");
  for(String path:files){Files.createDirectories(root.resolve(path).getParent());Files.writeString(root.resolve(path),"original");}
  List<Path> cp=List.of(root.resolve("classes"),root.resolve("dependency.jar"));
  var before=RuleAgentHttpRequestPackage.fingerprintInputs(root,root.resolve("dist"),List.of("source.java"),cp,"a".repeat(40),"b".repeat(40));
  Files.writeString(root.resolve(change),"changed");
  var after=RuleAgentHttpRequestPackage.fingerprintInputs(root,root.resolve("dist"),List.of("source.java"),cp,"a".repeat(40),"b".repeat(40));
  assertThat(after).isNotEqualTo(before);
 }
 @Test void unchangedBytesProduceSameIdentity()throws Exception{
  for(String path:List.of("source.java","knowledge/target/knowledge-0.1.0-rc.1-controlled.jar","dist/index.html","classes/App.class")){Files.createDirectories(root.resolve(path).getParent());Files.writeString(root.resolve(path),"same");}
  var before=RuleAgentHttpRequestPackage.fingerprintInputs(root,root.resolve("dist"),List.of("source.java"),List.of(root.resolve("classes")),"a".repeat(40),"b".repeat(40));
  assertThat(RuleAgentHttpRequestPackage.fingerprintInputs(root,root.resolve("dist"),List.of("source.java"),List.of(root.resolve("classes")),"a".repeat(40),"b".repeat(40))).isEqualTo(before);
 }
 void reservation(Path directory,String sha,String token)throws Exception{
  Path reservations=root.resolve("docs/evidence/rule-agent-integration/authorization-reservations");Files.createDirectories(reservations);Files.createDirectories(directory);
  var record=RuleAgentHttpRequestPackage.JSON.createObjectNode().put("packageSha256",sha).put("reservationId",token);
  Files.write(reservations.resolve(sha+".json"),RuleAgentHttpRequestPackage.JSON.writeValueAsBytes(record));
 }
 @Test void missingReservationCannotStartHost()throws Exception{
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.claimReservedExecution(root,root.resolve("batch"),"a".repeat(64),UUID.randomUUID().toString())).isInstanceOf(Exception.class);
 }
 @Test void differentReservationTokenCannotStartHost()throws Exception{
  Path directory=root.resolve("batch");String sha="a".repeat(64);reservation(directory,sha,UUID.randomUUID().toString());
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.claimReservedExecution(root,directory,sha,UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void approvedShaCannotBeConsumedByAnotherHostOrCopiedDirectory()throws Exception{
  Path directory=root.resolve("batch"),copy=root.resolve("copy");Files.createDirectory(copy);String sha="a".repeat(64),token=UUID.randomUUID().toString();reservation(directory,sha,token);
  RuleAgentHttpRequestPackage.claimReservedExecution(root,directory,sha,token);
  assertThatThrownBy(()->RuleAgentHttpRequestPackage.claimReservedExecution(root,copy,sha,token)).isInstanceOf(FileAlreadyExistsException.class);
 }
 @Test void capabilityAuthorityComesFromExecutingCodeRoot()throws Exception{
  String path="knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java";Files.createDirectories(root.resolve(path).getParent());Files.writeString(root.resolve(path),"executing-code");
  var model=pack().put("factCapabilityAuthoritySha256","old-assets-root-code");RuleAgentHttpRequestPackage.bindCapabilityAuthority(model,root);
  assertThat(model.path("factCapabilityAuthoritySha256").asText()).isEqualTo(hash(Files.readAllBytes(root.resolve(path))));
 }}
