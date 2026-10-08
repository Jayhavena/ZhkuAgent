from pathlib import Path
import hashlib,json,difflib
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before=read(batch/'source-before.json');diff=[];after=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8');(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
phase='r5-original-acceptance-20261008-01';r=read(batch.parent/phase/'results.json')
assert r['passed'] and r['testCount']==390 and len(r['suites'])==32 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited']
assert all(sha(root/x['path'])==x['sha256'] for x in read(batch.parent/phase/'source-after.json'))
assert all(sha(root/p)==h for p,h in before['unchanged'].items())
(batch/'review-package.md').write_text('''# Task2K / A06第一段有界原文定位组件最终增量审查

只审11项before→当前增量，before/*.bin/source-before.json/source-review.json/changes-review.diff及实际源，历史dirty不审。只读，不写文件/跑测试/DB/模型/网络，不派agent/发其他线程消息。

规范remaining-tasks A06、decision-contract及计划Task2K末尾、ledger三Ruling。内部原文组件只接服务端已核验VerifiedExcerpt，公开record构造器不授权客户端；本批无HTTP/新bean装配，正式CLOSED不变。未来原文HTTP需绑定本Session结果，输出最终窗口另A07。

JDBC read兼容委托readLocated，一次相同资格SQL正文/元数据：profile ACTIVE、run COMPLETED/profile/cut一致、来源PUBLISHED/STUDENT_VISIBLE/分享true/scope、版本/hash与entry一致；增加length(chunk.content)<=65536数据库字符，CASE source_url<=4096 bytes/section_title<=8192 bytes，否则NULL。title/locator已有varchar上限。metadata与snapshot在当前行读取完再rows.next检查唯一，避免游标离开行后getter。

Service固定referenceRevision及显式精确HTTPS host Set。先结构校验reference，无效不reader；实际read一次current located snapshot，匹配source/chunk/revision/hash/eligible。重构当轮citation别名captured，复用Verifier memo校验hash/Unicode/span/quote一致；**memo不是第二次fresh DB**。位置UTF16起含终不含，完整quote左右各至256不切代理对，contextUTF8<=8192否则无正文拒绝。超大quote不截断冒充原文。title/locator/section只是纯文本数据，未来前端必须逃逸显示，未称HTML已净化。只返回固定Code，异常无cause或旧正文。

page_number只RECORDED_SOURCE_PAGE；没有物理/印刷语义字段，标明映射未知，null页UNKNOWN，无外链#page生成。SourceURL仅https/精确host/no userinfo/port -1或443/合法URI/2048字符，不服务端fetch/redirect，不检查远端内容/实际版本；B01仍须资料元数据审查与页码对应。无安全URL/title/locator/page明确unknown。

Review focus：一次current资格SQL正文元数据绑定与旧read兼容、唯一行/JDBC映射、原单片段预算变化；引用匹配/本地Verifier memo不冒充current re-read；Unicode边界/有界原文context且完整quote；URL返回安全协议与明确host，不越界服务器抓取；页码/未知/纯文本边界；不能把构造器当HTTP授权或顺序read当最终原子性。

有效RED31/13（12unit9失败+19DB4失败）0errors/skips；首GREEN104有7fail+7error，新增metadata在rows.next后读取的游标错误，修为先完整取行；次GREEN104仅1fixtureerror，撤回/分享false仍STUDENT_VISIBLE违反已有V2约束，改同步INTERNAL而不放宽constraint；第三GREEN104全通过。故障报告保留，不计全部为有效业务RED。分享false合法组合带INTERNAL，因此不声称独立隔离了该SQL谓词因果。

最终32类390=297纯单元+81真库含12HTTP+12凭据配置；29runner计数替身合同；无卷55466/V22/连接0/退出，源码565/XMLhash，原DRAFT/20参考/36题/其余rulecheckJava/profileYAML/18迁移保护不变。原文DB4项新测试是实际metadata、撤回/资格组合、巨大chunk拒绝、metadata SQL suppression；其他旧reader反例继续同delegate查询。本批无实际原文HTTP点击/页面/真实PDF映射/学生许可。

阶段BOUNDED_ORIGINAL_LOCATOR_VERIFIED_NOT_HTTP_ASSEMBLED；A06第一段不整体勾选，A06后段/A07/A08–A10及B/C/G2保持OPEN。请返回Strengths/Critical/Important/Minor(file:line+触发+最小修复)/Declined to judge/Assessment，一次审查。
''',encoding='utf-8')
print('Review package: 32 classes / 390 tests')
