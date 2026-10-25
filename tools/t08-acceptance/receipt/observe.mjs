#!/usr/bin/env node
// T08 执行回执控制器 v1A（候选实现）。
// 只负责“固定提交 + 外部冻结夹具 + 新挑战 + 独占目录”的离线 Maven 单 case 观测；
// 它不是可信根，输出回执在外部 pin 本实现之前只能按 SELF_REPORTED 对待。
import {existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, statSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {
  CHALLENGE_RECORD_TYPE, CONTROLLER_NAME, CONTROLLER_VERSION, EXPECTED_TEST_COUNT, JAVA_TEST_PATH,
  MAX_EXPORT_BYTES, MAX_JSON_BYTES, OBSERVATION_STATUSES, RECEIPT_RECORD_TYPE, RECEIPT_SCHEMA_VERSION,
  ReceiptError, SUREFIRE_REPORT_PATH, assertSafeRelativePath, classifyReceiptTrust, isPathInside,
  parseStrictJson, parseSurefireReport, quoteWindowsArg, sha256Hex, stableStringify, validateChallenge,
  validateDatasetCase, validateFixtureManifest, validateReceipt,
} from './receipt.mjs';

import {importJavaTraceExport} from '../sprint02/java-trace-adapter.mjs';

const fail = code => { throw new ReceiptError(code); };
const RECEIPT_FILE = 'receipt.json';
const REQUIRED_FIXTURES = ['dataset', 'rubric', 'javaTest'];
const MAX_COMMAND_OUTPUT = 16 * 1024 * 1024;

function boundedRead(path, maxBytes) {
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink()) fail('NOT_REGULAR_FILE');
  if (stat.size > maxBytes) fail('FILE_TOO_LARGE');
  return readFileSync(path);
}

function readJSON(path, maxBytes = MAX_JSON_BYTES) {
  return parseStrictJson(boundedRead(path, maxBytes), {maxBytes});
}

function assertNoLinkPath(path) {
  if (lstatSync(path).isSymbolicLink()) fail('SYMLINK_FORBIDDEN');
}

// Windows junction 在部分 Node/文件系统组合下被 lstat 报成普通目录，必须再用
// realpath 与字面路径比较才能识别重解析点；否则夹具可借 junction 逃逸工作树。
export function assertNoReparsePath(root, relativePath) {
  assertSafeRelativePath(relativePath);
  let current = root;
  for (const part of relativePath.split('/')) {
    current = join(current, part);
    if (lstatSync(current).isSymbolicLink()) fail('SYMLINK_FORBIDDEN');
    const real = realpathSync(current);
    if (real.toLowerCase() !== resolve(current).toLowerCase()) fail('SYMLINK_FORBIDDEN');
  }
}

function defaultGit(cwd, args, {timeoutMs = 30000} = {}) {
  const result = spawnSync('git', ['--no-optional-locks','-c','core.fsmonitor=false','-c','core.longpaths=true',...args], {cwd, encoding: 'utf8', shell: false, timeout: timeoutMs,
    maxBuffer: 4 * 1024 * 1024, windowsHide: true});
  if (result.error || result.status !== 0) fail('GIT_COMMAND_FAILED');
  return String(result.stdout ?? '').trim();
}

function loadOptions(options = {}) {
  const required = ['root', 'candidateCommit', 'candidateTree', 'challengePath', 'challengeSha256',
    'fixtureManifestPath', 'fixtureManifestSha256', 'datasetPath', 'rubricPath', 'caseId', 'outDir', 'candidateId'];
  for (const key of required) if (typeof options[key] !== 'string' || !options[key].trim()) fail('MISSING_ARGUMENT');
  for (const key of ['root', 'challengePath', 'fixtureManifestPath', 'outDir']) options[key] = resolve(options[key]);
  for (const key of ['datasetPath', 'rubricPath']) assertSafeRelativePath(options[key]);
  if (!/^[a-f0-9]{40}(?:[a-f0-9]{24})?$/.test(options.candidateCommit)) fail('BAD_COMMIT');
  if (!/^[a-f0-9]{40}(?:[a-f0-9]{24})?$/.test(options.candidateTree)) fail('BAD_TREE');
  if (!/^[a-f0-9]{64}$/.test(options.challengeSha256) || !/^[a-f0-9]{64}$/.test(options.fixtureManifestSha256)) fail('BAD_PIN');
  if (!/^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/.test(options.caseId) || !/^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/.test(options.candidateId)) fail('BAD_ID');
  const timeoutMs = options.timeoutMs ?? 240000;
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 15 * 60 * 1000) fail('BAD_TIMEOUT');
  return {...options, timeoutMs};
}

