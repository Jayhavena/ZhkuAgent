import {test} from 'node:test';
import assert from 'node:assert/strict';

const names=['actualRunnerTraceHistoryExportsOnlySafeT08Fields','rejectsHiddenUuidAndUnreviewedRevision',
  'rejectsParentJunctionBeforeCreatingExport(Path)'];
const suite=(count=3,methods=names.slice(0,count))=>`<testsuite name="com.zhku.knowledge.ruleagent.RuleAgentOfflineTraceExportTest" tests="${count}" failures="0" errors="0" skipped="0">${methods.map(n=>`<testcase name="${n}" classname="com.zhku.knowledge.ruleagent.RuleAgentOfflineTraceExportTest"/>`).join('')}</testsuite>`;

test('v1B requires the three current named tests while v1A remains explicit legacy-only',async()=>{
  const {EXPECTED_TEST_COUNT,CONTROLLER_VERSION,parseSurefireReport}=await import('../receipt/receipt.mjs');
  assert.equal(CONTROLLER_VERSION,'t08-controller-v1b');assert.equal(EXPECTED_TEST_COUNT,3);
  assert.equal(parseSurefireReport(suite()).tests,3);
  assert.equal(parseSurefireReport(suite(2),{controllerVersion:'t08-controller-v1a'}).tests,2);
  assert.throws(()=>parseSurefireReport(suite(2)),/SUREFIRE_NOT_CLEAN/);
  assert.throws(()=>parseSurefireReport(suite(),{controllerVersion:'t08-controller-v1a'}),/SUREFIRE_NOT_CLEAN/);
  assert.throws(()=>parseSurefireReport(suite(3,['one','two','three'])),/SUREFIRE_TEST_IDENTITY/);
  assert.throws(()=>parseSurefireReport(suite(3,[names[0],names[0],names[2]])),/SUREFIRE_TEST_IDENTITY/);
  assert.throws(()=>parseSurefireReport(suite(3).replace('</testsuite>','<failure message="RAW_PRIVATE_MARKER"/></testsuite>')),/SUREFIRE_NOT_CLEAN/);
});

test('receipt JSON boundary preserves fatal UTF8, duplicate-key and depth checks',async()=>{
  const {parseStrictJson}=await import('../receipt/receipt.mjs');
  assert.throws(()=>parseStrictJson(Buffer.from([0xff])),/JSON_MALFORMED/);
  assert.throws(()=>parseStrictJson('{"a":1,"\\u0061":2}'),/JSON_DUPLICATE_KEY/);
  assert.throws(()=>parseStrictJson('['.repeat(25)+'0'+']'.repeat(25)),/JSON_TOO_DEEP/);
});

test('test counts cannot hide malformed XML structure',async()=>{
  const {parseSurefireReport}=await import('../receipt/receipt.mjs');
  assert.throws(()=>parseSurefireReport(suite().replace('/>','>')),/SUREFIRE_MALFORMED/);
  assert.throws(()=>parseSurefireReport(suite()+'<junk/>'),/SUREFIRE_MALFORMED/);
});
