> **状态更正（2026-10-16 22:57）**：本页表格中"T05 未通过"属当时状态。T05 当前判定为 **`T05_ENGINEERING_ACCEPTED`**（工程门禁通过并收口，`1c270c4`；**未宣告**质量/可靠性/学生收益/上线，**未合入主仓库**）。索引各行的证据身份与"待验"标注**不受影响**。

# T07 学生使用流程证据索引（student-entry-01）

**用途**：把已交付的学生使用流程逐项对到**现有**证据与源码身份。**本轮只整理证据**，不追加页面开发、不重启宿主、不重跑浏览器。

## 0. 证据类型图例与身份口径

| 标签 | 含义 |
| --- | --- |
| 单测 | `node:test` 直调工作流/模块（无 DOM） |
| SSR | vite `ssrLoadModule` + `renderToString` 渲染学生页 |
| 真实浏览器 | 系统 Edge 实机加载构建产物 |
| 模拟后端 | 浏览器下的后端为模拟/替身宿主（非真实 Java + 专用临时库） |
| 真实身份·资料 | 真实受控账号登录 + 真实审核资料/原文服务 |
| 在线供应商 | 真实模型供应商调用 |

**两组源码身份必须分开看**（这是本轮最重要的口径）：

| 代号 | 提交 | 说明 |
| --- | --- | --- |
| **S-A** | `09b4f07a9f1492dd3a506086d98f9839363d408f` | T06 提交，**早于本轮 T07 实现**。`reviewed-tools-01` 的 16 态真实浏览器证据绑定在此身份。 |
| **S-B** | `94dc928151efd96f08ff99f1280ee1871b05907a`（树 `8ca021a7`） | 本轮 T07 检查点。单测/SSR/构建/路由浏览器检查绑定在此身份。 |

S-B 的浏览器检查**只覆盖路由与入口**，不覆盖交互流程；交互流程的真实浏览器证据仍只有 S-A（本轮未重跑）。冻结源码指纹见 `baseline.json` / `source-manifest.json` 与第 5 节。

---

## 1. 提问、异步进度、必要澄清、同任务续答、部分答案与缺口、处理失败、取消受理／最终停止

| 流程项 | 证据 | 标签 | 结论范围 |
| --- | --- | --- | --- |
| 提问受理后进入异步进度 | `tests/rule-task-workflow.test.mjs`「create returns immediately then polling stops on published snapshot」 | 单测 | S-B：POST 返回 202 票据后立即轮询，快照 `RESOLVED` 时停止 |
| 异步进度（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `async-running`（"问题已受理，正在查证。"＋"停止本次查证"） | 真实浏览器 / 模拟后端 | S-A |
| 必要澄清（至多一项条件） | `tests/rule-task-page.test.mjs`「student presents shared partial-and-clarification」＋ `partial-and-clarification` 样例 | SSR | S-B：渲染 `需要补充条件`、条件问题与允许值 |
| 必要澄清（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `clarification` | 真实浏览器 / 模拟后端 | S-A |
| 同任务续答（携带 continuationToken/expectedVersion） | `tests/rule-task-workflow.test.mjs`「same-task clarification uses nonce/version; new run accepts safe old result」 | 单测 | S-B：`sent[0]===taskId`、token 与 version 取自快照 |
| 同任务续答（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `continued-answer`（diff 显示"有依据可确认"＋相关依据折叠） | 真实浏览器 / 模拟后端 | S-A |
| 部分答案与缺口共存 | `tests/rule-task-page.test.mjs`「student presents shared partial-and-clarification／partial-and-data-gap」 | SSR | S-B |
| 部分答案与缺口（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `partial-answer-and-gap`＋截图 `browser/partial.png` | 真实浏览器 / 模拟后端 | S-A |
| 处理失败并保留已支持部分 | `tests/rule-task-page.test.mjs`「partial answers, processing failure, missing data and replay have separate labels」＋「frozen T05 processing reasons…preserve safe partial content」 | SSR | S-B：`处理未完成/资料缺口/回放未覆盖/需要补充条件` 标签互不混同 |
| 处理失败（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `processing-failure-with-supported-partial` | 真实浏览器 / 模拟后端 | S-A |
| 取消＝受理而非已退出 | `tests/rule-task-workflow.test.mjs`「cancel accepted is not terminal and continues polling until backend termination」 | 单测 | S-B：`cancelRequested=true` 后 `polling` 仍为 true，直到快照 `STOPPED` |
| 取消受理 / 最终停止（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `cancellation-requested`（"停止请求已受理，等待本轮结束。"）、`cancelled-final`（"本轮已停止"） | 真实浏览器 / 模拟后端 | S-A |
| 查询故障不丢任务 | `tests/rule-task-workflow.test.mjs`「query failure keeps accepted task and does not repeat POST」 | 单测 | S-B |
| 未知受理按原键查询 | `tests/rule-task-workflow.test.mjs`「lost create acknowledgement recovers by original request key only」 | 单测 | S-B |
| 接口层（真实 HTTP 往返） | `reviewed-tools-01/browser/http-exchanges.json`（67 条实际请求/响应） | 真实浏览器 / 模拟后端 | S-A |
| 真实身份／资料 | — | 真实身份·资料 | **待验**（S-A/S-B 均为测试身份＋合成资料） |
| 在线供应商 | — | 在线供应商 | **不适用/待验**：本轮 `realProviderAttempts=0`，T05 未通过 |

