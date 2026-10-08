from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckController.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckMediaTypeFilter.java',
 'knowledge/src/main/java/com/zhku/knowledge/controlled/ControlledRunRequestLimitFilter.java',
 'knowledge/src/main/java/com/zhku/knowledge/security/PreviewSecurityConfiguration.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewApplication.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewConfiguration.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/web/RuleCheckControllerTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfigurationTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewConfigurationTest.java',
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
 'knowledge/src/main/resources/application-zhku-controlled-preview.yml',
 'knowledge/src/main/java/com/zhku/knowledge/controlled/ControlledPreviewConfiguration.java',
 'knowledge/src/main/resources/application-zhku-rule-check-preview.yml']
fixed += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources/db/migration').glob('V*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in fixed}),indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''

## Task2H / A03第一段：严格JSON业务接口（2026-10-08）

白名单与当前字节见r5-api-20261008-01/source-before.json。显式Import新Controller，只接受question/confirmedConditions；客户端审核/资料资格/快照/身份字段一律拒绝。JSON结构/类型/重复键/尾随文本严格；问题2048 UTF-16、条件16、键64/值128；全部声明需显式提交，不能模型补猜。

- [ ] 有效断言RED：五状态响应/引用映射、用户声明条件与事实区分；未知字段/错误类型/空缺/限长/重复JSON/尾随值拒绝，错误正文不回显。
- [ ] GREEN：复用应用服务，声明字段和知识scope/引用版本随响应；现有事实、澄清、未知、冲突完整投影，不自动补造独立例外/适用范围。审核的具体规则范围/结构化例外须后续A04/B02，不因此整体关闭A03。
- [ ] 请求媒体类型只读头部守卫先于CSRF，禁止表单参数触发提前解析业务body；CSRF/当前身份/授权之后复用限长过滤器，实际读32769最多，未知Content-Length同样413。旧v1默认16KiB/路由保持。
- [ ] 实际HTTP正常USER下CLOSED目录缺证、错误字段/重复/尾随/标量拒绝、CSRF/角色/媒体类型拒绝、固定及chunked过大body413；旧v1与全部相关回归/29合同/一次最终审查，记录边界。

Expected：新入口默认关闭、真实目录CLOSED不变；这仅严格接口工程交付，不是2025规则启用/真实模型/浏览器质量。A07最终身份/资料再次核验、A06原文查询、A04跨轮规则绑定未完成，不承诺最终外发原子性。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''

Task2H/A03第一段: start — r5-api-20261008-01/source-before.json，接续已授权持续任务。
Task2H: Ruling: 只接受两项显式JSON字段，条件原样标记USER_DECLARED，与已支持事实分开 — 条件输入不证明学籍或个人资格，模型不能补猜 — 代价：用户需明确填写决定性条件，跨轮上下文仍A04。
Task2H: Ruling: 媒体类型头守卫在CSRF前，不读body；实际32KiB限长在当前身份/授权之后，复用v1限长代码并保留默认 — 防止表单csrf参数解析绕过限长；匿名/错误角色不读取JSON业务body — 代价：非JSON请求优先415，旧v1回归必须通过。
Task2H: Ruling: API只投影现有五状态与支持摘录，加固定知识scope/参考版本；不造空例外数组声称无例外 — 当前模板尚无独立例外/规则范围输出对象，结构化说明随A04/B02补齐 — 代价：A03整体尚未完成；最终外发/原文定位也不能据此通过。
''')
print('Task2H baseline: 16 paths')
