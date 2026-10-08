from pathlib import Path
import hashlib,json,difflib
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before=read(batch/'source-before.json');diff=[];after=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
r=read(batch.parent/'r5-citation-proof-final-20261008-01/results.json')
assert len(after)==8 and r['passed'] and r['testCount']==463 and len(r['suites'])==46 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited']
assert all(sha(root/x['path'])==x['sha256'] for x in read(batch.parent/'r5-citation-proof-final-20261008-01/source-after.json'))
assert all(sha(root/p)==h for p,h in before['unchanged'].items())
(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8');(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
(batch/'review-package.md').write_text('''# Task2M / A06服务端引用凭据组件最终增量审查

仅8项before/*.bin→当前（source-before/source-review/changes-review.diff）及实际新source，巨大dirty其余修改不审。只读、不写文件、不测试/DB/模型/网络、不派agent/发线程消息。规范计划Task2M/ledger Rulings/剩余A06及decision-contract。无HTTP/Bean装配，Controller/Security/config及所有既有main保护不变。

Session当前仅一个Stored属性：随机generation/full TrustedActor/expiresAt/references(null pending,empty completed)。begin立即替旧轮；publish同owner/generation/未完成，开始后固定5min，验证和构造后再current，空终态也只能一次publish；resolve只canonical opaque UUID，返回内部Ticket(generation,handle,quote)。requireCurrent供future HTTP在原文I/O后再次校验owner/generation/TTL及完整server quote，公开Ticket构造器不授权输入。clearOwn只删同owner/generation，旧慢请求/旧actor不删新轮。wrongtoken/otherSession/actor不破坏新合法引用，expired owned清slot，invalidated统一固定Failure无cause。重复合法GET读不滑动TTL，不存question/conditions/fullreply/history/fulloriginal。

最多32输入项先计数再去重；alias/revision128UTF16、hash64hex/UUID/source/chunk/validspan/文本length匹配；每quote4096UTF16与8192UTF8，唯一quote总32768UTF8。精确重复samealias sameallfields只一个handle，同alias不同正文/身份拒绝。Unicode所有文本严格代理对检查。存有限excerpt正文，因此不能称无正文存储；不是source verifier，不证明提供的record真实/semantic/current qualification。

最初18项RED17assertfail+1NPE（测试未先断言put存在），补显式handle前置断言后第二RED18/18assertfail/0error；旧报告保留。首GREEN相邻4类56PASS。额外deadline反例19/1fail/0error：validation推进clock过5min，入口检查不足，构造后current最小修复。最终46类463=331纯单元+120真库含12HTTP+12凭据配置；29计数替身合同；无卷55468/V22/连接0/实际退出，568source0drift/XMLhash、保护不变。新增19测试是MockHttpSession+Clock纯单元，不声称实际HTTP/source资格/供应商。

本批阶段SESSION_CITATION_PROOF_VERIFIED_NOT_HTTP_ASSEMBLED。真正GET仅能传handle，fresh original locate后requireCurrent且A07当前身份/目录/证据复查仍后段；顺序检查不等于网络交付原子性。正式CLOSED、2025DRAFT不可见、B/C/G2保持OPEN。

请返回Strengths/Critical/Important/Minor(绝对file:line、触发、最小修复)/Declined to judge/Assessment。一轮审查；重点slot生命周期/迟到清理、TTL前后、输入预算/Unicode/引用去重、publicrecord不能自证、无误装配与证据边界。
''',encoding='utf-8')
print('Review package: 8 files / 46 classes / 463 tests')
