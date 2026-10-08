from pathlib import Path
import datetime, hashlib, json, subprocess
ROOT=Path(__file__).resolve().parents[4]
BASE=Path(__file__).resolve().parent/'r5-evidence-20261008-01'
BASE.mkdir(exist_ok=False)
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/'+x+'.java' for x in ['RuleCheckEvidenceSnapshot','RuleCheckEvidenceSpan','RuleCheckVerifiedExcerpt','RuleCheckEvidenceVerifier']]
paths+=['knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceVerifierTest.java','knowledge/scripts/verify-rule-check-refactor.ps1','docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md','docs/product/rule-check-blockers.md','docs/evidence/rule-check-refactor/2026-10-07/issues.json']
files=[]
for name in paths:
 p=ROOT/name
 if name.endswith('.java'): assert not p.exists(), name
 row={'path':name,'existed':p.exists()}
 if p.exists():
  dest=BASE/'before'/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(p.read_bytes())
  row['sha256']=hashlib.sha256(p.read_bytes()).hexdigest()
 files.append(row)
(BASE/'source-before.json').write_text(json.dumps({'createdAt':datetime.datetime.now().astimezone().isoformat(),'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'files':files},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
ledger=ROOT/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md';ledger.parent.mkdir(parents=True,exist_ok=True)
assert not ledger.exists()
ledger.write_text('''# R5 顺序实施账本

2026-10-08。计划：docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md。
P0最终20类151项、29合同、22样例恢复通过；见P0 closeout verification。HEAD未改，无提交/推送。

## Task1 start

Step1基线完成：新增4生产类型+测试路径不存在，before字节已保存。G0 NOT_PASSED；新组件无控制器/数据库/模型装配，不改变旧RAG4→40失败。

Ruling: 顺序内联执行，沿用当前非main隔离checkout和独立ledger，不另建worktree/提交 — 保留用户历史dirty修改及连续授权 — 成本是最终证据按本批增量审查而非整树提交。

Ruling: 先实现确定性原文组件，不猜审核规则/评分参考 — 已确认R5抽取路线允许独立合成合同 — 成本是Task2–4仍依赖B-01/B-02与真实参与者。

接口预检：四类型名称、UTF16范围、UTF8 SHA256及reader身份合同一致；结果只承载服务端substring。只捕获reader RuntimeException，运行时异常受控拒绝；不添加语义裁判。
''',encoding='utf-8')
print('R5 Task1 baseline and ledger created; G0 NOT PASSED')
