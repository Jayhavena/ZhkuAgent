from pathlib import Path
import json,hashlib
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/security/PreviewSecurityConfiguration.java',
 'knowledge/src/main/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfiguration.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckAuthController.java',
 'knowledge/src/test/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfigurationTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1','docs/product/rule-check-decision-contract.md',
 'docs/product/rule-check-blockers.md','docs/product/rule-check-remaining-tasks-2026-10-08.md',
 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md',
 '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists();(batch/'before').mkdir()
rows=[]
for i,path in enumerate(paths):
 p=root/path;data=p.read_bytes() if p.exists() else None;saved=f'before/{i:02d}.bin' if data is not None else None
 if saved:(batch/saved).write_bytes(data)
 rows.append(dict(path=path,before=saved,existed=data is not None,sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
fixed=['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json',
 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json',
 'knowledge/src/main/resources/application-zhku-controlled-preview.yml',
 'knowledge/src/main/java/com/zhku/knowledge/controlled/ControlledPreviewConfiguration.java']
fixed += [p.relative_to(root).as_posix() for p in (root/'knowledge/src/main/resources/db/migration').glob('V*.sql')]
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in fixed}),indent=2)+'\n',encoding='utf-8')
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''

## Task2F / A01：独立v2身份边界（2026-10-08）

用户已批准剩余任务清单并要求持续执行。本批细化既定受控身份合同：新`zhku-rule-check-preview`独立profile/应用上下文，固定v2路径与`ZHKU_RULE_CHECK_SESSION` cookie，不扩大v1 cookie路径；认证Provider与当前身份过滤复用同一实现，仅按可信服务端入口配置参数化。

白名单与进入本批字节见`r5-security-20261008-01/source-before.json`。框架沿Boot3.4.4/Security6.4.4，Context7版本文档已核对，未升级依赖。A01先验证真实过滤链+身份存储替身；A02继续独立启动、实际cookie创建与临时库认证，本批不创建模型/数据库/校园执行资源。

- [ ] 编写v2路径、可信USER/维护者、CSRF、身份撤销/不可用、登出、会话更新和入口混用拒绝的实际断言RED。
- [ ] 复用身份链实现；仅指定v2四个auth路径及USER查证POST放行；其他入口、方法、身份头拒绝。登出/撤销只清v2 cookie。
- [ ] 实施安全环境守卫：独立profile/显式开关/回环绑定/固定15分钟cookie会话配置，拒绝持久化、URL凭据和匿名/旧Agent混用。
- [ ] 旧v1框架与受控启动相关回归、新v2过滤链、查证相关单元全部通过；29隔离合同及一次独立增量审查，保留证据。

Expected：CSRF优先于身份库；非可信同名USER拒绝；维护者不继承USER；停用/revision变化清会话，身份库故障503且不放行；登入更新会话ID并刷新CSRF、旧token不能重用；新profile不能加载旧v1入口，新v2配置不进入旧v1上下文。业务接口暂无Controller，以测试sentinel证明授权，不宣称学生查证HTTP完成。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''

Task2F/A01: start — baseline r5-security-20261008-01/source-before.json; 用户授权47项清单后持续执行，内联实现/单次最终审查，保留当前dirty。
Task2F: Ruling: 采用独立v2 profile/应用上下文及cookie名称/路径，复用现有认证与当前身份逻辑 — v1固定cookie不能跨到v2且不能暗中扩大边界 — 成本：A02须完成独立启动与后续同源代理接线，当前不提供跨入口SSO。
Task2F: Ruling: 本批按已批准A01范围细化已有身份流程并直接执行，不重复请求设计或代码审批 — 用户明确要求持续推进，变动局限未启用的受控入口，无部署/真实资料许可修改 — 成本：配置策略仍须框架/后续真实HTTP证据验证，不能把范围批准解释为资料发布授权。
Task2F: Ruling: A01真实框架过滤链与身份替身分开于A02实际HTTP/数据库认证 — 先验证授权与会话安全，不伪造数据库或cookie创建证据 — 成本：本批不能宣布独立应用或浏览器登录已交付。
''')
print('Task2F A01 baseline: 10 paths; plan and rulings recorded')
