# 仲园 Agent 本机资料治理管理员验收手册

本文用于开发者在本机验证“PDF 资料 → 人工审核 → 事务发布 → 有出处问答”的完整路径。它不是学生端操作说明，也不是正式校务系统部署手册。

## 1. 验收边界

- 仅使用公开资料和你有权处理的本地 PDF。
- 管理服务只监听 `127.0.0.1:8124`，不得配置为局域网或公网服务。
- 未审核的原始 PDF、候选文本和本机文件路径不会进入问答接口。
- 发布只替换相同“资料族 + 知识范围”的已发布版本；发布失败时旧版本应继续可查询。

## 2. 启动前检查

在项目根目录确认：

1. Docker Desktop 已启动。
2. `infra/.env` 已存在，且只在本机保存数据库密码。
3. 数据库容器已就绪：

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
```

4. 执行自动验证；它不会上传、审核或发布真实资料：

```powershell
.\scripts\verify-zhku-local.ps1
```

## 3. 启动本机管理页面

新开一个 PowerShell 窗口，进入项目根目录后读取本机数据库密码并启动专用入口：

```powershell
$zhkuDbPassword = ((Get-Content -LiteralPath 'infra/.env' | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }) -replace '^ZHKU_DB_PASSWORD=', '')
$env:ZHKU_DB_PASSWORD = $zhkuDbPassword
.\mvnw.cmd '-Dspring-boot.run.main-class=com.yupi.yuaiagent.zhku.knowledge.governance.web.ZhkuLocalAdminApplication' spring-boot:run
```

浏览器访问：`http://127.0.0.1:8124/api/zhku/local-admin`。

## 4. 启动本机问答验证页

再新开一个 PowerShell 窗口，用同一份本机数据库密码启动问答专用入口：

```powershell
$zhkuDbPassword = ((Get-Content -LiteralPath 'infra/.env' | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }) -replace '^ZHKU_DB_PASSWORD=', '')
$env:ZHKU_DB_PASSWORD = $zhkuDbPassword
.\mvnw.cmd '-Dspring-boot.run.main-class=com.yupi.yuaiagent.zhku.knowledge.web.ZhkuLocalPreviewApplication' spring-boot:run
```

浏览器访问：`http://127.0.0.1:8123/api/zhku/local-preview`。管理员页第 5 步的“打开问答验证页”会打开这个地址。

## 5. 完整人工验收

1. 在“提交资料”选择可读取的 PDF，填写标题、版本、来源、适用范围、知识范围和审核提示。
2. 提交后，在投稿列表选择该资料，点击“提取候选片段”。
3. 对每条候选片段补齐标题、编辑后正文、资料定位和审核说明：有效内容选择“通过”，无关内容填写原因后选择“丢弃”。
4. 需要拆分一页多条规则时，在“编辑后正文”用单独一行 `---` 分隔两段，再点击“按正文两段拆分”，分别完成审核。
5. 确认没有待处理片段，点击“发布已审核资料”，在“发布前请确认”弹窗中确认操作。
6. 在第 5 步点击“打开问答验证页”，提问与该资料相关的问题；确认回答包含资料标题、页码定位和适用提示。
7. 再询问资料未覆盖的问题；确认系统明确说明证据不足，而不是猜测回答。

## 6. 三分钟演示顺序

1. 打开管理页，指出五步流程和“未审核资料不会进入问答”的边界。
2. 选择一份已有审核草稿，演示提取、审核状态和发布前确认；不要在面试现场处理敏感或未知来源的文件。
3. 发布后打开问答页，用示例问题展示回答、资料标题和页码。
4. 再提问资料未覆盖的问题，展示系统拒绝猜测的行为。
5. 最后说明：当前是仅本机、公开资料范围内的工程演示，不接入成绩、学籍或身份信息。

## 7. 安全排查

| 现象 | 处理方式 |
| --- | --- |
| 页面提示本机资料服务不可用 | 确认启动的是 `ZhkuLocalAdminApplication`，并确认数据库容器状态为 healthy。 |
| 上传被拒绝 | 确认文件扩展名为 `.pdf`、文件非空且可由 PDF 阅读器打开。 |
| 无法发布 | 检查是否仍有待处理候选片段，是否至少存在一条已通过片段，以及通过片段是否填写了标题、正文、页码定位和审核说明。 |
| 发布后问答未命中 | 检查知识范围、候选文本和问题关键词；确认发布成功后再启动或刷新问答预览。 |
| 发布报错 | 不要删除数据库卷或手工修改状态；发布事务失败时旧版应仍可使用，先保留错误现象再排查。 |

## 8. 验收记录建议

每次人工验收记录：资料标题与版本、知识范围、通过/丢弃片段数、一个有出处问题、一个拒答问题、验收日期。不要将原始 PDF、数据库密码或未审核文本写入公开记录。
