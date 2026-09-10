# 仲园 Agent 本机 HTTP 问答接口开发者验收手册

> 适用范围：仲园 Agent 当前的本机开发预览。它只验证“已审核资料—检索—回答—出处”的最小闭环，不面向学生、局域网或公网提供服务。

## 一、验收目标

完成本手册后，应能确认以下事实：

1. 服务只监听本机地址 `127.0.0.1:8123`；
2. 已覆盖的公共选修课程问题会返回回答和原始页码出处；
3. 当前资料范围外的问题会明确返回 `INSUFFICIENT_EVIDENCE`，不会猜测回答；
4. 预览服务不需要 DashScope、Ollama 或其他模型密钥；
5. 当前资料仍只用于本地验证，不因接口存在而变成学生可见资料。

## 二、前置条件

在开始前确认：

1. Docker Desktop 已启动；
2. 本机数据库容器已启动；
3. 已位于独立工作区：`D:\alldesk\Projects\SuperAgent-zhkuagent-mvp`；
4. `infra\.env` 中存在 `ZHKU_DB_PASSWORD`，且该文件没有提交到 Git；
5. 公共选修课程候选资料已经审核并导入本地数据库。

先打开 PowerShell，并执行：

```powershell
Set-Location D:\alldesk\Projects\SuperAgent-zhkuagent-mvp
docker compose --env-file infra/.env -f infra/docker-compose.yml ps
```

预期看到 PostgreSQL 容器处于运行状态。若未运行，执行：

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
```

## 三、启动本机预览服务

在当前 PowerShell 中执行以下命令。命令从已忽略的 `infra\.env` 读取密码，但不会把密码显示在终端或写入代码：

```powershell
$zhkuDbPassword = ((Get-Content -LiteralPath 'infra/.env' | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }) -replace '^ZHKU_DB_PASSWORD=', '')
$env:ZHKU_DB_PASSWORD = $zhkuDbPassword
.\mvnw.cmd '-Dspring-boot.run.main-class=com.yupi.yuaiagent.zhku.knowledge.web.ZhkuLocalPreviewApplication' spring-boot:run
```

等待终端出现类似信息：

```text
Tomcat started on port 8123
Started ZhkuLocalPreviewApplication
```

该命令必须使用 `ZhkuLocalPreviewApplication`，不能使用普通的 `YuAiAgentApplication`。专用入口会：

- 自动启用 `zhku-local-preview`；
- 将服务绑定到 `127.0.0.1`；
- 只扫描仲园 Agent 的预览组件；
- 禁用项目中与本次验证无关的 DashScope、Ollama 模型自动配置。

服务运行期间不要关闭这个 PowerShell。

## 四、检查本机监听边界

另开一个 PowerShell 窗口，执行：

```powershell
Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8123 -State Listen
```

预期结果：存在一条监听记录，且本地地址为 `127.0.0.1`。若地址是 `0.0.0.0`、局域网地址或没有记录，应停止服务并检查是否误用了启动命令。

## 五、执行问答验收

所有请求均使用：

```text
POST http://127.0.0.1:8123/api/zhku/local-preview/answers
```

### 5.1 已覆盖问题：必须有出处

```powershell
$body = @{ question = '一学期最多能选多少公共选修课学分？' } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8123/api/zhku/local-preview/answers' -ContentType 'application/json; charset=utf-8' -Body $body
```

预期结果包含：

```text
mode      : GROUNDED
answer    : 每学期公共选修课最高不得超过 4 学分。
citations : 至少 1 条，包含“PDF 第 76—77 页，第 8、10 条”和页码 76
notice    : 仅供本机开发验证，不构成面向学生的正式校务依据。
```

### 5.2 范围外问题：必须拒绝猜测

```powershell
$body = @{ question = '奖学金申请截止日期是什么时候？' } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8123/api/zhku/local-preview/answers' -ContentType 'application/json; charset=utf-8' -Body $body
```

预期结果：

```text
mode      : INSUFFICIENT_EVIDENCE
citations : 空列表
```

这是正确行为：当前首批资料只覆盖公共选修课程，不能把未知校务问题伪装成已知答案。

### 5.3 非法输入：必须返回 400

Windows PowerShell 对非 2xx 响应会抛出异常，因此使用 `curl.exe` 查看响应：

```powershell
curl.exe -i -X POST 'http://127.0.0.1:8123/api/zhku/local-preview/answers' -H 'Content-Type: application/json' -d '{"question":"   "}'
```

预期结果：HTTP 状态为 `400`，响应中包含：

```json
{"code":"INVALID_QUESTION","message":"问题不能为空"}
```

## 六、自动化验证

关闭服务前或后，均可执行本次功能的自动化测试：

```powershell
.\mvnw.cmd '-Dtest=ZhkuLocalPreviewControllerTest,ZhkuLocalPreviewConfigurationTest,ZhkuLocalPreviewApplicationTest' test
```

预期结果：`Tests run: 8, Failures: 0, Errors: 0`。

如果还要验证真实数据库发布器，可临时以 Java 系统属性传入密码：

```powershell
$zhkuDbPassword = ((Get-Content -LiteralPath 'infra/.env' | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }) -replace '^ZHKU_DB_PASSWORD=', '')
.\mvnw.cmd "-Dzhku.db.password=$zhkuDbPassword" '-Dtest=ReviewedKnowledgePublisherTest' test
```

当前不把整个仓库的 `.\mvnw.cmd test` 作为本机问答接口的验收门槛：仓库中仍有通用 Agent 的应用上下文测试，需要单独的模型和其他外部环境配置。这些问题不应通过给仲园 Agent 预览接口添加模型依赖来掩盖。

## 七、停止服务

回到运行服务的 PowerShell，按：

```text
Ctrl + C
```

然后确认端口已释放：

```powershell
Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8123 -State Listen -ErrorAction SilentlyContinue
```

没有输出即表示服务已停止。

## 八、常见故障排查

| 现象 | 原因 | 处理方式 |
| --- | --- | --- |
| 启动时报“缺少数据库配置” | 当前终端没有 `ZHKU_DB_PASSWORD` | 按第三节先从 `infra\.env` 读取并设置环境变量，再启动服务。 |
| 启动时报 DashScope 密钥缺失 | 启动了普通主应用，而不是专用入口 | 确认命令含 `ZhkuLocalPreviewApplication`。本机预览不需要模型密钥。 |
| 请求返回 503 | 数据库容器停止、密码变更，或数据库暂时不可用 | 检查 Docker Desktop、容器状态和 `infra\.env` 中的当前密码。 |
| 请求返回 404 | 服务未启动，或启动的不是专用入口 | 检查启动日志中的应用名称应为 `ZhkuLocalPreviewApplication`。 |
| 请求返回 `INSUFFICIENT_EVIDENCE` | 问题不属于当前公共选修课程资料范围 | 换用已覆盖问题，或走“资料审核—发布”流程扩充知识库。 |
| 8123 端口已占用 | 旧服务未停止或其他程序占用 | 先检查监听进程，确认后停止该进程，再重新启动。 |

## 九、验收记录模板

每次手工验收可记录：

```text
验收日期：
执行人：
数据库容器状态：正常 / 异常
本机监听地址：
有出处回答：通过 / 不通过
范围外拒答：通过 / 不通过
非法输入校验：通过 / 不通过
自动化测试：通过 / 不通过
发现的问题：
```
