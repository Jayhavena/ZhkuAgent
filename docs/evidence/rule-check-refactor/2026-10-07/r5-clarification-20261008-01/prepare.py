from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckApplicationService.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckScopedResult.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckClarificationStore.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckController.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewConfiguration.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckApplicationServiceTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/web/RuleCheckClarificationStoreTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/web/RuleCheckControllerTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckCitationAuditTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleCheckPreviewHttpTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1','docs/product/rule-check-decision-contract.md',
 'docs/product/rule-check-blockers.md','docs/product/rule-check-remaining-tasks-2026-10-08.md',
 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md','.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists();(batch/'before').mkdir()
rows=[]
for i,path in enumerate(paths):
 p=root/path;data=p.read_bytes() if p.exists() else None;saved=f'before/{i:02d}.bin' if data is not None else None
 if saved:(batch/saved).write_bytes(data)
 rows.append(dict(path=path,before=saved,existed=data is not None,sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
fixed=['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json',
 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json',
 'knowledge/src/main/resources/application-zhku-controlled-preview.yml','knowledge/src/main/resources/application-zhku-rule-check-preview.yml',
 'knowledge/src/main/java/com/zhku/knowledge/security/PreviewSecurityConfiguration.java',
 'knowledge/src/main/java/com/zhku/knowledge/controlled/ControlledPreviewConfiguration.java']
fixed += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources/db/migration').glob('V*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in fixed}),indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''

## Task2I / A04：只保存必要澄清上下文（2026-10-08）

白名单与当前字节见r5-clarification-20261008-01/source-before.json。内部应用新增带服务端规则范围/声明条件/事实前提的结果；旧check保留Decision兼容。NEED_CLARIFICATION才创建续答上下文，绑定原问题、明确声明、已审CatalogEntry全文快照、运行settings与当前待问条件；续答只补一个待问允许值，不重新调用选择器，复核审核目录/配置，重新检索核验/审计/决策，不缓存旧证据。

- [ ] 内部有效RED：首次范围/上下文、分支不重复澄清、跳过selector而重取证、规则退役/变化/不同settings、错误键/值/改已声明值、缺资料与来源撤回拒绝；真库续答/撤回。
- [ ] GREEN：复用统一应用链；NEED才生成Continuation，范围只来自服务端APPROVED目录，不证明个人学籍/语义相关，结构化例外不自动补造。
- [ ] Session Store实际合同RED→GREEN：每个HttpSession仅一个上下文，随机token、绑定当前actor全快照，绝对5分钟，到期边界清除；consume仅一次，错误token/不同账户/revision/已失效/不同问题拒绝，错误条件保留供改正；新问题/终态清除，无完整聊天历史/正文缓存。
- [ ] HTTP增加可选clarificationToken，只消费服务端Session上下文；新问题清旧上下文，续答只接受本轮delta，返回合并后的USER_DECLARED及规则问题范围/声明条件/事实前提；真实身份HTTP+合成服务范围/状态夹具验证续答/换账号/到期/一次消费，CLOSED正式目录不变。
- [ ] 相关单元/真库/实际HTTP、29合同、最终一次增量审查及证据；普通终端/浏览器/原文/最终外发/真实语义仍后续。

Expected：不会为资料缺失制造追问，不把客户端ruleId或条件当审核对象；续答途中规则/资料变化拒绝，旧证据不得复用。同浏览器并行新问题使旧token不可用，返回409重新查证；此有界取舍不暗建历史。正式应用没有合成审批模式。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''

Task2I/A04: start — r5-clarification-20261008-01/source-before.json，继续已授权任务。
Task2I: Ruling: 续答绑定服务端已选择规则和原问题，跳过重新选题但每轮重新取证/审计/决策 — 防止模型变题及旧来源资格复活 — 代价：更换问题或已确认条件必须重新查证，检索/审计成本每轮发生。
Task2I: Ruling: HttpSession只留一个必要上下文，5分钟绝对期限，actor userId/role/revision绑定；不保存完整历史或回答正文 — 窄查证流程不扩建聊天系统，换账号/并发/过期必须拒绝复用 — 代价：同浏览器并行新问题使旧token409；网络丢失续答响应后需要从新问题重建，不能声称完整咨询历史。
Task2I: Ruling: 问题范围/声明条件/事实前提只投影审核目录，独立例外仍在事实文本/真实语义审核中 — 补足A03范围透明度但不猜测原模板没有的结构化信息 — 代价：B02例外完整性和自然语言匹配仍需人工/eval。
''')
print('Task2I baseline: 16 paths')
