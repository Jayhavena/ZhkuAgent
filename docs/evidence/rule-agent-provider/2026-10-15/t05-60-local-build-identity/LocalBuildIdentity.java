package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.security.Permission;
import java.net.SocketPermission;
import com.fasterxml.jackson.databind.*;
/** 新jar身份探针只运行合同解析和文件读取；不构造Provider、数据源、任务或发布器。 */
public final class LocalBuildIdentity {
 @SuppressWarnings("removal") public static void main(String[] args)throws Exception {
  System.setSecurityManager(new SecurityManager(){@Override public void checkPermission(Permission p){if(p instanceof SocketPermission)throw new SecurityException("LOCAL_IDENTITY_NETWORK_FORBIDDEN");}});
  var j=new ObjectMapper().findAndRegisterModules();Path root=Path.of(args[0]);
  if(!RuleAgentProtocol.REVISION.equals("RULE_AGENT_ACTION_CONTRACT_06"))throw new AssertionError("Wrong action revision");
  if(!RulePublicationContract.REVISION.equals("RULE_PUBLICATION_REVIEW_02"))throw new AssertionError("Wrong review revision");
  var fields=Arrays.stream(RuleAgentRunner.View.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
  if(!fields.contains("publicationFactIds"))throw new AssertionError("Missing eligibility view");
  RuleAgentProtocol.Code.valueOf("REVIEW_CONDITION_UNKNOWN");
  var schema=RuleAgentProtocol.decisionSchema(List.of("c1"),List.of("ASK_NECESSARY_CONDITION"),List.of("local:eligible"));
  var ids=schema.path("oneOf").get(0).path("properties").path("supportingFactIds").path("items").path("enum");
  if(ids.size()!=1||!ids.get(0).asText().equals("local:eligible"))throw new AssertionError("Wrong eligible fact Schema");
  var pathField=RuleAgentOnlineProbeHost.class.getDeclaredField("PACKAGE");pathField.setAccessible(true);String packagePath=(String)pathField.get(null);
  var oldPacket=root.resolve("docs/evidence/rule-agent-provider/2026-10-15/t05-55-clarification-request/request-package.json");
  var validator=RuleAgentOnlineProbeHost.class.getDeclaredMethod("validatePackage",JsonNode.class);validator.setAccessible(true);
  boolean oldRejected=false;try{validator.invoke(null,j.readTree(oldPacket.toFile()));}catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof IllegalArgumentException))throw e;oldRejected=true;}
  if(!oldRejected)throw new AssertionError("Old55 package accepted");
  Path live=oldPacket.getParent().resolve("live-c284f31002e5419f8c4b9061dcf5a599");String run="7109d5ad-41ee-4841-ab9c-ee5c35587556";
  var snap=j.readTree(j.readTree(live.resolve(run+"-4-REVIEW_INPUT_SNAPSHOT.json").toFile()).path("body").asText());
  var input=j.treeToValue(snap.path("input"),RulePublicationContract.SemanticInput.class);
  var response=j.readTree(j.readTree(live.resolve(run+"-4-RESPONSE.json").toFile()).path("body").asText());String review=response.path("choices").get(0).path("message").path("content").asText();
  boolean conditionRejected=false;try{RulePublicationProtocol.decode(review,input);}catch(RuleAgentProtocol.Rejected e){conditionRejected=e.validation.code()==RuleAgentProtocol.Code.REVIEW_CONDITION_UNKNOWN;}
  if(!conditionRejected)throw new AssertionError("Old review prefix not rejected by actual new jar");
  var origins=new TreeMap<String,String>();
  for(Class<?> c:List.of(RuleAgentProtocol.class,RuleAgentProvider.class,RuleAgentRunner.class,RuleFactCapabilityCatalogue.class,RulePublicationGate.class,RulePublicationProtocol.class,RuleAgentOnlineProbeHost.class)){
   String origin=c.getProtectionDomain().getCodeSource().getLocation().toString();
   if(!origin.contains("knowledge-0.1.0-rc.1-controlled.jar")||!origin.contains("BOOT-INF/classes"))throw new AssertionError("Class loaded outside actual jar: "+origin);
   origins.put(c.getSimpleName(),origin);
  }
  var result=new LinkedHashMap<String,Object>();result.put("passed",true);result.put("javaVersion",System.getProperty("java.version"));result.put("javaVendor",System.getProperty("java.vendor"));result.put("actionRevision",RuleAgentProtocol.REVISION);result.put("reviewRevision",RulePublicationContract.REVISION);result.put("viewFields",fields);result.put("eligibilitySchemaVerified",true);result.put("old55PackageRejected",true);result.put("oldPrefixedReviewRejected",true);result.put("reviewRejectionCode","REVIEW_CONDITION_UNKNOWN");result.put("classOrigins",origins);result.put("packagePath",packagePath);result.put("currentPackagePathOccupiedByClosedOldPackage",Files.exists(root.resolve(packagePath)));result.put("newRequestPreparationRequiresNonOverwritingPathBinding",true);result.put("networkDeniedBySecurityManager",true);result.put("realProviderCalls",0);result.put("databaseOperations",0);result.put("taskOrFreshPublicationExecuted",false);
  System.out.println(j.writeValueAsString(result));
 }
}
