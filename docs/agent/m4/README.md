# M4 持续回归与开发基线

目标：把 M3 技术验收变成后续每个 PR 可重复执行的门禁，完成受控本地试用候选、独立审查、CI 及正式 main 的重新构建验收。当前准备候选，不代表正式版本已发布。

## 每个 PR 的必跑检查

`knowledge/scripts/core-regression.json` 是唯一 Maven 选类清单，包含既有学生契约与 M3 新业务回归。`verify_core_regression.py --selector` 输出选类；`--reports` 检查所有类有非空、零失败/错误/跳过的 Surefire 报告，且 M3 固定 21 条要求对应的方法实际执行。参数化用例按方法名前缀识别。清单必须包含矩阵所需类，不能删类后悄悄缩小验收。

CI 的 `trial-contracts` 跑上述 Java、绑定器和门禁负例、前端测试及生产构建，并保存门禁计数摘要。`replay` 使用仓库已有归档证据运行 Grader，不调用模型/数据库。两项都必须成功；重放绿灯不等于新模型质量通过。

本机从仓库根执行，与 CI 同选类：

```powershell
$selector=python knowledge/scripts/verify_core_regression.py --selector
if($LASTEXITCODE -ne 0) { throw 'Invalid selector' }
$evidence=Join-Path $PWD 'knowledge/target/m4-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
& .\mvnw.cmd -B -pl knowledge -am test "-Dtest=$selector" '-Dsurefire.failIfNoSpecifiedTests=false' "-Dzhku.rulecheck.evidenceDir=$evidence"
if($LASTEXITCODE -ne 0) { throw 'Core regression failed' }
python knowledge/scripts/verify_core_regression.py --reports knowledge/target/surefire-reports --summary knowledge/target/m4-core-summary.json
```

门禁只检查指定目录报告；CI 从干净检出开始，本机必须在 Maven 成功后检查本次新报告，不能拿遗留 XML 当作新执行。门禁摘要只记录类名/计数，不复制 XML 系统属性或测试正文。

## 外置资料与真实执行分开验收

3 个依赖真实资料包的测试从 OnlineFactory 核心类迁至 `RuleAgentProbePackageAssetsTest`；完整受控配置正例在 `RuleCheckPreviewAssetsTest`，纯参数边界仍在 PreviewConfiguration 核心类。两者与 `Reviewed2025RuntimeAssetsTest` 要在本机显式提供 `-Dzhku.t06.handbookPdf=<已核对PDF>` 和 evidenceDir 单独运行；缺资料应失败，不以跳过伪装通过。资产正例不进无 PDF 的公共 CI，外置 PDF、私有交换、密钥不上传。

真实模型执行继续要求新包、新授权、固定调用/费用范围；本轮不新增调用。已有真实两轮结论见 [M3 复测](../eval/m3-retest-20261117/RESULT.md)，实际账单仍 UNVERIFIED，不能用估算填实际费用。

## 候选到正式基线

1. 对官方 main 固定 SHA 做完整差异审查，明确依赖、脱敏证据及未验证范围；独立候选分支，PR 指向 main。
2. 独立审查无阻断、远程 `trial-contracts`/`replay` 通过后，才决定合并；PR 头 SHA 有变化须重新核验。
3. 合入后的正式 main 重新构建持久宿主，在专用数据库实例完成登录、任务、必要澄清、续答、原文权限、重启和停止，保存合并 SHA/构建身份/HTTP 回执。
4. 记录实际正式基线和恢复入口；不得把候选上的 M3 HTTP 回执直接标成合入后验收。

公开上线仍 NO_GO：持久学生 HTTP ONLINE、实际账单、整体模型质量、长期稳定性、HTTPS/安全 Cookie、邀请制与隐私准入未完成。本次安装还检测到既有前端依赖漏洞，见候选限制；本轮不混入未经验证的大版本依赖升级。
