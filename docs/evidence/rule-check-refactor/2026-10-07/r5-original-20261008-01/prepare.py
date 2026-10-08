from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckLocatedEvidence.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckOriginalEvidenceService.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/JdbcRuleCheckEvidenceReader.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckOriginalEvidenceServiceTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckEvidenceReaderTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1','docs/product/rule-check-decision-contract.md',
 'docs/product/rule-check-blockers.md','docs/product/rule-check-remaining-tasks-2026-10-08.md',
 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md','.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists();(batch/'before').mkdir()
rows=[]
for i,path in enumerate(paths):
 p=root/path;data=p.read_bytes() if p.exists() else None;saved=f'before/{i:02d}.bin' if data is not None else None
 if saved:(batch/saved).write_bytes(data)
 rows.append(dict(path=path,before=saved,existed=data is not None,sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
protected=['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json',
 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json']
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/java/com/zhku/knowledge/rulecheck').rglob('*.java') if p.relative_to(root).as_posix() not in paths]
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources').rglob('application-*.yml')]
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources/db/migration').glob('*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in protected}),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''

## Task2K / A06第一段：有界原文定位组件（2026-10-08）

11项当前字节/白名单见r5-original-20261008-01/source-before.json。现有JDBC reader增加同一资格SQL快照的title/source_url/source_locator/page_number/section_title，read兼容委托；该reader单片段文本至65536数据库字符，URL/section在SQL截为NULL而不搬运无限元数据。新内部服务只接服务端已核验摘录，按固定revision与当前reader匹配持久身份/hash/正文/span，输出有界上下文及摘录定位，不创建HTTP入口或学生资格。

- [ ] 有效RED：当前原文与元数据、hash/版本/资格/ID/span/Unicode变化拒绝，读取故障不回显；原文上下文/UTF8输出预算、安全HTTPS精确host和缺失元数据。
- [ ] GREEN：复用Verifier验证当前一次SQL快照的结构/hash/摘录跨度，不伪称第二次fresh SQL；正文/元数据同读，引用别名保持本轮值；来源URL无服务端抓取/重定向，仅显式host白名单，不造页码。
- [ ] 专用临时库正向元数据、ARCHIVED/INTERNAL/分享拒绝、版本/hash改变、单片段过大拒绝；旧reader及应用/审计/HTTP全相关回归。
- [ ] 29工具合同、一次增量审查、问题证据；A06真实HTTP会话绑定/点击/页面及B01原件映射继续后段，最终复查窗口A07，正式CLOSED不变。

Expected：page_number只标RECORDED_SOURCE_PAGE，不将未审映射的值冒称PDF物理或印刷页，不添加#page导航；无URL/locator时输出未知，不生成外链。上下文保留完整核验摘录，至8192 UTF8字节，左右最多256 UTF16且不切代理对，超限拒绝而非截断摘录。公开VerifiedExcerpt构造器不能授权客户端输入，未来HTTP必须绑定服务端会话结果；本批仅内部组件。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''

Task2K/A06第一段: start — r5-original-20261008-01/source-before.json，接续已授权持续任务。
Task2K: Ruling: 原文正文/元数据复用同一资格SQL快照，单片段65536数据库字符与元数据SQL限额 — 降低任意超大片段或TEXT元数据读流，原reader兼容但超大片段不再获准 — 成本：合法大段须重切或受控拒绝，不能将新预算称全检索链的物理下载限制。
Task2K: Ruling: page_number只标已记录来源页，物理/印刷映射与外链页锚依赖B01 — 表字段没有页码语义，不能凭PDF类型猜导航 — 成本：本批不能声称学生一键到实际PDF页，A06整体不关闭。
Task2K: Ruling: 内部服务只接服务端摘录；复用Verifier时reader返回本次当前SQL快照用于本地结构核验 — 不把memo第二次读取称fresh DB，也不开放客户端VerifiedExcerpt — 成本：原文HTTP会话proof/最终外发仍A06后段/A07，正式目录CLOSED不变。
''')
print('Task2K baseline: 11 paths')
