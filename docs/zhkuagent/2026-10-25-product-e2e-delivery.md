# ZhkuAgent 会话交付文档(2026-10-25)

本会话横跨三段任务:P2-C 回执闭环 → 产品真实端到端验证 → 一条命令启动收敛(进行中)。本文是完整交接记录:已完成成果、验证证据、提交清单、进行中任务的恢复步骤、未验证事项与环境事实。

## 一、固定决策(后续会话直接沿用)

| 事项 | 决策 |
| --- | --- |
| 唯一日常开发主目录 | `D:\alldesk\Projects\ZhkuAgent\worktrees\integration\mainline`(分支 `integration/zhkuagent-mainline-20261024`) |
| 审核资料根(AssetsRoot) | 默认=当前工作树自身(2026-10-24 迁移后外层 `D:\alldesk\Projects\ZhkuAgent` 只剩残缺 docs 副本,勿用) |
| 手册 PDF 原件 | `worktrees/projects/SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf`;由 `Reviewed2025ControlledCatalogue.handbookPdf()` 自动回溯定位,可用 `zhku.t06.handbookPdf` 属性显式覆盖 |
| 必须保留的目录 | 主检出 `repo/`、`worktrees/projects/ZhkuAgent`、`worktrees/projects/SuperAgent-zhkuagent-mvp`(PDF) |
| Maven | `D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd`(离线 `-o` 可用,本地仓已缓存) |
| 凭据 | `DASHSCOPE_API_KEY`、`ZHKU_DB_PASSWORD` 均在用户级环境变量,启动器自行读取 |

## 二、提交清单(全部在 `integration/zhkuagent-mainline-20261024`)

| SHA | 内容 |
| --- | --- |
| `d045d65` | P2-C:DSH 离线回执接入 java-offline 闭环(默认要求回执,trace-only 须显式选择;v1a=2/v1b=3 计数语义固化) |
| `4128860` | **主链路修复**:核验需求跨度改 excerpt 回显定位(根因:qwen3.8-flash 数 UTF-16 坐标出错导致 REVIEW_SPAN_OUT_OF_RANGE) |
| `1037968` | 两批真实在线验收证据(包、租约、轨迹、退出记录、UI 截图、一次性授权登记) |
| `0c013fa` | 手册 PDF 迁移布局回溯定位 + 回放启动器环境密码路径 + 一条命令演示包装脚本 |
| `b9b3ffe` | 两个启动器 AssetsRoot 默认值改为工作树自身(外层旧目录残缺) |
| `b139c2f` | 包装脚本编码修复(cmd 用英文注释/ps1 加 BOM)+ 裸参数构包证据 + 回放启动失败记录 |

工作树当前干净;运行资源已全部回收(无 java 进程、无 zhku 容器)。

## 三、已验证成果(真实执行,有证据)

### 3.1 产品真实端到端(本会话核心成果,PASS)

两批受控在线租约(qwen3.8-flash + 真实《2025版学生手册》2025.9.2 审核语料 + 真实浏览器 UI):

- **第一批(包 `ec8515be`)**:登录/提问/澄清/检索/原文引用全部真实成功;最终答案被独立发布核验拒绝(`REVIEW_SPAN_OUT_OF_RANGE`),系统按设计拒绝伪造成功。8 次供应商调用,资源全核销。
- **修复 `4128860` 后第二批(包 `4f755c4c`)**:**PASS**。t06-chain-a 登录 → 提问「我这门选修课没过,能不能重修?」→ 追问课程类型 → 选「公共选修课」(PUBLIC) → **RESOLVED/COMPLETE**,结论「公共选修课成绩不合格者不安排重修。」,引用手册第十八条原文(校验和绑定),原文对照面板含 PDF 物理第 77 页/印刷第 65 页定位。截图:`docs/evidence/rule-agent-integration/2026-10-25/online-http-151514-07b2b68a/live-ba5516f6-997f-44f1-a721-3a926dbfb364/student-ui-final-result.png`。

### 3.2 回归测试(本轮实际运行)

- ruleagent 包 261 项:0 失败(其中 3 个 PDF 路径错误经 stash 基线验证为迁移既有问题,后由 `0c013fa` 修复,`RuleAgentOnlineFactoryTest` 现 46/46 通过)。
- P1 隐私/OBS Maven 回归:knowledge 56 项 + agent 11 项全过(上一轮)。
- Node 侧 135 项全过(上一轮)。
- 裸参数构包:`start-rule-agent-http-check.cmd -Action Prepare` 无任何手工参数成功生成包 `ed2a86f5`(证据 `online-http-214811-60a29445/`)。

### 3.3 Worktree 清理:38 → 28

