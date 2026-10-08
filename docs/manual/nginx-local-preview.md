# ZhkuAgent 本机 Nginx 展示入口

## 当前做到哪里

2026-10-06 已接好 Nginx 容器托管和受控接口代理，删除旧外部站点转发。真实容器测试通过；后端只监听 Windows 的 127.0.0.1 也能从当前 Docker Desktop 访问。

后续P2/P3已接入校园登录、Run、本人历史和重修澄清。推荐使用[完整网页启动器](controlled-campus-web-demo.md)同时准备专用库、账号、真实后端和Nginx；P4人工验收尚未完成。旧通用聊天接口仍返回404。下文25项代理＋5项启动器结果保留为P0历史，不代表整个校园网页验收。

## 本项目只需要理解这四件事

1. **静态托管**：`npm run build` 把 Vue 编译到 `agent/frontend/dist`，Nginx把里面的HTML、JS和CSS提供给浏览器。修改页面后需重新构建，Nginx不会像开发服务一样自动编译。
2. **反向代理**：浏览器请求同一地址下的 `/api/zhku/controlled-preview/v1/...`，Nginx将它转发到校园服务；身份、CSRF、归属、政策和模型仍由Java后端负责。
3. **地址区别**：浏览器访问 `127.0.0.1:8080`；Nginx容器内通过 `host.docker.internal:<实际后端端口>` 访问Windows。容器自己的127.0.0.1不是Windows。当前机器实测可访问只监听回环的服务，不推断其他机器也相同。
4. **路径规则**：`/`及页面路由读取Vue构建产物；受控API前缀转发；其他 `/api` 路径拒绝。缺失JS返回404，不能返回HTML冒充JS。

```text
浏览器 http://127.0.0.1:8080
  ├─ /                        → Nginx读取 dist/index.html
  ├─ /assets/...              → Nginx读取对应JS/CSS
  ├─ /api/zhku/controlled-preview/v1/... → Windows上的受控Java服务
  └─ /api/ai/...、旧匿名/治理路径         → 404，不转发
```

## 启动和停止

先启动**既有受控服务**，确认它的实际HTTP端口；不要把通用Agent服务误当成受控服务。普通Windows PowerShell/CMD可调用包装入口，不依赖用户安装pwsh。

在仓库根目录启动（以下8123只是示例，替换为受控服务实际端口）：

```powershell
.\agent\frontend\start-nginx-preview.cmd -BackendPort 8123
```

启动器默认执行已有前端构建，然后启动固定镜像的Nginx容器，等待页面可读并执行 `nginx -t` 语法检查。浏览器打开 `http://127.0.0.1:8080/`。启动器返回后容器继续运行；不要把返回提示理解为真实后端、登录或模型已验收。

可选参数：

```powershell
# 只检查配置，不构建、不启动容器、数据库或模型
.\agent\frontend\start-nginx-preview.cmd -Action Check -BackendPort 8123

# 已有最新dist时跳过构建；换一个明确的本机展示端口
.\agent\frontend\start-nginx-preview.cmd -BackendPort 8123 -WebPort 8081 -SkipBuild

# 停止并删除本展示项目的Nginx容器与网络；不停止Java或数据库
.\agent\frontend\start-nginx-preview.cmd -Action Stop
```

重复启动会拒绝覆盖现有 `zhku-web-preview` 项目。换后端端口先Stop再Start。Java服务重启端口不变时不用换配置，但旧登录Cookie仍依后端规则失效。端口8080占用时显式换WebPort，不强杀占用者。

## 配置文件分别负责什么

| 文件 | 用途 |
| --- | --- |
| [nginx.conf](../../agent/frontend/nginx.conf) | 实际路径与代理规则，是模板，只有后端端口被替换 |
| [compose.preview.yaml](../../agent/frontend/compose.preview.yaml) | 固定镜像、127.0.0.1端口发布、页面和模板只读挂载、资源上限 |
| [start-nginx-preview.ps1](../../agent/frontend/start-nginx-preview.ps1) | 参数检查、构建、启动、等待页面与关闭 |
| [Dockerfile](../../agent/frontend/Dockerfile) | 原多阶段镜像构建也改用同一模板；本轮没有执行这条完整镜像构建路径 |

Compose入口复用本机镜像，Nginx版本1.29.8，使用镜像摘要固定内容。另一台机器第一次运行可能需下载镜像。后端端口是普通配置，**不要**将数据库密码、模型Key或账号密码作为Nginx环境变量。

API保留完整 `/api` 路径、Cookie、CSRF和状态码；不缓存答案或登录响应，不记录访问URL。浏览器看见页面与API同源，但它不会替代后端身份检查，也不会提供额外用户权限。

## 用现象定位问题

| 现象 | 在本项目中的含义 |
| --- | --- |
| 页面200，API502 | Nginx能提供页面，但未连通指定后端；检查服务、端口和Docker宿主机连通性 |
| 受控接口401 | 请求已经到达后端，缺少或失去有效登录；不是代理故障 |
| 受控接口403 | 可能是CSRF或角色拒绝，按后端错误分类处理；不关闭安全检查 |
| `/api/ai/manus/chat` 返回404 | 本入口明确不代理旧通用Agent；校园页面使用受控前缀 |
| 页面刷新仍正常 | Nginx将页面路由交给Vue入口；接口错误不会走此页面回退 |

## 本轮验证边界

验证使用真实Nginx和本轮Vue构建，后端为只监听127.0.0.1的一次性Node HTTP夹具。检查25项代理行为、5项启动器行为，以及缺端口失败、后端断开502和清理。合成Cookie/CSRF只验证传递，不证明实际账号登录。没有Maven、数据库、模型或真实浏览器校园业务验收。

详细证据、失败与修复：[本轮记录](../evidence/nginx-preview/2026-10-06/README.md)。既有校园终端演示仍见 [人工演示指南](controlled-preview-demo.md)。