/** 检查输出目录尚不存在且不在产品工作树内；父目录必须已存在，避免隐式递归创建。 */
function prepareOutputDir(root, outDir) {
  if (existsSync(outDir)) fail('OUTPUT_EXISTS');
  if (isPathInside(root, outDir)) fail('OUTPUT_INSIDE_WORKTREE');
  assertNoLinkPath(dirname(outDir));
  const parentReal = realpathSync(dirname(outDir));
  if (parentReal.toLowerCase() !== resolve(dirname(outDir)).toLowerCase()) fail('SYMLINK_FORBIDDEN');
  if (isPathInside(root, parentReal)) fail('OUTPUT_ESCAPE');
  mkdirSync(outDir, {recursive: false});
  return outDir;
}

function verifyFixture(root, relativePath, expected) {
  assertNoReparsePath(root, relativePath);
  const {hash, bytes} = readFileDigest(join(root, relativePath), MAX_EXPORT_BYTES);
  if (hash !== expected.sha256 || bytes !== expected.bytes) fail('FIXTURE_MISMATCH');
  return hash;
}

function readFileDigest(path, maxBytes) {
  const bytes = boundedRead(path, maxBytes);
  return {hash: sha256Hex(bytes), bytes: bytes.length, data: bytes};
}

function controllerSourceDigest() {
  const here = dirname(fileURLToPath(import.meta.url));
  const parts = ['receipt.mjs', 'observe.mjs'].map(name => {
    const bytes = readFileSync(join(here, name));
    return `${name}\0${bytes.length}\0${sha256Hex(bytes)}`;
  });
  return sha256Hex(parts.join('\n'));
}

function publicCommand(executable, argv, cwd, hostShell) {
  return {executable, argv: [...argv], cwd, hostShell};
}

function makeReceiptBase({challenge, challengeSha256, source, fixtures, command, exit, maven, exported, reasonCode, executionStatus, now}) {
  const body = {
    recordType: RECEIPT_RECORD_TYPE,
    schemaVersion: RECEIPT_SCHEMA_VERSION,
    issuedAt: now.toISOString(),
    observationStatus: executionStatus === 'EXECUTED' ? 'CONTROLLER_OBSERVED_UNPINNED' : 'NOT_EXECUTED',
    reasonCode: reasonCode ?? null,
    challenge: {
      challengeId: challenge.challengeId, nonce: challenge.nonce, sha256: challengeSha256,
      issuedAt: challenge.issuedAt, expiresAt: challenge.expiresAt, caseId: challenge.caseId,
      candidateCommit: challenge.candidateCommit, candidateTree: challenge.candidateTree,
      fixtureManifestSha256: challenge.fixtureManifestSha256, datasetSha256: challenge.datasetSha256, rubricSha256: challenge.rubricSha256,
    },
    source,
    fixtures,
    execution: {
      status: executionStatus,
      command: command ?? null,
      exit: exit ?? null,
      maven: maven ?? null,
      export: exported ? {sha256: exported.sha256, bytes: exported.bytes} : null,
    },
    controller: {name: CONTROLLER_NAME, version: CONTROLLER_VERSION, sourceSha256: controllerSourceDigest()},
    externalRegistration: {pinnedExternally: false, registryEntrySha256: null},
    providerCalls: 0,
    modelCalls: 0,
    qualityStatus: 'NOT_EVALUABLE',
  };
  body.receiptId = sha256Hex(stableStringify(body));
  return body;
}

function writeReceipt(outDir, receipt) {
  validateReceipt(receipt);
  const path = join(outDir, RECEIPT_FILE);
  writeFileSync(path, `${JSON.stringify(receipt, null, 2)}\n`, {encoding: 'utf8', flag: 'wx'});
  return path;
}