移除 10 个已合并且干净的工作树(分支引用全保留)。保留 4 个已合并但含未提交真实内容的工作树:`codex/rule-agent-integration`(审核资料 JSON 修改)、`codex/s02-eval-adapter`(暂存的 T08EvalAdapter 代码)、`codex/sprint01-question-loop`(.learnings)、`codex/t06-t07-student-http`(证据日志)。

## 四、一条命令学生回放演示(PASS,2026-10-25 修复并验证)

**结论**:一条命令 → 登录 → 提问 → 结论与引用 → 原文对照,全链路真实跑通,零模型外发,到点自动回收资源。

**真实阻断(不是 35 秒预算)**:「宿主35秒内未就绪」只是表象。把宿主 stdout/stderr 落盘后真实报错为
`java.lang.IllegalArgumentException: 受控资料、回放或根目录不合格`
(`RuleCheckPreviewConfiguration.validateEnvironment(RuleCheckPreviewConfiguration.java:83)`),宿主启动 3 秒即退出(已退出=True、退出码=1)。

**根因**:`SealedRuleSelectionReplay` 钉死的封存回放目录 `docs/evidence/rule-check-refactor/2026-10-10/package-01/`(`PACKAGE_SHA=62d759d9…168`、`SUMMARY_SHA=3fb4bca6…092`)在主工作树里缺失——迁移时漏搬了运行时输入。35 秒死线本身够用:宿主实测 7.9 秒就绪。

**修复(仅两处,不重构)**:
1. 补回 `docs/evidence/rule-check-refactor/2026-10-10/package-01/`(54 文件 385665B),逐文件 SHA256 与来源目录一致;不放宽任何指纹校验。
2. `knowledge/scripts/start-controlled-rule-check.ps1`:①宿主 stdout/stderr 落盘(`host-N/host-stdout.log`、`host-stderr.log`)并新增 `host-launch.json`(pid/耗时/是否退出/退出码/是否就绪);②35 秒硬编码改为 `$hostReadySeconds=120`(只用于识别死亡或挂死);③修复到点退出:`[Console]::In.ReadLineAsync()` 在真实控制台窗口里同步阻塞调用线程,`MaxMinutes` 永不生效(实测到点 10 分钟仍在运行),改为 `[Console]::KeyAvailable` 轮询,到点自动 `STOP` 并回收。

**验证记录(全部真实执行)**:

| # | 操作 | 结果 |
|---|------|------|
| 1 | `start-controlled-rule-check.ps1 -Smoke`(加固后首次) | passed:false,`host-1/host-stdout.log` 含上述异常栈;批次 `20261025-224555-6373b6f3` |
| 2 | 补回 package-01 后 `-Smoke` | passed:true,normalHostRuns:2,resourcesClosed:true,providerCalls:0;批次 `20261025-225002-b94b84dc` |
| 3 | `start-controlled-rule-check.cmd -MaxMinutes 1`(验证到点退出) | exit 0,111.5 秒自动退出,passed:true,host-closed.json resourcesClosed:true;批次 `20261025-230606-e6d43e82` |
| 4 | `$env:ZHKU_RULECHECK_STUDENT_PASSWORD='<16-64位>'; knowledge\scripts\start-student-replay-demo.cmd -MaxMinutes 3` | launchExit=0/38 秒;UI 退出码 0/6 秒;后端到点自动退出 passed:true、resourcesClosed:true;批次 `20261025-230818-2aeb3452` |
| 5 | 收尾 `agent\frontend\start-nginx-preview.cmd -Action Stop` | 容器与网络 Removed;无 java 进程;55500/58126/58080 均无监听 |

页面实测(Q17「一学期最多选多少公共选修学分?」):结论标题 **有原文支持**、依据版本 **2025.9.2**、正文「每学期选修公共选修课最高不得超过4个学分。」、引用「第十条　每学期选修公共选修课最高不得超过 4 个学分。」;点「核对原文」后右栏 **原文对照** 显示「本科生学生手册(2025版)·选修课程管理办法」、版本 2025.9.2、`PDF物理第77页/印刷第65页,第10条`、记录页码 77,高亮摘录与引用逐字一致,待核实项两条(物理页/印刷页映射未核实、暂无已验证安全的原件地址)。

**证据**:`docs/evidence/rule-agent-integration/2026-10-25/student-demo-e2e-20261025-230814/`(01-login/02-desk/03-result/04-original.png、`ui-evidence.json`、`demo-run.log`:`passwordLength=28`,`launchExit=0`,`uiExit=0`,无明文密码)。

