from pathlib import Path
import hashlib,json,difflib
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
before=read(batch/'source-before.json');diff=[];after=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=hashlib.sha256(data).hexdigest()))
 if row['path'].startswith('knowledge/'):
  old=(batch/row['before']).read_bytes() if row['before'] else b''
  diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8')
(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
phase='r5-security-acceptance-20261008-01';result=read(batch.parent/phase/'results.json')
assert result['passed'] and result['sourceDrift']==0 and result['resourcesVerified'] and result['containerExited']
assert result['testCount']==298 and len(result['suites'])==26
assert all(hashlib.sha256((root/r['path']).read_bytes()).hexdigest()==r['sha256'] for r in read(batch.parent/phase/'source-after.json'))
assert all(hashlib.sha256((root/p).read_bytes()).hexdigest()==h for p,h in before['unchanged'].items())
text='''# R5 Task2F / A01：独立v2身份链审查包

只审本批10条白名单的before字节→当前增量，不把历史dirty纳入。不改工作区/索引/HEAD，不跑Maven/DB/模型，不派子agent。changes-review.diff/source-review.json/source-before.json及before/*.bin；请阅读实际代码与必要依赖。

计划：docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md最后Task2F；范围docs/product/rule-check-remaining-tasks-2026-10-08.md A01，规范docs/product/rule-check-decision-contract.md/范围约定；账本末尾Task2F四条Ruling。用户要求列清单后持续推进，不要求重新批准每一批代码。

实现：旧PreviewSecurityConfiguration抽出同一Provider/过滤链组装，prefix/cookie来自受控服务端常量；旧v1包装保留路由/cookie/行为；新zhku-rule-check-preview独立profile，固定v2 cookie，只有四auth路径与USER查证POST。新profile的安全环境BFPP先检查独立profile、显式开关、回环地址、显式cookie配置/15分钟期限及禁止模式。新auth只提供csrf/me，尚无Application/数据库装配/查证业务Controller。

Review Focus逐项：
1. 旧v1认证限额、可信principal/角色、CSRF先于当前身份、会话更新/注销/错误码及cookie路径是否保持；ProviderManager不能双重尝试。
2. 新v2只允许显式auth路径与USER查证POST，方法/旧入口/匿名/治理/实验路由、自报身份、其他来源同名USER拒绝；新配置不加载到旧profile。
3. cookie/路径来自server constants；profile禁止混合，同名用户不是跨应用SSO证明。守卫须先于资源初始化；缺实际cookie配置不能凭守卫默认值通过。
4. 撤销/revision不符清对应cookie/session，身份库故障503并不输出正文；仅CSRF合格才查身份；登出框架负责，旧token登录后不能再次提交。
5. 纯框架测试仓储替身不能冒充真实HTTP、cookie创建或真库认证；A02仍必须独立profile启动/实际cookie/真实临时库auth与代理。真实2025资料、查证业务与页面未放行。
6. runner：ControlledPreviewConfigurationTest既有导入case构造process-only凭据DataSource，Unit移除密钥导致失败；因此归入凭据/临时库模式，而不是禁用生产守卫或注入假密码。该类12项配置回归不全称真库查询。其硬编码55435仅构造DataSource，不调用连接；本批真实DB测试仍专用55461随机无卷目标。H01其他旧测试默认目标风险未关闭。

证据：r5-security-red-20261008-01编译失败（test lambda变量），保留且不计RED；有效RED r5-security-red-20261008-03 14项13断言失败/0异常/0跳过。首次GREEN35中v2 14和旧v1 9全部通过，唯旧配置12中1因缺数据库凭据失败，已追读实际源确认分类。最终26类298=223纯单元+63实际真库+12凭据相关配置检查；新v2 14/旧v1 9/旧配置12全部通过；无卷V22连接0容器退出。29runner合同另存计数替身报告。

Scope：无新依赖/迁移、无原库/供应商/浏览器调用，旧controlled配置与yaml、真实模板/20参考/36题/18迁移指纹不变。A01阶段AUTH_FILTER_CHAIN_VERIFIED_NOT_APPLICATION_ASSEMBLED。A02启动/HTTP、A03业务API/限长、真实规则/展示资格、模型语义、报告和用户收益均后续任务。

请按实际用户影响输出Critical/Important/Minor，附file:line、具体触发与最小修复，列Declined to judge及Assessment；只作一次新上下文审查，不向其他任务发消息。
'''
(batch/'review-package.md').write_text(text,encoding='utf-8')
print('Review package: 26 classes / 298 tests')
