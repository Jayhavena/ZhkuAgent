from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before=read(batch/'source-before.json');after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
phase='r5-original-http-final-20261008-01';directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and len(r['suites'])==48 and r['testCount']==498 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited']
assert all(sha(root/x['path'])==x['sha256'] for x in read(directory/'source-after.json'))
assert all(sha(root/p)==h for p,h in before['unchanged'].items())
assert len(after)==15
for s in r['suites']:
 p=directory/'xml'/s['xml'];xml=ET.parse(p).getroot()
 assert sha(p)==s['sha256'] and int(xml.get('tests'))==s['tests'] and all(int(xml.get(k,'0'))==0 for k in ['failures','errors','skipped'])
(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8')
(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
(batch/'review-package.md').write_text('''# Task2O / A06原文HTTP与A07下发复查增量审查

只读本批15项before/*.bin→当前/source-review/changes-review.diff及所依赖真实源。不要运行测试/DB/模型/网络、写文件、派子agent/发线程消息。巨大dirty其余历史改动不审。规范task2o-brief.md/计划Task2O/账本及接口contract。无需原库凭证，不输出任何密钥。

POST同Session锁开始clarification/citation generations；续答先校验消费delta再开始新引用轮。实际决定后service.isCurrent重放原集合，identity requireCurrent独立fresh SQL，再Session保存的SecurityContext full actor和两个generation/TTL核对后finish+publish。引用发opaque randomUUID；异常只清自己的两代、不注销Session/删cookie，避免迟到旧actor伤新登录。没有释放网络与DB原子提交承诺，检查后仍有变化窗口；前端旧响应丢弃后段。错误输入不启动新轮。

GET只有USER /v2/rule-check/originals/{opaqueHandle}，参数拒绝400，unknown/expired/crossSession404；Server store提供可信quote；当前approved catalogue binding membership，再两次当前资格SQL/完整hash/span/metadata view比对，后置membership、fresh identity、Session full actor及citation generation/TTL后返回。source/hash/span不可从客户端自证；safeURL只有exact allowHosts。本机正式Bean allowedHosts空、真实CLOSED目录不变，测试Primary显式合成host/目录独立真实RuntimeFactory。新增beans不自动批准真实2025DRAFT/分享/目录。

有效初始Controller RED22/7fail0error；随后65/0fail2error为Mockito默认Answer在when(any())调用null夹具错误，修doReturn；96/2fail0error是新HTTP审计计数用不存在chunk_id夹具错误，修为index_entry_id，不称业务RED。注册模式首次误选Database混合被启动前拒绝，无DB/测试运行。完整48类498=355纯+131真库含20实际HTTP+12凭据配置检查；29启动器替身合同；55470无卷专用库V22/连接0/退出/570源码0drift/XMLhash。旧12HTTP中4澄清组显式mock isCurrent，不称实际政策决定；独立新增8HTTP是真实Runtime/词法/捕获/审计/决定/复查/原文，同步hook仅制造变化/阻塞，不mock决定或isCurrent，模型embedding抛固定故障词法fallback、selector固定合成ID，不称真实语义。覆盖回答原文、random/cross/expired/newquestion、withdraw/version/body-stalehash/cut、目录清空、postdecision authrev、原文读取中withdraw/locator/authrev、在途账号切换/GET被新题替代并保住新登录/新凭据。

重点审查Session对象在实际Tomcat封装的锁/actor语义与两个slot的一致性、 late response不破坏新登录、失效/错误无旧body、identity/资料检查时机、主CLOSED/最小namespace、旧澄清兼容、test实际路径/错误证据界限。返回Strengths/Critical/Important/Minor（绝对file:line、触发、最小修复）/Declined to judge/Assessment。一轮审查；重要修复RED→GREEN后不重审。当前真实绑定/语义资格/模型/浏览器/用户收益G2仍OPEN。
''',encoding='utf-8')
print('Review package verified: 15 files / 48 classes / 498 tests')
