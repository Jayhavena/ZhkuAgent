// T08 执行回执 v1A 的纯函数边界。
// 这里只校验结构与不变量，不把任何 JSON 自报字段升级为可信执行证明。
import {createHash} from 'node:crypto';
import {parseJavaExportJson} from '../sprint02/java-export-json.mjs';

export const RECEIPT_RECORD_TYPE = 'T08_EXECUTION_RECEIPT';
export const CHALLENGE_RECORD_TYPE = 'T08_EXECUTION_CHALLENGE';
export const RECEIPT_SCHEMA_VERSION = 1;
export const CONTROLLER_NAME = 't08-receipt-controller';
export const CONTROLLER_VERSION = 't08-controller-v1b';
export const SUITE_CONTRACTS = Object.freeze({
  't08-controller-v1a':['actualRunnerTraceHistoryExportsOnlySafeT08Fields','rejectsHiddenUuidAndUnreviewedRevision'],
  't08-controller-v1b':['actualRunnerTraceHistoryExportsOnlySafeT08Fields','rejectsHiddenUuidAndUnreviewedRevision','rejectsParentJunctionBeforeCreatingExport(Path)'],
});
export const TRUST_LEVELS = Object.freeze(['SELF_REPORTED', 'CONTROLLER_OBSERVED', 'SIGNED_ATTESTED']);
export const OBSERVATION_STATUSES = Object.freeze(['NOT_EXECUTED', 'CONTROLLER_OBSERVED_UNPINNED']);
export const JAVA_TEST_CLASS = 'com.zhku.knowledge.ruleagent.RuleAgentOfflineTraceExportTest';
export const JAVA_TEST_PATH = `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOfflineTraceExportTest.java`;
export const SUREFIRE_REPORT_PATH = `knowledge/target/surefire-reports/TEST-${JAVA_TEST_CLASS}.xml`;
export const EXPECTED_TEST_COUNT = 3;
export const MAX_JSON_BYTES = 256 * 1024;
export const MAX_EXPORT_BYTES = 2 * 1024 * 1024;
const HEX64 = /^[a-f0-9]{64}$/;
const HEX40 = /^[a-f0-9]{40}$/;
const HEX40_64 = /^[a-f0-9]{40}(?:[a-f0-9]{24})?$/;
const SAFE_ID = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/;
const SAFE_WIN_ARG = /^[A-Za-z0-9_@+=:,./\\ -]+$/;

