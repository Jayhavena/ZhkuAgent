from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
before=json.loads((batch/'source-before.json').read_text(encoding='utf-8-sig'))
pattern=re.compile(r'System\.getProperty\(\s*"zhku\.db\.url"\s*,\s*"jdbc:postgresql://127\.0\.0\.1:54329/zhkuagent"\s*\)')
count=0
for path in before['legacyEntrypoints']:
 p=root/path;data=p.read_bytes();text=data.decode('utf-8')
 changed,n=pattern.subn('com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl()',text);assert n==1,path
 changed=changed.replace('该测试在本机开发数据库执行版本化结构迁移','该测试仅在显式专用临时数据库执行版本化结构迁移')
 changed=changed.replace('允许命令行覆盖连接信息；默认值只服务于本机 Docker 开发环境。','显式临时库目标在字段初始化时验证，禁止连接、迁移或清理原演示库。')
 p.write_bytes(changed.encode('utf-8'));count+=n
assert count==32
print('Legacy defaults replaced: '+str(count))
