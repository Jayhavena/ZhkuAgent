package com.zhku.knowledge.ruleagent;
import java.util.*; import com.fasterxml.jackson.databind.ObjectMapper;
/** 仅反射实际jar的合同身份，不构造Provider、数据源或运行任务。 */
public final class LocalBuildIdentity {
 public static void main(String[] args)throws Exception {
  var field=RuleAgentOnlineProbeHost.class.getDeclaredField("PACKAGE");field.setAccessible(true);
  String revision=RulePublicationContract.REVISION;
  if(!revision.equals("RULE_PUBLICATION_REVIEW_02"))throw new AssertionError("Review revision mismatch");
  var fields=Arrays.stream(RulePublicationContract.SemanticInput.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
  if(!fields.contains("conditions"))throw new AssertionError("Missing reviewed condition directory");
  var dir=RuleAgentProvider.Direction.valueOf("REVIEW_INPUT_SNAPSHOT");
  var packagePath=(String)field.get(null);if(!packagePath.contains("t05-55-clarification-request"))throw new AssertionError("Old request path");
  var validator=RuleAgentOnlineProbeHost.class.getDeclaredMethod("validatePackage",com.fasterxml.jackson.databind.JsonNode.class);validator.setAccessible(true);
  var oldPacket=java.nio.file.Path.of(System.getenv("ZHKU_RULE_AGENT_ROOT"),"docs/evidence/rule-agent-provider/2026-10-15/t05-51-p02-diagnostic-request/request-package.json");
  boolean rejected=false;try{validator.invoke(null,new ObjectMapper().readTree(oldPacket.toFile()));}catch(java.lang.reflect.InvocationTargetException invalid){if(!(invalid.getCause() instanceof IllegalArgumentException))throw invalid;rejected=true;}
  if(!rejected)throw new AssertionError("Old51 packet accepted");
  var result=new LinkedHashMap<String,Object>();result.put("old51PacketRejected",true);result.put("javaVersion",System.getProperty("java.version"));result.put("javaVendor",System.getProperty("java.vendor"));
result.put("passed",true);result.put("reviewRevision",revision);result.put("semanticInputFields",fields);result.put("snapshotDirection",dir);result.put("packagePath",packagePath);result.put("policy",RulePublicationContract.policy());
  result.put("contractClassOrigin",RulePublicationContract.class.getProtectionDomain().getCodeSource().getLocation().toString());result.put("hostClassOrigin",RuleAgentOnlineProbeHost.class.getProtectionDomain().getCodeSource().getLocation().toString());result.put("providerCalls",0);result.put("databaseOperations",0);
  System.out.println(new ObjectMapper().writeValueAsString(result));
 }
}