---

## 2. “能确认什么—还缺什么—下一步”顺序、依据关系、默认折叠、当前原文核对

| 要求 | 证据 | 标签 | 结论范围 |
| --- | --- | --- | --- |
| 先确认→再缺口/下一步→后依据 | `tests/rule-task-page.test.mjs` 断言 `html.indexOf('能确认') < html.indexOf('依据')`，以及「rules are escaped and only related evidence is rendered after explanations」中 `能确认 < 相关依据` | SSR | S-B：**顺序断言**，非人工目视 |
| 展示顺序（真实页面） | `reviewed-tools-01/browser/browser-dom.json` 的 `clarification` / `partial-answer-and-gap` / `processing-failure-with-supported-partial` 三态节点树顺序：`当前能确认什么` → `还缺什么，下一步怎么办` → `相关依据` | 真实浏览器 / 模拟后端 | S-A |
| 依据只显示与结果关联的证据 | `RuleCheckView.vue` 的 `relatedEvidence` 按 `evidenceRelations` 过滤；SSR 用例只对**样例声明的** claim/issue 文本断言 | SSR + 源码 | S-B |
| 依据关系来自后端结构 | 合同消费校验 `api/rule-agent-contract.js`（只读，`ebbe9bd4…7ecd`）；不校验不复用为事实判定 | 单测（`rule-agent-contract.test.mjs`） | S-B：字段/关系完整性，**不含事实是否真支持主张** |
| 原文默认折叠 | `tests/rule-task-page.test.mjs` 断言 `doesNotMatch(html, /<details[^>]*open/)`；`RuleCheckView.vue` 相关依据与原文均用 `<details>`（无 `open`） | SSR + 源码 | S-B |
| 原文默认折叠（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `original-current-collapsed`（`button (collapsed) 展开本条原文与必要上下文`） | 真实浏览器 / 模拟后端 | S-A |
| 当前原文核对（点"核对原文"取当前上下文） | `tests/rule-task-workflow.test.mjs`「late original and dispose cannot retain old identity content」；`rule-task-workflow.js` `openOriginal` 校验 token 属于当前结果 | 单测 + 源码 | S-B：请求与竞态控制；**未**验证原文内容真实性 |
| 原文核对（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `continued-answer`（"查看相关段落 · 2025.9.2"）与 `original-current-collapsed`（版本/页码/原件地址记录情况） | 真实浏览器 / 模拟后端 | S-A |
| 真实身份·资料下的原文服务 | — | 真实身份·资料 | **待验**（S-A 的原文地址为"暂无已验证安全的原件地址"） |

---

## 3. 两个实际实验入口在学生／工程模式中的行为

**入口的权威定义**：`/campus/run`（学分实验）与 `/super-agent`（通用助手实验），出自 `Home.vue` 的 `aria-label="实验入口"` 导航与 `RuleCheckReplayView.vue` 的"查证导航"。

