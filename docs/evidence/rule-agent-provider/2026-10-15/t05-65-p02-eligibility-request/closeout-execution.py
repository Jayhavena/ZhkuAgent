# T05-65 执行收口：授权关闭、尝试台账、结果归档（零外发，只读 live 证据 + 只写本目录）
from pathlib import Path
import hashlib, json, subprocess, datetime, re, os

R = Path.cwd()
O = R/'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request'
LIVE = O/'live-9e82970ee7d048adb14fa7f666e8019b'
P63 = R/'docs/evidence/rule-agent-provider/2026-10-15/t05-63-p02-eligibility-live'
read = lambda p: json.loads(Path(p).read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(n, d): (O/n).write_bytes((json.dumps(d, ensure_ascii=False, indent=2)+'\n').encode('utf-8'))
def wtext(n, t): (O/n).write_bytes(t.encode('utf-8'))

PKG = 'ce4ddb851fd59328bd039e75e3e837e5891e08d6011a83ba954530ed0692754e'
JAR = '79a22f1c24c0a893d52a888c7e5678e4988f3e8484088ee2ead85ba89259ed70'
SC = '9e28e451ed02a1b2fa733192828e0913d9ead0b5'
now = datetime.datetime.now(datetime.timezone.utc).isoformat()
RUN = '16a00fc5-86c5-4b26-81ec-0b69fe211158'

# ---------- 1. live 证据完整性 ----------
outcome = read(LIVE/(RUN+'-outcome.json'))
result = read(LIVE/'probe-result.json')
snap = outcome['snapshot']
assert snap['taskStatus'] == 'FAILED' and snap['runState'] == 'FAILED' and snap['terminated'] is True
assert result['passed'] is False and result['allowanceConsumed'] == 4 and result['resourcesClosed'] is True
assert result['maximumProviderCalls'] == 10 and result['caseSelection'] == 'P02_CONTINUATION_01'
assert snap['execution']['modelAttemptCount'] == 4 and snap['execution']['providerResponseReceived'] is True
assert snap['result']['coverage'] == 'NONE' and snap['result']['items'][0]['issues'][0]['reasonCode'] == 'INVALID_MODEL_RESPONSE'
# 尝试轨迹：4 次派发全部 HTTP 200；失败在宿主侧核验合同解码
traces = [json.loads(l) for l in (LIVE/'provider-traces.jsonl').read_text(encoding='utf-8-sig').splitlines() if l.strip()]
finished = [t for t in traces if t.get('phase') == 'FINISHED']
assert len(finished) == 4 and all(t['status'] == 200 for t in finished)
review = next(t for t in finished if t['purpose'] == 'PUBLICATION_REVIEW')
assert review['validation']['stage'] == 'REVIEW_BINDING' and review['validation']['code'] == 'REVIEW_CONDITION_UNKNOWN'
decisions = [t for t in finished if t['purpose'] == 'DECISION']
assert all(t['reason'] is None and t['validation']['code'] == 'NONE' for t in decisions), 'decision-side provider calls all succeeded'
tin = sum(t['inputTokens'] for t in finished); tout = sum(t['outputTokens'] for t in finished)
assert (tin, tout) == (6566, 638)
estimate = tin*0.8/1e6 + tout*2.7/1e6
# ASK 本体合格：裸 conditionId + 已登记事实；核验输入快照已落盘（precheck 与 requireProposal 均通过后才 captureInput）
ask = read(LIVE/(RUN+'-3-PROPOSED_ACTION.json'))
ask_body = json.loads(ask['body'])
assert ask_body['conditionId'] == 'courseType'
assert ask_body['supportingFactIds'] == ['S10/RETAKE_ONLY:public-failed-no-retake', 'S10/RETAKE_ONLY:professional-failed-retake-application']
assert (LIVE/(RUN+'-4-REVIEW_INPUT_SNAPSHOT.json')).exists()
review_resp = json.loads(read(LIVE/(RUN+'-4-RESPONSE.json'))['body'])
review_content = json.loads(review_resp['choices'][0]['message']['content'])
bad_keys = sorted({c for req in review_content['requirements'] for c in req['necessaryConditionIds'] if c != 'courseType'})
assert bad_keys == ['S10/RETAKE_ONLY:courseType'], bad_keys
assert review_content['inputFingerprint'] == json.loads(read(LIVE/(RUN+'-4-REQUEST.json'))['body'] and
       [m for m in json.loads(read(LIVE/(RUN+'-4-REQUEST.json'))['body'])['messages'] if m['role'] == 'user'][0]['content'])['input']['inputFingerprint']
# 旧批次先例：t05-63 同一死因（核验侧带前缀键）
auth63 = read(P63/'authorization.json')
assert auth63['status'] == 'CLOSED_FAILED' and auth63['consumedCalls'] == 4

# ---------- 2. 授权关闭 ----------
save('authorization.json', {
  'status': 'CLOSED_FAILED',
  'humanAuthorization': 'User explicitly approved in conversation on 2026-10-16: 明确批准：包 ce4ddb85…、一次 P02、2 run、10 次调用、12 元上限',
  'packageSha256': PKG, 'disclosureSha256': 'b1d900b877beb81d854e9954f90c4f5158f87060c3d5010ed8083be983b09a13',
  'sourceCommit': SC, 'jarSha256': JAR, 'preparationHead': '43ecebc638cfe3788071b6ecdff47ed26523fd1c', 'applicationCommit': '2272425',
  'maximumBatches': 1, 'maximumRuns': 2, 'maximumCalls': 10, 'maximumCny': 12,
  'consumedCalls': 4, 'remainingAuthorizedCalls': 0, 'unusedCallsClosed': 6,
  'priorAllowances': 'CLOSED_NOT_REUSED', 'automaticRetries': 0, 'automaticNextBatch': False,
  'maximumActiveExecutionMinutesPerTurn': 20,
  'executionStartedUtc': '2026-10-16T00:32:40+00:00', 'executionFinishedUtc': '2026-10-16T00:33:23+00:00',
  'timeBasis': 'execution window bounded by host.log first migration line 00:33:07Z and probe-result.json mtime 00:33:22Z; ps1 invocation approximated before container startup',
  'taskId': snap['taskId'], 'runId': RUN, 'publishedVersion': snap['result']['publishedVersion'],
  'runsExecuted': 1, 'secondRunNotStarted': True,
  'failureReason': 'REVIEW_CONDITION_UNKNOWN at PUBLICATION_REVIEW; run FAILED before WAITING_USER, so PUBLIC was never injected and run 2 never started',
  'pricingPreflight': {
    'checkedUtc': '2026-10-16T00:31:00+00:00', 'source': 'https://help.aliyun.com/zh/model-studio/qwen3-8-flash',
    'snapshotSha256': '25e568713d61fc6c6e8b63bf7dfd2a6b575fb611c0e6b5311aee76deac344b55',
    'inputCnyPerMillion': '0.8', 'outputCnyPerMillion': '2.7',
    'publicNonThinkingMaximumInputTokens': 991808, 'publicMaximumOutputTokens': 131072,
    'maximumPublicTokenReservationCny': '11.473408', 'withinRequestedCap': True},
  'environmentPreflight': {
    'checkedUtc': '2026-10-16T00:30:00+00:00', 'javaAvailable': True, 'dockerAvailable': True,
    'dockerServerVersion': '29.8.2', 'keyAvailable': True,
    'pgvectorImage': 'pgvector/pgvector:0.8.6-pg16-bookworm (ccc6e83d6e35, local, --pull never)',
    'databasePortPolicy': 'dedicated random loopback port 55518; 54329 demo database refused and untouched',
    'checkPackagePassedZeroCalls': True},
  'usage': {'inputTokens': tin, 'outputTokens': tout,
    'usageEstimateCny': str(estimate), 'estimateBasis': 'qwen3.8-flash Beijing public list price; estimate only, not an invoice',
    'actualInvoiceCny': None, 'invoiceStatus': 'UNKNOWN_UNTIL_RECONCILED; never recorded as zero'},
  'closedUtc': now})

# ---------- 3. 尝试台账 ----------
attempts = []
for t in finished:
    attempts.append({'attempt': t['attempt'], 'purpose': t['purpose'],
        'dispatchState': 'DISPATCHED', 'httpStatus': t['status'], 'responseReceived': True,
        'requestSha256': t['requestSha256'], 'responseSha256': t['responseSha256'],
        'receivedHeaders': 'flag only; raw HTTP headers not retained', 'requestId': None,
        'elapsedMillis': t['diagnostics'].get('elapsedMillis'),
        'inputTokens': t['inputTokens'], 'outputTokens': t['outputTokens'],
        'usageEstimateCny': str(t['inputTokens']*0.8/1e6 + t['outputTokens']*2.7/1e6),
        'actualInvoiceCny': None, 'feeStatus': 'UNKNOWN_UNTIL_RECONCILED',
        'validationStage': t['validation']['stage'], 'validationCode': t['validation']['code'],
        'terminalState': 'FAILED'})
names = {1: 'SEARCH_REVIEWED_RULES', 2: 'READ_VERIFIED_RULE', 3: 'ASK_NECESSARY_CONDITION', 4: 'PUBLICATION_REVIEW'}
for a in attempts: a['proposedActionFile'] = RUN+'-'+str(a['attempt'])+'-PROPOSED_ACTION.json' if a['attempt'] <= 3 else None
save('attempt-ledger.json', {'status': 'CLOSED_FAILED_ALL_ATTEMPTS_RECORDED', 'packageSha256': PKG,
  'attempts': attempts, 'dispatchedCalls': 4, 'authorizedCalls': 10, 'consumedCalls': 4,
  'remainingAuthorizedCalls': 0, 'unusedCallsClosed': 6,
  'actualInvoiceCny': None, 'invoiceStatus': 'UNKNOWN_UNTIL_RECONCILED; usage estimate 0.0069754 CNY is not an invoice',
  'phaseAccounting': 'BEGIN/RECEIVED/FINISHED are phases of one attempt, not extra calls; 4 dispatched calls total',
  'dedupKey': ['taskId', 'runId', 'attempt'],
  'timeoutPolicy': 'Dispatched timeout consumes allowance; missing usage -> UNKNOWN fee, never zero or extra replacement call',
  'closeoutPolicy': 'Record every attempt and unknown cost, close all unused allowance, no repair/retry/next batch'})

# ---------- 4. 执行记录 ----------
save('execution.json', {
  'executedUtc': now, 'mode': 'ONLINE', 'case': 'P02_CONTINUATION_01',
  'entry': '普通Windows PowerShell 5.1 → start-rule-agent-probe.cmd -Probe -OnlyP02 -ApprovedPackageSha256 '+PKG,
  'approvedPackageSha256': PKG, 'approvedBy': 'user explicit approval 2026-10-16',
  'temporaryDatabase': {'image': 'pgvector/pgvector:0.8.6-pg16-bookworm', 'port': 55518,
    'database': 'zhkuagent_rulecheck_e2e', 'containerRemovedAfterRun': True, 'demoDatabase54329Touched': False},
  'modelCalls': 4, 'runsExecuted': 1, 'maximumRuns': 2,
  'hostExit': 'non-zero by design: P02首轮未形成合格必要追问，停止本批 (RuleAgentOnlineProbeHost.java:90) → ps1 line 88 throw, no extra calls or retries',
  'probeResult': result, 'resourcesClosed': True,
  'resourceVerification': {'leftoverProbeContainers': 0, 'probeJvmExited': True,
    'note': 'only remaining java.exe is the Qoder IDE language server (redhat.java), unrelated to the probe; not terminated by name',
    'launcherLog': 'launcher-6c5c079473fe4e608c862f7cf425dadf.log', 'liveEvidenceDirectory': LIVE.name},
  'sequence': [
    {'attempt': 1, 'action': 'SEARCH_REVIEWED_RULES', 'result': 'SUCCEEDED', 'note': '11 candidates incl. 第十八条 retake/deferred-exam article'},
    {'attempt': 2, 'action': 'READ_VERIFIED_RULE', 'result': 'SUCCEEDED', 'note': 'read 第十八条; 6 verified facts across S10/COMBINED, S10/DEFERRED_EXAM_ONLY, S10/RETAKE_ONLY'},
    {'attempt': 3, 'action': 'ASK_NECESSARY_CONDITION', 'result': 'PROPOSAL ACCEPTED (precheck + capability + snapshot capture passed)',
     'note': 'bare conditionId courseType with two registered eligible facts; decision-side namespace confusion did NOT recur this run'},
    {'attempt': 4, 'action': 'PUBLICATION_REVIEW', 'result': 'REJECTED REVIEW_CONDITION_UNKNOWN',
     'note': 'model emitted prefixed key S10/RETAKE_ONLY:courseType in necessaryConditionIds; host rejects without silent normalization; provider Failure propagated to both decision and review events by design'}],
  'run1Outcome': {'taskStatus': 'FAILED', 'runState': 'FAILED', 'coverage': 'NONE',
    'publishedVersion': snap['result']['publishedVersion'], 'waitingUserNotFormed': True,
    'publicInjected': False, 'secondRunStarted': False}})

# ---------- 5. 诊断 ----------
save('diagnosis.json', {
  'diagnosedUtc': now, 'outcome': 'FAILED_SAME_ROOT_CAUSE_AS_T05_63',
  'rootCause': 'Review-side identifier namespace confusion: the model wrote necessaryConditionIds as S10/RETAKE_ONLY:courseType (rule-prefixed) while input.conditions only declares the bare key courseType; RulePublicationProtocol.decode rejects with REVIEW_CONDITION_UNKNOWN (one-vote veto, no silent normalization).',
  'evidence': [
    'provider-traces.jsonl attempt 4 FINISHED: validation stage=REVIEW_BINDING code=REVIEW_CONDITION_UNKNOWN',
    '4-RESPONSE.json content: both requirements carry necessaryConditionIds ["S10/RETAKE_ONLY:courseType"]',
    '4-REQUEST.json input.conditions: only bare courseType under S10/RETAKE_ONLY and S10/DEFERRED_EXAM_ONLY',
    'RulePublicationProtocol.java:55-58 explicit rejection of prefixed/unknown condition keys'],
  'progressVsT05_63': 'Decision side improved: the ASK used the bare courseType key and only registered eligible fact IDs (t05-52 style ASK prefix failure did not recur); review side still failed, so the run still died before Gate.',
  'whyMoreCallsCouldNotHelp': 'The failure is a deterministic contract rejection at the reserved review call, not a wandering path; attempts 1-3 succeeded and the 5th per-run attempt was never reached. Extra batch allowance cannot bypass a one-vote veto.',
  'localReplayConsistency': 't05-64 local replay already proved the host publishes within 4 calls when the review output is well-formed; the remaining gap is model behavior on the review contract, not host engineering.',
  'eventReadingNote': 'The attempt-3 event reason INVALID_MODEL_RESPONSE is the attempt-4 provider Failure propagated onto the decision event by design (RuleAgentRunner: Provider失败越过原领域异常分支；先留下提议和核验失败). The ASK itself passed precheckAsk, requireProposal and input capture; do not misread it as an ASK-side rejection.',
  'priorBatchPrecedent': 't05-63 (package 1e44705a, authorization CLOSED_FAILED, 4 calls) failed with the same review-side prefixed key.',
  'conclusion': 'P02 online acceptance still NOT passed; T05 remains incomplete. No budget/shape/contract change was made; batch closed with 6 unused calls closed and actual invoice unknown.'})

# ---------- 6. 结果摘要 + README ----------
save('result-summary.json', {
  'summarizedUtc': now, 'packageSha256': PKG, 'case': 'P02_CONTINUATION_01',
  'passed': False, 'authorizationStatus': 'CLOSED_FAILED',
  'modelCallsDispatched': 4, 'modelCallsAuthorized': 10, 'unusedCallsClosed': 6,
  'runsExecuted': 1, 'runsAuthorized': 2,
  'failureCode': 'REVIEW_CONDITION_UNKNOWN', 'failureStage': 'REVIEW_BINDING',
  'finalState': 'FAILED', 'coverage': 'NONE', 'usageEstimateCny': str(estimate), 'actualInvoiceCny': None,
  't05StatusUnchanged': 'T05 未完成；真实在线验收未通过'})
readme = '''# T05-65 新请求包执行收口（CLOSED_FAILED）

包SHA256：`@PKG@`；jar：`@JAR@`；实现提交9e28e45。用户于2026-10-16明确批准：一次P02、同task最多2 run、最多10次真实调用、12元上限。执行1个run后失败关闭，**剩余6次调用已关闭不复用**。

[授权申请](REQUEST-APPLICATION.md)｜[完整披露](EXTERNAL-DATA-DISCLOSURE.md)｜[执行记录](execution.json)｜[诊断](diagnosis.json)｜[结果摘要](result-summary.json)。尝试台账见attempt-ledger.json，原始证据见live-9e82970ee7d048adb14fa7f666e8019b/。

执行结果：4次调用全部HTTP 200（SEARCH/READ/ASK/REVIEW）。**决策侧ASK合格**（裸courseType+两个已登记事实，t05-52式前缀失败未复现）；**核验侧失败**：模型在necessaryConditionIds输出带规则前缀键`S10/RETAKE_ONLY:courseType`，宿主以REVIEW_CONDITION_UNKNOWN一票否决（不静默归一化）。run未形成合格WAITING_USER，PUBLIC未注入，第二run未启动。与t05-63死因相同。

attempt-3事件上的INVALID_MODEL_RESPONSE是attempt-4核验失败按设计回填到决策事件的（RuleAgentRunner保留提议与核验双侧失败），不是ASK本身被拒——ASK已通过precheck、能力校验并落盘核验输入快照。

费用：usage估算0.0069754元（6566输入/638输出token，按北京公开价），**实际账单未知不记零**；12元上限未触及。资源：临时容器已删除、专用55518端口临时库已回收、演示库54329未动、探针JVM已退出（残留java进程为Qoder IDE语言服务器，与本探针无关，未按名终止）。

工程结论：T05仍未完成，真实在线验收未通过。剩余缺口是模型对核验合同的条件键命名空间混淆，非宿主工程问题（t05-64本地回放已证明模型输出全对时宿主4次调用内可完成发布）。换模型/改合同/再批须重新授权。

恢复：停留在关闭状态，不修改/覆盖本包及live证据；实现回退须独立逆向提交并重新申请新包，不恢复历史额度。
'''
for _k, _v in (('@PKG@', PKG), ('@JAR@', JAR)):
    readme = readme.replace(_k, _v)
wtext('README.md', readme)

# ---------- 7. 秘密扫描（新增件） ----------
patterns = {'dashscopeKeyPrefix': re.compile(rb'sk-[A-Za-z0-9]{20,}'),
            'bearerToken': re.compile(rb'Bearer\s+[A-Za-z0-9._\-]{20,}'),
            'apiKeyAssignment': re.compile(rb'(?i)(api[_-]?key|secret|token|password|passwd)\s*["\':=]\s*["\'][^"\']{12,}["\']'),
            'awsStyleKey': re.compile(rb'AKIA[0-9A-Z]{16}')}
new_files = ['authorization.json', 'attempt-ledger.json', 'execution.json', 'diagnosis.json', 'result-summary.json', 'README.md']
hits = []
for n in new_files:
    data = (O/n).read_bytes()
    for name, pat in patterns.items():
        for m in pat.finditer(data):
            hits.append({'file': n, 'pattern': name, 'offset': m.start()})
live_hits = []
for p in sorted(LIVE.iterdir()):
    if p.is_file():
        for name, pat in patterns.items():
            for m in pat.finditer(p.read_bytes()):
                live_hits.append({'file': 'live-9e82970ee7d048adb14fa7f666e8019b/'+p.name, 'pattern': name, 'offset': m.start()})
assert not hits and not live_hits, hits + live_hits
save('secret-scan-closeout.json', {'checkedUtc': now, 'scoped': 'closeout artifacts + live evidence directory',
  'newFilesScanned': new_files, 'liveFilesScanned': len([p for p in LIVE.iterdir() if p.is_file()]),
  'patternsChecked': list(patterns), 'matchedSecrets': 0, 'passed': True,
  'note': 'redacted local exchange recording contains API_KEY_REDACTED marker only, no credential value'})

# ---------- 8. 清单 / stage / 提交消息 ----------
stage = sorted('docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/'+p.name
               for p in O.iterdir() if p.is_file() and p.suffix != '.py' and p.name not in ('commit-message.txt',))
stage += ['docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/commit-message.txt']
save('stage-files.json', stage)
manifest = {p.name: sha(p) for p in sorted(O.iterdir()) if p.is_file() and p.name not in ('artifact-manifest.json', 'stage-files.json', 'commit-message.txt')}
save('artifact-manifest.json', {'packageSha256': PKG, 'authorizationStatus': 'CLOSED_FAILED',
  'executedUtc': now, 'files': manifest})
wtext('commit-message.txt', '''docs(t05): 归档t05-65 P02在线执行结果并关闭授权（CLOSED_FAILED）

用户2026-10-16明确批准包 ce4ddb85（一次P02/2run/10次/12元）后执行1个run：
4次调用全部HTTP 200。决策侧ASK合格（裸courseType+已登记事实）；
核验侧模型输出带规则前缀键S10/RETAKE_ONLY:courseType，宿主以
REVIEW_CONDITION_UNKNOWN一票否决，run FAILED未形成WAITING_USER，
PUBLIC未注入、第二run未启动。与t05-63死因相同。

剩余6次调用已关闭不复用；usage估算0.0069754元，实际账单未知不记零。
临时容器/临时库已回收，演示库54329未动，探针JVM已退出。
attempt-3事件的INVALID_MODEL_RESPONSE为attempt-4失败按设计回填，
非ASK被拒（快照已落盘为证）。

结论：T05仍未完成，真实在线验收未通过；剩余缺口为模型对核验合同
条件键命名空间的混淆，非宿主工程问题。只新增本目录收口文件，
不改动t05-62/t05-63/t05-64既有证据，不push/PR/标签/部署/合并。
''')
print(json.dumps({'authorization': 'CLOSED_FAILED', 'consumedCalls': 4, 'unusedCallsClosed': 6,
                  'failureCode': 'REVIEW_CONDITION_UNKNOWN', 'usageEstimateCny': str(estimate),
                  'actualInvoiceCny': None, 'stageFiles': len(stage), 'manifestEntries': len(manifest),
                  'secretScanPassed': True}, ensure_ascii=False))