/** 抛出的错误只带稳定错误码，避免把路径、正文或异常消息写入回执/日志。 */
const SAFE_ERROR_CODES=new Set(["BAD_COMMIT", "BAD_ID", "BAD_PIN", "BAD_TIMEOUT", "BAD_TREE", "CHALLENGE_CASE", "CHALLENGE_FIELDS", "CHALLENGE_HASH", "CHALLENGE_ID", "CHALLENGE_MISMATCH", "CHALLENGE_NOT_FRESH", "CHALLENGE_PIN_MISMATCH", "CHALLENGE_RECORD_TYPE", "CHALLENGE_SOURCE", "CHALLENGE_TIME", "CHALLENGE_VERSION", "CHECKOUT_DIRTY", "CHECKOUT_TREE_MISMATCH", "CMD", "COMMAND_FIELDS", "COMMIT_TIME", "CONTROLLER_FIELDS", "CONTROLLER_IDENTITY", "CONTROLLER_INTERNAL_ERROR", "CONTROLLER_OBSERVED", "CONTROLLER_OBSERVED_UNPINNED", "DATASET_CASE", "DATASET_EXPECTED", "DATASET_MULTITURN", "DATASET_QUESTION", "DATASET_SCOPE", "DATASET_VERSION", "DIRECT", "ENOENT", "ETIMEDOUT", "EVALUATOR_FAILED", "EXECUTED", "EXECUTED_STATUS", "EXECUTION_FIELDS", "EXECUTION_STATUS", "EXIT_FIELDS", "EXIT_NOT_CLEAN", "EXPORT_EXISTS", "EXPORT_FIELDS", "EXPORT_INVALID", "EXPORT_MISSING", "EXPORT_TOO_LARGE", "EXTERNAL_PIN_VERIFIER_NOT_IMPLEMENTED", "FAILED", "FAILED_DETAIL_MISSING", "FAILED_STATUS", "FILE_TOO_LARGE", "FIXTURE_CHALLENGE_MISMATCH", "FIXTURE_FIELDS", "FIXTURE_IDENTITY", "FIXTURE_MISMATCH", "FIXTURE_NOT_PINNED", "FIXTURE_PIN_MISMATCH", "G2", "GIT_COMMAND_FAILED", "HEAD", "INVALID_ARGUMENT", "JSON_DUPLICATE_KEY", "JSON_MALFORMED", "JSON_NOT_TEXT", "JSON_TOO_DEEP", "JSON_TOO_LARGE", "JSON_UNSAFE_KEY", "LEGACY_RECEIPT_NOT_CURRENT", "MANIFEST_DUPLICATE", "MANIFEST_ENTRY", "MANIFEST_FIELDS", "MANIFEST_FILES", "MANIFEST_VERSION", "MAVEN_EXECUTION_NOT_ENABLED", "MAVEN_EXIT_NONZERO", "MAVEN_FIELDS", "MAVEN_NOT_CLEAN", "MAVEN_OUTSIDE_CHECKOUT", "MAVEN_REPORT_INVALID", "MAVEN_REPORT_MISSING", "MAVEN_TIMEOUT", "MISSING_ARGUMENT", "NOT_EVALUABLE", "NOT_EXECUTED", "NOT_EXECUTED_DETAIL", "NOT_EXECUTED_STATUS", "NOT_REGULAR_FILE", "NO_EXTERNAL_TRUST_ROOT", "OUTPUT_ESCAPE", "OUTPUT_EXISTS", "OUTPUT_INSIDE_WORKTREE", "PATH_UNSAFE", "RECEIPT_ARTIFACT_MISSING", "RECEIPT_BUNDLE_FIELDS", "RECEIPT_CHALLENGE_FIELDS", "RECEIPT_CHALLENGE_HASH", "RECEIPT_CHALLENGE_MISMATCH", "RECEIPT_COMMAND_MISMATCH", "RECEIPT_COUNT", "RECEIPT_DUPLICATE", "RECEIPT_EVIDENCE_CHANGED", "RECEIPT_EXECUTION_FAILED", "RECEIPT_EXPORT_MISMATCH", "RECEIPT_FIELDS", "RECEIPT_FIXTURE_MISMATCH", "RECEIPT_ID", "RECEIPT_INPUT_IDENTITY", "RECEIPT_INPUT_INVALID", "RECEIPT_INTEGRITY", "RECEIPT_INTERNAL_ERROR", "RECEIPT_NOT_EXECUTED", "RECEIPT_REASON", "RECEIPT_REPORT_MISMATCH", "RECEIPT_REQUIRED", "RECEIPT_RESULT_MISMATCH", "RECEIPT_SCOPE", "RECEIPT_STATUS", "RECEIPT_VERSION", "REGISTRATION_FIELDS", "ROOT_NOT_WORKTREE", "SELF_REPORTED", "SIGNED_ATTESTED", "SOURCE_CHALLENGE_MISMATCH", "SOURCE_FIELDS", "SOURCE_NOT_CLEAN", "STALE_TARGET", "SUREFIRE_CASE_COUNT", "SUREFIRE_CLASS", "SUREFIRE_COUNTS", "SUREFIRE_MALFORMED", "SUREFIRE_NOT_CLEAN", "SUREFIRE_SUITE_COUNT", "SUREFIRE_TEST_IDENTITY", "SUREFIRE_VERSION", "SYMLINK_FORBIDDEN", "T08_EXECUTION_CHALLENGE", "T08_EXECUTION_RECEIPT", "T08_JAVA_TRACE_EXPORT", "T08_S02_DATASET", "T08_S02_FREEZE", "TEMP_INSIDE_WORKTREE", "TREE_MISMATCH", "UNSAFE_COMMAND_ARGUMENT", "UNVERIFIED_EXTERNAL_REGISTRATION", "WORKTREE_DIRTY"]);
export class ReceiptError extends Error {
  constructor(code) {
    super(SAFE_ERROR_CODES.has(code)?code:'RECEIPT_INTERNAL_ERROR');
    this.name = 'ReceiptError';
    this.code = this.message;
  }
}
const fail = code => { throw new ReceiptError(code); };
const isObject = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const isHex64 = v => typeof v === 'string' && HEX64.test(v);
const isCommit = v => typeof v === 'string' && HEX40_64.test(v);
const nonNegativeInt = v => Number.isSafeInteger(v) && v >= 0;