| 场景 | 证据 | 标签 | 结论范围 |
| --- | --- | --- | --- |
| 工程路由表保留两个入口与底层能力 | `tests/entry-routes.test.mjs`「engineering entry table keeps both experiment entries…」 | 单测 | S-B：`/super-agent`→`Agent.vue`、`/campus`→`ControlledLayout.vue` 及 `run/history/conversation`、`/campus/login`、`/engineering/rules-replay`、`/rules`、`/`→`Home.vue` |
| 工程模式两个入口可达（路由解析） | `tests/entry-routes.test.mjs`「engineering router resolves both experiment entries…」 | 单测 | S-B |
| 工程页面仍声明两个实验入口导航 | `tests/entry-routes.test.mjs`「experiment entry navigation is still declared…」（读 `Home.vue` / `RuleCheckReplayView.vue` 源码） | 单测（源码文本） | S-B |
| 学生直接访问两个入口回查证 | `tests/entry-routes.test.mjs`「student direct access to either experiment entry returns to rule check」；`tests/student-routes.test.mjs`（含 `entry=rules` 断言） | 单测 | S-B：`/campus/run`、`/super-agent` 等 → `/rules?entry=rules` |
| 学生表不含工程视图 | `tests/entry-routes.test.mjs`「student table never reaches an engineering page」 | 单测 | S-B |
| **真实浏览器（学生模式）** | `browser-entry-routes.json` 5 例：`/`、`/rules` → `/rules`；`/campus/run`、`/super-agent`、`/engineering/rules-replay` → `/rules?entry=rules`，且无 `学分实验/通用助手实验/学分任务/任务助手/工程回放` 泄漏 | 真实浏览器（模拟后端） | **S-B**，仅路由/文案，不含交互 |
| **真实浏览器（工程模式）** | `browser-entry-routes.json` 5 例：`/` 含两个实验入口链接；`/super-agent` 渲染"任务助手"；`/campus/run` → `/campus/login`；`/engineering/rules-replay`、`/rules` 可达 | 真实浏览器（模拟后端） | **S-B** |
| 早前记录中"第二入口"的代表性 | `reviewed-tools-01/browser/browser-dom.json` → `student-experiment-direct-route`、`student-second-experiment-direct-route` | 真实浏览器 / 模拟后端 | **S-A 且不代表第二入口**：该例用未知路由（`love-master`）代替，T06 回执已自认不能代表；**已被 S-B 的 10 例取代** |
| 真实身份·资料下的两个入口 | — | 真实身份·资料 | **待验**（工程 `/campus/run` 仅验证到"重定向登录页"，未验证登录后能力） |

---

## 4. 同窗口／跨窗口身份失效、迟到响应保护、会话订阅与工作流销毁边界

| 关注点 | 证据 | 标签 | 结论范围 |
| --- | --- | --- | --- |
| 同窗口：编辑问题后旧依据不再对应 | `RuleCheckView.vue` `watch(question, …)` → `flow.reset()` | 源码 | S-B（无独立用例） |
| 同窗口：迟到轮询/原文不复活旧结果 | `tests/rule-task-workflow.test.mjs`「old polling success cannot repopulate next question or identity」（覆盖 `reset()` 与 `session.clear()` 两条路径）、「late original and dispose cannot retain old identity content」 | 单测 | S-B |
| 同窗口：查询故障保留任务不误判失败 | `tests/rule-task-workflow.test.mjs`「snapshot identity and version conflicts are rejected without inventing task failure」 | 单测 | S-B |
| 同窗口（真实页面） | `reviewed-tools-01/browser/browser-dom.json` → `logout-during-poll`、`identity-switch-clears-old-results`、`final-logout`、`brand-return-and-late-old-result-hidden` | 真实浏览器 / 模拟后端 | S-A |
| 跨窗口：另一窗口换代即撤销本窗口结果 | `tests/identity-bus.test.mjs`「the other window drops published results and a late response cannot restore them」 | 单测 | S-B：`actor=null`、`epoch+1`、`snapshot=null`、`ticket=null`，迟到快照不写回 |
| 跨窗口：一次换代只广播一条、接收端不回声 | `tests/identity-bus.test.mjs`「local identity change is broadcast once and the receiving window never echoes」 | 单测 | S-B |
| 跨窗口：页面提示并回到登录态 | `tests/rule-task-page.test.mjs`「another window changing identity is explained and restores no old result」 | SSR | S-B |
| 跨窗口：**真实两窗口/跨标签页** | — | 真实浏览器 + 模拟后端 | **待验**（需稳定宿主与 T06 可登录装配） |
| 广播的语义边界 | `rule-check-session.js:17` 订阅回调**不接收参数**；`state.actor` 非空写入点仅 `api/rule-check.js:137`（`/auth/me`）与 `api/controlled.js:75`；`clear` 清空 `actor/checked/csrf` | 源码（结构事实） | S-B：广播**只撤销展示**，身份必须由后端确认；**无对应测试** |
| 通道不可用时降级 | `tests/identity-bus.test.mjs`「a session without a document degrades to single-window behaviour instead of failing」 | 单测 | S-B：`available=false`、`publish` 不抛、`subscribe` 返回函数 |
| 通道不可用时整页仍可用（间接） | `rule-task-page.test.mjs` 全部用例（含共享样例逐条展开的用例组）在 `document` 未定义、无通道环境下通过；该文件加载学生页 → 学生会话模块（模块级 `bindIdentityBus()` 在无通道时降级） | SSR | S-B |
| （旁证，非学生页） | `rule-check-page.test.mjs` 渲染的是**工程回放页** `RuleCheckReplayView.vue`（`rule-check-runtime`），不经过学生会话模块，**不能用作学生页证据** | SSR | S-B，与 T07 学生页无关 |
| **会话订阅的销毁边界** | `bindIdentityBus()` 在 `rule-check-session.js` **模块作用域**执行，订阅挂在模块实例上，**不随 Vue 组件 mount/unmount 变化**；产品路径上没有组件级订阅，也就没有组件级退订 | 源码 | S-B：**不能表述为"与页面组件同生命周期"**；实际边界是"模块实例（JS 上下文）存在期间" |
| 销毁后的行为 | `identity-bus.test.mjs` 与 `rule-task-page.test.mjs` 只在用例收尾调用 `dispose()` / `flow.dispose()` **用于清理替身**，**没有**断言 dispose 之后广播是否仍生效、`publish` 是否仍投递、epoch 变化是否仍触发 reset | — | **待验**（按实际验证范围：仅有"可调用不抛错"，无"已静默"的证明） |

