# T07 学生入口与实验入口隔离：工程局部通过，真实集成待宿主

本轮起始 2026-10-14 18:51 +08（本时段上限 20 分钟）。独立分支 `codex/t07-student-entry`，基线 `88e57d68799d3a5996bb84346090a84040a88387`，独立 worktree，未共用其他会话目录/暂存区，未复制其改动。schemaVersion=3，共享权威合同、模型提示、事实/范围守卫、发布语义、TaskStore 与 T05 源码均未改动。真实供应商尝试 0、数据库调用 0。

## 本轮补齐的两个实际缺口

1. **两个实验入口按实际工程路由定义核对，而不是用未知路由代替。** 原浏览器证据用 `love-master`（未知路由）代表第二实验入口，回执已自认不能代表。实际定义来自 `Home.vue` 的 `aria-label="实验入口"` 导航：`/campus/run`（学分实验）与 `/super-agent`（通用助手实验）。工程路由表原先内联在 `router/index.js` 的三元表达式里，依赖 `import.meta.env`，Node 侧无法核验。现抽出 `src/router/engineering-routes.js`（与既有 `student-routes.js` 对称），行为不变、可独立核对。
2. **两窗口身份失效不恢复旧结果。** 身份是服务端 Cookie，同源窗口共用一份，而会话状态只在各窗口内存里；一个窗口退出/换身份后，另一个窗口仍会保留主体与已发布结果，直到下一次请求回来才可能发觉。新增 `src/state/identity-bus.js`（BroadcastChannel，注入式工厂便于测试），`rule-check-session.js` 把本地换代接到通道上：本地持有身份时的换代才广播，接收端走未包装的 `clear` 以免两窗口互相回声无限换代。工作流既有的 epoch 观察者随之清空 ticket/snapshot，迟到响应被 `current()` 守卫丢弃。学生页新增“登录状态已在其他窗口变更，请重新登录。”说明。`controlled-session.js` 未改，学分入口行为不受影响。

## 实际验证

- **前端回归 176/176 通过**（`node --test "agent/frontend/tests/*.test.mjs"`）。基线与本会话记录为 167 项，本轮新增 9 项（entry-routes 5、identity-bus 3、学生页 1），无既有用例回归。红/绿日志与完整回归日志见本目录。
- **两种入口模式均构建成功**：默认构建（学生）与 `VITE_ENTRY_MODE=engineering` 构建。学生产物资产名称集合与基线 `reviewed-tools-01/student-build.log` **逐项一致**（24 个：16 JS + 8 CSS，`diff` 为空），本轮路由拆分未改变打包结果；模块数 132→134，只多出本轮新增的 `engineering-routes.js` 与 `identity-bus.js`。构建日志已关色（`NO_COLOR=1`）以免 ANSI 干扰比对。
- **系统 Edge 实机核对 10/10 通过**（`browser-entry-routes.json`，playwright-core + `channel:'msedge'`，自有端口 8131/8132，零外部请求、零 pageerror）：
  - 学生构建：`/` 与 `/rules` → `/rules`；`/campus/run`、`/super-agent`、`/engineering/rules-replay` → `/rules?entry=rules`，页面只出现查证登录区，`学分实验/通用助手实验/学分任务/任务助手/工程回放` 均未泄漏。
  - 工程构建：`/` 同时含 `学分实验` 与 `通用助手实验`；`/super-agent` → `/super-agent` 渲染任务助手；`/campus/run` → `/campus/login`（底层能力保留且仍要求身份）；`/engineering/rules-replay` 与 `/rules` 可达。
- 浏览器输入仅测试身份/合成问题，未接真实供应商；`TEST_SIMULATION_NO_PROVIDER`。

## 发现但未修（超出本会话文件范围，需用户裁决）

学生构建**仍然打包** `Agent/CampusRun/CampusHistory/CampusConversation/ControlledLogin/ControlledLayout/Home/RuleCheckReplayView` 等工程 chunk。原因是 `import.meta.env.VITE_ENTRY_MODE === 'engineering'` 在构建期没有被静态折叠，三元两支都进入产物；**该现象在基线同样存在**（见基线 student-build.log），不是本轮引入。运行期路由仍由构建期的 `studentRoutes` 决定，学生无法访问工程页面；但“学生产物只含学生页与主包”的既有说法与事实不符。要真正裁剪需在 `vite.config.js` 用 `define` 做常量折叠或改为动态导入，属构建配置改动，不在本会话允许的文件清单内，故只登记。

## 合同与等待项

未修改共享权威合同，未修改 `agent/frontend/src/api/rule-agent-contract.js`、共享样例与 Java 权威定义（SHA 见 baseline.json）。**合同缺口 0 项**，无需用户协调增量。

等待依赖：① 验收宿主 Agent 的稳定提交与退出方式（浏览器验收卡点，本轮不绕过）；② T06 Agent 的明确提交，真实身份/资料验收才可开始；③ T05 仍未通过，本轮零真实供应商调用，不复用旧响应、不关闭额度。

未验证范围：跨窗口失效只用注入的通道替身做了单元级证明，**真实两窗口/跨标签页实机未跑**（需要可登录的宿主，本轮无此条件）；真实身份、真实资料、正式 nginx 代理、在线模型与同版本整链浏览器验收均未覆盖。T05 未通过，T07 只标工程局部通过待真实集成，不进入 T08。

## 来源与命令

```powershell
node --test "agent/frontend/tests/*.test.mjs"                       # 前端回归
npm run build                                                      # 学生入口产物 dist/
$env:VITE_ENTRY_MODE='engineering'; npm run build -- --outDir dist-engineering
# 浏览器：node t07-entry-routes-check.js <studentDist> <engineeringDist> <输出json>
```

dist/ 与 dist-engineering/ 均不提交，dist-engineering 已在取证后删除。
