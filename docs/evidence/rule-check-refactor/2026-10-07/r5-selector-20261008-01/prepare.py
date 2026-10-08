from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/BoundedRuleSelector.java',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/BoundedRuleSelectorTest.java',
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
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/java/com/zhku/knowledge/rulecheck').rglob('*.java')]
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources').rglob('application-*.yml')]
protected += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources/db/migration').glob('*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in protected}),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''

## Task2J / A05第一段：有界选题协议组件（2026-10-08）

本批8项白名单与当前字节见r5-selector-20261008-01/source-before.json。采用provider-neutral原始JSON Transport，不沿用事实生成ModelResponse。只发送明确问题与审核目录ID/questionScope，不发送原文、个人条件或凭据。一次只选一个目录ID或显式null，不接收自由文本/事实/工具调用。

- [ ] 有效RED：合法ID/null、目录空/重复/超限/非法ID、问题输入/请求UTF8预算、响应重复键/尾随/额外字段/错误类型/越权/过长/故障；无外发反例。
- [ ] GREEN：仅精确白名单ruleId，严格JSON输出，固定选择约束；空/歧义/异常拒绝，不猜规则或学生条件，不重试。
- [ ] 资源合同：每实例单worker/至多一个未完成外发、无调用等待队列；实例存续总尝试预算及每轮有界等待。超时取消不证明供应商终止，未协作任务退出前保持占用；close拒绝新请求并interrupt，不无限等待。
- [ ] 全相关回归/29工具合同/一次最终增量审查/问题证据；正式CLOSED不装配新选择器，真实供应商物理响应/网络超时/重试/语义/简单基线效果仍A05后段与C组。

Expected：prompt注入输出越权或自由事实拒绝；合法ID却选错仍可能发生，协议白名单不能证明相关性。Transport须承担真实网络响应字节/重试/连接超时约束，此批只用进程内替身，响应String限长是解析前检查而非网络读取限长。不添加框架/依赖/资料发布开关。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''

Task2J/A05第一段: start — r5-selector-20261008-01/source-before.json，接续已授权开发，不请求批次审批。
Task2J: Ruling: 选题Transport使用独立原始JSON协议，不复用事实生成ModelResponse — 模型只能选择服务端审核目录ID/null，旧answer/citation工具通道会扩大权限 — 成本：真实Spring AI/供应商适配另需后段，当前不声称模型已接入。
Task2J: Ruling: 每实例单worker、总尝试预算、单在途和每轮超时；取消不等于供应商停止，非协作任务继续占用 — 防止超时不断创建新线程/外发以及隐性重试 — 成本：可能拒绝后续请求直到任务实际退出；仅本机单实例限额，不宣称分布式/全局配额。
Task2J: Ruling: 输入/响应预算按UTF8字节，String输出在解析前检查；物理网络读取限额由Transport后续适配证明 — 不把返回后校验包装成供应商下载已受限 — 成本：本批只完成组件合同，A05整体及真实语义/C组保持OPEN。
''')
print('Task2J baseline: 8 paths')
