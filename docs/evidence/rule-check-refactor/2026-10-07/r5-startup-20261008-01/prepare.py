from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewApplication.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewConfiguration.java',
 'knowledge/src/main/resources/application-zhku-rule-check-preview.yml',
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
 'knowledge/src/main/java/com/zhku/knowledge/security/PreviewSecurityConfiguration.java',
 'knowledge/src/main/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfiguration.java']
fixed += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources/db/migration').glob('V*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in fixed}),indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''

## Task2G / A02第一段：默认关闭的独立应用与真实HTTP认证（2026-10-08）

白名单/当前字节：r5-startup-20261008-01/source-before.json。新Application仅显式Import新配置、安全链和AuthController，不扫描旧Controller、Agent或治理。主入口/直接配置都在资源初始化前验证A01会话守卫、专用临时库精确目标、process-only凭据来源、关闭目录模式及固定profile/切片/scope/引用版本/预算。数据源和迁移/身份/内部RuntimeFactory共享一个目标，Flyway自动装配关闭，由显式bean执行既有V22迁移。

- [ ] 有效断言RED：开关/profile/数据库目标/覆盖凭据/运行边界/目录模式/迁移同意缺失均拒绝；默认YAML关闭；主入口无隐式扫描。
- [ ] 最小GREEN：只允许专用zhkuagent_rulecheck_e2e回环非54329目标，显式initialize=true才迁移；实际目录固定为空，无模型/资料发布路径，构造完整内部应用Bean。
- [ ] 实际socket HTTP+临时库合成账号：Cookie路径/name/HttpOnly/SameSite、登录session换ID/token刷新、me、logout、revision撤销与旧路径拒绝；关闭监听/身份线程并清本夹具UUID账号。
- [ ] 相关综合验收、29启动器合同、一次独立审查及证据；普通用户终端启动器/同源代理/业务Controller留A02后段/A03，不整体勾选A02。

Expected：真实2025规则/许可未通过时目录CLOSED、无外发；正式查证业务接口未装配。只允许新无卷临时库，绝不通过初始化开关迁移原演示库。不将真实HTTP身份验收当作真实学生政策效果或浏览器完成。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''

Task2G/A02第一段: start — r5-startup-20261008-01/source-before.json，继续已授权清单，不停在A01。
Task2G: Ruling: 新Application采用显式Import，不组件扫描；单一DataSource/迁移/身份/RuntimeFactory — 防止同目录旧入口或未来Controller隐式进入边界 — 代价：新增入口必须显式登记并验证。
Task2G: Ruling: 本阶段仅允许专用e2e临时库且必须显式initialize=true，关闭目录/选择器/embedding外发 — 真实模板/资料资格仍未审，不能制造学生问答；验收写入只在专用无卷目标 — 代价：非验收演示数据库、真实规则装配和普通终端启动器仍属A02后续，不宣称全部完成。
Task2G: Ruling: 显式固定检索profile/切片/scope/引用版本/预算，但关闭目录下不要求该profile已经激活 — 本阶段仅装配边界与认证；空目录在应用服务检索前拒绝 — 代价：不能据此证明真实索引绑定，B01–B03仍OPEN。
''')
print('Task2G baseline: 11 paths')
