from pathlib import Path
import datetime, hashlib, json
HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[4]
names=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckRuleTemplate.java',
       'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckDecision.java',
       'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckDecisionService.java',
       'knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckDecisionServiceTest.java',
       'knowledge/scripts/verify-rule-check-refactor.ps1',
       'docs/product/rule-check-2025-rule-template-draft.json']
assert not (HERE/'source-before.json').exists()
rows=[]
for name in names:
    p=ROOT/name; row={'path':name,'existed':p.exists()}
    if name.endswith('.java') or name.endswith('-draft.json'): assert not p.exists(),name
    if p.exists():
        b=HERE/'before'/name;b.parent.mkdir(parents=True,exist_ok=True);b.write_bytes(p.read_bytes())
        row['sha256']=hashlib.sha256(p.read_bytes()).hexdigest()
    rows.append(row)
(HERE/'source-before.json').write_text(json.dumps({'createdAt':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),'files':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
ledger=ROOT/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md'
with ledger.open('a',encoding='utf-8') as f:
    f.write('''
## Task2B start：审核模板草案与五状态决策组件

Pre-flight: Task1/Task2A提供当前证据核验；Task2B消费服务端snapshot并复用核验器，不新增检索路径。真实PDF条款尚无运行时UUID映射与展示资格，不能直接装配成已发布规则。

Ruling: 在已有linked worktree顺序开发，保留历史dirty文件，不创建新checkout或自动提交 — 已验证git-dir不同于common-dir且非子模块 — 成本是最终审查只覆盖本批白名单增量。

Ruling: 20题参考确认允许导出真实规则草案及实现独立决策合同；不等于生产规则或资料放行 — 服务端模板审核、运行时绑定、资料资格各自独立 — 成本是自然语言路由/HTTP应用/学生试点保持未完成。

Ruling: 合成工程组件使用显式纯单元白名单及runner安全合同；不运行会触碰历史库或供应商的全仓默认测试 — 用户规定数据库只能专用临时库，真实模型单独验收 — 成本是报告只声明本次实际选类，不能称全仓全过。
''')
print('Task2B baseline and rulings recorded.')
