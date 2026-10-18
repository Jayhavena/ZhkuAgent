# 学生浏览器验收：工具连接阻塞

候选：`4de322d20899cf793c130c97ee92186f93bd9e3e`。后端使用独立空临时库的真实身份仓储、2025.9.2 已审核资料与显式模拟决策，供应商调用 0。学生实际构建为同一 worktree 的 `agent/frontend/dist-student`。

本轮完成了后端显式租约和同源测试代理的准备，未完成任何浏览器行为验收。CUA 按要求恢复说明后，创建 IAB 标签立即返回 `Browser is not available: iab`；首次及根会话尝试打开浏览器后的唯一复查均返回 `apps=[]`、`browsers=[]`。根会话打开页面返回 queued，未恢复当前唤醒会话的浏览器连接。未使用其他浏览器路径。

因此首页/品牌、登录、提交/轮询、ASK/PUBLIC 续答、结果/原文、两标签退出撤回、迟到保护、部分覆盖、资料资格撤回 UI 全部保持未验证。没有创建标签、登录或提交任务；没有截图。本轮不能表述为真实浏览器、在线模型或最终 T06/T07 通过。

收到根会话指令后提前写入 STOP。代理执行退出码 0、PID 41036 已消失，16426 与 28841 的实际连接均被拒。宿主退出回执显示 workerExited=true、activeJobs=0、executions=0、providerCalls=0、explicitStopReceived=true。临时数据库容器由根会话的 owned-runner 负责后续回收。

证据：browser-ready.json、browser-proxy-ready.json、browser-tool-blocker.json、STOP、browser-proxy-exit.json、browser-host-exit.json、browser-resource-observation.json。源码保持冻结；本目录只记录本轮准备、失败与退出事实。
