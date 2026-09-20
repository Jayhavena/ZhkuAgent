# 仲园 Agent 本机验收问题复盘

> 记录范围：本机 HTTP 问答预览接口从启动到人工验收期间发现的问题。本文不记录数据库密码、模型密钥或其他敏感配置。

## 一、结论

本次问题主要不是问答规则错误，而是“独立预览边界、配置传递和客户端显示”三个工程边界没有被显式说明。解决后，开发者可在不配置模型密钥的前提下启动本机接口，并正确看到带出处的中文回答。

## 二、问题与解决方案

| 问题 | 根因 | 解决方案 | 如何避免再次发生 |
| --- | --- | --- | --- |
| 普通主应用启动时报 DashScope 密钥缺失 | 主应用扫描通用 Agent，`YuManusFactory` 会在启动期依赖模型 | 新增 `ZhkuLocalPreviewApplication`，只扫描仲园预览组件，并排除 DashScope 与 Ollama 的模型自动配置 | 本机问答预览必须使用专用启动入口，不用普通主应用启动。 |
| 数据库验收测试提示没有密码 | 部分既有测试只读取 Java 系统属性 `zhku.db.password`，而预览接口还支持环境变量 `ZHKU_DB_PASSWORD` | 启动服务时设置环境变量；运行真实数据库测试时临时传入 Java 系统属性 | 手册明确区分两种命令，不把密码写进源码、文档或 Git。 |
| PowerShell 中中文显示为 `å­¦...` | 接口返回 `application/json` 但未声明 UTF-8，Windows PowerShell 对响应进行了错误解码 | 控制器对成功、400 与 503 响应统一声明 `application/json;charset=UTF-8`，并新增响应头契约测试 | 面向多客户端的中文 JSON 接口必须把字符集作为可测试契约。 |
| PowerShell 表格中回答、出处和提示显示不全 | `Invoke-RestMethod` 默认使用表格展示，列宽有限，嵌套出处对象会折叠 | 保存响应后使用 `Format-List *`、直接访问字段，或使用 `ConvertTo-Json -Depth 5` | 验收手册不以默认表格输出作为数据完整性判断。 |
| 找不到 `mvnw.cmd` | 在 `C:\Users\nangua` 执行命令，且将相对路径写成 `./mvnw\.cmd` | 先进入独立工作区，再使用 PowerShell 写法 `.\mvnw.cmd` | 所有手册命令均从 `Set-Location` 开始，并使用 `.\mvnw.cmd`。 |

## 三、关键操作对照

### 启动专用预览服务

```powershell
Set-Location D:\alldesk\Projects\SuperAgent-zhkuagent-mvp
$zhkuDbPassword = ((Get-Content -LiteralPath 'infra/.env' | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }) -replace '^ZHKU_DB_PASSWORD=', '')
$env:ZHKU_DB_PASSWORD = $zhkuDbPassword
.\mvnw.cmd '-Dspring-boot.run.main-class=com.zhku.knowledge.web.ZhkuLocalPreviewApplication' spring-boot:run
```

### 完整查看接口响应

```powershell
$response = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8123/api/zhku/local-preview/answers' -ContentType 'application/json; charset=utf-8' -Body $body
$response | ConvertTo-Json -Depth 5
```

## 四、可验证证据

本次修复后已验证：

1. 专用预览上下文不创建通用 Agent 或聊天模型；
2. 接口契约测试覆盖成功回答、出处、资料不足、输入校验、数据库不可用和 UTF-8 响应头；
3. 本机服务监听 `127.0.0.1:8123`；
4. 已覆盖问题返回 `GROUNDED` 与页码出处；
5. 范围外问题返回 `INSUFFICIENT_EVIDENCE`；
6. PowerShell 可正确显示中文响应。

## 五、后续约束

1. 新增资料时，仍必须经过审核、适用范围确认和发布边界检查；
2. 在引入学生端页面或公网部署前，不得把本机预览接口当作公开接口；
3. 新的 HTTP 接口测试应同时覆盖业务结果、引用边界与中文响应编码；
4. 通用 Agent 的模型依赖与仲园 Agent 的本机验证应持续隔离。