export function sha256Hex(value) {
  return createHash('sha256').update(value).digest('hex');
}

/** 稳定序列化只用于计算本地回执摘要，不构成签名。 */
export function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (isObject(value)) {
    return `{${Object.keys(value).sort().map(k => `${JSON.stringify(k)}:${stableStringify(value[k])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function exactKeys(value, keys, code) {
  if (!isObject(value)) fail(code);
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((k, i) => k !== expected[i])) fail(code);
}

// 自带严格 JSON 解析器：JSON.parse 会静默接受重复键，可能让后一个值覆盖前一个安全值。
export function parseStrictJson(input, {maxBytes = MAX_JSON_BYTES} = {}) {
  const bytes=typeof input==='string'?Buffer.from(input,'utf8'):input;
  if(!Buffer.isBuffer(bytes))fail('JSON_NOT_TEXT');
  if(bytes.length>Math.min(maxBytes,MAX_EXPORT_BYTES))fail('JSON_TOO_LARGE');
  try {return parseJavaExportJson(bytes);}
  catch(error){
    const codes={JAVA_EXPORT_FORMAT:'JSON_MALFORMED',JAVA_EXPORT_DUPLICATE_KEY:'JSON_DUPLICATE_KEY',
      JAVA_EXPORT_UNSAFE_KEY:'JSON_UNSAFE_KEY',JAVA_EXPORT_LIMIT:'JSON_TOO_DEEP'};
    fail(codes[error?.message]??'JSON_MALFORMED');
  }
}

/** 路径白名单：拒绝绝对路径、盘符、反斜杠、. / ..、NUL 和控制字符。 */
export function assertSafeRelativePath(relativePath) {
  if (typeof relativePath !== 'string' || !relativePath || relativePath.length > 512) fail('PATH_UNSAFE');
  if (relativePath.includes('\\') || relativePath.includes('\0') || /[\u0000-\u001f]/.test(relativePath)) fail('PATH_UNSAFE');
  if (relativePath.startsWith('/') || /^[A-Za-z]:/.test(relativePath)) fail('PATH_UNSAFE');
  const parts = relativePath.split('/');
  if (parts.some(p => p === '' || p === '.' || p === '..')) fail('PATH_UNSAFE');
  return parts.join('/');
}

export function resolveInside(root, relativePath) {
  assertSafeRelativePath(relativePath);
  const normalizedRoot = root.replace(/[\\/]+$/, '');
  return `${normalizedRoot}/${relativePath}`;
}

export function isPathInside(parent, child) {
  const norm = p => p.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();
  const p = norm(parent); const c = norm(child);
  return c === p || c.startsWith(`${p}/`);
}

export function quoteWindowsArg(arg) {
  if (typeof arg !== 'string' || !SAFE_WIN_ARG.test(arg)) fail('UNSAFE_COMMAND_ARGUMENT');
  return arg.includes(' ') ? `"${arg}"` : arg;
}

export function validateChallenge(challenge, {now = new Date(), expected} = {}) {
  exactKeys(challenge, ['recordType', 'schemaVersion', 'challengeId', 'nonce', 'issuedAt', 'expiresAt',
    'candidateCommit', 'candidateTree', 'caseId', 'fixtureManifestSha256', 'datasetSha256', 'rubricSha256'], 'CHALLENGE_FIELDS');
  if (challenge.recordType !== CHALLENGE_RECORD_TYPE || challenge.schemaVersion !== RECEIPT_SCHEMA_VERSION) fail('CHALLENGE_VERSION');
  if (typeof challenge.challengeId!=='string'||!SAFE_ID.test(challenge.challengeId)||typeof challenge.nonce!=='string'||!/^[a-f0-9]{32,64}$/.test(challenge.nonce)) fail('CHALLENGE_ID');
  if (!isCommit(challenge.candidateCommit) || !isCommit(challenge.candidateTree)) fail('CHALLENGE_SOURCE');
  if (typeof challenge.caseId!=='string'||!SAFE_ID.test(challenge.caseId)) fail('CHALLENGE_CASE');
  for (const key of ['fixtureManifestSha256', 'datasetSha256', 'rubricSha256']) if (!isHex64(challenge[key])) fail('CHALLENGE_HASH');
  const issued = Date.parse(challenge.issuedAt); const expires = Date.parse(challenge.expiresAt);
  if (typeof challenge.issuedAt!=='string'||typeof challenge.expiresAt!=='string'||!Number.isFinite(issued)||!Number.isFinite(expires)||!Number.isFinite(now.getTime())) fail('CHALLENGE_TIME');
  const nowMs = now.getTime();
  if (issued > nowMs || nowMs >= expires || expires <= issued || expires - issued > 30 * 60 * 1000) fail('CHALLENGE_NOT_FRESH');
  if (expected) {
    for (const key of Object.keys(expected)) if (challenge[key] !== expected[key]) fail('CHALLENGE_MISMATCH');
  }
  return challenge;
}

export function validateFixtureManifest(manifest) {
  exactKeys(manifest, ['recordType', 'schemaVersion', 'algorithm', 'files'], 'MANIFEST_FIELDS');
  if (manifest.recordType !== 'T08_S02_FREEZE' || manifest.schemaVersion !== 1 || manifest.algorithm !== 'SHA-256') fail('MANIFEST_VERSION');
  if (!Array.isArray(manifest.files) || !manifest.files.length) fail('MANIFEST_FILES');
  const seen = new Set();
  const byPath = new Map();
  for (const entry of manifest.files) {
    exactKeys(entry, ['path', 'sha256', 'bytes'], 'MANIFEST_ENTRY');
    const path = assertSafeRelativePath(entry.path);
    if (seen.has(path.toLowerCase())) fail('MANIFEST_DUPLICATE');
    seen.add(path.toLowerCase());
    if (!isHex64(entry.sha256) || !nonNegativeInt(entry.bytes)) fail('MANIFEST_ENTRY');
    byPath.set(path, {path, sha256: entry.sha256, bytes: entry.bytes});
  }
  return byPath;
}

export function validateDatasetCase(dataset, {caseId}) {
  if (!isObject(dataset) || dataset.recordType !== 'T08_S02_DATASET' || dataset.schemaVersion !== 1) fail('DATASET_VERSION');
  if (dataset.split !== 'development' || !Array.isArray(dataset.cases) || dataset.cases.length !== 1) fail('DATASET_SCOPE');
  const selected = dataset.cases[0];
  if (selected.caseId !== caseId || selected.group !== 'G2' || selected.synthetic !== true) fail('DATASET_CASE');
  if (!isObject(selected.input) || !Array.isArray(selected.input.supplements) || selected.input.supplements.length !== 0) fail('DATASET_MULTITURN');
  if (typeof selected.input.question !== 'string' || !selected.input.question.trim()) fail('DATASET_QUESTION');
  if (!isObject(selected.expected) || !Array.isArray(selected.expected.taskStatus) || selected.expected.taskStatus.length !== 1) fail('DATASET_EXPECTED');
  return selected;
}

/** 版本决定完整测试集合；不能用“2或3都算通过”掩盖漏执行。 */
function surefireTags(xml){
  const stack=[],suites=[],cases=[];let cursor=0,roots=0,declarations=0;
  for(const m of xml.matchAll(/<[^>]*>/g)){
    const text=xml.slice(cursor,m.index);if(text.trim()&&!['system-out','system-err'].includes(stack.at(-1)))fail('SUREFIRE_MALFORMED');
    cursor=m.index+m[0].length;const tag=m[0];
    if(/^<\?xml\s[^<>]*\?>$/.test(tag)){if(roots||stack.length||declarations++)fail('SUREFIRE_MALFORMED');continue;}
    const end=/^<\/([A-Za-z][A-Za-z0-9_.:-]*)\s*>$/.exec(tag);
    if(end){if(stack.pop()!==end[1])fail('SUREFIRE_MALFORMED');continue;}
    const start=/^<([A-Za-z][A-Za-z0-9_.:-]*)([\s\S]*?)(\/?)>$/.exec(tag);if(!start)fail('SUREFIRE_MALFORMED');
    const [,name,raw,closed]=start,attributes={};let remaining=raw;
    while(remaining.trim()){
      const a=/^\s+([A-Za-z][A-Za-z0-9_.:-]*)\s*=\s*(?:"([^"<]*)"|'([^'<]*)')/.exec(remaining);
      if(!a||Object.hasOwn(attributes,a[1]))fail('SUREFIRE_MALFORMED');
      attributes[a[1]]=a[2]??a[3];remaining=remaining.slice(a[0].length);
    }
    const parent=stack.at(-1);
    const allowed={testsuite:[undefined],properties:['testsuite'],property:['properties'],testcase:['testsuite'],
      'system-out':['testsuite','testcase'],'system-err':['testsuite','testcase']};
    if(!allowed[name]?.includes(parent))fail('SUREFIRE_MALFORMED');
    if(name==='testsuite'){if(roots++)fail('SUREFIRE_MALFORMED');suites.push(attributes);}
    if(name==='testcase')cases.push(attributes);
    if(!closed)stack.push(name);
  }
  if(stack.length||xml.slice(cursor).trim()||roots!==1)fail('SUREFIRE_MALFORMED');
  return {suites,cases};
}
export function parseSurefireReport(xml,{controllerVersion=CONTROLLER_VERSION}={}) {
  const expected=SUITE_CONTRACTS[controllerVersion];
  if(!expected)fail('SUREFIRE_VERSION');
  if(typeof xml!=='string'||Buffer.byteLength(xml,'utf8')>MAX_EXPORT_BYTES||/<!DOCTYPE|<!ENTITY/i.test(xml))fail('SUREFIRE_MALFORMED');
  const structure=xml.replace(/<!\[CDATA\[[\s\S]*?\]\]>/g,'').replace(/<!--[\s\S]*?-->/g,'');
  if(/<(?:failure|error|skipped)\b/.test(structure))fail('SUREFIRE_NOT_CLEAN');
  const {suites,cases}=surefireTags(structure);
  const header=suites[0];if(header.name!==JAVA_TEST_CLASS)fail('SUREFIRE_CLASS');
  const counts={};for(const k of ['tests','failures','errors','skipped']){
    if(!/^(?:0|[1-9][0-9]{0,5})$/.test(header[k]??''))fail('SUREFIRE_COUNTS');counts[k]=Number(header[k]);
  }
  if(cases.length!==counts.tests)fail('SUREFIRE_CASE_COUNT');
  if(counts.tests!==expected.length||counts.failures||counts.errors||counts.skipped||/<(?:failure|error|skipped)\b/.test(structure))fail('SUREFIRE_NOT_CLEAN');
  if(new Set(cases.map(c=>c.name)).size!==expected.length||cases.some(c=>!expected.includes(c.name)||c.classname!==JAVA_TEST_CLASS))fail('SUREFIRE_TEST_IDENTITY');
  return counts;
}

