from pathlib import Path
import json, hashlib, difflib

root = Path(__file__).resolve().parents[5]
batch = Path(__file__).resolve().parent
legacy = batch.parent / 'r5-legacy-copy-20261008-01'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
write = lambda p, x: p.write_text(json.dumps(x, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
assert not (batch/'checkpoint.md').exists()
before = read(batch/'source-before.json')
extension = read(batch/'scope-extension.json')['files']
old = read(legacy/'source-before.json')
# 分批前后有显式交叉改动，保留原清单，以联合范围核对，不能冒称各批保护文件全部不变。
allowed = {r['path'] for r in before['files']+extension+old['files']}
drift = []
for name, manifest in [('browser', before), ('legacy', old)]:
    drift += [dict(batch=name, path=p) for p,h in manifest['unchanged'].items()
              if p not in allowed and sha(root/p) != h]
assert not drift, drift
for r in extension:
    if r['sha256'] is not None:
        assert before['unchanged'][r['path']] == r['sha256']
live = batch.parent/'r5-browser-live-20261008-03'
result = read(live/'results.json')
assert result['passed'] is False and result['resourcesVerified'] and result['sourceDrift'] == 0
assert read(batch/'runner-contract.json')['passed']
text = '''# 当前收尾记录：已验证核心路径，完整验收仍未完成

2026-10-08。当前不是整体验收通过，不是资料发布许可。

已观察到实际 Vue → Nginx → Java → 专用临时库：学生登录成功，提问“合成条款”得到有原文支持的回答，事实“合成上限4”；核对原文显示“合成HTTP原文”、记录页码1、正确摘录高亮，并明确页码映射未知。1280宽无水平溢出。没有打开合成外链。

撤回本夹具资料后，再点原文提示资料变化并清除原文正文；再次查证显示“暂无充分依据”，不会继续用已撤回资料支持回答。退出后登录表单显示，旧问题、回答与原文不再展示。

随后尝试学生B登录时，六分钟验收租约到期，后端自动关闭；登录结果未知。账号切换成功后的空白状态、维护者拒绝、澄清/部分回答/冲突与旧v1浏览器整链尚未验证。本次live03的JUnit结果为1项失败（超时未显式STOP），不能用已观察路径覆盖这个失败。Java/专用数据库自动退出、sourceDrift=0；Nginx专用容器与网络已关闭，临时浏览器页关闭，55470/20699/58580无监听。没有真实供应商调用或原演示库写入。

实际浏览器发现Java字段scope.declaredConditions与前端自造夹具scope.conditions不一致，正常回答被误拒绝。保存实际HTTP合成响应为回归夹具；先观察17项中1项BAD_RESPONSE有效失败，再在客户端严格核验HTTP合同并映射页面字段。全前端85/85、无失败/取消/跳过；最终实际构建成功。验收脚本29项合同通过（externalStarts=9是计数替身）。先前后端全503通过是历史批次结果，本批未重跑整套，不算新增浏览器通过数。

旧v1文案修复已实现并纳入85项：同键不同内容说明冲突、已找到任务不再显示“正在确认”。该文案与新增浏览器测试宿主尚未进行最终合并审查，A09/A10保持部分完成。live01夹具身份版本错误、live02暴露接口合同错误、live03超时均保留原日志，不改写失败为成功。

执行节奏纠正：用户指出耗时过长后，缩小到可用核心链；暂停外围索引整理、逐项审查和无新风险的重复全回归。后续先完成缺少的浏览器状态矩阵与一次合并审查，再准备真实2025资料绑定及人工语义审核。真实资料仍DRAFT/不可运行/不可学生展示，默认目录CLOSED；真实效果、用户节省时间、B/C/G2均未放行。
'''
(batch/'checkpoint.md').write_text(text, encoding='utf-8')
write(batch/'browser-observations.json', dict(synthetic=True, providerCalled=False,
    observed=['student-login','answered','original-highlight','withdraw-original-denied','withdraw-insufficient','logout-clears-view'],
    notVerified=['student-B-login-success','maintainer-denied','clarification','partial-answer','conflict','legacy-v1','ordinary-terminal'],
    livePhasePassed=False, reason='bounded host lease expired before explicit STOP', resourcesVerified=True))
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md'
t=backlog.read_text(encoding='utf-8')
t=t.replace('旧v1文案/浏览器待续','旧v1文案已修复待合并审查，完整浏览器待续')
t=t.replace('v2代理夹具已通过，实际Java整链待续','实际合成回答/原文/撤回已观察，完整矩阵未通过')
t=t.replace('主路线图/问题索引部分摘要仍为较早批次，按最新证据追加当前状态、延期项和回退说明；保留旧记录，不让263工程测试替代质量/用户成绩','当前索引已同步至Task2P/3B/3C1；后续按新增证据维护，保留历史记录，不让工程测试替代质量/用户成绩')
backlog.write_text(t, encoding='utf-8')
note='''\n## 2026-10-08 核心浏览器链检查点（未整体验收通过）

实际合成学生登录→回答→原文高亮、撤回后原文拒绝/缺证、退出清显示已观察。Java字段declaredConditions与前端条件夹具不一致已有效17/1 RED→85/85 GREEN，构建通过；runner合同29项通过。live03六分钟到期未显式STOP，JUnit失败保留，账号B/维护者/其余状态矩阵未验证，A10继续OPEN。专用DB/Java/Nginx与临时浏览器已退出，无真实模型/原库写入。旧v1两处文案已修复，待一次合并审查。[检查点与问题证据](../evidence/rule-check-refactor/2026-10-07/r5-browser-chain-20261008-01/checkpoint.md)。真实资料/B/C/G2不放行。
'''
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(note)
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''\n## Task3C2 / A09–A10 当前检查点

- [x] 实际HTTP响应反例17/1 RED，客户端严格核验declaredConditions后映射页面；Node85/85及构建、runner29合同通过。
- [x] 实际合成浏览器登录/回答/原文/资料撤回/退出观察已记录，专用资源退出。
- [ ] 完整浏览器矩阵及普通终端；live03超时失败，不能标整体验收通过。
- [ ] 旧文案与本批改动的一次合并审查；真实资料和G2另按门禁。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''\nTask3C2: partial — 实际Java响应字段不一致17/1有效RED→全Node85/85/构建，runner29合同通过。实际浏览器已观察登录/回答/原文高亮/撤回拒绝与缺证/退出清显示；live03六分钟到期、JUnit1fail，账号B/维护者/其他状态与旧v1仍未完成，不宣布A10通过。资源退出，失败日志保留。
TaskA09: implemented, final-review-pending — 旧v1内容冲突与已有任务文案有效4/3 RED→全Node85/85；尚未最终合并审查。
Ruling: 用户指出耗时过长后暂停外围整理和逐项审查，以完整可用核心链为下一交付点，不再无新风险重跑503全套 — 当前审查与浏览器矩阵保持未完成 — 成本：未审查改动暂不作最终交付/发布声明。
''')
for folder,rows in [(batch,before['files']+extension),(legacy,old['files'])]:
    after=[];diff=[]
    for r in rows:
        p=root/r['path']; after.append(dict(path=r['path'],sha256=sha(p)))
        prior=(folder/r['before'].replace('\\','/')).read_bytes() if r['before'] else b''
        diff.extend(difflib.unified_diff(prior.decode('utf-8').replace('\r\n','\n').splitlines(True),p.read_text(encoding='utf-8').splitlines(True),fromfile='before/'+r['path'],tofile='checkpoint/'+r['path']))
    write(folder/'source-checkpoint.json',after)
    (folder/'changes-checkpoint.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'checkpoint-integrity.json',dict(protectedOutsideCombinedScopeUnchanged=True, combinedScope=sorted(allowed),
    artifacts={str(p.relative_to(batch)).replace('\\','/'):sha(p) for p in batch.iterdir() if p.is_file() and p.name!='checkpoint-integrity.json'}))
print('Partial checkpoint recorded; 85 frontend and 29 contract pass; live03 failed honestly retained; A10 remains OPEN')
