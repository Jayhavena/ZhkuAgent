from pathlib import Path
import datetime, hashlib, json
HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[4]
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/JdbcRuleCheckEvidenceReader.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckEvidenceReaderTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckEvidenceReaderTargetTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1']
assert not (HERE/'source-before.json').exists()
rows=[]
for name in paths:
 p=ROOT/name
 if name.endswith('.java'): assert not p.exists(),name
 row={'path':name,'existed':p.exists()}
 if p.exists():
  backup=HERE/'before'/name;backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
  row['sha256']=hashlib.sha256(p.read_bytes()).hexdigest()
 rows.append(row)
(HERE/'source-before.json').write_text(json.dumps({'createdAt':datetime.datetime.now().astimezone().isoformat(),'files':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
ledger=ROOT/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md'
with ledger.open('a',encoding='utf-8') as f:
 f.write('''
## Task1 GREEN / Task2A start

Task1 RED19项17断言失败/错误跳过0，GREEN19项全过，相关15类130项全过，合同29项全过；独立最终审查进行中。尚未装配学生端，旧RAG语义OPEN。

Ruling: citationId是本轮别名，当前读取以持久source/chunk身份校验，latest别名不同允许 — 数据库无法独立重建本轮别名 — 成本是应用必须从本轮approved绑定别名，不接受客户端自证。

Ruling: Task2A真实数据库读取准备不依赖真实审核模板，可按补充清单单独实施 — 连续授权要求推进无外部依赖的工程项 — 成本是只证明现有字段复核，不代表完整时间/最终下发竞态或真实资格审核。

Task2A基线已保存：新reader及DB/目标纯单元测试路径不存在，runner before字节保存。SQL要求服务端固定profile/chunking/scope，按source/chunk精确读取；不排序候选，不新增第三套RAG。不装配控制器/发布入口。
''')
print('Task2A baseline recorded')
