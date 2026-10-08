from pathlib import Path
import hashlib,json,difflib,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
log=(batch/'final-green-02.log').read_text(encoding='utf-8-sig')
for key,count in [('tests',78),('pass',78),('fail',0),('cancelled',0),('skipped',0)]:assert re.search(rf'^# {key} {count}$',log,re.M)
assert 'RuleCheckView-' in (batch/'final-build-02.log').read_text(encoding='utf-8-sig')
bundle={str(p.relative_to(root)).replace('\\','/'):sha(p) for p in (root/'agent/frontend/dist').rglob('*') if p.is_file()}
(batch/'source-review.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8');(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8')
(batch/'build-manifest-review.json').write_text(json.dumps(bundle,indent=2)+'\n',encoding='utf-8')
(batch/'review-package.md').write_text('''# Task3B / Vue查证页面和首页入口增量审查

只读10项before/*.bin→当前（source-before/source-review/changes-review.diff）和依赖API/Workflow；不写、不跑测试/DB/模型/网络、不派agent/发其他thread。规范task3b-brief/计划Task3B/账本Rulings。旧v1/API/Java/真实DRAFT/20参考/36候选保护不变，巨大dirty其余不审。

新增/rules独立v2登录+问题五状态+必要一个allowed条件+明确条件/范围/未知/冲突两侧+当前原文旁栏；复用Task3A API/state和PasswordInput。业务状态独立pending，资料全部插值转义，无v-html；metadata分页只记录页码并展示unknown，原文窗口按context相对跨度标mark；safeSourceUrl仅HTTPS无userinfo/非443，一层客户端安全校验不假官网认可（server默认hosts空）。无真实policy示例/资格或办理承诺；首轮2025依据，现行适用未知。首页主CTA/rules和品牌/metadata，/campus/run /super-agent实验URL保留，v1身份独立不授v2资格。新body规则class独立解除Vite居中/宽度限制，路由离开class移除，不改旧校园CSS。

Login串行、密码参数传递后清页面，不储存；身份epoch同步清输入，Workflow立即清内容，编辑问题reset上轮，onUnmountdispose。当前actor role非USER没有textarea。登录NETWORK/BAD结果未知设置LOGIN_UNCONFIRMED禁再登录按钮，只读me确认后恢复或401清主体；不能从丢响应推断未登录。Logout未知fixed文案+重试退出，仍不宣称跨浏览器Cookie/服务器网络交付顺序原子保证。onMounted me新读，不假router旧v1 actor。独立身份检测pending、查证pending、五业务状态。

有效SFC编译SSR RED8/8→全Node77，新增LOGIN_UNCONFIRMED SSR有效RED9/1→最终78=旧69+新增SSR9，0fail/cancel/skip；实际新页面bundle约20KB，build日志/manifest hash。SSR实际vue-router memory+Vue server render，runtime通过provide仅渲染状态替身，不运行me/login/POST/点击/watch/浏览器Cookie；生命周期/网络行为由Task3A独立Node和后Task3C真实浏览器验证。SSR转义原文/政策及javascript不出href，five states/必要选项/原文relative highlight/页码未知/当前维护者/登录未知字段检查，不将其称真实UI交互。生产未provide测试runtime，只v2server结果。Nginx尚只v1，/rules登录目前未代理连通；真实CLOSED/DRAFT/G2不变。

重点审查可见文案/结构与状态一致、因await/身份变化造成新登录内容泄露或按钮失效、必要delta与引用转义、实际Vue模板/错误/未知/声明条件、路径兼容/bodyCSS、登录/退出/页面销毁逻辑；SSR测试判断范围。返回Strengths/Critical/Important/Minor（绝对file:line/触发/最小修复）/Declined to judge/Assessment；一次fresh review，重要修复RED→GREEN后不重审。
''',encoding='utf-8')
print('Task3B review: 10 files / Node78 including9 real SFC SSR / new page bundled; browser pending')
