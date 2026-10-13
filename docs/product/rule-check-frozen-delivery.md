# 2025规则查证受控版本：冻结交付索引

已收口冻结：`rule-check-2025-rc1-20261012`。项目版本仍为`0.1.0-rc.1`，候选标识不是Git标签。

本次冻结范围是已审核2025版资料、受控本机、封存模型响应回放。40题内容此前已确认；新增E09与工程E11由用户在已呈现建议后指示“开始吧”，按接受建议并执行收口补录，非独立逐题复审。

| 验收 | 最终记录 |
| --- | --- |
| 旧16题／另24题 | 16/16、24/24通过；原8项失败完成、原7项通过保留；关键错误0 |
| 工程检查 | 136项相关后端、41项前端测试，构建和普通用户终端两次正常启动/退出通过 |
| 实际页面 | 完整40题、54次原文点击；N45澄清续答及N47对象拒答原文路径完成 |
| 接线问题 | P01混合缓存准入、P02澄清/拒答原文凭据已关闭 |
| 本轮收口补录 | 模型调用0、数据库操作0、构建0、源码修改0 |

## 交付与证据入口

- 修复报告：[接线修复与工程冻结](rule-check-wiring-repair-results.md)。
- 最终评估：[完整合并评分](../evidence/rule-check-wiring-repair/2026-10-12/closeout/combined-final-assessment.json)。
- 最终门槛：[受控范围验收结论](../evidence/rule-check-wiring-repair/2026-10-12/closeout/release-gate-final.json)。
- 当前版本身份：[冻结清单](../evidence/rule-check-wiring-repair/2026-10-12/freeze-manifest.json)。689项源码/产物指纹不变，仅补录状态。
- 可恢复源码及构建快照：[冻结快照](../evidence/rule-check-wiring-repair/2026-10-12-rulecheck-rc1-source-build-snapshot.zip)。
- 开发证据：[接线修复证据包](../evidence/rule-check-wiring-repair/2026-10-12-wiring-repair-evidence.zip)。原失败、超时、编码边界及修复记录保留。
- 用户决定原文：[收口决定](../evidence/rule-check-wiring-repair/2026-10-12/closeout/user-closeout-decision.json)。

早期快照/证据包中的“待确认”反映归档当时状态，保持原样；最终状态以当前清单及`closeout/`补录为准。快照不是完整的跨机器部署包：正式回放仍依赖仓库内审核/原始响应材料，以及本机相邻项目中指纹匹配的2025版学生手册PDF。没有公开整本PDF，也没有伪造40次新增调用授权。

## 本机启动

在项目根目录的普通终端执行：

```cmd
knowledge\scripts\start-controlled-rule-check.cmd -TaskPlanReplay -CheckEnvironment
knowledge\scripts\start-controlled-rule-check.cmd -TaskPlanReplay -Smoke -MaxMinutes 2
knowledge\scripts\start-controlled-rule-check.cmd -TaskPlanReplay -MaxMinutes 5
```

检查模式不连接数据库/模型。Smoke自动检查临时账号、原文和退出；人工模式按提示设置本次临时密码。后端默认端口58126，前端同源代理需指向该端口；包装器不会关闭Codex或浏览器。受控回放入口不会自动把新问题发送给模型。

### 输入临时密码后，打开网页

第三条命令启动的是后端。等它打印接口地址后，保持该终端运行，暂时不要按回车；回车或运行时限到达会关闭本次临时库和后端。

另开一个终端，进入同一项目根目录，启动已构建的网页并连接该后端：

```cmd
agent\frontend\start-nginx-preview.cmd -Action Start -BackendPort 58126 -WebPort 58581 -ProjectName zhku-rulecheck-final-demo -SkipBuild
```

看到`Nginx page ready`后，浏览器打开`http://127.0.0.1:58581/rules`。账号为`controlled-2025-student`，密码为第三条命令中自己刚设置的临时密码。

登录后可直接试用已覆盖的问题，例如：`以2025版手册历史文本为依据：正好缺课30%就能据这条确认不能考试吗？`。点击“查证规则”，查看条件解释，再点击“核对原文”。回放入口对未封存的任意新提问不保证回答能力。

若后端已经超时退出，可将人工启动时限改为`-MaxMinutes 15`重新启动；新临时库会清除原登录状态，需用本次设置的密码重新登录。

演示结束后，在后端终端按回车正常退出，再关闭本次网页代理：

```cmd
agent\frontend\start-nginx-preview.cmd -Action Stop -ProjectName zhku-rulecheck-final-demo
```

该停止动作只针对本次命名的网页代理，不关闭Codex或浏览器。已有同名代理时先核对其归属，启动器会拒绝覆盖它。

### Docker Desktop未运行时

若出现`dockerDesktopLinuxEngine`命名管道不存在，先启动Docker Desktop并等待引擎就绪。本机已核对支持以下命令：

```cmd
docker desktop start --timeout 60
docker info --format "{{.OSType}}"
```

第二条输出`linux`后再运行演示启动命令。网页和后端分别启动：只有网页就绪仍不能登录查证，后端运行时限到达后需要重新启动人工模式。恢复时不必重跑环境检查和Smoke，不需要重置Docker或修改项目代码。

## 验证范围与停止边界

问题均已曝光；候选模型响应为4条新增、36条同结构缓存。此结果不证明任意问题泛化、当前政策有效性、真实学生节省时间或线上发布资格；G2未确认、E12未评估。这些属于后续独立验证范围，不作为本次冻结新增开发任务。

本次收口剩余任务为0。只有出现阻断性缺陷或用户明确开启新范围，才恢复代码变更；不自动扩大资料、测试集或模型调用。
