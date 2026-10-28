# E13 标准构建与最终收尾验证（2026-10-28 20:28 - 20:35）

对象：已完成的 E13「资料不足/实时事项」终态修复。HEAD `0a440f12e448484f9affc2dce73c99d0f1098b88`（main），未推送、未创建 Worktree。

## 1. 标准前端构建（真实子进程权限）

| 命令（`agent/frontend`） | 退出码 | 结果 |
| --- | --- | --- |
| `npm run build` | 0 | `dist/` 生成，`RuleCheckView` 产物含 `UNSUPPORTED_DEMAND` 映射（见 `std-build.log`） |
| `node tests/rule-task-page.test.mjs` | 0 | 19 通过 / 0 失败（`std-page.log`） |
| `node tests/rule-agent-contract-parity.test.mjs` | 0 | 2 通过 / 0 失败（`std-parity.log`） |
| `node tests/rule-agent-contract.test.mjs` | 0 | 45 通过 / 0 失败（`std-contract.log`） |

同一命令在默认沙箱内会因禁止 node 创建子进程而报 `spawn EPERM`（原始记录见相邻 `e13-crosslayer/frontend-build.log`）；本轮在放宽到正常子进程权限后执行，属于标准构建，不是替代构建。

## 2. Java 离线回归

`cd knowledge && mvn -o -DforkCount=0 -Dtest='RuleAgentE13*,RulePublication*' test`

- Tests run: 39，Failures: 0，Errors: 0，Skipped: 0；**exit 0**（`java-regression.log`）
- 分组：`RuleAgentE13ArchivedReviewRegressionTest` 4、`RuleAgentE13InsufficientMaterialTerminalTest` 3、`RulePublicationEligibilityTest` 10、`RulePublicationGateTest` 15、`RulePublicationSpanExcerptTest` 7

## 3. 页面验证（标准构建产物）

- 宿主：`RuleAgentTaskBrowserHttpTest`（回环夹具，`providerCalls=0`、`databaseCalls=0`），端口 18089；`browser-ready.json` 为本轮生成。
- 页面：`knowledge/target/e13-crosslayer/serve.mjs` 同源托管 `agent/frontend/dist` 并代理 `/api` 到 18089；用 DSH 内置浏览器打开 `/rules`，登录 `student-a`。
- 提问「今晚补选几点关？帮我补上。」→「仍有未确认项 / 资料缺口」，文案明确“无法确认今晚补选几点关闭”，下一步说明系统无办理权限并指向教务系统/教务处；无臆造时间、无已代办表述、无空白或整页解析失败（`page-insufficient-material.png`）。
- 对照「故障」→「本轮处理失败 / 处理未完成 / 模型处理失败 / 稍后重新查证」，技术失败未被写成资料不足（`page-technical-failure.png`）。

## 4. 进程清理（本轮实际回执）

- `browser-host-exit.json`（本轮 20:33 生成）：`backendListenerExited=true`、`workerExited=true`、`activeJobs=0`、`providerCalls=0`、`databaseCalls=0`。
- 静态代理与浏览器会话已停止；脚本确认 18089/18090 无监听。

## 5. 未纳入

- 未调用真实模型或 Embedding，未运行在线 E13。
- `docs/product/campus-rule-capability-baseline-and-gaps.md` 属于上一任务产物，本轮不改动、不纳入本次提交。
