import {test} from 'node:test';
import assert from 'node:assert/strict';
import {copyFileSync, mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import {
  ReceiptError, SUREFIRE_REPORT_PATH, assertSafeRelativePath, classifyReceiptTrust, parseStrictJson,
  parseSurefireReport, sha256Hex, stableStringify, validateChallenge, validateFixtureManifest, validateReceipt,
} from '../receipt/receipt.mjs';
import {runController} from '../receipt/observe.mjs';
import {fixture as bridgeFixture,xml} from './receipt-fixture.mjs';

const TMP = mkdtempSync(join(tmpdir(), 't08-receipt-test-'));
const CASE_ID = 'case-1';
const JAVA_TEST = 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOfflineTraceExportTest.java';
const DATASET = 'tools/t08-acceptance/sprint02/java-offline-runner-dataset.json';
const RUBRIC = 'docs/preparation/t08/sprint02/rubric.json';
const put = (root, relative, text) => {
  const path = join(root, ...relative.split('/'));
  mkdirSync(dirname(path), {recursive: true});
  writeFileSync(path, text);
  return path;
};
const entry = (root, relative) => {
  const bytes = readFileSync(join(root, relative));
  return {path: relative, sha256: sha256Hex(bytes), bytes: bytes.length};
};
function fixture() {
  const root = join(TMP, 'repo');
  mkdirSync(root);
  put(root, DATASET, JSON.stringify({recordType: 'T08_S02_DATASET', schemaVersion: 1, datasetId: 'd', revision: '1',
    split: 'development', cases: [{caseId: CASE_ID, group: 'G2', synthetic: true,
      input: {question: 'synthetic only', supplements: []},
      expected: {taskStatus: ['RESOLVED'], coverage: ['COMPLETE']}}]}));
  put(root, RUBRIC, JSON.stringify({recordType: 'T08_S02_RUBRIC', schemaVersion: 1, status: 'PROPOSED_NOT_APPROVED'}));
  put(root, JAVA_TEST, '// fixture only\n');
  const files = [entry(root, DATASET), entry(root, RUBRIC), entry(root, JAVA_TEST)];
  const manifest = {recordType: 'T08_S02_FREEZE', schemaVersion: 1, algorithm: 'SHA-256', files};
  const manifestPath = put(TMP, 'fixture-manifest.json', JSON.stringify(manifest));
  const manifestSha = sha256Hex(readFileSync(manifestPath));
  const now = new Date();
  const challenge = {recordType: 'T08_EXECUTION_CHALLENGE', schemaVersion: 1, challengeId: 'challenge-1',
    nonce: 'a'.repeat(32), issuedAt: new Date(now.getTime() - 60000).toISOString(),
    expiresAt: new Date(now.getTime() + 9 * 60 * 1000).toISOString(),
    candidateCommit: 'a'.repeat(40), candidateTree: 'b'.repeat(40), caseId: CASE_ID, fixtureManifestSha256: manifestSha,
    datasetSha256: files[0].sha256, rubricSha256: files[1].sha256};
  const challengePath = put(TMP, 'challenge.json', JSON.stringify(challenge));
  return {root, commit: challenge.candidateCommit, tree: challenge.candidateTree, head: challenge.candidateCommit,
    manifestPath, manifestSha, challengePath, challengeSha: sha256Hex(readFileSync(challengePath)),
    challenge, now, commitTime: new Date(now.getTime() - 120000).toISOString(), dirty: false,
    outBase: mkdtempSync(join(TMP, 'out-'))};
}
const f = fixture();
// 离线沙箱不允许 Node 打开管道捕获 git 输出，因此测试注入确定性 Git 视图；
// 生产路径仍由 defaultGit 以 argv 数组调用真实 git，未被本测试覆盖。
const fakeGit = (cwd, args) => {
  const key = args.join(' ');
  if (key === 'rev-parse --show-toplevel') return f.root;
  if (args[0] === 'cat-file') return '';
  if (key === 'rev-parse HEAD') return f.head;
  if (key === `rev-parse ${f.commit}^{tree}`) return f.tree;
  if (key === 'show -s --format=%cI ' + f.commit) return f.commitTime;
  if (args[0] === 'status') return f.dirty ? ' M fixture' : '';
  throw new Error(`unexpected fake git: ${key}`);
};
const prepareCheckout = async (root, commit, baseDir) => {
  const checkoutDir = join(baseDir, 'checkout');
  for (const relative of [DATASET, RUBRIC, JAVA_TEST]) {
    const destination = join(checkoutDir, ...relative.split('/'));
    mkdirSync(dirname(destination), {recursive: true});
    copyFileSync(join(root, ...relative.split('/')), destination);
  }
  return checkoutDir;
};
let outputSeq = 0;
const outDir = () => join(f.outBase, `run-${++outputSeq}`);
const options = overrides => ({root: f.root, candidateCommit: f.commit, candidateTree: f.tree,
  challengePath: f.challengePath, challengeSha256: f.challengeSha, fixtureManifestPath: f.manifestPath,
  fixtureManifestSha256: f.manifestSha, datasetPath: DATASET, rubricPath: RUBRIC, caseId: CASE_ID,
  candidateId: 'candidate-1', outDir: outDir(), ...overrides});
const exportDoc=()=>{const doc=bridgeFixture().doc;Object.assign(doc,{caseId:CASE_ID,candidateId:'candidate-1',sourceSha:f.commit,datasetSha256:f.challenge.datasetSha256});return JSON.stringify(doc);};
const suite=({tests=3,failures=0,errors=0,skipped=0}={})=>xml(tests).toString().replace('failures="0"',`failures="${failures}"`).replace('errors="0"',`errors="${errors}"`).replace('skipped="0"',`skipped="${skipped}"`);
const fakeRunner = ({writeExport = true, writeReport = true, report = suite(), exitCode = 0, timedOut = false} = {}) =>
  async ({checkoutDir, exportPath}) => {
    if (writeExport) writeFileSync(exportPath, exportDoc());
    if (writeReport) {
      const reportPath = join(checkoutDir, ...SUREFIRE_REPORT_PATH.split('/'));
      mkdirSync(dirname(reportPath), {recursive: true});
      writeFileSync(reportPath, report);
    }
    return {command: {executable: 'fake-mvn', argv: ['-o', 'test'], cwd: checkoutDir, hostShell: 'DIRECT'},
      exit: {code: timedOut ? null : exitCode, signal: timedOut ? 'SIGTERM' : null, timedOut, durationMs: 5}};
  };
const run = (opts = {}, deps = {}) => runController({...options(), ...opts}, {now: f.now, git: fakeGit, prepareCheckout, ...deps});
const rejects = async (promise, code) => {
  await assert.rejects(promise, error => {
    assert.ok(error instanceof ReceiptError, `expected ReceiptError, got ${error?.name}: ${error?.message}`);
    assert.equal(error.code, code);
    return true;
  });
};

test('strict JSON rejects duplicate keys, unsafe keys and trailing bytes', () => {
  assert.throws(() => parseStrictJson('{"a":1,"a":2}'), /JSON_DUPLICATE_KEY/);
  assert.throws(() => parseStrictJson('{"__proto__":{"x":1}}'), /JSON_UNSAFE_KEY/);
  assert.throws(() => parseStrictJson('{"a":1} trailing'), /JSON_MALFORMED/);
  assert.deepEqual(parseStrictJson('{"b":[1,true,null],"a":"x"}'), {b: [1, true, null], a: 'x'});
});

test('path, challenge, manifest and surefire validators fail closed', () => {
  for (const bad of ['../escape', '/abs', 'C:/x', 'a\\b', 'a/../../b', 'a//b', './a']) {
    assert.throws(() => assertSafeRelativePath(bad), /PATH_UNSAFE/);
  }
  assert.equal(assertSafeRelativePath('a/b.json'), 'a/b.json');
  assert.throws(() => validateChallenge({...f.challenge, expiresAt: new Date(f.now.getTime() - 1).toISOString()}, {now: f.now}), /CHALLENGE_NOT_FRESH/);
  assert.throws(() => validateChallenge({...f.challenge, candidateCommit: 'b'.repeat(40)}, {now: f.now, expected: {candidateCommit: f.commit}}), /CHALLENGE_MISMATCH/);
  const dup = {recordType: 'T08_S02_FREEZE', schemaVersion: 1, algorithm: 'SHA-256',
    files: [entry(f.root, DATASET), entry(f.root, DATASET)]};
  assert.throws(() => validateFixtureManifest(dup), /MANIFEST_DUPLICATE/);
  assert.throws(() => validateFixtureManifest({...dup, extra: true}), /MANIFEST_FIELDS/);
  assert.deepEqual(parseSurefireReport(suite()), {tests: 3, failures: 0, errors: 0, skipped: 0});
  assert.throws(() => parseSurefireReport(suite({tests: 0})), /SUREFIRE_NOT_CLEAN/);
  assert.throws(() => parseSurefireReport(suite({skipped: 2})), /SUREFIRE_NOT_CLEAN/);
  assert.throws(() => parseSurefireReport(suite().replace('name="com.zhku.knowledge.ruleagent.RuleAgentOfflineTraceExportTest"','name="other.Class"')), /SUFFIRE_CLASS|SUREFIRE_CLASS/);
});

test('valid controller run binds source, fixture, Maven counts and export digest', async () => {
  const result = await run({}, {runner: fakeRunner()});
  assert.equal(result.ok, true);
  assert.equal(result.receipt.observationStatus, 'CONTROLLER_OBSERVED_UNPINNED');
  assert.equal(result.receipt.execution.maven.tests, 3);
  assert.equal(result.receipt.execution.exit.code, 0);
  assert.equal(result.receipt.execution.export.bytes > 0, true);
  assert.equal(result.receipt.source.commit, f.commit);
  assert.equal(result.receipt.fixtures.datasetSha256, f.challenge.datasetSha256);
  assert.equal(validateReceipt(result.receipt), result.receipt);
  const trust = classifyReceiptTrust(result.receipt, {externalPin: result.receipt.controller.sourceSha256});
  assert.equal(trust.trustLevel, 'SELF_REPORTED');
  assert.equal(trust.externalPinVerified, false);
});

test('tampered hashes, unknown trust fields and self-declared pin are rejected', async () => {
  const {receipt} = await run({}, {runner: fakeRunner()});
  const changed = {...receipt, execution: {...receipt.execution,
    export: {...receipt.execution.export, sha256: '0'.repeat(64)}}};
  assert.throws(() => validateReceipt(changed), /RECEIPT_INTEGRITY/);
  const claimed = {...receipt, trustLevel: 'CONTROLLER_OBSERVED', verified: true};
  assert.throws(() => validateReceipt(claimed), /RECEIPT_FIELDS/);
  const pinned = {...receipt, externalRegistration: {pinnedExternally: true, registryEntrySha256: 'b'.repeat(64)}};
  const {receiptId, ...body} = pinned;
  pinned.receiptId = sha256Hex(stableStringify(body));
  assert.throws(() => validateReceipt(pinned), /UNVERIFIED_EXTERNAL_REGISTRATION/);
});

test('wrong tree, changed fixture, dirty workspace and replay are rejected', async () => {
  await rejects(run({candidateTree: 'd'.repeat(40)}, {runner: fakeRunner()}), 'TREE_MISMATCH');
  const original = readFileSync(join(f.root, ...DATASET.split('/')));
  try {
    writeFileSync(join(f.root, ...DATASET.split('/')), `${original}\n`);
    await rejects(run({}, {runner: fakeRunner()}), 'FIXTURE_MISMATCH');
  } finally {
    writeFileSync(join(f.root, ...DATASET.split('/')), original);
  }
  const untracked = join(f.root, 'unexpected.txt');
  writeFileSync(untracked, 'x');
  f.dirty = true;
  try {
    await rejects(run({}, {runner: fakeRunner()}), 'WORKTREE_DIRTY');
  } finally {
    f.dirty = false;
    rmSync(untracked);
  }
  const opts = options(); const first = await runController(opts, {now: f.now, git: fakeGit, prepareCheckout, runner: fakeRunner()});
  assert.equal(first.ok, true);
  await rejects(runController(opts, {now: f.now, git: fakeGit, prepareCheckout, runner: fakeRunner()}), 'OUTPUT_EXISTS');
});

test('no runner yields NOT_EXECUTED rather than a fake completed receipt', async () => {
  const result = await run();
  assert.equal(result.ok, false);
  assert.equal(result.receipt.observationStatus, 'NOT_EXECUTED');
  assert.equal(result.receipt.execution.status, 'NOT_EXECUTED');
  assert.equal(result.receipt.reasonCode, 'MAVEN_EXECUTION_NOT_ENABLED');
  assert.equal(validateReceipt(result.receipt), result.receipt);
});

test('timeout, nonzero exit, skipped/zero tests and missing export stay NOT_EXECUTED', async () => {
  const cases = [
    [{timedOut: true, writeExport: false, writeReport: false}, 'MAVEN_TIMEOUT'],
    [{exitCode: 1, writeExport: false, writeReport: false}, 'MAVEN_EXIT_NONZERO'],
    [{report: suite({skipped: 2})}, 'MAVEN_REPORT_INVALID'],
    [{report: suite({tests: 0})}, 'MAVEN_REPORT_INVALID'],
    [{writeExport: false}, 'EXPORT_MISSING'],
  ];
  for (const [runnerOptions, code] of cases) {
    const result = await run({}, {runner: fakeRunner(runnerOptions)});
    assert.equal(result.ok, false, code);
    assert.equal(result.reasonCode, code);
    assert.equal(result.receipt.observationStatus, 'NOT_EXECUTED');
    assert.equal(result.receipt.execution.status, 'FAILED');
    assert.notEqual(result.receipt.observationStatus, 'CONTROLLER_OBSERVED_UNPINNED');
    assert.equal(validateReceipt(result.receipt), result.receipt);
  }
});

test('stale HEAD and unacceptable output location fail closed', async () => {
  f.head = 'c'.repeat(40);
  try {
    await rejects(run({}, {runner: fakeRunner()}), 'STALE_TARGET');
  } finally {
    f.head = f.commit;
  }
  const inside = join(f.root, 'receipt-out');
  await rejects(run({outDir: inside}, {runner: fakeRunner()}), 'OUTPUT_INSIDE_WORKTREE');
  assert.equal(existsSync(inside), false);
});

test.after(() => rmSync(TMP, {recursive: true, force: true}));
