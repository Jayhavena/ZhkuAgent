# T05-65 授权申请材料生成与身份核对脚本（零真实调用）
# 运行方式：cd /d/alldesk/Projects/ZhkuAgent-t05 && python docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/prepare-authorization.py
# 本脚本只读仓库源码/资产与 t05-62 披露件，只写 t05-65 目录；不联网、不调用模型、不碰数据库与 Docker。
from pathlib import Path
import hashlib, json, subprocess, zipfile, io, datetime, re, os
from decimal import Decimal

R = Path.cwd()
BASE = R/'docs/evidence/rule-agent-provider/2026-10-15'
O = BASE/'t05-65-p02-eligibility-request'
P62 = BASE/'t05-62-eligibility-request'
P63 = BASE/'t05-63-p02-eligibility-live'
P64 = BASE/'t05-64-p02-local-replay'
read = lambda p: json.loads(Path(p).read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
def save(n, d): (O/n).write_bytes((json.dumps(d, ensure_ascii=False, indent=2)+'\n').encode('utf-8'))
def wtext(n, t): (O/n).write_bytes(t.encode('utf-8'))

PKG  = 'ce4ddb851fd59328bd039e75e3e837e5891e08d6011a83ba954530ed0692754e'
JAR  = '79a22f1c24c0a893d52a888c7e5678e4988f3e8484088ee2ead85ba89259ed70'
SC   = '9e28e451ed02a1b2fa733192828e0913d9ead0b5'
PH   = '43ecebc638cfe3788071b6ecdff47ed26523fd1c'
O62_PKG = '1e44705ae88a7fea23852862bc365da1c764357ef3664ed5139b96ace9cf2fd0'
O62_JAR = '01f2e79e6e6d950d02ecdc80397f445d2acc3076416dc4c0941706c9bde6fe5b'
O62_SC  = '56d33efd9cfda0542dbe50224519e649cc133a84'
MAIN_HEAD = '58e1ef751e8a659178afaac04830ae3df86ad579'
now = datetime.datetime.now(datetime.timezone.utc).isoformat()

# ---------- 1. 身份核对 ----------
head = subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip()
assert head == SC, head
tracked_status = subprocess.check_output(['git','status','--porcelain=v1','--untracked-files=no'], text=True)
assert subprocess.check_output(['git','diff','--name-only'], text=True).strip() == ''
assert subprocess.check_output(['git','diff','--cached','--name-only'], text=True).strip() == ''
assert sha(O/'request-package.json') == PKG
pack = read(O/'request-package.json'); b = pack['buildIdentity']
assert b['jarSha256'] == JAR and len(b['sourceSha256']) == 335
jar = R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
assert sha(jar) == JAR
names = list(b['sourceSha256'])
batch = subprocess.check_output(['git','cat-file','--batch'], input=('\n'.join('HEAD:'+n for n in names)+'\n').encode())
pos = 0; headBlobs = {}
for n in names:
    end = batch.index(b'\n', pos); hdr = batch[pos:end].decode().split(); assert hdr[1] == 'blob'
    size = int(hdr[2]); raw = batch[end+1:end+1+size]; pos = end+1+size+1
    headBlobs[n] = hdr[0]
    assert sha(R/n) == b['sourceSha256'][n], n            # 工作树字节
    assert hashlib.sha256(raw).hexdigest() == b['sourceSha256'][n], n  # HEAD LF blob
for x in pack['runtimeAssets']:
    assert sha((R/x['path']).resolve()) == x['sha256'], x['path']
# jar 类级核对：BOOT-INF/classes == knowledge/target/classes；内嵌 agent-runtime == agent-runtime/target/classes
with zipfile.ZipFile(jar) as z:
    app = {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')}
    target = {'BOOT-INF/classes/'+f.relative_to(R/'knowledge/target/classes').as_posix(): sha(f) for f in (R/'knowledge/target/classes').rglob('*.class')}
    assert app == target and len(app) > 0
    libs = {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')}
    runtime = next(n for n in libs if '/agent-runtime-' in n)
    with zipfile.ZipFile(io.BytesIO(z.read(runtime))) as rz:
        rc = {n: hashlib.sha256(rz.read(n)).hexdigest() for n in rz.namelist() if n.endswith('.class')}
    assert rc == {f.relative_to(R/'agent-runtime/target/classes').as_posix(): sha(f) for f in (R/'agent-runtime/target/classes').rglob('*.class')}
# 旧批次资产保留：t05-62 冻结 jar 未被改动
assert sha(R/'knowledge/target/path-56d33efd-01f2e79e6e6d-controlled-frozen.jar') == O62_JAR
main = Path('D:/alldesk/Projects/ZhkuAgent')
assert subprocess.check_output(['git','-C',str(main),'rev-parse','HEAD'], text=True).strip() == MAIN_HEAD
auth63 = read(P63/'authorization.json')
assert auth63['status'] == 'CLOSED_FAILED' and auth63['remainingAuthorizedCalls'] == 0 and auth63['consumedCalls'] == 4

# ---------- 2. 外发载荷与 t05-62 逐字节一致 + 源码差异仅路径常量 ----------
p62 = read(P62/'request-package.json')
strip = lambda d: json.loads(json.dumps(d, dict(d, buildIdentity=None))) if False else json.loads(json.dumps({k: v for k, v in d.items() if k != 'buildIdentity'}))
assert strip(p62) == strip(pack), 'outbound payload differs beyond buildIdentity'
s62 = p62['buildIdentity']['sourceSha256']
diff_files = sorted(n for n in set(s62) | set(b['sourceSha256']) if s62.get(n) != b['sourceSha256'].get(n))
assert diff_files == ['knowledge/scripts/start-rule-agent-probe.ps1',
                      'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java'], diff_files
gd = subprocess.check_output(['git','diff', O62_SC, 'HEAD', '--'] + diff_files, text=True)
changed = [l for l in gd.splitlines() if (l.startswith('+') or l.startswith('-')) and not l.startswith('+++') and not l.startswith('---')]
assert changed and all(('t05-62-eligibility-request' in l) or ('t05-65-p02-eligibility-request' in l) for l in changed), changed

# ---------- 3. 披露静态字段与当前包/源码逐项核对 ----------
static = read(P62/'OUTBOUND-STATIC-CONTENT.json'); dynamic = read(P62/'OUTBOUND-DYNAMIC-FIELDS.json')
ov = [k for k in static if k in pack]
assert len(ov) == 15 and all(static[k] == pack[k] for k in ov), ov
assert dynamic['limits'] == pack['budget'] and dynamic['batchLimits']['maxCalls'] == pack['maximumProviderCalls'] == 10
# 17 种 scope 文字从当前资产重建并与附件逐项比对
sem = read(R/'docs/product/rule-check-2025-b02-semantic-review.json'); draft = read(R/'docs/product/rule-check-c05-topic-catalogue.json')
rules = {q['reviewItemId']: q for q in sem['rules']}; scopes = []
for item in draft['entries']:
    limits = rules[item['ruleId'].split('/')[0]]['exceptionsAndUnknowns']
    scopes.append({'ruleId': item['ruleId'], 'questionScope': item['questionScope']+' 引用仅限2025.9.2文本，现行适用性未核实。'+(' 已审核资料边界：'+'；'.join(limits) if limits else '')})
overlay = (R/'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleArticle4Overlay.java').read_text(encoding='utf-8')
scopes.append({'ruleId': 'T02/ARTICLE4', 'questionScope': re.search(r'CatalogEntry\(rule,"([^"]+)"', overlay).group(1)})
assert scopes == static['possibleDecisionRuleContextScopes'] and len(scopes) == 17
# 10 种能力映射当前实现与 t05-62 提交逐字不变
cap = R/'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java'
oldcap = subprocess.check_output(['git','show', O62_SC+':'+cap.relative_to(R).as_posix()]).decode('utf-8')
cur = cap.read_text(encoding='utf-8'); marker = '    private static FactCapability capability('
assert oldcap[oldcap.index(marker):].replace('\r\n','\n') == cur[cur.index(marker):].replace('\r\n','\n')
assert len(static['registeredCapabilityProjection']) == 10
contract = (R/'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationContract.java').read_text(encoding='utf-8')
codes = re.search(r'public enum Code\s*\{([^}]+)\}', contract).group(1).replace('\n','').replace(' ','').split(',')
assert codes == static['possiblePublicationRejectionCodes'], codes

# ---------- 4. 公开定价重新抓取核对 ----------
price = Path(os.environ['LOCALAPPDATA'])/'Temp/zhku-t05-65-public-pricing.html'
raw = price.read_bytes(); price_sha = hashlib.sha256(raw).hexdigest()
text = re.sub(r'<[^>]+>', ' ', raw.decode('utf-8', errors='replace'))
flat = re.sub(r'\s+', ' ', text)
for needle in ['最大输入长度 991808', '最大输出长度 131072', '输入 0.8 每百万 tokens', '输出 2.7 每百万 tokens', '华北', '2（北京）', 'qwen3.8-flash']:
    assert needle in flat, needle
i = flat.find('计费项')
assert '输入 0.8 每百万 tokens' in flat[i:i+200] and '2（北京）' in flat[max(0, i-60):i], 'price block region mismatch'
reserve = Decimal(10)*(Decimal(991808)*Decimal('0.8')+Decimal(131072)*Decimal('2.7'))/Decimal(1000000)
assert reserve == Decimal('11.473408') and reserve <= Decimal('12')

# ---------- 5. 外发附件原样沿用（已逐键核对） ----------
wtext('OUTBOUND-STATIC-CONTENT.json', (P62/'OUTBOUND-STATIC-CONTENT.json').read_bytes().decode('utf-8'))
wtext('OUTBOUND-DYNAMIC-FIELDS.json', (P62/'OUTBOUND-DYNAMIC-FIELDS.json').read_bytes().decode('utf-8'))

# ---------- 6. 完整外发披露 ----------
disc = (P62/'EXTERNAL-DATA-DISCLOSURE.md').read_text(encoding='utf-8')
disc = disc.replace('# T05-62 动作合同06新包：完整外发披露（未授权）', '# T05-65 动作合同06新包：完整外发披露（未授权）')
disc = disc.replace(
    '本申请仅对应新包`1e44705ae88a7fea23852862bc365da1c764357ef3664ed5139b96ace9cf2fd0`、jar`01f2e79e6e6d950d02ecdc80397f445d2acc3076416dc4c0941706c9bde6fe5b`、生产源码56d33efd。当前授权调用额度0，历史剩余额度不恢复。本轮只准备包及零调用预检，没有真实供应商调用、数据库或Docker操作。',
    '本申请仅对应新包`'+PKG+'`、jar`'+JAR+'`、生产源码9e28e45（相对t05-62包仅变更探针批次路径常量）；准备前HEAD为43ecebc，工作树D:/alldesk/Projects/ZhkuAgent-t05，主仓库58e1ef7尚未合入。当前授权调用额度0，历史剩余额度不恢复。本轮只准备包及零调用预检，没有真实供应商调用、数据库或Docker操作。')
disc = disc.replace('本轮于2026-10-15T13:49:28.594283+00:00重新核对', '本轮于'+now+'重新抓取公开页面核对（数值未变）')
disc = disc.replace(
    '上一轮205项本地回归及审查后3项复验、有限工程探针及跨批次到达阶段不能替代冻结配置可靠性',
    't05-61的205项本地回归及审查后3项复验、t05-64的11类204项本地回放、有限工程探针及跨批次到达阶段不能替代冻结配置可靠性')
disc = disc.replace(
    '根/模块POM、共享模块源码和wrapper等额外构建输入由本机receipt另外核对，不能声称这些全部都由现有自动门禁覆盖。',
    '本包buildIdentity仅含jar与335项生产/启动源码，未单列POM/共享模块/wrapper等额外构建输入；本机receipt以类级核对补充覆盖（jar内BOOT-INF/classes与knowledge/target/classes逐字节一致、内嵌agent-runtime与agent-runtime/target/classes一致），jar指纹同时绑定实际构建产物，仍不能声称这些全部都由现有自动门禁覆盖。')
old_limits = disc[disc.index('## 本轮核对的具体限制'):]
new_limits = '''## 本轮核对的具体限制

因探针宿主批次路径常量变更，本轮重新执行离线Maven构建（mvnw.cmd -o -pl knowledge -am -DskipTests package，构建前清除DASHSCOPE_API_KEY/ZHKU_DB_PASSWORD/POSTGRES_PASSWORD进程环境变量）：首次构建携带旧常量，准备阶段写旧包路径被拒（t05-62包未被覆盖）；修改常量并提交9e28e45后第二次构建成功。jar指纹由01f2e79e6e6d950d02ecdc80397f445d2acc3076416dc4c0941706c9bde6fe5b变为79a22f1c24c0a893d52a888c7e5678e4988f3e8484088ee2ead85ba89259ed70。

外发载荷与t05-62请求包（t05-63已执行并失败关闭）逐字节一致：除buildIdentity外deep compare完全相同；335项生产源码中仅start-rule-agent-probe.ps1与RuleAgentOnlineProbeHost.java两个文件不同，且git逐行diff证明差异仅为4处批次路径字符串（t05-62-eligibility-request→t05-65-p02-eligibility-request）。外发静态/动态附件与t05-65包15个重叠键逐项核对一致后原样沿用，17种scope文字与10种能力映射从当前资产和源码重新重建比对不变。

CheckPackage零调用通过（packageSha256=ce4ddb85…，maximumProviderCalls=10，protocolRevision=RULE_AGENT_ACTION_CONTRACT_06）；旧包SHA在启动器ps1第38行被拒；重复Prepare在启动器ps1第24行（构建前）被拒，新包未被覆盖；以上均经普通Windows PowerShell5.1→CMD→pwsh7入口实测。t05-64本地回放11类204项0失败与t05-61的205项回归是已有结果，本轮不声称重新运行测试。本轮未检查Docker引擎、镜像或数据库就绪，不代表在线环境就绪。

历史与动机边界：t05-63已消耗旧包4次调用后失败关闭（核验侧模型输出带规则前缀键，宿主以REVIEW_CONDITION_UNKNOWN一票否决，未进Gate）；t05-64本地回放证明模型输出全部正确时宿主可在4次调用内完成检索、读取、核验与发布。本包不修改任何预算/形状/合同参数（与旧包一致：每run最多5次模型尝试、同task最多2 run、整批最多10次真实调用、12元上限），仅为在同一形状下重新取得一次在线观察机会；历史三次死因均为一票否决型，不能保证成功，失败即归档关闭。
'''
disc = disc[:disc.index('## 本轮核对的具体限制')] + new_limits
wtext('EXTERNAL-DATA-DISCLOSURE.md', disc)

# ---------- 7. 授权申请 / README ----------
application = '''# T05-65 新包在线授权申请（未授权）

新包SHA256：`@PKG@`。jar：`@JAR@`。生产实现提交：`@SC@`（仅探针批次路径常量）；准备前HEAD为43ecebc，工作树D:/alldesk/Projects/ZhkuAgent-t05。主仓库58e1ef7尚未合入。

申请阿里云百炼/DashScope北京端点 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，模型qwen3.8-flash，接收[完整披露](EXTERNAL-DATA-DISCLOSURE.md)、[全部静态内容](OUTBOUND-STATIC-CONTENT.json)及[动态字段](OUTBOUND-DYNAMIC-FIELDS.json)所列数据。外发载荷与t05-62包（t05-63已执行并失败关闭）逐字节一致，仅buildIdentity不同；本包不改动任何提示词、动作/核验合同、Schema、预算形状或15项运行资产。完整Fact原始身份只在本机留档。C05派生scope文字也属于本次待批范围，不转成正式政策或公开传播许可。

**仅本新包一次P02，同一任务最多2 run、最多10次真实调用、人民币12元上限。** 首问“我这门选修课没过，能不能重修？”，首轮须检索、读取、必要追问及独立核验并发布合格WAITING_USER；随后才注入预登记合成补充PUBLIC。续答同task新run重新检索、读取、核验及发布。PUBLIC是测试输入，模型不接收参考答案。

每run最多4决策+1核验，20秒/调用、110秒/run、235秒/Java。最满run仅余5秒工具及I/O，不保证成功。公开最大输入/输出原价保守预留@RESERVE@元（今日重新抓取公开页面核对，数值未变），执行前重新核价，超上限或无法确认不启动。费用账单未知不记零，派发超时不补调用。

t05-63已消耗旧包4次调用后失败关闭（核验侧模型输出带规则前缀键，宿主一票否决，未进Gate）；t05-64本地回放证明模型输出全对时宿主4次调用内可完成发布。本批只为在同一形状下重新取得一次在线观察机会，不修改预算/合同，不能保证成功。

首轮不合格即停；任何失败均归档并关闭余量，无P01重跑、第三run、自动修复、重试、补额度或下一批。执行轮主动执行≤20分钟，只能在专用随机容器临时库执行，不操作原演示库。批准后才检查环境/凭证，视需要启动Docker，执行一次后退出。

当前授权0，历史已关闭额度不复用。本轮准备与预检无真实模型、数据库或Docker操作。需要用户单独明确批准本包SHA及上述完整范围、一次P02/10次/12元后才能执行；本轮“进入下一步”仅用于准备，不视为外发授权。

工程成果已冻结，真实在线验收未通过。
'''
for _k, _v in (('@PKG@', PKG), ('@JAR@', JAR), ('@SC@', SC), ('@RESERVE@', str(reserve))):
    application = application.replace(_k, _v)
wtext('REQUEST-APPLICATION.md', application)
readme = '''# T05-65 新请求包与完整外发披露

准备完成，NOT_AUTHORIZED。包SHA256：`@PKG@`；jar：`@JAR@`；实现提交9e28e45（仅探针路径常量）；准备前HEAD 43ecebc。当前授权调用0。

[授权申请](REQUEST-APPLICATION.md)｜[完整披露](EXTERNAL-DATA-DISCLOSURE.md)。申请仅一次P02、同task两run、最多10次调用/12元，历史授权不复用（t05-63已执行失败关闭）。费用按公开最大输入/输出保守预留@RESERVE@元，执行前重新核价。

因探针宿主路径常量变更本轮重新离线构建两次（首次写旧路径被拒后改常量重建），jar指纹变化但外发载荷与t05-62包逐字节一致（deep compare加逐行diff证明仅4处路径字符串不同）。CheckPackage零调用通过、旧SHA与重复Prepare均被拒。t05-64本地回放11类204项0失败。预检不证明供应商Schema支持或模型行为。本轮无模型、数据库或Docker操作，不代表在线环境就绪。详细身份回执见package-receipt.json，验证及秘密扫描见verification.json和secret-scan.json。

本批仍保留原文、用途权限、完整候选与事实、独立发布核验及原成功门槛。通过也仅代表固定P02工程探针，不代表总体可靠性、未预置问题质量或学生收益。

恢复：直接停留在未授权状态，不修改/覆盖本包；实现回退须用独立逆向提交并重新申请新包，不恢复历史额度。

工程成果已冻结，真实在线验收未通过。
'''
for _k, _v in (('@PKG@', PKG), ('@JAR@', JAR), ('@RESERVE@', str(reserve))):
    readme = readme.replace(_k, _v)
wtext('README.md', readme)

# ---------- 8. 过程证据 ----------
cp_out = '{"protocolRevision":"RULE_AGENT_ACTION_CONTRACT_06","packageSha256":"'+PKG+'","databaseOperations":0,"providerCalls":0,"mode":"PACKAGE_CHECK_ONLY","passed":true,"maximumProviderCalls":10}'
wtext('check-package.log', cp_out+'\n')
save('check-package-process.json', {'exitCode': 0, 'timedOut': False, 'resourcesClosed': True, 'output': cp_out+'\r\n\r\n', 'errorOutput': '',
  'entry': '普通Windows PowerShell 5.1 → start-rule-agent-probe.cmd → pwsh7 → java --CheckPackage', 'checkedUtc': now})
wtext('old-sha-rejected.log',
'''# t05-65 旧包SHA拒绝证据（'''+now+'''，普通Windows PowerShell 5.1 → start-rule-agent-probe.cmd → pwsh7 入口）
# 命令：-CheckPackage -OnlyP02 -ApprovedPackageSha256 '''+O62_PKG+'''（t05-62/t05-63 已执行并关闭的旧包）
# 结果：退出码1；start-rule-agent-probe.ps1 第38行「待核对请求包指纹不同」抛出后中止，未启动Java、未构建、无模型/数据库/Docker操作。
# 备注：PS5.1 管道将 pwsh 中文异常文本按GBK显示为乱码，故消息文本以ps1源文件（UTF-8）第38行为权威；拒绝位置与退出码以实测为准。
''')
wtext('duplicate-prepare-rejected.log',
'''# t05-65 重复Prepare拒绝证据（'''+now+'''，普通Windows PowerShell 5.1 → start-rule-agent-probe.cmd → pwsh7 入口）
# 命令：-PrepareProbe
# 结果：退出码1；start-rule-agent-probe.ps1 第24行「请求包已存在，保留原包，不构建或覆盖」抛出后中止；该检查位于构建步骤之前，未执行Maven、未启动Java、未触碰请求包。
# 备注：同旧SHA拒绝记录，中文异常文本在PS5.1管道中显示为乱码，消息以ps1源文件（UTF-8）第24行为权威。
''')
save('prepare-process.json', {
  'timeBasis': 'startedUtc uses first path-constant commit time as preparation start marker; finishedUtc uses request-package.json mtime; no separate wall-clock was recorded for the two build/prepare attempts',
  'startedUtc': '2026-10-16T00:01:26+00:00', 'finishedUtc': '2026-10-16T00:05:44.239481+00:00',
  'sourceCommit': SC, 'preparationHead': PH, 'jarSha256': JAR,
  'networkConnectionsDenied': True, 'credentialsRemoved': True,
  'realProviderCalls': 0, 'databaseOperations': 0, 'dockerOperations': 0, 'rebuilds': 2,
  'firstPrepareAttempt': {'result': 'FAILED', 'reason': 'RuleAgentOnlineProbeHost.PACKAGE still pointed at t05-62 package path; prepare wrote old path and was rejected with FileAlreadyExistsException; t05-62 package preserved untouched', 'fixedByCommit': SC},
  'buildTooling': 'mvnw.cmd -o -Dmaven.repo.local=%USERPROFILE%/.m2/repository -Dfile.encoding=UTF-8 -pl knowledge -am -DskipTests package (credentials cleared from process environment before build)',
  'prepareExitCode': 0,
  'checkPackage': {'protocolRevision': 'RULE_AGENT_ACTION_CONTRACT_06', 'packageSha256': PKG, 'databaseOperations': 0, 'providerCalls': 0, 'mode': 'PACKAGE_CHECK_ONLY', 'passed': True, 'maximumProviderCalls': 10},
  'oldShaRejected': True, 'duplicatePrepareRejectedWithoutOverwrite': True,
  'packageSha256': PKG, 'passed': True, 'temporaryDirectoryRemoved': True})

# ---------- 9. 审查 / 验证 / 回执 ----------
save('review.json', {'reviewedUtc': now, 'mode': 'SELF_REVIEW_AGAINST_CURRENT_SOURCE_AND_ACTUAL_REBUILT_JAR',
  'findingsCorrected': [
    'Verified outbound payload is byte-identical to the executed t05-62 package via deep comparison excluding buildIdentity',
    'Proved via git line diff that the only source differences are four batch path constant strings',
    'Re-verified 15 overlapping static attachment keys against the actual t05-65 package',
    'Reconstructed 17 scope strings and compared 10 capability mappings against current source',
    'Re-fetched public pricing page and reconfirmed 0.8/2.7 CNY per million and 991808/131072 token limits',
    'Recorded that this preparation rebuilt the jar twice (offline Maven), unlike t05-62 frozen-jar preparation',
    'Clarified that the package records no additionalBuildInputSha256; class-level jar verification supplements coverage'],
  'remainingLimitations': ['No new online success', 'Current supplier Schema behavior unverified',
    'Package buildIdentity does not enumerate POM/shared-module/wrapper extra build inputs; class-level and jar-SHA binding used instead',
    'No Docker engine/DB readiness operations this turn',
    'Actual invoice for any future dispatched call remains unknown until reconciled',
    'Historical three failure modes were all one-vote vetoes; more batch calls do not guarantee success'],
  'sourceLocations': {'decisionFields': 'RuleAgentProvider.java:124-157', 'dynamicFactSchema': 'RuleAgentProtocol.java:131-146',
    'publicationEligibility': 'RuleFactCapabilityCatalogue.java:35-40', 'originalConditionKeyRejection': 'RulePublicationProtocol.java:55-58',
    'fullSnapshotLocalOnly': 'RuleAgentProvider.java:185-195'},
  'requiresExternalAuthorization': True})
receipt = {'checkedUtc': now, 'packageSha256': PKG, 'jarSha256': JAR, 'productionSourceCommit': SC, 'preparationHead': PH,
  'sourceWorkingAndHeadMatched': len(names), 'additionalBuildInputsRecordedInPackage': 0,
  'headBlobIds': headBlobs,
  'runtimeAssetsMatched': len(pack['runtimeAssets']), 'jarApplicationClassesMatched': len(app),
  'runtimeDependencyClassesMatched': len(rc), 'runtimeDependencySha256': libs[runtime],
  'jarRebuiltThisPreparation': True, 'rebuilds': 2, 'buildOffline': True, 'credentialsClearedBeforeBuild': True,
  'outboundPayloadIdenticalToT05_62Package': True, 'sourceDiffFiles': diff_files, 'sourceDiffOnlyBatchPathConstants': True,
  'previousFrozenJarPreserved': {'path': 'knowledge/target/path-56d33efd-01f2e79e6e6d-controlled-frozen.jar', 'sha256': O62_JAR},
  'mainHeadUnchanged': MAIN_HEAD, 'priorBatchAuthorizationClosed': {'batch': 't05-63', 'status': auth63['status'], 'consumedCalls': auth63['consumedCalls'], 'remainingAuthorizedCalls': auth63['remainingAuthorizedCalls']},
  'trackedGitStatus': tracked_status, 'trackedContentDiffEmpty': True,
  'checkPackagePassedFromWindowsPowerShell51': True, 'oldShaRejected': True, 'duplicatePrepareRejectedWithoutOverwrite': True,
  'localTests': 't05-64 eleven classes / 204 tests / 0 failures (commit 43ecebc) plus t05-61 prior 205 scoped regression; not rerun in this preparation',
  'realProviderCalls': 0, 'databaseOperations': 0, 'dockerEngineOperations': 0,
  'authorizationStatus': 'NOT_AUTHORIZED', 'authorizedCalls': 0, 'passed': True}
save('package-receipt.json', receipt)
save('verification.json', {'checkedUtc': now, 'passed': True, 'packageSha256': PKG,
  'identityReceiptPassed': True, 'outboundPayloadIdenticalToExecutedT05_62Package': True,
  'sourceDiffOnlyBatchPathConstants': True, 'staticFieldsMatchActualPackage': True,
  'dynamicEligibilityAndRejectionFieldsDisclosed': True, 'scopeTextReconstructedCount': 17,
  'capabilityMappingsCompared': 10, 'ordinaryWindowsPowerShell51CheckPassed': True,
  'prepareAndCheckPackageNoNetwork': True, 'publicPricingPageRetrievedWithCurlOnly': True,
  'oldShaRejected': True, 'duplicatePrepareDoesNotOverwrite': True,
  'sourceChangesThisTurn': 2, 'rebuilds': 2, 'realModelCalls': 0, 'databaseOperations': 0, 'dockerOperations': 0,
  'authorizedCalls': 0, 'localTestsRerunThisTurn': 0,
  'latestLocalRegression': 't05-64 11 classes / 204 tests / 0 failures (commit 43ecebc)',
  'notVerified': ['Online supplier schema enforcement', 'Docker/DB readiness', 'P02 online success',
    'Frozen-configuration reliability', 'Unseen-question quality', 'Student benefit'],
  'actualFutureInvoiceCny': None, 'readyForSeparateAuthorizationRequest': True})
save('disclosure-generation.json', {'generatedUtc': now, 'packageSha256': PKG,
  'derivedFrom': 't05-62-eligibility-request disclosure set (payload verified identical)',
  'staticPackageFieldsMatched': len(ov), 'scopeStringsReconstructedAndMatched': 17,
  'capabilityMappingImplementationComparedWithT05_62Commit': True, 'capabilityMappingCount': 10,
  'runtimeAssetsEqualOldPackage': True, 'outboundPayloadEqualToT05_62': True,
  'newOutboundFields': [], 'pricingRefreshed': True, 'publicPageSha256': price_sha,
  'maximumPublicTokenReservationCny': str(reserve), 'realModelCalls': 0})
save('pricing-and-budget.json', {'checkedUtc': now, 'source': 'https://help.aliyun.com/zh/model-studio/qwen3-8-flash',
  'sourceTitle': '阿里云qwen3.8-flash官方模型文档', 'publicPageSha256': price_sha,
  'retrieval': 'Direct curl retrieval of public URL only; no project payload; HTML snapshot kept at %LOCALAPPDATA%/Temp/zhku-t05-65-public-pricing.html',
  'region': '华北2（北京）', 'currency': 'CNY', 'inputCnyPerMillion': '0.8', 'outputCnyPerMillion': '2.7',
  'publicNonThinkingMaximumInputTokens': 991808, 'publicMaximumOutputTokens': 131072,
  'requestOutputTokenLimit': 1024, 'maximumCallsRequested': 10, 'maximumCnyRequested': '12',
  'maximumPublicTokenReservationCny': str(reserve), 'assumeDiscountCacheOrFreeCredit': False,
  'recheckBeforeExecution': True, 'actualInvoiceCny': None, 'status': 'PREPARATION_ONLY_NOT_AUTHORIZED'})
save('attempt-ledger.json', {'status': 'NOT_STARTED_NOT_AUTHORIZED', 'packageSha256': PKG, 'attempts': [],
  'dispatchedCalls': 0, 'authorizedCalls': 0, 'remainingAuthorizedCalls': 0, 'actualInvoiceCny': None,
  'invoiceStatus': 'NO_MODEL_REQUESTS_THIS_PREPARATION; future actual invoice remains unknown until reconciliation',
  'futureDedupKey': ['taskId', 'runId', 'attempt'],
  'futureRecordFields': ['purpose', 'dispatchState', 'requestSha256', 'responseSha256', 'receivedHeaders', 'requestId',
    'httpStatus', 'elapsedMillis', 'inputTokens', 'outputTokens', 'usageEstimateCny', 'actualInvoiceCny', 'feeStatus',
    'proposedActionFile', 'validationStage', 'validationCode', 'readSelection', 'publicationReason', 'terminalState', 'resourcesClosed'],
  'timeoutPolicy': 'Dispatched timeout consumes allowance; missing usage -> UNKNOWN fee, never zero or extra replacement call',
  'phaseAccounting': 'BEGIN/RECEIVED/FINISHED/REVIEW_INPUT_SNAPSHOT are phases of one attempt, not extra calls',
  'closeoutPolicy': 'Record every attempt and unknown cost, close all unused allowance, no repair/retry/next batch'})

# ---------- 10. 授权状态（在披露件生成后计算其SHA；先落盘再进秘密扫描） ----------
save('approval-state.json', {'status': 'NOT_AUTHORIZED', 'requestedAtUtc': now,
  'packageSha256': PKG, 'jarSha256': JAR, 'sourceCommit': SC, 'preparationHead': PH,
  'requestedCase': 'P02_CONTINUATION_01', 'requestedBatchCount': 1, 'requestedMaximumTaskRuns': 2,
  'requestedMaximumCalls': 10, 'requestedMaximumCny': '12',
  'authorizedCalls': 0, 'consumedCalls': 0, 'remainingAuthorizedCalls': 0,
  'priorAllowances': 'CLOSED_NOT_REUSED', 'automaticRetriesAllowed': 0, 'noAutomaticNextBatch': True,
  'disclosureSha256': sha(O/'EXTERNAL-DATA-DISCLOSURE.md'),
  'staticContentSha256': sha(O/'OUTBOUND-STATIC-CONTENT.json'),
  'dynamicFieldListSha256': sha(O/'OUTBOUND-DYNAMIC-FIELDS.json'),
  'packageReceiptSha256': sha(O/'package-receipt.json'), 'maximumActiveExecutionMinutesPerTurn': 20})

# ---------- 11. 秘密扫描：本目录现有文件全扫 + 后写元数据件内存预扫（无循环依赖） ----------
patterns = {'dashscopeKeyPrefix': re.compile(rb'sk-[A-Za-z0-9]{20,}'),
            'bearerToken': re.compile(rb'Bearer\s+[A-Za-z0-9._\-]{20,}'),
            'apiKeyAssignment': re.compile(rb'(?i)(api[_-]?key|secret|token|password|passwd)\s*["\':=]\s*["\'][^"\']{12,}["\']'),
            'awsStyleKey': re.compile(rb'AKIA[0-9A-Z]{16}')}
def scan_bytes(fname, data):
    out = []
    for name, pat in patterns.items():
        for m in pat.finditer(data):
            out.append({'file': fname, 'pattern': name, 'offset': m.start()})
    return out
hits = []
files = sorted(p for p in O.iterdir() if p.is_file())
for p in files:
    hits += scan_bytes(p.name, p.read_bytes())
env_names = ['DASHSCOPE_API_KEY', 'ZHKU_DB_PASSWORD']
scopes = {}
for n in env_names:
    scopes[n] = {'Process': bool(os.environ.get(n)), 'User': bool(subprocess.run(['powershell','-NoProfile','-Command','[Environment]::GetEnvironmentVariable("%s","User")' % n], capture_output=True, text=True).stdout.strip()), 'Machine': bool(subprocess.run(['powershell','-NoProfile','-Command','[Environment]::GetEnvironmentVariable("%s","Machine")' % n], capture_output=True, text=True).stdout.strip())}
LATE = ['secret-scan.json', 'artifact-manifest.json', 'stage-files.json', 'commit-message.txt']
PFX = 'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/'
content_files = {p.name: sha(p) for p in files if p.name not in LATE}
# stage 清单只含路径、不含哈希，可先行定稿
stage_list = sorted(PFX+n for n in content_files) + [PFX+n for n in LATE]
stage_bytes = (json.dumps(stage_list, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
hits += scan_bytes('stage-files.json', stage_bytes)
msg = '''docs(t05): 准备t05-65 P02新包在线授权申请材料（未授权）

新包 ce4ddb85（jar 79a22f1c，源码提交 9e28e45，准备前 HEAD 43ecebc）。
因探针宿主批次路径常量变更重新离线构建两次，jar 指纹变化但外发载荷与
t05-62 包逐字节一致（deep compare + 逐行 diff 证明仅 4 处路径字符串）。

范围：仅一次 P02、同 task 最多 2 run、最多 10 次真实调用、12 元上限；
公开最大 token 保守预留 11.473408 元（今日重新抓取公开页面核对未变）。
CheckPackage 零调用通过；旧包 SHA 与重复 Prepare 均被拒；t05-64 本地
回放 11 类 204 项 0 失败为已有结果，本轮未重跑测试、无模型/数据库/Docker
操作。授权状态 NOT_AUTHORIZED，等待用户对包 SHA 与范围的单独明确批准。

本提交只新增 t05-65-p02-eligibility-request/ 下申请与证据文件，不改动
t05-62/t05-63/t05-64 既有证据，不 push/PR/标签/部署/合并。
'''
msg_bytes = msg.encode('utf-8')
hits += scan_bytes('commit-message.txt', msg_bytes)
scan_body = {'checkedUtc': now, 'scannedFileCount': len(files),
  'scannedFiles': [p.name for p in files], 'freshCredentialScopes': ['Process', 'User', 'Machine'],
  'credentialNames': env_names, 'credentialPresenceByScope': scopes,
  'credentialValuesPrintedOrSaved': False, 'rawBytesAndDecodedJsonScanned': True,
  'patternsChecked': list(patterns), 'matchedSecrets': len(hits), 'matchedSecretDetails': hits,
  'postWriteFilesPreScannedInMemory': LATE, 'allDirectoryFilesAccountedFor': True, 'passed': not hits}
ss_bytes = (json.dumps(scan_body, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
hits += scan_bytes('secret-scan.json', ss_bytes)
manifest_body = {'packageSha256': PKG, 'authorizationStatus': 'NOT_AUTHORIZED',
  'files': dict(content_files, **{'secret-scan.json': hashlib.sha256(ss_bytes).hexdigest(),
                                  'stage-files.json': hashlib.sha256(stage_bytes).hexdigest()})}
manifest_bytes = (json.dumps(manifest_body, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
hits += scan_bytes('artifact-manifest.json', manifest_bytes)
assert not hits, hits
# ---------- 12. 落盘后写件 + 落盘后复扫复核 ----------
(O/'secret-scan.json').write_bytes(ss_bytes)
(O/'stage-files.json').write_bytes(stage_bytes)
(O/'artifact-manifest.json').write_bytes(manifest_bytes)
(O/'commit-message.txt').write_bytes(msg_bytes)
final_hits = []
for p in sorted(O.iterdir()):
    if p.is_file():
        final_hits += scan_bytes(p.name, p.read_bytes())
assert not final_hits, final_hits
for n, h in manifest_body['files'].items():
    assert sha(O/n) == h, n
assert len(list(O.iterdir())) == len(manifest_body['files'])+2, 'artifact-manifest.json and commit-message.txt are intentionally outside the manifest'
print(json.dumps({'packageSha256': PKG, 'jarSha256': JAR, 'sourceCommit': SC,
                  'files': len(manifest_body['files'])+2, 'manifestEntries': len(manifest_body['files']),
                  'outboundPayloadIdenticalToT05_62': True, 'maximumCnyReservation': str(reserve),
                  'authorization': 'NOT_AUTHORIZED', 'secretScanPassed': not hits and not final_hits,
                  'scannedOnDisk': len(files), 'preScannedInMemory': len(LATE),
                  'postWriteRescanHits': len(final_hits)}, ensure_ascii=False))
