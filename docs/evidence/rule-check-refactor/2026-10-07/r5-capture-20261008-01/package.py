from pathlib import Path
import difflib, hashlib, json

root = Path(__file__).resolve().parents[5]
batch = Path(__file__).resolve().parent
baseline = json.loads((batch / 'source-before.json').read_text(encoding='utf-8'))
diff, after = [], []
for row in baseline['files']:
    p = root / row['path']
    data = p.read_bytes() if p.exists() else b''
    after.append(dict(path=row['path'], sha256=hashlib.sha256(data).hexdigest(), exists=p.exists()))
    if row['path'].startswith('knowledge/'):
        before = (batch / row['before']).read_bytes() if row['before'] else b''
        diff.extend(difflib.unified_diff(before.decode('utf-8').splitlines(True), data.decode('utf-8').splitlines(True),
            fromfile='before/'+row['path'], tofile='after/'+row['path']))
(batch / 'changes-review.diff').write_text(''.join(diff), encoding='utf-8')
(batch / 'source-review.json').write_text(json.dumps(after, indent=2), encoding='utf-8')
phases = ['r5-capture-red-20261008-01','r5-capture-red-20261008-02',
          'r5-capture-db-red-20261008-01','r5-capture-green-20261008-01',
          'r5-capture-db-green-20261008-01','r5-capture-regression-20261008-01']
results = {phase: json.loads((batch.parent / phase / 'results.json').read_text(encoding='utf-8-sig')) for phase in phases}
assert all(results[phase]['passed'] for phase in phases[-3:])
assert results[phases[1]]['suites'][0]['failures'] == 9
assert results[phases[2]]['suites'][0]['failures'] == 3
assert all(results[phase]['sourceDrift'] == 0 for phase in phases[1:])
assert all(results[phase]['resourcesVerified'] for phase in phases)
lines = ['# R5 Task2C独立审查包', '',
    '审查范围为本批白名单before字节→当前源码，不是HEAD整个历史dirty工作区。未提交或部署。', '',
    '计划：docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md Task2C。规范：docs/product/rule-check-decision-contract.md及rule-check-scope.md。',
    '接口裁定：.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md最后Task2C三条Ruling。', '',
    '实现：两路SQL映射保留sourceId；旧七参构造器兼容且新链拒绝null；capture复用引用工厂/verifier/审计，整批当前核验及审计成功才返回不可变证据。无HTTP装配或真实模板激活。', '',
    '重点逐一检查：',
    '1. 旧七参对象不能伪造sourceId；两路SQL是否实际携带正确来源。旧v1对外DTO是否受到字段新增影响。',
    '2. 同chunk重复/不同别名、重复或异常工厂、null/空输入不可交出未经核验快照。',
    '3. 全文4→40、当前撤回/身份/版本/hash变更及reader不可用是否拒绝。',
    '4. 审计部分写入失败不返回证据，CAPTURE审计不能误称已向学生下发；没有事务原子性承诺。',
    '5. 捕获资格是当前reader核验结果；不得信任客户端或跳过后续决策重读；不将顺序读称最终下发原子性。',
    '6. 合成真实SQL验收不等于2025资料/规则放行，不关闭RC-04-SEMANTICS或产品质量门禁。', '',
    '证据：changes-review.diff、source-review.json、source-before.json及before/*.bin；请阅读实际文件，勿只凭计数。', '',
    '|阶段|通过|测试|失败/异常/跳过|资源退出|', '|---|---|---:|---|---|']
for phase, r in results.items():
    counts = [sum(int(s[k]) for s in r['suites']) for k in ('failures','errors','skipped')]
    lines.append(f"|{phase}|{r['passed']}|{r['testCount']}|{counts}|{r['resourcesVerified']}|")
lines += ['', '首轮仅测试构造器调用错误导致编译失败，不算RED；有效RED为第二轮12项9失败与真库26项3失败。',
          'Unit不连库；DB仅无卷独立临时库，含合成embedding及内存审计替身，没有供应商调用。',
          '引用审计真正数据库持久化、语义模板审核/匹配、身份/CSRF/页面、真实用户及报告留出效果未验收。']
(batch / 'review-package.md').write_text('\n'.join(lines)+'\n', encoding='utf-8')
print(json.dumps({p:dict(passed=r['passed'],tests=r['testCount']) for p,r in results.items()}, ensure_ascii=False))
