from pathlib import Path
import hashlib,json,difflib
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
before=json.loads((batch/'source-before.json').read_text(encoding='utf-8-sig'));rows=[];diff=[]
assert 'actual: 404' in (batch/'red.log').read_text(encoding='utf-8-sig')
assert 'checks=5 passed=true' in (batch/'green.log').read_text(encoding='utf-8-sig')
for row in before['files']:
 p=root/row['path'];rows.append(dict(path=row['path'],sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
 diff.extend(difflib.unified_diff((batch/row['before']).read_text(encoding='utf-8-sig').splitlines(True),p.read_text(encoding='utf-8-sig').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
(batch/'source-review.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8');(batch/'changes-review.diff').write_text(''.join(diff),encoding='utf-8')
(batch/'review-package.md').write_text('''# Task3C1 Nginx v2转发增量

只读2项before→current nginx.conf/start-nginx-preview.ps1及相关compose，禁止修改、执行测试/容器/网络/DB/模型/派agent/发其他thread。既有巨大dirty其他不审。沿已授权Task3C代理边界；仍loopback网页127.0.0.1，仅现有官方固定digest Nginx模板机制，无新镜像/依赖/DB/provider凭据。原v1段字节不变，新增v2尾斜杠prefix上游同一个显式BackendPort，保留完整URI，bare v2精确404避免301；匿名/管理/其他api拒绝。未扩大后端授权，v2Java仍拒绝旧v1，不称双后端同时可用。api不缓存、不拦截固定错误，proxy_next_upstream off不重放POST，accesslog off。启动文案改为所选后端决定能力，不声称已装配所有功能。

实际Nginx→127.0.0.1 Node测试夹具，RED v2上游预期403却404，修改后5项GREEN：v2状态/URI/body/no-store、v1 URI兼容、bare及其他namespace404不进upstream、POST计数1、SPA与缺资源404。Docker host.docker.internal实际可接宿主机loopback夹具；非Java/数据库/浏览器/真实身份链。专用项目58480和Node58184均结束，监听0。日志/脚本保留。不要把单次403请求计数泛化所有断连情况自动重试证明；配置off及本场景分别证据。

重点代理范围/原v1兼容/权限与身份隔离/URI/缓存及重试/源日志泄露/cleanup/证据范围。返回Strengths/Critical/Important/Minor(绝对path:line/trigger/minimal fix)/Declined/Assessment；一次重要修复、不二次审查。
''',encoding='utf-8')
print('Proxy review packaged: 2 files / 5 actual Nginx-to-Node checks, not full chain')