**复现与收尾**:
```
$env:ZHKU_RULECHECK_STUDENT_PASSWORD='<16-64位>'
knowledge\scripts\start-student-replay-demo.cmd -MaxMinutes 8
# 浏览器 http://127.0.0.1:58080/rules ;账号 controlled-2025-student + 上面密码;提交页面预置问题
agent\frontend\start-nginx-preview.cmd -Action Stop
```
已知噪声(既有设计路径,不影响结论):登录前 `auth/me` 探测返回 401、`favicon` 404。注意:同工作树禁止并行 Maven;`-EntryMode replay` 会重建前端(~40 秒)。

## 五、未验证事项与已知问题(如实,勿当已通过)

1. **6 个数据库初始化错误**:自始至终未验证,未触碰。
2. **在线质量(T08 正式)/T09/T10**:未做,不宣称。回执信任链仍是 SELF_REPORTED;签名控制器仍被 ISOLATION_REQUIRED 阻塞。
3. **单题验证**:真实链路只验证了「选修课重修」一题(两批租约);多题/并发/长会话未验证。
4. **核价窗口**:两批包价格核对 2026-10-25 到期;**下次在线验收前必须按流程重新核价构包**(`Prepare` → 审阅 request-package → 新 SHA 授权)。每批历史上界约 ¥8、≤10 次调用、不可退。
5. 模拟演示启动器(`start-rule-agent-simulated-demo.ps1`)被 `docs/preparation/m1-04a/identity.json` 钉死在历史源码 SHA;本次改动后其 `Assert-Rows` 会拒绝,需重新生成 identity 才能用(未处理)。
6. 提交时 `safeguard-refs.ps1` 钩子报错(脚本在 worktree 内存在、钩子按主检出路径找,找不到):无害,提交均成功,未处理。
7. 4 个含未提交内容的工作树(见 3.3)待人工决定收编或废弃;另有旧 stash `stash@{0}: On zhkuagent-modules: WB-1 状态板纪律纠正登记`(**不要**随手 pop,它属于别的分支)。
8. 外层 `D:\alldesk\Projects\ZhkuAgent\docs` 是迁移残缺副本(缺 verbatim-import-draft.json 等),已从所有默认路径移除依赖,但目录本身未清理。
9. **演示启动器依赖控制台窗口**:早期版本用 `[Console]::In.ReadLineAsync()` 等待回车,在真实控制台里会同步阻塞,`MaxMinutes` 失效(首轮演示批次 `20261025-225214-19c896d3` 因此卡到人工终止,该批次无 `results.json`,保留作缺陷证据)。现已改为按键轮询:到点自动退出;仅在 stdin 被重定向时按键提前退出不可用(仍按 `MaxMinutes` 结束)。
10. **失败批次的 `resourcesClosed` 是误标**:`finally` 里只要宿主对象还在就把 `resourcesClosed` 置 `false`,即使容器已回收(如 `20261025-224555-6373b6f3`)。成功批次不受影响(`StopHost` 已清空对象)。本轮未改,避免扩大范围。

## 六、关键路径速查

| 用途 | 路径 |
| --- | --- |
| 在线验收流程 | `knowledge/scripts/start-rule-agent-http-check.cmd -Action Prepare/CheckPackage/StartApproved`(StartApproved 需 env `ZHKU_HTTP_APPROVED_PACKAGE_SHA256=<包SHA>`) |
| 一条命令演示(PASS) | `knowledge/scripts/start-student-replay-demo.cmd -MaxMinutes <1-15>`(后端独立窗口,到点自动退出) |
| 回放后端 | `knowledge/scripts/start-controlled-rule-check.cmd`(密码:交互或 env `ZHKU_RULECHECK_STUDENT_PASSWORD`) |
| 前端 | `agent/frontend/start-nginx-preview.cmd -BackendPort <端口> [-WebPort 58080] [-EntryMode replay]` |
| 在线证据 | `docs/evidence/rule-agent-integration/2026-10-25/`(两批租约 + 裸参数包) |
| 回放演示证据 | `docs/evidence/rule-agent-integration/2026-10-25/student-demo-e2e-20261025-230814/`(四张截图 + `ui-evidence.json` + `demo-run.log`) |
| 回放封存输入 | `docs/evidence/rule-check-refactor/2026-10-10/package-01/`(缺它宿主 3 秒即退出) |
| 学生页 API 前缀 | `/api/zhku/controlled-preview/v2`;登录账号合成:`t06-chain-a`(在线租约)/`controlled-2025-student`(回放),密码均为启动时设定 |
| 前端 node_modules | 已 `npm ci` 安装(含 esbuild 手动 postinstall),dist-student/dist 已构建 |

## 七、原则备忘(用户本轮确立)

- 每轮必须产出可运行能力或可验证产品结果,不产出下一轮计划文档。
- 工程治理服务于产品交付,治理本身不再扩张。
- 真实外发(模型调用)必须走审包-授权流程;失败不重试不伪造;证据如实记录。
