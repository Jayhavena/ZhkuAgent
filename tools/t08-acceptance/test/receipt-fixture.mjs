import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {JAVA_TEST_PATH,stableStringify} from '../receipt/receipt.mjs';
const root=new URL('../../../',import.meta.url);
export const hash=b=>createHash('sha256').update(b).digest('hex');
export const bytes=v=>Buffer.from(JSON.stringify(v));
export const sign=r=>{const {receiptId,...body}=r;return {...body,receiptId:hash(stableStringify(body))};};
export const methods=['actualRunnerTraceHistoryExportsOnlySafeT08Fields','rejectsHiddenUuidAndUnreviewedRevision','rejectsParentJunctionBeforeCreatingExport(Path)'];
export const xml=(count=3,names=methods.slice(0,count))=>Buffer.from(`<testsuite name="com.zhku.knowledge.ruleagent.RuleAgentOfflineTraceExportTest" tests="${count}" failures="0" errors="0" skipped="0">${names.map(n=>`<testcase name="${n}" classname="com.zhku.knowledge.ruleagent.RuleAgentOfflineTraceExportTest"/>`).join('')}</testsuite>`);
export function fixture(){
  const datasetPath='tools/t08-acceptance/sprint02/java-offline-runner-dataset.json',rubricPath='docs/preparation/t08/sprint02/rubric.json';
  const dataset=readFileSync(new URL(datasetPath,root)),rubric=readFileSync(new URL(rubricPath,root)),javaTest=readFileSync(new URL(JAVA_TEST_PATH,root));
  const manifest=bytes({recordType:'T08_S02_FREEZE',schemaVersion:1,algorithm:'SHA-256',files:
    [[datasetPath,dataset],[rubricPath,rubric],[JAVA_TEST_PATH,javaTest]].map(([path,b])=>({path,bytes:b.length,sha256:hash(b)}))});
  const identity={candidateId:'candidate-1',sourceSha:'a'.repeat(40),caseId:'java-offline-runner-pass',manifestSha256:hash(manifest),datasetSha256:hash(dataset),rubricSha256:hash(rubric)};
  const doc={recordType:'T08_JAVA_TRACE_EXPORT',schemaVersion:1,sourceLayer:'ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED',executionMode:'OFFLINE_SIMULATED',providerCalls:0,
    candidateId:identity.candidateId,sourceSha:identity.sourceSha,caseId:identity.caseId,datasetSha256:identity.datasetSha256,
    traces:[{taskId:'task-1',turnId:'turn-1',runId:'run-1',version:2,taskStatus:'RESOLVED',runState:'SUCCEEDED',exceptionalFailure:false,truncated:false,
      actions:[{actionId:'action-1',type:'SEARCH_REVIEWED_RULES',step:1,modelAttempt:1},{actionId:'action-2',type:'READ_VERIFIED_RULE',step:2,modelAttempt:2},{actionId:'action-3',type:'FINISH_CHECK',step:3,modelAttempt:3}],
      observations:[{actionId:'action-1',status:'SUCCEEDED',dataLevel:'CANDIDATE'},{actionId:'action-2',status:'SUCCEEDED',dataLevel:'VERIFIED_FACT'},{actionId:'action-3',status:'NOT_OBSERVED',dataLevel:null}],
      result:{coverage:'COMPLETE',evidence:[{id:'evidence-1',revision:'2025.9.2'}],evidenceRelations:[{targetId:'claim-1',evidenceId:'evidence-1',role:'CLAIM'}],items:[{claims:[{id:'claim-1',factId:'sha256:'+'b'.repeat(64)}]}],issues:[]}}]};
  const javaExport=bytes(doc),surefire=xml(),now=Date.now();
  const challengeObject={recordType:'T08_EXECUTION_CHALLENGE',schemaVersion:1,challengeId:'challenge-1',nonce:'a'.repeat(32),issuedAt:new Date(now-1000).toISOString(),expiresAt:new Date(now+29*60000).toISOString(),
    candidateCommit:identity.sourceSha,candidateTree:'b'.repeat(40),caseId:identity.caseId,fixtureManifestSha256:identity.manifestSha256,datasetSha256:identity.datasetSha256,rubricSha256:identity.rubricSha256};
  const challenge=bytes(challengeObject),{recordType,schemaVersion,...challengeBody}=challengeObject;
  const receipt=sign({recordType:'T08_EXECUTION_RECEIPT',schemaVersion:1,issuedAt:new Date(now).toISOString(),observationStatus:'CONTROLLER_OBSERVED_UNPINNED',reasonCode:null,
    challenge:{...challengeBody,sha256:hash(challenge)},source:{commit:identity.sourceSha,tree:'b'.repeat(40),head:identity.sourceSha,clean:true,untrackedCount:0},
    fixtures:{manifestSha256:identity.manifestSha256,datasetSha256:identity.datasetSha256,rubricSha256:identity.rubricSha256,javaTestSha256:hash(javaTest),caseId:identity.caseId,group:'G2',split:'development'},
    execution:{status:'EXECUTED',command:{executable:'fake-mvn',argv:['-o','-Dtest=RuleAgentOfflineTraceExportTest',`-Dzhku.t08.candidateId=${identity.candidateId}`,`-Dzhku.t08.sourceSha=${identity.sourceSha}`,`-Dzhku.t08.caseId=${identity.caseId}`,'test'],cwd:'fixture-only',hostShell:'DIRECT'},
      exit:{code:0,signal:null,timedOut:false,durationMs:5},maven:{tests:3,failures:0,errors:0,skipped:0,reportSha256:hash(surefire)},export:{bytes:javaExport.length,sha256:hash(javaExport)}},
    controller:{name:'t08-receipt-controller',version:'t08-controller-v1b',sourceSha256:'c'.repeat(64)},externalRegistration:{pinnedExternally:false,registryEntrySha256:null},providerCalls:0,modelCalls:0,qualityStatus:'NOT_EVALUABLE'});
  return {dataset,rubric,javaTest,manifest,challenge,javaExport,surefire,identity,receipt,doc,
    receipts:bytes({recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[receipt]})};
}
