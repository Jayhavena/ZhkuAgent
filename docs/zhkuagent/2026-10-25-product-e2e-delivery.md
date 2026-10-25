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

## 四、进行中任务:一条命令学生回放演示(恢复指南)

**目标**:从主目录一条命令启动零模型成本的完整演示(登录→提问→引用),已完成 80%,卡在回放后端就绪。

**已完成**:
1. `knowledge/scripts/start-student-replay-demo.cmd/.ps1`(包装:后端独立窗口→等端口→nginx 回放前端→开浏览器;支持 `ZHKU_RULECHECK_STUDENT_PASSWORD` 环境密码免交互)。
2. 包装脚本编码问题已修(cmd 英文注释、ps1 BOM)。

**当前阻断**:`start-controlled-rule-check.ps1` 的回放后端报「宿主35秒内未就绪」。两次失败证据在 `docs/evidence/rule-check-refactor/2026-10-10/package-02/startup/20261025-*/`(build 成功、容器已起、host-N 目录为空)。

**下一步(按序)**:
1. 先判别「宿主秒挂」还是「35 秒预算太紧」:在 `start-controlled-rule-check.ps1` 的 StartHost 里把宿主 stdout/stderr 落盘到 `$runDirectory`(现在只在内存 `$hostOut/$hostErr`,失败也不写),重跑一次拿真实报错。临时库脚本位置:第 95-118 行 StartHost、第 113 行 35 秒死线。
2. 若是冷启动超预算:把 35 秒放宽(如 120 秒)——该常量只为识别宿主死亡,不应紧绷启动时间;顺带把宿主输出落盘补上。
3. 若是秒挂:按报错修(注意宿主是 `com.zhku.knowledge.rulecheck.web.Controlled2025RuleCheckHost`,jar 为 `knowledge-0.1.0-rc.1-controlled.jar`,env:`ZHKU_RULECHECK_ROOT/PORT/EVIDENCE/STUDENT_PASSWORD`)。
4. 修好后完整 E2E:`export ZHKU_RULECHECK_STUDENT_PASSWORD='<16-64位>'` → `knowledge\scripts\start-student-replay-demo.cmd -MaxMinutes 8` → 浏览器 `http://127.0.0.1:58080/rules` 用 `controlled-2025-student` + 该密码登录 → 页面预置问题提问 → 引用展示 → 截图留证 → 收尾(后端窗口退出、`agent\frontend\start-nginx-preview.cmd -Action Stop`)。
5. 注意:同工作树禁止并行 Maven;演示前端用 `-EntryMode replay` 会重建前端(~40 秒)。

## 五、未验证事项与已知问题(如实,勿当已通过)

1. **6 个数据库初始化错误**:自始至终未验证,未触碰。
2. **在线质量(T08 正式)/T09/T10**:未做,不宣称。回执信任链仍是 SELF_REPORTED;签名控制器仍被 ISOLATION_REQUIRED 阻塞。
3. **单题验证**:真实链路只验证了「选修课重修」一题(两批租约);多题/并发/长会话未验证。
4. **核价窗口**:两批包价格核对 2026-10-25 到期;**下次在线验收前必须按流程重新核价构包**(`Prepare` → 审阅 request-package → 新 SHA 授权)。每批历史上界约 ¥8、≤10 次调用、不可退。
5. 模拟演示启动器(`start-rule-agent-simulated-demo.ps1`)被 `docs/preparation/m1-04a/identity.json` 钉死在历史源码 SHA;本次改动后其 `Assert-Rows` 会拒绝,需重新生成 identity 才能用(未处理)。
6. 提交时 `safeguard-refs.ps1` 钩子报错(脚本在 worktree 内存在、钩子按主检出路径找,找不到):无害,提交均成功,未处理。
7. 4 个含未提交内容的工作树(见 3.3)待人工决定收编或废弃;另有旧 stash `stash@{0}: On zhkuagent-modules: WB-1 状态板纪律纠正登记`(**不要**随手 pop,它属于别的分支)。
8. 外层 `D:\alldesk\Projects\ZhkuAgent\docs` 是迁移残缺副本(缺 verbatim-import-draft.json 等),已从所有默认路径移除依赖,但目录本身未清理。

## 六、关键路径速查

| 用途 | 路径 |
| --- | --- |
| 在线验收流程 | `knowledge/scripts/start-rule-agent-http-check.cmd -Action Prepare/CheckPackage/StartApproved`(StartApproved 需 env `ZHKU_HTTP_APPROVED_PACKAGE_SHA256=<包SHA>`) |
| 一条命令演示(修完待验) | `knowledge/scripts/start-student-replay-demo.cmd` |
| 回放后端 | `knowledge/scripts/start-controlled-rule-check.cmd`(密码:交互或 env `ZHKU_RULECHECK_STUDENT_PASSWORD`) |
| 前端 | `agent/frontend/start-nginx-preview.cmd -BackendPort <端口> [-WebPort 58080] [-EntryMode replay]` |
| 在线证据 | `docs/evidence/rule-agent-integration/2026-10-25/`(两批租约 + 裸参数包) |
| 学生页 API 前缀 | `/api/zhku/controlled-preview/v2`;登录账号合成:`t06-chain-a`(在线租约)/`controlled-2025-student`(回放),密码均为启动时设定 |
| 前端 node_modules | 已 `npm ci` 安装(含 esbuild 手动 postinstall),dist-student/dist 已构建 |

## 七、原则备忘(用户本轮确立)

- 每轮必须产出可运行能力或可验证产品结果,不产出下一轮计划文档。
- 工程治理服务于产品交付,治理本身不再扩张。
- 真实外发(模型调用)必须走审包-授权流程;失败不重试不伪造;证据如实记录。
