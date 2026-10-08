from pathlib import Path
import hashlib,json,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
pattern=re.compile(r'System\.getProperty\(\s*"zhku\.db\.url"\s*,\s*"jdbc:postgresql://127\.0\.0\.1:54329/zhkuagent"\s*\)')
legacy=[p for p in (root/'knowledge/src/test/java').rglob('*.java') if pattern.search(p.read_text(encoding='utf-8'))]
paths=[p.relative_to(root).as_posix() for p in legacy]+['knowledge/src/test/java/com/zhku/knowledge/ZhkuDatabaseTestConfig.java',
 'knowledge/src/test/java/com/zhku/knowledge/ZhkuFlywayTestSupport.java','knowledge/src/test/java/com/zhku/knowledge/ZhkuDatabaseTestTargetTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1','docs/product/rule-check-decision-contract.md','docs/product/rule-check-blockers.md',
 'docs/product/rule-check-remaining-tasks-2026-10-08.md','docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md',
 '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert len(paths)==len(set(paths)) and legacy and not (batch/'source-before.json').exists();(batch/'before').mkdir()
rows=[]
for i,path in enumerate(paths):
 p=root/path;data=p.read_bytes() if p.exists() else None;saved=f'before/{i:02d}.bin' if data is not None else None
 if saved:(batch/saved).write_bytes(data)
 rows.append(dict(path=path,before=saved,existed=data is not None,sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
protected=['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json',
 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json']
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main').rglob('*') if p.is_file()]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,legacyEntrypoints=[p.relative_to(root).as_posix() for p in legacy],unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in protected}),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write(f'''

## Task2L / H01：旧数据库测试拒绝原库（2026-10-08）

本批{len(paths)}项白名单/当前字节见h01-database-isolation-20261008-01/source-before.json，其中{len(legacy)}个真实旧测试仍从系统属性回退原库。仅测试侧改造，生产Java/资源/原件/参考保护。新测试配置databaseUrl/verifyTarget只接受显式系统属性、精确127.0.0.1非54329端口和既有rulecheck/run_archive两个e2e数据库名，无URL扩展或凭据。旧fallback替换为guarded getter，在static字段/DS构造/测试迁移/cleanup之前失败；统一Flyway入口再次验证传入url，不能靠外层runner。

- [ ] 无数据库RED：缺失/原库/原端口/非回环/不精确测试名/URL凭据及参数/越界端口；显式安全目标允许。反射隔离加载代表性旧fixture，仅静态字段、不setup/cleanup/连接，非法目标必须在class init拒绝。
- [ ] GREEN：精确目标helper及{len(legacy)}个旧fallback统一替换；共享Flyway配置前guard；旧密码来源不变，凭证守卫不放宽。
- [ ] 安全目标下无模型旧库正向选类+当前R5相关回归/29工具合同；独立增量审查与完整文件/SQL/XML/退出证据。供应商/报告集测试只做目标静态改造，不自动外发/消费留出。
- [ ] 记录直接全量Maven缺目标将fail-closed的取舍；runner分类/CI分层F06另续；H01是否完整关闭以连接入口清单审查为准，不以零网络错误当拒绝证据。

Expected：不连接/迁移/清理原库，非法URL本身不写日志或cause；negative literal配置/原库拒绝单测保持，不机械移除全部54329字样。ArchiveTestDatabase既有专用run_archive guard保留，R5reader更窄rulecheck guard保留。测试守卫允许的两个名字不授予任意原库写入或生产资格。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write(f'''

Task2L/H01: start — h01-database-isolation-20261008-01/source-before.json，{len(legacy)}个旧fallback/{len(paths)}项白名单；持续已授权工作。
Task2L: Ruling: 先清P0旧测试默认库风险，再扩大HTTP验收 — 新runner隔离不保护直接运行旧fixture，当前审查已定位实际默认原库路径 — 成本：A06后段/A07顺延一个独立安全批次，不跳过产品主线。
Task2L: Ruling: 测试helper只允许既有两个精确e2e库名，URL显式系统属性；旧fixture在static捕获时guard、Flyway再次guard — 缺配置/cleanup也不能复活原库默认；run_archive已有隔离兼容 — 成本：默认全量Maven会安全失败，纯/数据库/供应商分类仍F06，不能由本批宣称全仓CI完成。
Task2L: Ruling: 不自动运行供应商/留出评测类，先选无模型旧库及R5回归 — 目标改造不是模型外发/提前消费报告集的理由 — 成本：这些类仅源/配置保护验证，实际模型语义与完整旧功能另有门禁。
''')
print(json.dumps(dict(legacyEntrypoints=len(legacy),whitelist=len(paths)),ensure_ascii=False))