const RECEIPT_TOP = ['recordType', 'schemaVersion', 'receiptId', 'issuedAt', 'observationStatus', 'reasonCode',
  'challenge', 'source', 'fixtures', 'execution', 'controller', 'externalRegistration', 'providerCalls', 'modelCalls', 'qualityStatus'];
const REASON_CODES = new Set(['MAVEN_EXECUTION_NOT_ENABLED', 'MAVEN_EXIT_NONZERO', 'MAVEN_TIMEOUT',
  'MAVEN_REPORT_MISSING', 'MAVEN_REPORT_INVALID', 'EXPORT_MISSING', 'EXPORT_INVALID', 'EXPORT_TOO_LARGE']);

function validateReceiptIntegrity(receipt) {
  if (!isHex64(receipt.receiptId)) fail('RECEIPT_ID');
  const {receiptId, ...body} = receipt;
  if (sha256Hex(stableStringify(body)) !== receiptId) fail('RECEIPT_INTEGRITY');
}

export function validateReceipt(receipt) {
  exactKeys(receipt, RECEIPT_TOP, 'RECEIPT_FIELDS');
  if (receipt.recordType !== RECEIPT_RECORD_TYPE || receipt.schemaVersion !== RECEIPT_SCHEMA_VERSION) fail('RECEIPT_VERSION');
  if(typeof receipt.issuedAt!=='string'||!Number.isFinite(Date.parse(receipt.issuedAt)))fail('CHALLENGE_TIME');
  if (!OBSERVATION_STATUSES.includes(receipt.observationStatus)) fail('RECEIPT_STATUS');
  if (receipt.providerCalls !== 0 || receipt.modelCalls !== 0 || receipt.qualityStatus !== 'NOT_EVALUABLE') fail('RECEIPT_SCOPE');
  if (receipt.reasonCode !== null && !REASON_CODES.has(receipt.reasonCode)) fail('RECEIPT_REASON');
  exactKeys(receipt.challenge, ['challengeId', 'nonce', 'sha256', 'issuedAt', 'expiresAt', 'caseId', 'candidateCommit',
    'candidateTree', 'fixtureManifestSha256', 'datasetSha256', 'rubricSha256'], 'RECEIPT_CHALLENGE_FIELDS');
  if (!isHex64(receipt.challenge.sha256)) fail('RECEIPT_CHALLENGE_HASH');
  const {sha256: challengeSha256, ...challengeBody} = receipt.challenge;
  validateChallenge({recordType: CHALLENGE_RECORD_TYPE, schemaVersion: RECEIPT_SCHEMA_VERSION, ...challengeBody},
    {now: new Date(receipt.issuedAt)});
  exactKeys(receipt.source, ['commit', 'tree', 'head', 'clean', 'untrackedCount'], 'SOURCE_FIELDS');
  if (!isCommit(receipt.source.commit) || !isCommit(receipt.source.tree) || !isCommit(receipt.source.head) ||
      receipt.source.commit !== receipt.source.head || receipt.source.clean !== true || receipt.source.untrackedCount !== 0) fail('SOURCE_NOT_CLEAN');
  exactKeys(receipt.fixtures, ['manifestSha256', 'datasetSha256', 'rubricSha256', 'javaTestSha256', 'caseId', 'group', 'split'], 'FIXTURE_FIELDS');
  if (![receipt.fixtures.manifestSha256, receipt.fixtures.datasetSha256, receipt.fixtures.rubricSha256, receipt.fixtures.javaTestSha256].every(isHex64) ||
      receipt.fixtures.caseId !== receipt.challenge.caseId || receipt.fixtures.group !== 'G2' || receipt.fixtures.split !== 'development') fail('FIXTURE_IDENTITY');
  if (receipt.source.commit !== receipt.challenge.candidateCommit || receipt.source.tree !== receipt.challenge.candidateTree) fail('SOURCE_CHALLENGE_MISMATCH');
  if(['manifestSha256','datasetSha256','rubricSha256'].some(k=>receipt.fixtures[k]!==receipt.challenge[k==='manifestSha256'?'fixtureManifestSha256':k]))fail('FIXTURE_CHALLENGE_MISMATCH');
  exactKeys(receipt.execution, ['status', 'command', 'exit', 'maven', 'export'], 'EXECUTION_FIELDS');
  exactKeys(receipt.controller, ['name', 'version', 'sourceSha256'], 'CONTROLLER_FIELDS');
  if (receipt.controller.name !== 't08-receipt-controller' || typeof receipt.controller.version!=='string'||!Object.hasOwn(SUITE_CONTRACTS,receipt.controller.version) || !isHex64(receipt.controller.sourceSha256)) fail('CONTROLLER_IDENTITY');
  exactKeys(receipt.externalRegistration, ['pinnedExternally', 'registryEntrySha256'], 'REGISTRATION_FIELDS');
  // v1A 没有可验证的外部注册或签名；任何自报 pinned=true 都必须失败关闭。
  if (receipt.externalRegistration.pinnedExternally !== false || receipt.externalRegistration.registryEntrySha256 !== null) fail('UNVERIFIED_EXTERNAL_REGISTRATION');
  if (receipt.execution.status === 'EXECUTED') {
    if (receipt.observationStatus !== 'CONTROLLER_OBSERVED_UNPINNED' || receipt.reasonCode !== null) fail('EXECUTED_STATUS');
    exactKeys(receipt.execution.command, ['executable', 'argv', 'cwd', 'hostShell'], 'COMMAND_FIELDS');
    if (typeof receipt.execution.command.executable !== 'string' || !Array.isArray(receipt.execution.command.argv) ||
        receipt.execution.command.argv.some(a => typeof a !== 'string') || typeof receipt.execution.command.cwd !== 'string' ||
        !['DIRECT', 'CMD'].includes(receipt.execution.command.hostShell)) fail('COMMAND_FIELDS');
    exactKeys(receipt.execution.exit, ['code', 'signal', 'timedOut', 'durationMs'], 'EXIT_FIELDS');
    if (receipt.execution.exit.code !== 0 || receipt.execution.exit.timedOut !== false ||
        receipt.execution.exit.signal !== null || !nonNegativeInt(receipt.execution.exit.durationMs)) fail('EXIT_NOT_CLEAN');
    exactKeys(receipt.execution.maven, ['tests', 'failures', 'errors', 'skipped', 'reportSha256'], 'MAVEN_FIELDS');
    const m = receipt.execution.maven;
    if (m.tests !== SUITE_CONTRACTS[receipt.controller.version].length || m.failures !== 0 || m.errors !== 0 || m.skipped !== 0 || !isHex64(m.reportSha256)) fail('MAVEN_NOT_CLEAN');
    exactKeys(receipt.execution.export, ['sha256', 'bytes'], 'EXPORT_FIELDS');
    if (!isHex64(receipt.execution.export.sha256) || !Number.isSafeInteger(receipt.execution.export.bytes) ||
        receipt.execution.export.bytes <= 0 || receipt.execution.export.bytes > MAX_EXPORT_BYTES) fail('EXPORT_INVALID');
  } else if (receipt.execution.status === 'FAILED') {
    if (receipt.observationStatus !== 'NOT_EXECUTED' || receipt.reasonCode === null) fail('FAILED_STATUS');
    if (receipt.execution.command === null && receipt.execution.exit === null &&
        receipt.execution.maven === null && receipt.execution.export === null) fail('FAILED_DETAIL_MISSING');
  } else if (receipt.execution.status === 'NOT_EXECUTED') {
    if (receipt.observationStatus !== 'NOT_EXECUTED' || receipt.reasonCode === null) fail('NOT_EXECUTED_STATUS');
    if (receipt.execution.command !== null || receipt.execution.exit !== null ||
        receipt.execution.maven !== null || receipt.execution.export !== null) fail('NOT_EXECUTED_DETAIL');
  } else fail('EXECUTION_STATUS');
  validateReceiptIntegrity(receipt);
  return receipt;
}

/**
 * v1A 不实现外部 pin 校验或签名验证，因此任何通过结构校验的回执最多只是 SELF_REPORTED。
 * 即使所有哈希互相一致，也只证明文件未被同步改坏，不证明控制器来自可信根。
 */
export function classifyReceiptTrust(receipt, {externalPin = null} = {}) {
  validateReceipt(receipt);
  return {
    trustLevel: 'SELF_REPORTED',
    claimedByInput: null,
    externalPinVerified: false,
    reason: externalPin ? 'EXTERNAL_PIN_VERIFIER_NOT_IMPLEMENTED' : 'NO_EXTERNAL_TRUST_ROOT',
    note: 'CONTROLLER_OBSERVED 只能由外部评审在固定控制器实现并独立重放后授予，本模块不会自行升级。',
  };
}
