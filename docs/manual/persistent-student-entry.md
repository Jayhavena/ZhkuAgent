# 持久学生入口：本地零模型运行手册

此入口使用正式 persistent.jar，持久账号、学生 Task API、真实审核资料工具与原文接口在同一宿主中装配，不依赖 JUnit、测试源码或 900 秒浏览器租约。宿主等待显式 Stop/EOF；任务仍有 15 分钟 TTL，澄清等待有独立期限。尚未做 24 小时稳定性测试，也不是公网生产发布。

## 首次启动

需要本机 Docker、Java 21、Node/npm、离线 Maven 缓存以及已经核对的审核资料/PDF。账号库是每个 InstanceName 独享的命名卷，规则库每次使用新的临时容器；不写共享演示库。执行前按 Process→User→Machine 检查 ZHKU_DB_PASSWORD 可读性，启动器仍为隔离库使用本实例独立随机口令。

在仓库根的普通 Windows PowerShell/CMD 中：

```powershell
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly
.\knowledge\scripts\start-persistent-rule-check.cmd -InstanceName student-local -IdentityPort 55460 -RulePort 55461 -BackendPort 58150 -AssetsRoot '<已核对的审核资料绝对根>' -HandbookPdf '<已核对的原PDF绝对路径>'
.\agent\frontend\start-nginx-preview.cmd -BackendPort 58150 -WebPort 58155 -ProjectName zhku-student-local -EntryMode student
```

打开 `http://127.0.0.1:58155/rules`，在注册页创建本地账号再登录。使用页面展示的固定问题“我这门选修课没过，能不能重修？”，选择公共选修课或专业选修课，确认并继续，展开依据/原文。固定动作与独立核验均为模拟；检索、原文、身份、状态机、审核资格和发布门禁是真实实现。未覆盖输入返回 `REPLAY_NOT_COVERED`，不会伪造自然语言模型回答。

页面模式由登录后的 `GET /api/zhku/controlled-preview/v2/tasks/runtime` 声明；SIMULATED 显示覆盖范围，结果元数据为 ENGINEERING_REPLAY、modelAttemptCount=0、providerResponseReceived=false。启动器 ready.json 同时记录模式与 onlineEnabled=false，不记录密钥。

首次构建后，源码未变化时可使用 `-SkipBuild`。新鲜度检查失败时先构建，不能跳过检查强用旧包。指定同一个 InstanceName 的后续启动会复用已验证的 AssetsRoot/HandbookPdf 状态；每次仍检查实际字节、分享资格和原件指纹，不把保存路径当成永久授权。

## 重启与停止

保持同一组实例名和端口。Nginx 名称和 InstanceName 是各自的资源边界，不要混用别的试用实例。

```powershell
.\knowledge\scripts\start-persistent-rule-check.cmd -Action Restart -SkipBuild -InstanceName student-local -IdentityPort 55460 -RulePort 55461 -BackendPort 58150
.\agent\frontend\start-nginx-preview.cmd -Action Stop -ProjectName zhku-student-local
.\knowledge\scripts\start-persistent-rule-check.cmd -Action Stop -InstanceName student-local -IdentityPort 55460 -RulePort 55461 -BackendPort 58150
```

重启保留账号与身份库，旧服务器会话/任务失效，重新登录后旧任务应为 404/409，页面要求新建任务，不自动重放 POST。任务按账号+会话隔离，不支持跨设备或跨重启历史恢复。Stop 先禁止新任务、取消并等待工作线程退出，再卸载资料/身份资源；监督进程落 stopped.json。规则临时容器删除、身份容器停止、命名卷保留。不要删除卷、重置密码或运行全局 Docker 清理。

## 在线入口默认关闭

默认 `ExecutionMode=SIMULATED`，子进程会移除供应商密钥、在线模式和旧回放批次变量；仅设置环境中的 ONLINE 不会打开真实调用。

本次未授权、未运行真实模型。将来在独立确认外发范围和费用后，必须同时显式选择 `-ExecutionMode ONLINE`，并通过环境提供 `DASHSCOPE_API_KEY`、`ZHKU_RULE_AGENT_AUTHORIZATION_ID`、`ZHKU_RULE_AGENT_CALL_ALLOWANCE`。密钥按 Process→User→Machine 重新解析，不写入参数或实例状态；额度必须为 1–24，作用于整个宿主、包括澄清与独立核验，不随任务重置。缺配置、未知模式、多工厂、混合模拟/在线都应启动失败，不自动回退。

当前在线工厂固定使用仓库已定义的端点和模型；仅提供装配入口，不代表当前候选的真实模型质量已验证。重启在线宿主意味着新运行批次，不能拿旧授权无限续费；实际批次授权、账单核验和持久费用账本仍属于后续在线准入。用量日志只含白名单阶段/状态/Token 数，不保存原始请求、响应或学生正文。

## 可重复 HTTP 验收

仅在新建的专用测试实例注册脚本内合成账号。脚本不用于真实学生账号，不读取供应商密钥；输出排除口令、Cookie、CSRF 和条文正文。

```powershell
$env:ZHKU_STUDENT_TEST_BASE_URL='http://127.0.0.1:58150'
$env:ZHKU_STUDENT_TEST_OUTPUT=Join-Path (Get-Location) 'tmp/student-http'
node agent/frontend/tests/manual/persistent-student-http.mjs initial
# 重启同一实例后：
node agent/frontend/tests/manual/persistent-student-http.mjs restart
```

覆盖匿名拒绝、注册登录、模式与任务能力、CSRF、创建/续答幂等、真实必要澄清、原文、跨账号隔离、零模型结果、未覆盖问题、取消/终态竞态、退出及重启边界。取消已受理则必须 STOPPED/CANCELLED；工作已完成则允许明确 409，不伪称取消成功。

## 故障定位

- 403：检查是否登录、CSRF 和角色；新的持久入口必须能取得 `/tasks/capabilities` 的 200，不能把注册成功当作任务链已接通。
- ONLINE 缺授权/额度：启动前失败是预期保护；不要改成自动模拟回退。
- 原件/资格失败：立即停止，核对实际 AssetsRoot、PDF 与审核文件，禁止放宽指纹。
- 端口占用：换专用端口，不停止他人的实例让位。
- 账号卷与凭据状态不符：保留卷及 tmp 状态，核对实例身份，不删除重建。
- 日志：`tmp/rule-check-identity-<InstanceName>/host-out.log`、`host-err.log`、`ready.json`、`stopped.json`。state.json 含本机数据库口令，禁止提交或分享。

验收只支持本地受控工程试用。HTTPS、邀请制、真实模型质量/账单、隐私与生产运维准入仍未完成。
