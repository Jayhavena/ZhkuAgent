from pathlib import Path
import hashlib, json
root=Path(__file__).resolve().parents[5]
batch=Path(__file__).resolve().parent
paths=[
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckRuntimeSettings.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckApplicationService.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckRuntimeFactory.java',
 'knowledge/src/main/java/com/zhku/knowledge/retrieval/JdbcLexicalCandidateRetriever.java',
 'knowledge/src/main/java/com/zhku/knowledge/retrieval/JdbcVectorCandidateRetriever.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckApplicationServiceTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckCitationAuditTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1',
 'docs/product/rule-check-decision-contract.md','docs/product/rule-check-blockers.md',
 'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md',
 '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists()
(batch/'before').mkdir()
rows=[]
for i,path in enumerate(paths):
 p=root/path; data=p.read_bytes() if p.exists() else None
 saved=f'before/{i:02d}.bin' if data is not None else None
 if saved: (batch/saved).write_bytes(data)
 rows.append(dict(path=path,existed=data is not None,before=saved,sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
fixed=['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json',
 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json']
fixed += [str(p.relative_to(root)).replace('\\','/') for p in (root/'knowledge/src/main/resources/db/migration').glob('V*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in fixed}),indent=2),encoding='utf-8')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md'
plan.write_text(plan.read_text(encoding='utf-8')+'''

## Task2E：统一固定配置与应用编排（2026-10-08）

本批白名单见 `r5-application-20261008-01/source-before.json`，按进入本批时的字节比较，不能把其他未提交工作纳入重构。

- [ ] API与真实断言RED：服务端固定profile/切片/scope/引用版本和候选预算；仅审核目录内的规则ID可选择；模型不能生成事实或补造条件。
- [ ] GREEN：复用混合检索→捕获核验/审计→目录复核→五状态决策，固定切片过滤在词法和向量SQL截断之前，旧调用兼容。
- [ ] 专用无卷临时库验证实际工厂装配、双路固定切片、输出前资料撤回；相关单元/真库回归及启动器合同全部通过。
- [ ] 一次新上下文增量审查、记录裁定与证据、真实规则/学生发布仍关闭。

Expected：空/未审目录不调用选择器或检索；目录越权、选择器异常、未知条件键、审核途中撤回拒绝且不泄漏正文；独立部分事实、必要澄清、缺证及冲突状态保留。真实资料草稿、人工参考、36题原件和既有迁移均不改变。
''',encoding='utf-8')
ledger=root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md'
ledger.write_text(ledger.read_text(encoding='utf-8')+'''

Task2E: start — baseline r5-application-20261008-01/source-before.json; inline executing-plans/TDD, current isolated dirty checkout preserved.
Task2E: Ruling: HTTP装配延后到独立身份与会话边界步骤 — 现有v1 cookie与权限只覆盖v1，扩大路由会改变安全合同；本批提供可装配应用工厂但无Controller/Bean/资料放行 — 成本：暂不能宣称浏览器或学生入口完成。
Task2E: Ruling: 选择器只消费审核目录的ID与问题范围并返回ID，显式条件来自调用方；本批不实现供应商语义适配器 — 避免编造规则、事实及个人条件，语义选错仍需独立eval — 成本：合成测试不能证明自然语言选题质量。
Task2E: Ruling: 目录在捕获后再次读取，变化则整轮拒绝；源资格由决策前当前reader复核 — 把运行中撤回纳入链路，不声称数据库/目录/HTTP原子提交 — 成本：最终外发时间窗仍需后续门禁。
''',encoding='utf-8')
print('Task2E baseline and brief saved: 13 paths')
