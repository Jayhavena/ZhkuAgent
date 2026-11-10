# C3 有限恢复修复

用户已明确选择：同一标签页保存最近 5 个任务的账号绑定元数据，刷新后重新向后端鉴权取快照；不保存问题正文、原文或澄清 Token。退出/换账号清理；服务器重启与跨设备历史不支持。

修前真实浏览器证据位于本地 student-trial-20261110/g2-browser-01/C3-refresh-red.json：同会话任务 GET 200，登录恢复成功，但刷新后答案入口消失且没有自动 POST。根因是 workflow ticket/snapshot 仅存在 Vue 内存，页面挂载没有恢复入口。没有把空页面当作验收通过。

修复在 sessionStorage 中只记录账号与 taskId/runId/turnId/version/recordedAt，最多 5 条，约 15 分钟过期；严格白名单拒绝损坏缓存。恢复只 GET 当前账号可访问的 snapshot，重新取得服务端权限 Token；404/409 删除过期记录，网络失败保留入口但不重放 POST。最近查证入口说明有限范围；正文显示“已恢复的查证任务”，不伪造原问题。明确 logout 即便无当前身份也清缓存。

独立审查 `c3_recovery_review` 未发现阻断级问题；审查者仅只读审代码，未冒称跑测试。现有 readActor 身份变化会递增 epoch，迟到响应受原 guard 拒绝。可选 ownerId 额外 guard 留作非阻断加固，不扩大本次修复。

离线 Node 198 tests，198 PASS、0 FAIL/SKIP；Vite build 通过。新增测试覆盖 5 条边界：上限/过期/元数据字段、跨账号/损坏缓存、存储不可用、重载 GET-only、过期任务及迟到恢复不能跨身份。最初新增测试导入不存在模块失败仅为开发脚手架失败，不算有效业务 RED；业务 RED 来自上述真实浏览器复现。

本代码提交时浏览器 GREEN 复验仍待执行；不以 Node 或构建代替真实浏览器结果。后续复验会记录实际代码 SHA、构建/页面 asset 摘要、临时库与资源回收。无真实模型调用或供应商预算扩展。

## 会话失效二次修正

首次真实浏览器复验已通过 WAITING_USER 刷新、澄清续答、RESOLVED 刷新与最近两任务选择，均没有自动 POST。继续模拟真实 HTTP 会话失效时发现：旧结论已撤回，但缓存 2→null。根因为 clear 先写 epoch 再写 reason，flush:sync 的 epoch 回调读取上一轮 reason，误删本应保留的 AUTH_EXPIRED 元数据。

补强回归后得到有效断言 RED：预期保留 1 条元数据，实际为 0；同时要求过期任务用例确实发出一次 GET，避免原测试因缓存已空而空跑。修正为 epoch 只撤回显示，明确 LOGOUT/身份变化 reason 再清缓存，并在同页登录成功后调用恢复。独立 `c3_expiry_review` 只读复核无阻断；199 Node tests 与构建通过。最终真实浏览器复验仍另行记录，未把中间租约的 PASS 当作全部 C3 通过。
