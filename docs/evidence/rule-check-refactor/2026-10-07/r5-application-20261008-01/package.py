from pathlib import Path
import hashlib,json,difflib
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
before=read(batch/'source-before.json');diff=[];after=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=hashlib.sha256(data).hexdigest()))
 if row['path'].startswith('knowledge/'):
  old=(batch/row['before']).read_bytes() if row['before'] else b''
  diff.extend(difflib.unified_diff(old.decode('utf-8').splitlines(True),data.decode('utf-8').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8')
(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
phase='r5-application-acceptance-20261008-01';final=read(batch.parent/phase/'results.json')
assert final['passed'] and final['sourceDrift']==0 and final['resourcesVerified'] and final['containerExited']
assert final['testCount']==262 and len(final['suites'])==23
db=read(batch.parent/phase/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert all(hashlib.sha256((root/r['path']).read_bytes()).hexdigest()==r['sha256'] for r in read(batch.parent/phase/'source-after.json'))
assert all(hashlib.sha256((root/p).read_bytes()).hexdigest()==h for p,h in before['unchanged'].items())
phases=['r5-application-red-20261008-01','r5-application-db-red-20261008-01','r5-application-db-red-20261008-02','r5-application-green-20261008-01',phase]
results={p:read(batch.parent/p/'results.json') for p in phases}
lines=['# Task2E统一配置与内部应用编排审查包','',
 '只读审查本批13路径的before字节→当前增量；巨大历史dirty树不纳入，不改变工作区/索引/HEAD，不跑Maven/DB/模型，不派子agent。',
 '范围：RuleCheckRuntimeSettings/ApplicationService/Factory及词法/向量固定切片新重载、测试、runner登记；changes-review.diff/source-before.json/before/*.bin/source-review.json保留。请读实际源码和相关依赖。',
 '计划docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md最后Task2E；规范docs/product/rule-check-decision-contract.md最后Task2E、rule-check-scope.md；账本最后Task2E的3条Ruling。',
 '无提交/部署/原库操作/供应商调用；真实2025模板/资料未激活。工厂不是HTTP入口。',
 '', '审查重点逐项：',
 '1. 四个JDBC组件固定profile/cut/scope一致，切片SQL在截断前，不影响旧构造器/离线BUILDING资格。无latest回退。',
 '2. 仅APPROVED且精确引用版本目录可选，选择器只返回目录ID，不接受模型事实/资格/个人条件；空目录、非法ID、重复ID、异常、不声明条件键不继续。白名单不保证自然语言语义相关。',
 '3. 有界输入、目录快照、不可变options/显式条件；中途规则撤回/改动拒绝，资料撤回由决策再次reader检查。异常信息不回显。',
 '4. 复用Hybrid→Capture→Audit→Decision，无另造RAG/Agent循环；单路降级、五状态保留；审计成功不等于外发成功或跨组件原子性。',
 '5. 工厂构造不能外发/连接；旧v1 cookie/权限/HTTP、历史迁移、真实草稿/人工参考/36题保持原样。没有新Controller/Bean。',
 '6. 真库合成fixture验证两种切片并存、两路过滤、工厂实际审计、输出前来源撤回；供应商选择/embedding为替身，无产品成绩。',
 '', '|阶段|通过|测试|失败/异常/跳过|','|---|---|---:|---|']
for p,r in results.items():
 counts=[sum(int(s[k]) for s in r['suites']) for k in ['failures','errors','skipped']]
 lines.append(f'|{p}|{r["passed"]}|{r["testCount"]}|{counts}|')
lines+=['','首轮DB编译失败：漏传EligibilityFilter时钟，修正后第二轮DB得到21项/3断言失败/0异常/0跳过；保留首次记录，不计为RED。',
 '最终23类262＝20类200单元+3类62真库；29项runner合同；无卷临时库V22、连接0、容器退出，源码/XML指纹相符。',
 '尚待：真实条款绑定/语义审核/展示资格；供应商有界选择器及语义eval；v2身份/CSRF/HTTP/页面；报告补样、评分、真实用户对照。当前R5/G2均未完成。',
 '请按实际用户影响输出Critical/Important/Minor、file:line、具体触发与最小修复；列Declined to judge并说明原因。不要把明确未开放的入口当已有学生功能。']
(batch/'review-package.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('Review package ready: 23 classes / 262 tests')