/** 真实 Maven 运行器：只允许固定离线参数，命令行走 cmd.exe 时逐参数白名单校验。 */
export function createMavenRunner({mvnw = 'mvnw.cmd', mavenExecutable, timeoutMs = 240000} = {}) {
  return async ({checkoutDir, exportPath, caseId, candidateId, sourceSha, datasetPath, rubricPath}) => {
    const args = ['-o', '-B', '-ntp', '-pl', 'knowledge', '-am', '-Dtest=RuleAgentOfflineTraceExportTest',
      '-Dsurefire.failIfNoSpecifiedTests=false', `-Dzhku.t08.export=${exportPath}`,
      `-Dzhku.t08.dataset=${join(checkoutDir, datasetPath)}`, `-Dzhku.t08.caseId=${caseId}`,
      `-Dzhku.t08.candidateId=${candidateId}`, `-Dzhku.t08.sourceSha=${sourceSha}`,
      `-Dzhku.rulecheck.evidenceDir=${join(dirname(exportPath),'evidence')}`,
      `-DargLine=-Djava.io.tmpdir=${join(dirname(exportPath),'tmp')}`, 'test'];
    const executablePath = mavenExecutable?resolve(mavenExecutable):resolve(checkoutDir,mvnw);
    if(!mavenExecutable&&!isPathInside(checkoutDir,executablePath))fail('MAVEN_OUTSIDE_CHECKOUT');
    if(!lstatSync(executablePath).isFile()||realpathSync(executablePath).toLowerCase()!==executablePath.toLowerCase())fail('NOT_REGULAR_FILE');
    const env=Object.fromEntries(Object.entries(process.env).filter(([k])=>!['PASSWORD','TOKEN','SECRET','API_KEY','CREDENTIAL'].some(v=>k.toUpperCase().includes(v))));
    for(const key of ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','MAVEN_ARGS'])delete env[key];
    env.MAVEN_OPTS='-Dfile.encoding=UTF-8';
    const started = Date.now();
    let result;
    if (process.platform === 'win32') {
      const commandLine = [executablePath, ...args].map(quoteWindowsArg).join(' ');
      result = spawnSync(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', commandLine],
        {cwd: checkoutDir, env, encoding: 'utf8', shell: false, timeout: timeoutMs, maxBuffer: MAX_COMMAND_OUTPUT, windowsHide: true});
    } else {
      result = spawnSync(executablePath, args,
        {cwd: checkoutDir, env, encoding: 'utf8', shell: false, timeout: timeoutMs, maxBuffer: MAX_COMMAND_OUTPUT, windowsHide: true});
    }
    const timedOut = result.error?.code === 'ETIMEDOUT';
    return {
      command: publicCommand(executablePath, args, checkoutDir, process.platform === 'win32' ? 'CMD' : 'DIRECT'),
      exit: {code: timedOut ? null : result.status, signal: result.signal ?? null, timedOut, durationMs: Date.now() - started},
      stdoutBytes: Buffer.byteLength(String(result.stdout ?? '')), stderrBytes: Buffer.byteLength(String(result.stderr ?? '')),
    };
  };
}

async function defaultCheckout(root, commit, baseDir, gitFn = defaultGit) {
  const checkoutDir = join(baseDir, 'checkout');
  gitFn(baseDir, ['clone', '--shared', '--no-checkout', '--quiet', root, checkoutDir]);
  // 历史归档不是运行依赖；临时稀疏检出仍保留全部三个模块及其测试和固定夹具。
  gitFn(checkoutDir,['sparse-checkout','set','--cone','agent-runtime','agent','knowledge','tools/t08-acceptance','docs/preparation/t08/sprint02']);
  gitFn(checkoutDir, ['checkout', '--detach', '--quiet', commit]);
  return checkoutDir;
}

/**
 * 运行一次受控观测。deps.runner 供离线测试注入 fake runner；未注入且未显式 allowMaven 时
 * 只生成 NOT_EXECUTED 回执，绝不伪造已运行结果。
 */
export async function runController(options, deps = {}) {
  const opts = loadOptions(options);
  const now = deps.now ?? new Date();
  const root = opts.root;
  // deps.git 供离线受控测试替换；生产默认仍走 git 可执行文件 + argv 数组。
  const git = deps.git ?? defaultGit;

  // 1) 产品工作树身份：根目录、HEAD、提交->树、绝对干净，缺一不可。
  const top = realpathSync(git(root, ['rev-parse', '--show-toplevel']));
  if (top.toLowerCase() !== realpathSync(root).toLowerCase()) fail('ROOT_NOT_WORKTREE');
  git(root, ['cat-file', '-e', `${opts.candidateCommit}^{commit}`]);
  const resolvedTree = git(root, ['rev-parse', `${opts.candidateCommit}^{tree}`]);
  if (resolvedTree !== opts.candidateTree) fail('TREE_MISMATCH');
  const head = git(root, ['rev-parse', 'HEAD']);
  if (head !== opts.candidateCommit) fail('STALE_TARGET');
  const status = git(root, ['status', '--porcelain=v1', '-z', '--untracked-files=all']);
  if (status !== '') fail('WORKTREE_DIRTY');
  const commitTime = Date.parse(git(root, ['show', '-s', '--format=%cI', opts.candidateCommit]));
  if (!Number.isFinite(commitTime)) fail('COMMIT_TIME');

  // 2) 挑战必须由外部提供并固定摘要；它绑定提交、树、case 和夹具。
  const challengeBytes = boundedRead(opts.challengePath, MAX_JSON_BYTES);
  if (sha256Hex(challengeBytes) !== opts.challengeSha256) fail('CHALLENGE_PIN_MISMATCH');
  const challenge = parseStrictJson(challengeBytes);
  if (challenge.recordType !== CHALLENGE_RECORD_TYPE) fail('CHALLENGE_RECORD_TYPE');
  validateChallenge(challenge, {now, expected: {
    candidateCommit: opts.candidateCommit, candidateTree: opts.candidateTree, caseId: opts.caseId,
    fixtureManifestSha256: opts.fixtureManifestSha256,
  }});
  if (Date.parse(challenge.issuedAt) < commitTime) fail('STALE_TARGET');

  // 3) 外部夹具清单本身先按外部 pin 验证，再逐文件核对字节；清单不得自冻结。
  const manifestBytes = boundedRead(opts.fixtureManifestPath, MAX_JSON_BYTES);
  if (sha256Hex(manifestBytes) !== opts.fixtureManifestSha256) fail('FIXTURE_PIN_MISMATCH');
  const entries = validateFixtureManifest(parseStrictJson(manifestBytes));
  const fixturePaths = {dataset: opts.datasetPath, rubric: opts.rubricPath, javaTest: JAVA_TEST_PATH};
  const fixtureHashes = {};
  for (const key of REQUIRED_FIXTURES) {
    const entry = entries.get(fixturePaths[key]);
    if (!entry) fail('FIXTURE_NOT_PINNED');
    fixtureHashes[key] = verifyFixture(root, fixturePaths[key], entry);
  }
  if (challenge.datasetSha256 !== fixtureHashes.dataset || challenge.rubricSha256 !== fixtureHashes.rubric) fail('FIXTURE_CHALLENGE_MISMATCH');
  const datasetBytes = boundedRead(join(root, opts.datasetPath), MAX_EXPORT_BYTES);
  const dataset = parseStrictJson(datasetBytes);
  validateDatasetCase(dataset, {caseId: opts.caseId});

  // 4) 输出目录独占且在工作树外；之后任何失败都不会覆盖已有回执。
  const outDir = prepareOutputDir(root, opts.outDir);
  const source = {commit: head, tree: resolvedTree, head, clean: true, untrackedCount: 0};
  const fixtures = {
    manifestSha256: opts.fixtureManifestSha256, datasetSha256: fixtureHashes.dataset,
    rubricSha256: fixtureHashes.rubric, javaTestSha256: fixtureHashes.javaTest,
    caseId: opts.caseId, group: 'G2', split: 'development',
  };
  const base = {challenge, challengeSha256: opts.challengeSha256, source, fixtures, now};

  let checkoutDir = null;
  let baseDir = null;
  try {
    baseDir = mkdtempSync(join(tmpdir(), 't08-receipt-'));
    if (isPathInside(root, baseDir)) fail('TEMP_INSIDE_WORKTREE');
    const prepare = deps.prepareCheckout ?? ((r, c, b) => defaultCheckout(r, c, b, git));
    checkoutDir = await prepare(root, opts.candidateCommit, baseDir);
    const checkoutTree = git(checkoutDir, ['rev-parse', `${opts.candidateCommit}^{tree}`]);
    if (checkoutTree !== opts.candidateTree) fail('CHECKOUT_TREE_MISMATCH');
    if (git(checkoutDir, ['status', '--porcelain=v1', '-z', '--untracked-files=all']) !== '') fail('CHECKOUT_DIRTY');
    for (const key of REQUIRED_FIXTURES) verifyFixture(checkoutDir, fixturePaths[key], entries.get(fixturePaths[key]));

    const exportDir = join(checkoutDir, 'knowledge', 'target', 't08-receipt');
    mkdirSync(exportDir, {recursive: true});
    for(const relative of ['knowledge','knowledge/target','knowledge/target/t08-receipt'])assertNoReparsePath(checkoutDir,relative);
    mkdirSync(join(exportDir,'tmp'));
    const exportPath = join(exportDir, 'java-export.json');
    if(existsSync(join(checkoutDir,SUREFIRE_REPORT_PATH)))fail('MAVEN_REPORT_INVALID');
    if (existsSync(exportPath)) fail('EXPORT_EXISTS');

    let runner = deps.runner;
    if (!runner && opts.allowMaven === true) runner = createMavenRunner({mavenExecutable:opts.mavenExecutable,timeoutMs: opts.timeoutMs});
    if (!runner) {
      const receipt = makeReceiptBase({...base, executionStatus: 'NOT_EXECUTED',
        reasonCode: 'MAVEN_EXECUTION_NOT_ENABLED'});
      return {ok: false, receipt, receiptPath: writeReceipt(outDir, receipt), reasonCode: receipt.reasonCode};
    }

    const observed = await runner({checkoutDir, exportPath, caseId: opts.caseId, candidateId: opts.candidateId,
      sourceSha: opts.candidateCommit, datasetPath: opts.datasetPath, rubricPath: opts.rubricPath, timeoutMs: opts.timeoutMs});
    const command = observed.command ? publicCommand(observed.command.executable, observed.command.argv,
      observed.command.cwd, observed.command.hostShell) : null;
    const exit = observed.exit ? {code: observed.exit.code, signal: observed.exit.signal ?? null,
      timedOut: observed.exit.timedOut === true, durationMs: observed.exit.durationMs} : null;

    const failed = (reasonCode, maven = null, exported = null) => {
      const receipt = makeReceiptBase({...base, command, exit, maven, exported, executionStatus: 'FAILED', reasonCode});
      return {ok: false, receipt, receiptPath: writeReceipt(outDir, receipt), reasonCode};
    };
    if (!exit || exit.timedOut) return failed('MAVEN_TIMEOUT');
    if (exit.code !== 0 || exit.signal !== null) return failed('MAVEN_EXIT_NONZERO');

    let maven = null,reportBytes=null;
    try {
      const report = readFileDigest(join(checkoutDir, SUREFIRE_REPORT_PATH), MAX_EXPORT_BYTES);
      reportBytes=report.data;
      const counts=parseSurefireReport(new TextDecoder('utf-8',{fatal:true}).decode(report.data));
      maven = {...counts, reportSha256: report.hash};
      if (counts.tests !== EXPECTED_TEST_COUNT) return failed('MAVEN_REPORT_INVALID', maven);
    } catch (error) {
      if (error?.code === 'ENOENT') return failed('MAVEN_REPORT_MISSING', maven);
      if (error instanceof ReceiptError) return failed(error.code === 'NOT_REGULAR_FILE' || error.code === 'FILE_TOO_LARGE' ? 'MAVEN_REPORT_MISSING' : 'MAVEN_REPORT_INVALID', maven);
      throw error;
    }

    let exported = null;
    try {
      const file = readFileDigest(exportPath, MAX_EXPORT_BYTES);
      if (file.bytes === 0) return failed('EXPORT_INVALID', maven);
      exported = {sha256: file.hash, bytes: file.bytes};
      // 导出必须自带与挑战/case 一致的身份；哈希匹配本身仍只是自报字段。
      const doc = parseStrictJson(file.data);
      if (doc.recordType !== 'T08_JAVA_TRACE_EXPORT' || doc.caseId !== opts.caseId ||
          doc.candidateId !== opts.candidateId || doc.sourceSha !== opts.candidateCommit ||
          doc.datasetSha256 !== fixtureHashes.dataset || doc.providerCalls !== 0 ||
          !Array.isArray(doc.traces) || doc.traces.length !== 1) return failed('EXPORT_INVALID', maven, exported);
      try{importJavaTraceExport(doc,{candidateId:opts.candidateId,sourceSha:opts.candidateCommit,caseId:opts.caseId,datasetSha256:fixtureHashes.dataset});}
      catch{return failed('EXPORT_INVALID',maven,exported);}
      writeFileSync(join(outDir,'java-export.json'),file.data,{flag:'wx'});
      writeFileSync(join(outDir,'surefire.xml'),reportBytes,{flag:'wx'});
    } catch (error) {
      if (error?.code === 'ENOENT') return failed('EXPORT_MISSING', maven);
      if (error instanceof ReceiptError) return failed('EXPORT_MISSING', maven);
      throw error;
    }

    const receipt = makeReceiptBase({...base, command, exit, maven, exported, executionStatus: 'EXECUTED', reasonCode: null});
    return {ok: true, receipt, receiptPath: writeReceipt(outDir, receipt), reasonCode: null};
  } finally {
    // 临时检出只用于本次观测；失败也清理，绝不动产品工作树或已有 worktree。
    if(baseDir){
      // 仅清理本次mkdtemp拥有的临时检出，拒绝重解析路径；不触及任何已有Worktree。
      const parent=resolve(tmpdir());
      if(dirname(baseDir)===parent&&realpathSync(baseDir).toLowerCase()===resolve(baseDir).toLowerCase()&&
          !isPathInside(root,baseDir)&&isPathInside(parent,baseDir))rmSync(baseDir,{recursive:true,force:true});
    }
  }
}

function parseArgs(argv) {
  const allowed = new Set(['root', 'candidate-commit', 'candidate-tree', 'challenge', 'challenge-sha256',
    'fixture-manifest', 'fixture-manifest-sha256', 'dataset', 'rubric', 'case-id', 'candidate-id', 'out-dir', 'execute', 'maven']);
  const out = {}; const alias = {'candidate-commit': 'candidateCommit', 'candidate-tree': 'candidateTree',
    challenge: 'challengePath', 'challenge-sha256': 'challengeSha256', 'fixture-manifest': 'fixtureManifestPath',
    'fixture-manifest-sha256': 'fixtureManifestSha256', dataset: 'datasetPath', rubric: 'rubricPath',
    'case-id': 'caseId', 'candidate-id': 'candidateId', 'out-dir': 'outDir',maven:'mavenExecutable'};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (token === '--execute') { out.allowMaven = true; continue; }
    if (!token.startsWith('--')) fail('INVALID_ARGUMENT');
    const key = token.slice(2);
    if (!allowed.has(key) || out[alias[key] ?? key] !== undefined || i + 1 >= argv.length) fail('INVALID_ARGUMENT');
    out[alias[key] ?? key] = argv[++i];
  }
  return out;
}

export async function main(argv = process.argv.slice(2), io = {out: v => process.stdout.write(`${v}\n`), err: v => process.stderr.write(`${v}\n`)}) {
  try {
    const result = await runController(parseArgs(argv));
    io.out(JSON.stringify({ok: result.ok, receiptPath: result.receiptPath,
      observationStatus: result.receipt.observationStatus, trustLevel: classifyReceiptTrust(result.receipt).trustLevel,
      reasonCode: result.reasonCode}));
    return result.ok ? 0 : 1;
  } catch (error) {
    const code = error instanceof ReceiptError ? error.code : 'CONTROLLER_INTERNAL_ERROR';
    io.err(JSON.stringify({ok: false, error: code}));
    return 2;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) process.exitCode = await main();
