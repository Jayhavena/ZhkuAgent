# 210519 浏览器批次：独立证据核对

只读核对 `browser-20261018-210519/browser-observations.json`、`selected-http-observations.json`、代理事件、租约身份、资格撤销回执及撤回截图。未运行 Maven、数据库、Provider 或 CUA，未改既有记录。

结论：未发现观察记录与传输证据错配。实际代理日志共 42 条事件；4 条节选均唯一匹配，method、path、status、receivedAt、deliveryAttemptAt、clientDisconnected 逐字段一致。

- 澄清续答任务 `6dd8e6ff-435b-449a-b8cf-868288472392` 保持同 task：ACTIVE v1 → WAITING_USER v2 → 续答受理 v3 → ACTIVE v3 → RESOLVED/COMPLETE v4。
- PARTIAL 任务 `c31be43b-4f12-4dd6-8014-76de30880576` 在 v2 发布部分结果；同一原文 token `2665a34c-0381-410b-9ba5-9589aeeaffbc` 于 `13:08:19.657Z` 返回 200，资格撤销后于 `13:08:35.827Z` 返回 409，正文为 `TASK_EXPIRED`。`review-revoked.json` 记录真实临时库资格变更；目视 `eligibility-withdrawn.jpg` 可见旧结果暂停提示，结果及原文内容已清空。
- 迟到任务 `63941a29-63b6-4b6c-9c7d-be515a9bed8d` 的 RESOLVED v2 响应于 `13:07:48.317Z` 被接收并暂扣；退出于 `13:08:00.801Z` 返回 204；`13:08:05.437Z` 才尝试交付，`clientDisconnected=true`。该时序与 root 观察的退出后结果未复活一致。

模式为 `SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS`，供应商调用为 0，不证明真实模型在线成功。`uiResult=PASS_LOCAL_ENGINEERING_REPLAY` 与 `overallResult=INTERRUPTED_BEFORE_RESOURCE_EXIT_RECEIPTS` 的区分准确；恢复时写 STOP 和后续回收检查不能回填为原租约正常退出或整体 PASS。

除已目视的撤回截图外，首页、登录、跨页撤回等 UI 行为采用记录中明确标注的 root 直接 CUA 观察；HTTP 事件只佐证传输与任务时序，本审阅未独立操作浏览器或将 HTTP 当作完整 UI 证明。
