from pathlib import Path
import hashlib, json, difflib
root=Path(__file__).resolve().parents[5]; batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
baseline=read(batch/'source-before.json'); diff=[]; after=[]
for row in baseline['files']:
 p=root/row['path']; data=p.read_bytes(); after.append(dict(path=row['path'],sha256=hashlib.sha256(data).hexdigest()))
 if row['path'].startswith('knowledge/'):
  old=(batch/row['before']).read_bytes() if row['before'] else b''
  diff.extend(difflib.unified_diff(old.decode('utf-8').splitlines(True),data.decode('utf-8').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8')
(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
phases=['r5-audit-red-20261008-01','r5-audit-db-red-20261008-01','r5-audit-green-20261008-01','r5-audit-acceptance-20261008-01']
results={p:read(batch.parent/p/'results.json') for p in phases}
assert all(results[p]['passed'] for p in phases[-2:])
assert all(results[p]['sourceDrift']==0 and results[p]['resourcesVerified'] for p in phases)
final=results[phases[-1]]
db=read(batch.parent/phases[-1]/'database.json')
assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
for row in read(batch.parent/phases[-1]/'source-after.json'):
 assert hashlib.sha256((root/row['path']).read_bytes()).hexdigest()==row['sha256']
lines=['# Task2D精确绑定引用审计独立审查包','','审查仅before字节→当前15条白名单；历史dirty不纳入。未commit/部署或更改原库。',
 '计划：docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md Task2D；规范docs/product/rule-check-decision-contract.md、rule-check-scope.md；账本最后4条Task2D Ruling。',
 '增量：changes-review.diff、source-review.json、source-before.json及before/*.bin，请同时阅读实际源码。',
 '实现：捕获将source/revision/hash/全文绑定写入兼容旧四参的CitationAuditRecord；旧仓储拒绝CAPTURE；新JDBC仓储按固定server profile/切分/scope使用参数化INSERT SELECT重新匹配捕获文字/资格并绑定唯一entry；新增V22扩充路由CHECK，V1–V21不变。',
 '', '审查重点逐项：',
 '1. 任意较新BUILDING/其他切分/缺配置条目不能抢审计；记录必须符合捕获来源、版本、hash、原文，不只chunk或latest。',
 '2. 无完整绑定/错路由/误装旧仓储是否在连接前拒绝；四参旧路由是否兼容，未知路由仍受DB CHECK。',
 '3. SQL参数绑定、ACTIVE/资格/scope/run/profile/切分一致性、零行/重复引用/DB异常不能当成功；没有先select latest再insert的空隙。',
 '4. 审计固定配置应在后续装配与检索/current reader一致；当前快照无召回entryId，不能声称自动校验配置一致或追踪原召回entry。',
 '5. V22保留旧路由/历史审计、不更改历史迁移，仅新临时库运行；原项目容器停止，无原DB连接/迁移。',
 '6. 真库共享词法/混合→捕获→实际JDBC审计→规则决策与撤回拒绝；合成规则不等于2025审核，单条SQL快照不等于整批/最终HTTP下发原子性。',
 '', '|阶段|通过|类数|测试|失败/异常/跳过|','|---|---|---:|---:|---|']
for p,r in results.items():
 sums=[sum(int(s[k]) for s in r['suites']) for k in ['failures','errors','skipped']]
 lines.append(f'|{p}|{r["passed"]}|{len(r["suites"])}|{r["testCount"]}|{sums}|')
lines += ['',f'最终22类242：19类183纯单元+3类59真库，其中新JDBC审计18项、目标合同6项。V{db["migration"]}、连接0、容器退出；runner合同29通过（外部计数替身）。',
 '完整源码/XML指纹/退出记录均已保存；不是全仓默认测试通过，也无供应商效果/费用。',
 'source-readiness.json已核对实际原件、manifest/curation指纹；外部资料本地验证/学生不可见，在线来源UNKNOWN；版本2025版与2025.9.2未自动互换。',
 '现实规则仍DRAFT、HTTP/CSRF/自然语言匹配/页面、报告及用户收益保持未完成。请按实际用户影响分Critical/Important/Minor，列Declined to judge，不派子agent、不改文件、不跑Maven/DB/模型。']
(batch/'review-package.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(json.dumps(dict(passed=True,total=final['testCount'],classes=len(final['suites']),migration=db['migration']),ensure_ascii=False))