---

## 5. 冻结源码指纹（S-B = `94dc928`，树 `8ca021a7`）

| 文件 | SHA256 |
| --- | --- |
| `agent/frontend/src/views/RuleCheckView.vue` | `b3b30aadd79d7863e7ba84207731aef73caa807ae7a8d256fa4bacf175737c8a` |
| `agent/frontend/src/state/rule-task-workflow.js` | `c930552cb27026014e81b31ffcaf1cb3f396c01957f0780f7abc4eff81721af0` |
| `agent/frontend/src/state/identity-bus.js` | `92cccb17f2dcbb0344ff2e3b058d8404e7561ae591c0e67d73aa9d8f41ec3e15` |
| `agent/frontend/src/state/rule-check-session.js` | `543de077d5246f1124205d62fabb67b4cdf666a0ccc0fea796f86d68a48220dd` |
| `agent/frontend/src/state/controlled-session.js` | `1cf183ad2f1b4fb5da4c3f9718fd429af248d61b2f311e60f5c05f9d66702855` |
| `agent/frontend/src/router/index.js` | `2a2829c57618e5340f21e1112e2661760db4352fa178f75c87004c6a13461fc5` |
| `agent/frontend/src/router/engineering-routes.js` | `b2e80d3e1141f18f68ebeba47537be19302dd2b98e1e4d31394ab0e6d3c8c858` |
| `agent/frontend/src/router/student-routes.js` | `a93edadd0d88b9d518305bd19657b08bfcd17a39f6247a86ec0cb61caf8b22d6` |
| `agent/frontend/src/api/rule-check.js` | `0d55c4181a63982b845992b086f56132f644bb03579aa3035d04ae8471434722` |
| `agent/frontend/src/api/rule-task.js` | `c3fd64ca6713ee60067d63cebe2a41da496957f7dd313f060693a16537d01ab9` |
| `agent/frontend/src/api/rule-agent-contract.js`（只读） | `ebbe9bd4bdf3489402641a039bc274d6db1bdb7ec652aea5d13b040c088e7ecd` |
| `agent/frontend/tests/rule-task-page.test.mjs` | `cb7f826bd521a6e3dd0d34b80fc95c916d53a9a86c4449933734adc13bf9d0dd` |
| `agent/frontend/tests/rule-task-workflow.test.mjs` | `223a1e0f31d4451825ce34c6f6cf23861191cedd0616da2dba22c378d6da7085` |
| `agent/frontend/tests/rule-check-page.test.mjs` | `340d1b05a87d1578055c93bfa9475d51e2c1d50788799c653a218430439827dc` |
| `agent/frontend/tests/identity-bus.test.mjs` | `4f2a52dbecedbd62f7e94b703098a8df80e36d189fdccb2d7260945451547d40` |
| `agent/frontend/tests/entry-routes.test.mjs` | `e6cb33ce19d73619eb1245b279c308b60469f7f024a3b9afd5b9b36d43244db1` |
| `agent/frontend/tests/student-routes.test.mjs` | `a13d899c9b1837630a32533ed6fdab4a8a563815770cac75601b3477c326d82c` |
| `agent/frontend/tests/rule-agent-contract.test.mjs` | `afaed210eb92ee3db2eecec16976c615c4321c041779a8c12e58fc21c861db47` |

---

## 6. 三条本地结果（分别成立，不合并）

| 结果 | 数量 | 绑定身份 | 覆盖范围 |
| --- | --- | --- | --- |
| 前端回归 | 176/176（基线 167 + 新增 9） | S-B | 单测 + SSR |
| 两种入口模式构建 | 学生 / `VITE_ENTRY_MODE=engineering` 均 exit 0 | S-B | 产物结构与资产集合一致（24 个） |
| 路由浏览器检查 | 10/10（系统 Edge） | S-B | **仅路由与入口文案**，模拟后端 |

三者**不能**合并表述为真实在线整链通过：真实身份／资料、正式 nginx、在线供应商、真实两窗口均未覆盖。T05 未通过 → T07 标"工程局部通过，待真实集成"，不进入 T08。
